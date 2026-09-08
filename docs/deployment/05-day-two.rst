Step 7 — Day two: running it
============================

Everything here happens on the server unless stated otherwise. Log in with
``ssh -i ~/.ssh/sharpen_vm ubuntu@<public-ip>`` and use the ``dc`` alias from :doc:`04-deploying` (or type
the full ``docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env`` each time).

Updating to a new version
-------------------------

Push your changes from IntelliJ to GitHub as usual, then on the server:

.. code-block:: bash

   cd ~/sharpen && git pull
   dc up -d --build app

Only the ``app`` container is rebuilt and replaced; PostgreSQL and Caddy keep running, the data is untouched.
The site is down for about thirty seconds while the new jar starts. Signed-in users are logged out (sessions
are in memory in the prototype; Release 1 moves them to the database).

If a database column was added, apply the change to PostgreSQL before starting the new version, for example
``dc exec db psql -U sharpen -c "ALTER TABLE person ADD COLUMN …"``. The app runs with ``ddl-auto=validate``
in production, so it refuses to start against a schema that does not match — deliberately, so a mismatch is
noticed at deploy time rather than as a runtime error later. (Flyway migrations are on the Release 1 list to
make this automatic.)

Looking at what is happening
----------------------------

.. list-table::
   :header-rows: 1
   :widths: 45 55

   * - Command
     - Shows
   * - ``dc ps``
     - The three containers and whether each is *Up*.
   * - ``dc logs -f app``
     - Live application log. Ctrl+C to stop following.
   * - ``dc logs --since 1h app | grep -i error``
     - Errors in the last hour.
   * - ``dc logs caddy | tail``
     - Certificate and request-level problems.
   * - ``docker stats --no-stream``
     - Memory and CPU per container. The app settles around 350–500 MB.
   * - ``df -h /``
     - Disk usage. ``docker system prune -f`` reclaims old images after several updates.
   * - ``https://…/api/v1/health``
     - ``{"status":"ok"}`` from the running app — handy for an uptime checker.
   * - ``https://…/admin/feedback``
     - Counters and the latest feedback messages (admin account only).

Restarting things
-----------------

.. code-block:: bash

   dc restart app          # just the application
   dc down && dc up -d     # stop and start all three; data is kept (it lives in volumes)
   sudo reboot             # the whole server; containers come back by themselves (restart: unless-stopped)

Backups
-------

PostgreSQL's data lives in a Docker volume on the server's disk. If the server is lost, so is the data,
unless a copy exists elsewhere. A nightly dump is one line:

.. code-block:: bash

   dc exec -T db pg_dump -U sharpen sharpen | gzip > ~/backups/sharpen-$(date +%F).sql.gz

Make it automatic with ``cron``, the server's scheduler. ``crontab -e`` opens a file; add one line
(``0 3 * * *`` means 03:00 every day, server time is UTC):

.. code-block:: text

   0 3 * * * mkdir -p ~/backups && cd ~/sharpen && docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env exec -T db pg_dump -U sharpen sharpen | gzip > ~/backups/sharpen-$(date +\%F).sql.gz && find ~/backups -mtime +14 -delete

And copy the latest dump somewhere off the server now and then — from your Mac:

.. code-block:: bash

   scp -i ~/.ssh/sharpen_vm "ubuntu@<public-ip>:~/backups/*.sql.gz" ~/Desktop/sharpen-backups/

To restore into a fresh database: ``gunzip -c sharpen-2026-09-10.sql.gz | dc exec -T db psql -U sharpen sharpen``.

Changing a setting
------------------

Edit ``deploy/.env`` on the server with ``nano``, then ``dc up -d`` — Compose notices the changed values and
recreates only the containers that use them. Two settings deserve a warning:

* **DB_PASSWORD** is baked into the database the first time it starts. Changing it in ``.env`` later makes the
  app unable to log in. Either change it in both places (``dc exec db psql -U sharpen -c "ALTER USER sharpen
  PASSWORD 'new'"`` then ``.env``), or leave it alone.
* **DOMAIN**: after changing it, update DuckDNS too, and Caddy fetches a new certificate on the next visit.

Keeping the domain pointed at the server
----------------------------------------

Oracle's public IP stays the same for the life of the instance, so a one-time DuckDNS update is enough. If you
ever recreate the server, update DuckDNS again. Optionally, let the server refresh DuckDNS itself every five
minutes (uses the token from the DuckDNS page):

.. code-block:: bash

   mkdir -p ~/duckdns && echo 'curl -s "https://www.duckdns.org/update?domains=sharpen-ai&token=YOUR-TOKEN&ip=" > ~/duckdns/last.txt' > ~/duckdns/update.sh
   chmod 700 ~/duckdns/update.sh
   (crontab -l 2>/dev/null; echo "*/5 * * * * ~/duckdns/update.sh") | crontab -

Keeping the server healthy
--------------------------

* **Security updates**: ``sudo apt-get update && sudo apt-get upgrade -y`` monthly; if it says *System restart
  required*, ``sudo reboot`` at a quiet moment.
* **Docker images**: ``dc pull db caddy && dc up -d`` picks up new PostgreSQL 16.x and Caddy 2.x builds.
* **Certificates** renew themselves; nothing to do.
* **Free-tier limits**: one A1 instance up to 4 cores / 24 GB, 200 GB block storage, 10 TB outbound traffic
  per month. Sharpen uses a fraction of each. The console's *Billing & Cost Management* page shows a zero
  bill; if it ever does not, an Always Free resource was created as a paid one — the instance page says
  *Always Free* next to the shape.

Starting over
-------------

To wipe the data and begin with an empty database (for example after testing with fake accounts), on the
server:

.. code-block:: bash

   dc down -v         # -v also deletes the volumes: database AND Caddy's certificates
   dc up -d

Caddy fetches a new certificate on the first visit. To also throw away the server itself, terminate the
instance in the console (*Compute → Instances → sharpen → More actions → Terminate*, tick *permanently delete
the boot volume*) — and remember that the retry script hunt starts again if you want a new one.
