# Deploying Sharpen for free

Sharpen runs in production on one **Oracle Cloud Always Free** ARM VM as three Docker containers — PostgreSQL,
the app, and Caddy for automatic HTTPS — behind a free DuckDNS name. Zero monthly cost, real certificate,
one-command updates.

**The full, beginner-level walkthrough is in the docs:** `docs/deployment/` (build with `./docs/view.sh`, or
read the `.rst` files directly). It explains every term, every command and what to expect from each, plus
troubleshooting and day-two operations. This file is the short version for people who already know the tools.

## Files here

| File | Role |
|---|---|
| `docker-compose.prod.yml` | db (postgres:16) + app (built from `../Dockerfile`) + caddy (ports 80/443) |
| `Caddyfile` | `{$DOMAIN}` → automatic Let's Encrypt TLS, `reverse_proxy app:8080`, security headers |
| `.env.example` → `.env` | `DOMAIN`, `DB_PASSWORD`, `ADMIN_EMAIL`, `DEMO_DATA`. `.env` is git-ignored |
| `setup-vm.sh` | one-time VM prep: Docker, ufw 22/80/443, Oracle iptables fix |
| `oci-retry-a1.sh` | polls Oracle for a free `VM.Standard.A1.Flex` across all ADs and sizes until one is created |
| `oci-create-micro.sh` | Plan B: creates the always-available free `VM.Standard.E2.1.Micro` (1 GB) |
| `docker-compose.micro.yml` | overrides for a 1 GB machine (JVM 320 MB heap, small Postgres); on when `SMALL_VM=true` in `.env` |
| `remote-deploy.sh` | runs on the VM: pull `APP_IMAGE`, restart, wait for `/api/v1/health`, roll back on failure |
| `Dockerfile.ci` | pipeline image: JRE + prebuilt jar, built for arm64 and amd64 in seconds |

## Quick path

1. **Prepare once** — Oracle Free Tier account (home region Ashburn); SSH key pair (`~/.ssh/sharpen_vm[.pub]`);
   `brew install oci-cli jq && oci setup config` + register the API key under *My profile → API keys*;
   VCN default security list: ingress TCP 80 and 443 from `0.0.0.0/0`; a DuckDNS name; `cp .env.example .env`
   and fill it (`openssl rand -base64 24` for the password); repo pushed to GitHub.
2. **Get a server** — Console → Compute → Create instance: Ubuntu 24.04, `VM.Standard.A1.Flex` 1 OCPU / 6 GB,
   public IP, your `.pub` key. If *Out of capacity*: `SSH_PUB=~/.ssh/sharpen_vm.pub bash deploy/oci-retry-a1.sh`
   (better on an always-on box with `nohup … &`; `SIZES="1:6 2:12"` if your A1 quota is 2 cores). It prints
   `Public IP:` when done.
3. **DNS** — put the IP into DuckDNS; `dig +short <name>.duckdns.org` must return it.
4. **Prepare the VM**
   ```bash
   ssh -i ~/.ssh/sharpen_vm ubuntu@<ip>
   curl -fsSL https://raw.githubusercontent.com/bhushanladde02/sharpen/main/deploy/setup-vm.sh | bash
   exit   # log out so the docker group applies
   ```
5. **Deploy**
   ```bash
   ssh -i ~/.ssh/sharpen_vm ubuntu@<ip>
   git clone https://github.com/bhushanladde02/sharpen.git && cd sharpen
   # from your Mac: scp -i ~/.ssh/sharpen_vm deploy/.env ubuntu@<ip>:~/sharpen/deploy/.env
   docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env up -d --build
   docker compose -f deploy/docker-compose.prod.yml logs -f app   # wait for "Started SharpenApplication"
   ```
   First build is 5–10 min on A1. Caddy fetches the certificate on the first visit to `https://<DOMAIN>`.
6. **Check** — register with `ADMIN_EMAIL` first; that account can read `/admin/feedback`.

## Pipeline (CI/CD)

`.github/workflows/ci.yml` tests every push (unit + MockMvc, a boot-and-serve smoke test, docs build).
`deploy.yml` runs when CI passes on `main`: builds a multi-arch image, pushes it to `ghcr.io/bhushanladde02/sharpen`,
and — once the `production` environment has `DEPLOY_HOST` and `DEPLOY_SSH_KEY` secrets — rolls it out over SSH
via `remote-deploy.sh` with a health check and automatic rollback. `codeql.yml`, `release.yml` (tag `v*` → GitHub
Release with the jar) and `dependabot.yml` round it out. Setup and troubleshooting: `docs/deployment/07-ci-cd.rst`.

## Day two

```bash
alias dc='docker compose -f ~/sharpen/deploy/docker-compose.prod.yml --env-file ~/sharpen/deploy/.env'
cd ~/sharpen && git pull && dc up -d --build app      # update (db and caddy keep running)
dc exec -T db psql -U sharpen sharpen < src/main/resources/db/migrations/<file>.sql   # first, if the release added columns
dc ps · dc logs -f app · dc restart app · docker stats --no-stream
dc exec -T db pg_dump -U sharpen sharpen | gzip > ~/backups/sharpen-$(date +%F).sql.gz   # backup (cron it)
```

Live counters on every page come from `/api/v1/public/stats` (30 s refresh); `/api/v1/health` is open for
uptime checks. Keep `DEMO_DATA=false` on a public site.

## Alternatives if you cannot get Oracle ARM capacity

| Host | Free tier | Trade-off |
|---|---|---|
| Oracle `VM.Standard.E2.1.Micro` | always available, 1 GB RAM | supported: `oci-create-micro.sh`, `SMALL_VM=true`, deploy the pipeline image (`docs/deployment/08-small-vm.rst`) |
| Fly.io | small shared VM + 3 GB volume | Postgres is a separate app; usage-based above the allowance |
| Render | free web service + free Postgres (90 days) | sleeps after 15 min idle |
| Koyeb | one free nano instance | no free managed Postgres; use Neon/Supabase |
| Google Cloud Run | generous free requests | scales to zero, so the monthly scheduler will not fire |

For any of these the app is the same jar: set `SPRING_PROFILES_ACTIVE=postgres` and the three `SHARPEN_DB_*`
variables, apply `src/main/resources/db/schema-postgres.sql` once.
