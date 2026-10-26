#!/usr/bin/env bash
# Runs ON THE VM every five minutes from cron (docs/deployment/05-day-two.rst, "Keeping Sharpen up"):
#
#   */5 * * * * bash ~/sharpen/deploy/watchdog.sh
#
# Asks /api/v1/health through Caddy, exactly as a visitor would. One failure is checked again after 20 seconds
# (a deploy in progress looks like one); two in a row mean the app is hung or dead, so it is restarted and the
# site is watched until it answers again — at most once per 15 minutes, so a broken build cannot be restarted
# in a loop. Everything it does goes to ~/watchdog.log, which also gets a line when memory or disk is under
# 10 %. Docker's restart policy already covers a crashed process; this covers a hung one, and the VM-down
# case needs an outside monitor (same doc). Prints nothing when all is well, so cron sends no mail.
set -uo pipefail

cd "$(dirname "$0")/.." || exit 2                        # ~/sharpen
LOG=~/watchdog.log
STAMP=~/.sharpen-watchdog-restart                        # mtime = last restart
DOMAIN=$(grep -E '^DOMAIN=' deploy/.env | cut -d= -f2-)
FILES=(-f deploy/docker-compose.prod.yml)
grep -qE '^SMALL_VM=true' deploy/.env && FILES+=(-f deploy/docker-compose.micro.yml)
dc() { docker compose "${FILES[@]}" --env-file deploy/.env "$@"; }
say() { printf '%s %s\n' "$(date -u +%FT%TZ)" "$*" >> "$LOG"; }

health() { curl -fsS --max-time 8 -k --resolve "$DOMAIN:443:127.0.0.1" "https://$DOMAIN/api/v1/health" >/dev/null 2>&1; }

# --- resources: a warning line, nothing else ------------------------------------------------------------
avail=$(awk '/MemAvailable/ {print int($2/1024)}' /proc/meminfo); total=$(awk '/MemTotal/ {print int($2/1024)}' /proc/meminfo)
[ "$avail" -lt $((total / 10)) ] && say "warn: memory low — ${avail} MB of ${total} MB available ($(docker stats --no-stream --format '{{.Name}} {{.MemUsage}}' 2>/dev/null | tr '\n' ';'))"
disk=$(df --output=pcent / | tail -1 | tr -dc '0-9')
[ "$disk" -gt 90 ] && say "warn: disk ${disk}% full on /"

# --- the site ---------------------------------------------------------------------------------------------
health && exit 0
sleep 20
health && exit 0

if [ -f "$STAMP" ] && [ "$(( $(date +%s) - $(stat -c %Y "$STAMP") ))" -lt 900 ]; then
  say "down: health failed twice; restarted less than 15 minutes ago, leaving it alone (app: $(docker inspect --format '{{.State.Status}}/{{.State.Health.Status}}' "$(dc ps -q app 2>/dev/null)" 2>/dev/null))"
  exit 1
fi
touch "$STAMP"
say "down: health failed twice (app: $(docker inspect --format '{{.State.Status}}/{{.State.Health.Status}}' "$(dc ps -q app 2>/dev/null)" 2>/dev/null)); restarting app"
dc restart app >/dev/null 2>&1 || dc up -d --no-deps app >/dev/null 2>&1
for i in $(seq 1 24); do                                 # up to two minutes
  sleep 5
  if health; then say "up: answering again after $((i * 5)) s"; exit 0; fi
done
say "still down after restart — last app log lines: $(dc logs --tail 15 app 2>&1 | tr '\n' ' ' | cut -c1-1500)"
exit 1
