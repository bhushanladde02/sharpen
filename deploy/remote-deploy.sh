#!/usr/bin/env bash
# Runs ON THE PRODUCTION VM, called by .github/workflows/deploy.yml over SSH (or by hand):
#
#   APP_IMAGE=ghcr.io/bhushanladde02/sharpen:<tag> bash ~/sharpen/deploy/remote-deploy.sh
#
# Applies any database migration scripts that have not run yet (src/main/resources/db/migrations/*.sql, each
# recorded in a schema_migration table so it runs exactly once), pulls the given image, restarts the stack
# with it, waits until /api/v1/health answers through Caddy, and if it never does, puts the previous image
# back so the site is not left broken. Prints what it did.
set -euo pipefail

cd "$(dirname "$0")/.."                      # repository root on the VM (~/sharpen)
[ -n "${APP_IMAGE:-}" ] || { echo "APP_IMAGE is not set"; exit 2; }
[ -f deploy/.env ] || { echo "deploy/.env is missing on the VM"; exit 2; }
DOMAIN=$(grep -E '^DOMAIN=' deploy/.env | cut -d= -f2-)
FILES=(-f deploy/docker-compose.prod.yml)
# SMALL_VM=true in deploy/.env (a 1 GB Micro) adds the memory-trimmed overrides.
grep -qE '^SMALL_VM=true' deploy/.env && FILES+=(-f deploy/docker-compose.micro.yml)
dc() { docker compose "${FILES[@]}" --env-file deploy/.env "$@"; }

# --- database migrations, before the new version starts (it validates the schema on boot) ---------------
migrate() {
  local dir=src/main/resources/db/migrations
  [ -d "$dir" ] || return 0
  dc up -d db >/dev/null
  for i in $(seq 1 24); do dc exec -T db pg_isready -q -U sharpen && break; sleep 5; done
  local psql=(dc exec -T -e PGOPTIONS=-cclient_min_messages=warning db psql -q -v ON_ERROR_STOP=1 -U sharpen sharpen)
  "${psql[@]}" -c "create table if not exists schema_migration (name text primary key, applied_at timestamptz not null default now())" >/dev/null
  for f in "$dir"/*.sql; do
    [ -e "$f" ] || continue
    local name; name=$(basename "$f")
    if [ "$("${psql[@]}" -tA -c "select count(*) from schema_migration where name = '$name'")" = "1" ]; then
      continue
    fi
    echo "migration     : $name"
    "${psql[@]}" < "$f"
    "${psql[@]}" -c "insert into schema_migration (name) values ('$name')" >/dev/null
  done
}
migrate

previous=$(docker inspect --format '{{.Config.Image}}' "$(dc ps -q app 2>/dev/null)" 2>/dev/null || true)
echo "current image : ${previous:-<none>}"
echo "new image     : $APP_IMAGE"

health() {   # true when the app answers through Caddy; --resolve keeps the check local to the VM
  curl -fsS --max-time 5 -k --resolve "$DOMAIN:443:127.0.0.1" "https://$DOMAIN/api/v1/health" >/dev/null 2>&1
}

rollout() {  # $1 = image
  APP_IMAGE="$1" dc pull -q app
  APP_IMAGE="$1" dc up -d --no-build --remove-orphans
  for i in $(seq 1 36); do            # up to 3 minutes; the JVM needs ~20 s on A1, more on first start
    health && return 0
    sleep 5
  done
  return 1
}

if rollout "$APP_IMAGE"; then
  echo "healthy       : https://$DOMAIN/api/v1/health"
  docker image prune -f >/dev/null
  echo "OK"
else
  echo "!! $APP_IMAGE never became healthy. Last log lines:"
  dc logs --tail 40 app || true
  if [ -n "$previous" ] && [ "$previous" != "$APP_IMAGE" ]; then
    echo "!! rolling back to $previous"
    rollout "$previous" && echo "rolled back   : $previous (site is up on the previous version)" || echo "!! rollback also failed — investigate on the VM"
  fi
  exit 1
fi
