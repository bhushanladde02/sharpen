#!/usr/bin/env bash
# Create an Always Free VM.Standard.E2.1.Micro instance (x86, 1 OCPU, 1 GB) — the free shape that is almost
# always available, unlike the Ampere A1. Same setup as oci-retry-a1.sh: oci CLI + jq configured on this machine.
#
#   SSH_PUB=~/.ssh/sharpen_vm.pub bash deploy/oci-create-micro.sh
#
# Prints the public IP when the instance is RUNNING. Safe to re-run: exits if an instance named $NAME exists.
set -euo pipefail

NAME="${NAME:-sharpen-micro}"
SUBNET_NAME="${SUBNET_NAME:-subnet-20260505-0014}"
SSH_PUB="${SSH_PUB:-$HOME/.ssh/id_rsa.pub}"
OS_VERSION="${OS_VERSION:-24.04}"
SHAPE="VM.Standard.E2.1.Micro"

command -v oci >/dev/null || { echo "oci CLI not found: brew install oci-cli && oci setup config"; exit 1; }
command -v jq  >/dev/null || { echo "jq not found: brew install jq"; exit 1; }
[ -f "$SSH_PUB" ] || { echo "SSH public key not found at $SSH_PUB (set SSH_PUB=...)"; exit 1; }

TENANCY=$(grep -E '^tenancy' ~/.oci/config | head -1 | sed 's/.*= *//')
COMPARTMENT="${COMPARTMENT:-$TENANCY}"

existing=$(oci compute instance list --compartment-id "$COMPARTMENT" --display-name "$NAME" --all \
  | jq -r '.data[] | select(."lifecycle-state" != "TERMINATED") | .id' | head -1)
if [ -n "$existing" ]; then
  ip=$(oci compute instance list-vnics --instance-id "$existing" | jq -r '.data[0]."public-ip"')
  echo "An instance named $NAME already exists: $existing"; echo "Public IP: $ip"; exit 0
fi

SUBNET_ID=$(oci network subnet list --compartment-id "$COMPARTMENT" --all \
  | jq -r --arg n "$SUBNET_NAME" '.data[] | select(."display-name"==$n) | .id' | head -1)
[ -n "$SUBNET_ID" ] || { echo "Subnet '$SUBNET_NAME' not found"; exit 1; }

IMAGE_ID=$(oci compute image list --compartment-id "$COMPARTMENT" \
  --operating-system "Canonical Ubuntu" --operating-system-version "$OS_VERSION" \
  --shape "$SHAPE" --sort-by TIMECREATED --sort-order DESC \
  | jq -r '.data[] | select(."display-name" | test("aarch64") | not) | select(."display-name" | test("Minimal") | not) | .id' | head -1)
[ -n "$IMAGE_ID" ] || { echo "No Ubuntu $OS_VERSION x86 image for $SHAPE found"; exit 1; }
echo "Image: $(oci compute image get --image-id "$IMAGE_ID" | jq -r '.data."display-name"')"

# Any AD works for the Micro; use the one the subnet/other instances are in, falling back to AD-1..3.
ADS=()
while IFS= read -r ad; do [ -n "$ad" ] && ADS+=("$ad"); done < <(oci iam availability-domain list --compartment-id "$COMPARTMENT" 2>/dev/null | jq -r '.data[].name' 2>/dev/null || true)
if [ "${#ADS[@]}" -eq 0 ]; then
  sample=$(oci compute instance list --compartment-id "$COMPARTMENT" --all | jq -r '.data[0]."availability-domain" // empty')
  [ -n "$sample" ] && { prefix="${sample%AD-*}"; ADS=("${prefix}AD-1" "${prefix}AD-2" "${prefix}AD-3"); }
fi
[ "${#ADS[@]}" -gt 0 ] || { echo "Could not determine availability domains"; exit 1; }

for ad in "${ADS[@]}"; do
  printf '%s  %s  %s ... ' "$(date '+%H:%M:%S')" "$ad" "$SHAPE"
  if out=$(oci compute instance launch \
      --compartment-id "$COMPARTMENT" --availability-domain "$ad" \
      --display-name "$NAME" --shape "$SHAPE" \
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
    echo "Next: ssh -i ${SSH_PUB%.pub} ubuntu@$ip"
    exit 0
  fi
  if echo "$out" | grep -qi "out of capacity\|Out of host capacity"; then echo "out of capacity — trying the next AD"
  else echo "error:"; echo "$out" | head -8; exit 1; fi
done
echo "No AD had Micro capacity right now (rare) — run again in a few minutes."; exit 1
