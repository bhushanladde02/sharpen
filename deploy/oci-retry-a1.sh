#!/usr/bin/env bash
# Keep trying to create an Always Free Ampere (VM.Standard.A1.Flex) instance until Oracle has capacity.
#
# Why: Oracle's free ARM pool in popular regions is often full ("Out of capacity for shape VM.Standard.A1.Flex").
# Capacity frees up unpredictably; polling every couple of minutes across all availability domains gets most
# people an instance within hours. This never creates anything billable: the shape/size below is Always Free
# and a Free Tier tenancy rejects anything else anyway.
#
# One-time setup on your Mac:
#   brew install oci-cli
#   oci setup config          # tenancy OCID, user OCID, region us-ashburn-1; it generates an API key pair
#   # then Console → Profile (top-right) → My profile → API keys → Add API key → paste ~/.oci/oci_api_key_public.pem
#   oci iam region list       # should print regions, proving the CLI is authenticated
#
# Run:   bash deploy/oci-retry-a1.sh
# Stop:  Ctrl+C. Safe to re-run; it checks for an existing instance named $NAME first.
set -euo pipefail

NAME="${NAME:-sharpen}"
# Sizes to try, "ocpus:memoryGB". All are within the Always Free quota (4 OCPU / 24 GB total). Oracle frees
# capacity in uneven chunks, so a bigger request sometimes succeeds when a smaller one does not.
SIZES="${SIZES:-1:6 2:12 4:24}"
SUBNET_NAME="${SUBNET_NAME:-subnet-20260505-0014}"
SSH_PUB="${SSH_PUB:-$HOME/.ssh/id_rsa.pub}"
INTERVAL="${INTERVAL:-120}"          # seconds between attempts; keep >= 60 to stay polite
OS_VERSION="${OS_VERSION:-24.04}"

command -v oci >/dev/null || { echo "oci CLI not found: brew install oci-cli && oci setup config"; exit 1; }
command -v jq  >/dev/null || { echo "jq not found: brew install jq"; exit 1; }
[ -f "$SSH_PUB" ] || { echo "SSH public key not found at $SSH_PUB (set SSH_PUB=...)"; exit 1; }

TENANCY=$(grep -E '^tenancy' ~/.oci/config | head -1 | sed 's/.*= *//')
COMPARTMENT="${COMPARTMENT:-$TENANCY}"   # root compartment, same as the console default

echo "Looking up resources in compartment ${COMPARTMENT:0:30}..."
SUBNET_ID=$(oci network subnet list --compartment-id "$COMPARTMENT" --all \
  | jq -r --arg n "$SUBNET_NAME" '.data[] | select(."display-name"==$n) | .id' | head -1)
[ -n "$SUBNET_ID" ] || { echo "Subnet '$SUBNET_NAME' not found"; exit 1; }

IMAGE_ID=$(oci compute image list --compartment-id "$COMPARTMENT" \
  --operating-system "Canonical Ubuntu" --operating-system-version "$OS_VERSION" \
  --shape VM.Standard.A1.Flex --sort-by TIMECREATED --sort-order DESC \
  | jq -r '.data[] | select(."display-name" | test("aarch64")) | select(."display-name" | test("Minimal") | not) | .id' | head -1)
[ -n "$IMAGE_ID" ] || { echo "No Ubuntu $OS_VERSION aarch64 image for A1 found"; exit 1; }
echo "Image: $(oci compute image get --image-id "$IMAGE_ID" | jq -r '.data."display-name"')"

# Availability domains: ask identity; if that call is flaky (a fresh API key propagates unevenly), derive them
# from any existing instance's AD name, e.g. "Vgdy:US-ASHBURN-AD-1" -> AD-1..AD-3 with the same prefix.
ADS=()
while IFS= read -r ad; do [ -n "$ad" ] && ADS+=("$ad"); done < <(oci iam availability-domain list --compartment-id "$COMPARTMENT" 2>/dev/null | jq -r '.data[].name' 2>/dev/null || true)
if [ "${#ADS[@]}" -eq 0 ]; then
  sample=$(oci compute instance list --compartment-id "$COMPARTMENT" --all | jq -r '.data[0]."availability-domain" // empty')
  if [ -n "$sample" ]; then
    prefix="${sample%AD-*}"
    ADS=("${prefix}AD-1" "${prefix}AD-2" "${prefix}AD-3")
  fi
fi
[ "${#ADS[@]}" -gt 0 ] || { echo "Could not determine availability domains"; exit 1; }
echo "Availability domains: ${ADS[*]}"

existing=$(oci compute instance list --compartment-id "$COMPARTMENT" --display-name "$NAME" --lifecycle-state RUNNING \
  | jq -r '.data[0].id // empty')
if [ -n "$existing" ]; then echo "An instance named $NAME is already running: $existing"; exit 0; fi

attempt=0
while true; do
  for ad in "${ADS[@]}"; do for size in $SIZES; do
    OCPUS="${size%%:*}"; MEMORY_GB="${size##*:}"
    attempt=$((attempt + 1))
    printf '%s  attempt %d  %s  %s OCPU/%s GB ... ' "$(date '+%H:%M:%S')" "$attempt" "$ad" "$OCPUS" "$MEMORY_GB"
    if out=$(oci compute instance launch \
        --compartment-id "$COMPARTMENT" --availability-domain "$ad" \
        --display-name "$NAME" --shape VM.Standard.A1.Flex \
        --shape-config "{\"ocpus\": $OCPUS, \"memoryInGBs\": $MEMORY_GB}" \
        --image-id "$IMAGE_ID" --subnet-id "$SUBNET_ID" --assign-public-ip true \
        --ssh-authorized-keys-file "$SSH_PUB" 2>&1); then
      id=$(echo "$out" | jq -r '.data.id')
      echo "CREATED"
      echo "Instance $id — waiting for it to run..."
      for _ in $(seq 1 30); do
        state=$(oci compute instance get --instance-id "$id" | jq -r '.data."lifecycle-state"')
        [ "$state" = "RUNNING" ] && break
        sleep 10
      done
      ip=$(oci compute instance list-vnics --instance-id "$id" | jq -r '.data[0]."public-ip"')
      echo "Public IP: $ip"
      echo "Next: ssh ubuntu@$ip"
      exit 0
    fi
    if echo "$out" | grep -qi "out of capacity\|Out of host capacity"; then
      echo "out of capacity"; transient=0
    elif echo "$out" | grep -qiE "timed out|timeout|TooManyRequests|ServiceUnavailable|InternalError|Connection (reset|refused|aborted)|Max retries|status 5[0-9][0-9]"; then
      # Network hiccup or Oracle throttling: not our fault, just try again a bit later.
      transient=$((${transient:-0} + 1))
      echo "transient error ($transient in a row) — will retry"
      [ "$transient" -ge 10 ] && { echo "$out" | head -5; echo "Too many transient errors; check your network and re-run."; exit 1; }
      sleep 30
    else
      echo "error:"; echo "$out" | head -5
      echo "(not a capacity error — fix the cause before retrying)"; exit 1
    fi
    sleep 5
  done; done
  sleep "$INTERVAL"
done
