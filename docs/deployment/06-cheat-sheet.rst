Cheat sheet
===========

Everything from this chapter on one page, for when you already understand it and just need the command.
``<ip>`` is the Sharpen server's public IP; ``<bot-vm-ip>`` and ``K`` are the address and SSH key of the always-on machine that runs the retry script.

.. list-table::
   :header-rows: 1
   :widths: 34 66

   * - I want to…
     - Command (Mac unless marked *server*)
   * - see whether the retry script found a server
     - ``ssh -i "$K" ubuntu@<bot-vm-ip> 'tail -3 retry.log'``
   * - start the retry script on the bot VM
     - ``ssh -i "$K" ubuntu@<bot-vm-ip> 'export PATH=$HOME/ocienv/bin:$PATH; SIZES="1:6 2:12" SSH_PUB=~/sharpen_vm.pub nohup bash ~/oci-retry-a1.sh > ~/retry.log 2>&1 &'``
   * - stop it
     - ``ssh -i "$K" ubuntu@<bot-vm-ip> 'pkill -f oci-retry-a1.sh'``
   * - run it on my Mac instead
     - ``SSH_PUB=~/.ssh/sharpen_vm.pub caffeinate -i bash deploy/oci-retry-a1.sh``
   * - check my A1 quota
     - ``oci limits resource-availability get --service-name compute --limit-name standard-a1-core-count --compartment-id <tenancy-ocid> --availability-domain RUzw:US-ASHBURN-AD-1``
   * - create the 1 GB Micro instead (Plan B)
     - ``SSH_PUB=~/.ssh/sharpen_vm.pub bash deploy/oci-create-micro.sh`` then add ``SMALL_VM=true`` to ``deploy/.env``
   * - log in to the Sharpen server
     - ``ssh -i ~/.ssh/sharpen_vm ubuntu@<ip>``
   * - copy the settings file to it
     - ``scp -i ~/.ssh/sharpen_vm deploy/.env ubuntu@<ip>:~/sharpen/deploy/.env``
   * - first-time server setup (*server*)
     - ``curl -fsSL https://raw.githubusercontent.com/bhushanladde02/sharpen/main/deploy/setup-vm.sh | bash`` then log out and in
   * - deploy for the first time (*server*)
     - ``git clone https://github.com/bhushanladde02/sharpen.git && cd sharpen`` → copy ``.env`` → ``docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env up -d --build``
   * - update to the latest code
     - merge to ``main`` — the pipeline deploys it (:doc:`07-ci-cd`); by hand on the *server*: ``cd ~/sharpen && git pull && dc up -d --build app``
   * - deploy a specific image by hand (*server*)
     - ``APP_IMAGE=ghcr.io/bhushanladde02/sharpen:<tag> bash ~/sharpen/deploy/remote-deploy.sh``
   * - see the app log (*server*)
     - ``dc logs -f app``
   * - see what is running (*server*)
     - ``dc ps`` · ``docker stats --no-stream``
   * - restart (*server*)
     - ``dc restart app`` · ``dc down && dc up -d`` · ``sudo reboot``
   * - back up the database (*server*)
     - ``dc exec -T db pg_dump -U sharpen sharpen | gzip > ~/backups/sharpen-$(date +%F).sql.gz``
   * - check DNS
     - ``dig +short sharpenscore.com`` · ``dig +short sharpen-ai.duckdns.org`` (old name, must still point here)
   * - check the redirects (old name and www → 301 to the site)
     - ``for u in http://sharpen-ai.duckdns.org/ https://sharpen-ai.duckdns.org/ https://www.sharpenscore.com/; do curl -s -o /dev/null -w "$u %{http_code} -> %{redirect_url}\n" $u; done``
   * - reload Caddy after editing ``deploy/caddy-extra/*.caddy`` (*server*)
     - ``dc exec caddy caddy reload --config /etc/caddy/Caddyfile``
   * - check a port from outside
     - ``nc -zv <ip> 443``
   * - open the admin inbox
     - ``https://sharpenscore.com/admin/feedback`` (signed in as ``ADMIN_EMAIL``)
   * - health check URL
     - ``https://sharpenscore.com/api/v1/health``

``dc`` is the alias ``docker compose -f ~/sharpen/deploy/docker-compose.prod.yml --env-file ~/sharpen/deploy/.env``
defined in ``~/.bashrc`` on the server.

Files that matter
-----------------

.. list-table::
   :header-rows: 1
   :widths: 40 60

   * - File
     - Role
   * - ``deploy/docker-compose.prod.yml``
     - Describes the three containers (db, app, caddy), their ports, volumes and environment.
   * - ``deploy/Caddyfile``
     - Caddy's two-line config: serve ``$DOMAIN`` with automatic HTTPS, forward to ``app:8080``, add security headers.
   * - ``deploy/.env.example`` → ``deploy/.env``
     - The four settings. ``.env`` is git-ignored; ``.env.example`` is the template.
   * - ``deploy/setup-vm.sh``
     - One-time server preparation: Docker, ufw, Oracle iptables fix.
   * - ``deploy/oci-retry-a1.sh``
     - Polls Oracle for a free ARM server until one is created.
   * - ``deploy/oci-create-micro.sh``, ``deploy/docker-compose.micro.yml``
     - Plan B: create the free 1 GB Micro; memory-trimmed overrides applied when ``SMALL_VM=true``.
   * - ``deploy/remote-deploy.sh``, ``deploy/Dockerfile.ci``
     - The VM-side rollout script (pull, restart, health check, rollback) and the pipeline's image recipe.
   * - ``.github/workflows/*.yml``, ``.github/dependabot.yml``
     - CI (test, smoke, docs), Deploy (image to GHCR, SSH rollout), CodeQL, Release, Dependabot.
   * - ``Dockerfile`` (project root)
     - Two-stage build: Maven compiles the jar, a slim JRE image runs it.
   * - ``src/main/resources/db/schema-postgres.sql``
     - Mounted into the db container; PostgreSQL runs it once when the volume is first created.
   * - ``src/main/resources/application-postgres.yml``
     - The production profile: reads ``SHARPEN_DB_*`` and ``SHARPEN_ADMIN_EMAIL`` from the environment.
   * - ``~/.oci/config`` + ``~/.oci/*.pem`` (Mac and bot VM)
     - Credentials for the ``oci`` tool. Never commit.
   * - ``~/.ssh/sharpen_vm`` (Mac)
     - Private key that opens the Sharpen server. Never commit or share.
