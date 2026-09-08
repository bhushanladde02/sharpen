# Deploying Sharpen for free

Goal: a public URL people can try, at zero monthly cost, with real HTTPS, that you can update with one
command. The recommended path is **Oracle Cloud Always Free** (you already run a workload there), which gives a
permanent VM large enough for PostgreSQL + the app. Everything below also works on any Ubuntu VM.

## What you need

| Item | Free option | Notes |
|---|---|---|
| VM | Oracle Cloud Always Free — Ampere A1, up to 4 OCPU / 24 GB (shape `VM.Standard.A1.Flex`), Ubuntu 24.04 | 1 OCPU / 6 GB is plenty. Capacity in some regions is scarce; retry creation or pick another AD. |
| Domain | a subdomain of one you own, or a free `*.duckdns.org` name pointed at the VM's public IP | Caddy needs a real name to get a certificate. Free-tier DNS: DuckDNS; a paid `.dev`/`.io` is ~$10–15/yr. |
| TLS | Let's Encrypt via Caddy | automatic, renews itself |
| Database | PostgreSQL in Docker on the same VM | data in a Docker volume; back it up (below) |

## If Oracle says "Out of capacity for shape VM.Standard.A1.Flex"

The free ARM pool in busy regions is often full. It is not a billing block; capacity comes and goes. Try each
availability domain (AD-1/2/3) from the console; if all fail, let `deploy/oci-retry-a1.sh` poll every two minutes
across all ADs until one succeeds (one-time setup: `brew install oci-cli jq && oci setup config`, then add the
generated public key under *Profile → API keys*). It prints the public IP when done. Off-peak hours (early morning
US Eastern, weekends) have the best odds.

## Steps

1. **Create the VM** in the Oracle console: Compute → Instances → Create. Image *Canonical Ubuntu 24.04*, shape
   *Ampere A1 Flex* (1 OCPU, 6 GB), add your SSH key. Note the public IP.
2. **Open ports 80 and 443** in the VCN: Networking → Virtual Cloud Networks → your VCN → Security Lists → Default →
   *Add Ingress Rules*: source `0.0.0.0/0`, TCP, destination port `80`; repeat for `443`.
3. **Point DNS** at the IP: an `A` record for `sharpen.yourdomain.com` (or create a DuckDNS name).
4. **SSH in and prepare the VM**:

   ```bash
   ssh ubuntu@<public-ip>
   curl -fsSL https://raw.githubusercontent.com/bhushanladde02/sharpen/main/deploy/setup-vm.sh | bash
   exit   # log out so the docker group applies
   ```

5. **Deploy**:

   ```bash
   ssh ubuntu@<public-ip>
   git clone https://github.com/bhushanladde02/sharpen.git && cd sharpen
   cp deploy/.env.example deploy/.env && nano deploy/.env    # DOMAIN, DB_PASSWORD, ADMIN_EMAIL
   docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env up -d --build
   docker compose -f deploy/docker-compose.prod.yml logs -f app   # wait for "Started SharpenApplication"
   ```

   The first build compiles the jar inside Docker (a few minutes on A1). Caddy fetches the certificate on the
   first request to `https://<DOMAIN>`.

6. **Register your own account first** with the `ADMIN_EMAIL` address — that account can open
   `/admin/feedback` to read what people write.

## Updating

```bash
cd ~/sharpen && git pull
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env up -d --build app
```

Postgres keeps running; only the app container is rebuilt. Users are not logged out unless the app restarts
during their session (it does — sessions are in-memory; Release 1 can move them to the database).

## Backups

```bash
docker compose -f deploy/docker-compose.prod.yml exec db pg_dump -U sharpen sharpen | gzip > sharpen-$(date +%F).sql.gz
```

Run it from cron nightly and copy the file off the VM (`scp`, or Oracle Object Storage which is also free-tier).

## Before you share the link

* `DEMO_DATA=false` (the default in `.env.example`) so demo accounts are not created in production.
* The README and login page mention demo credentials — those only exist when demo data is seeded, so they are
  harmless, but remove the note from `login.html` if you prefer.
* Company registration is open. If you want to control who sees profiles, set it aside for the pilot
  (see the Release 1 checklist) or simply tell early users the company view is for demo purposes.
* Watch the counters: the landing page and every signed-in page show live member and session counts from
  `/api/v1/public/stats`, refreshed every 30 seconds; `/admin/feedback` shows the same numbers plus messages.

## Alternatives if Oracle capacity is unavailable

| Host | Free tier | Trade-off |
|---|---|---|
| Fly.io | small allowance for a shared VM + 3 GB volume | Postgres needs a separate app; usage-based billing above the allowance |
| Render | free web service + free Postgres (expires after 90 days) | service sleeps after 15 min idle; first visit is slow |
| Koyeb | one free nano instance | no free managed Postgres; use Neon/Supabase free Postgres |
| Google Cloud Run | generous free requests | scales to zero, so the monthly scheduler will not fire; needs Cloud SQL ($) or Neon |

For any of these, the app is the same jar; set `SPRING_PROFILES_ACTIVE=postgres` and the three `SHARPEN_DB_*`
variables to the hosted database and apply `src/main/resources/db/schema-postgres.sql` once.
