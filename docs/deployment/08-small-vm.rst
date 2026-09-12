Plan B — Launching on the 1 GB Micro
=====================================

When the Ampere A1 pool stays "out of capacity" for days, the other Always Free shape gets the site live
today: ``VM.Standard.E2.1.Micro`` — x86, 1 OCPU, **1 GB of memory**, two per tenancy, and practically never
short of capacity. 1 GB is tight for a JVM plus PostgreSQL, so three things are different from the A1 path:

* the image is **never built on the machine** (Maven would need more memory than exists) — it is pulled from
  GitHub's registry, where the pipeline already publishes an amd64 build of every commit;
* ``deploy/docker-compose.micro.yml`` shrinks the JVM (320 MB heap, serial GC, C1-only JIT) and PostgreSQL
  (32 MB shared buffers, 20 connections), and ``setup-vm.sh`` adds a 2 GB swap file;
* first page loads are a little slower. For a pilot with a handful of users that is fine.

Everything else — DNS, Caddy, HTTPS, the pipeline, backups — is identical, and moving to an A1 later is a
database dump and a DNS change (bottom of this page). Keep the A1 retry script running meanwhile.

1. Create the Micro
-------------------

From your Mac (the ``oci`` tool is already set up from :doc:`02-one-time-setup`):

.. code-block:: bash

   cd ~/codebase/sharpen
   SSH_PUB=~/.ssh/sharpen_vm.pub bash deploy/oci-create-micro.sh

It finds the Ubuntu 24.04 x86 image and your subnet, tries the availability domains in turn, and ends with
``Public IP: …``. Takes about a minute. (On 9 September 2026 it succeeded in AD-1 on the first attempt and
produced ``sharpen-micro`` at ``150.136.83.28`` — the full story is in :doc:`09-pilot-log`.) The instance is named ``sharpen-micro`` (so the A1 hunter, which looks
for one named ``sharpen``, keeps going). The console alternative is *Compute → Create instance* with shape
*Specialty and previous generation → VM.Standard.E2.1.Micro*, otherwise the same choices as :doc:`03-getting-a-server`.

2. Point the name, prepare the machine
--------------------------------------

Exactly Steps 3 and 4 of :doc:`04-deploying`: put the IP into DuckDNS — the **current ip** box, not the
*ipv6* one next to it (leave ipv6 empty) — click *update ip*, confirm with ``dig +short <name>.duckdns.org``,
then

.. code-block:: bash

   ssh -i ~/.ssh/sharpen_vm ubuntu@<public-ip>
   curl -fsSL https://raw.githubusercontent.com/bhushanladde02/sharpen/main/deploy/setup-vm.sh | bash
   exit

The script notices the 1 GB of RAM and prints *Added a 2 GB swap file*. Log back in; ``free -m`` shows a
``Swap:`` line with 2047.

3. Deploy the pipeline's image
------------------------------

On your Mac, add one line to ``deploy/.env`` so the deploy script picks the small-machine overrides:

.. code-block:: bash

   echo 'SMALL_VM=true' >> ~/codebase/sharpen/deploy/.env

Then, on the server, fetch the code and settings and start everything from the published image:

.. code-block:: bash

   ssh -i ~/.ssh/sharpen_vm ubuntu@<public-ip>
   git clone https://github.com/bhushanladde02/sharpen.git && cd sharpen
   # from your Mac in another tab:  scp -i ~/.ssh/sharpen_vm deploy/.env ubuntu@<public-ip>:~/sharpen/deploy/.env
   cat deploy/.env                                   # five lines, ending with SMALL_VM=true
   APP_IMAGE=ghcr.io/bhushanladde02/sharpen:latest bash deploy/remote-deploy.sh

``remote-deploy.sh`` pulls PostgreSQL, Caddy and the Sharpen image (about 400 MB in total, a minute or two),
starts the three containers, and polls ``/api/v1/health`` through Caddy. On the Micro the JVM needs 40–60
seconds to start, and the very first request also makes Caddy fetch the certificate; the script waits up to
three minutes. It ends with ``healthy : https://sharpen-ai.duckdns.org/api/v1/health`` and ``OK``.

Then :doc:`04-deploying` Step 6 — open the site, register the admin account — and :doc:`07-ci-cd`
"Turning the deploy stage on", so future merges roll out by themselves.

Watching memory
---------------

.. code-block:: bash

   free -m                      # used / free / swap on the machine
   docker stats --no-stream     # per container; app ~380 MB, db ~40 MB, caddy ~15 MB is normal

Some swap in use is expected and harmless. If ``app`` keeps restarting, ``dc logs app | tail`` will say
``OutOfMemoryError`` or the kernel log (``dmesg | grep -i kill``) will show an OOM kill — lower ``-Xmx`` in
``deploy/docker-compose.micro.yml`` by 32 MB and ``dc up -d app``. That has not been necessary in testing.

The manual ``dc`` alias on this machine needs both compose files:

.. code-block:: bash

   alias dc='docker compose -f ~/sharpen/deploy/docker-compose.prod.yml -f ~/sharpen/deploy/docker-compose.micro.yml --env-file ~/sharpen/deploy/.env'

Moving to an A1 later
---------------------

When the hunter finally prints an A1 ``Public IP:`` (it did, on 12 September — see :doc:`09-pilot-log`):

#. Prepare the A1: ``setup-vm.sh`` and ``git clone`` (Step 4 of :doc:`04-deploying`), then copy your
   ``deploy/.env`` across with ``SMALL_VM=false``. Do **not** start the app yet.
#. On the Micro, stop the app and dump the database (the site is down from here):
   ``dc stop app`` and ``dc exec -T db pg_dump -U sharpen sharpen | gzip > ~/sharpen.sql.gz``; copy the file
   to the A1 via your Mac (``scp`` down, ``scp`` up).
#. On the A1, start only the database and restore. The container applies ``schema-postgres.sql`` on its
   first start, so the tables already exist — **drop that empty schema first** or the restore collides with
   it (every ``CREATE`` fails and rows with foreign keys are rejected):

   .. code-block:: bash

      dc up -d db && sleep 10
      dc exec -T db psql -q -U sharpen sharpen -c 'drop schema public cascade; create schema public;'
      gunzip -c ~/sharpen.sql.gz | dc exec -T db psql -q -v ON_ERROR_STOP=1 -U sharpen sharpen
      dc exec -T db psql -U sharpen sharpen -c 'select count(*) from person'

#. Update DuckDNS to the A1's IP and wait for ``dig +short`` to agree.
#. ``APP_IMAGE=ghcr.io/bhushanladde02/sharpen:latest bash deploy/remote-deploy.sh`` on the A1. It applies any
   pending migrations, pulls the image, and Caddy fetches the certificate on the first health check.
#. Point the pipeline at the A1: ``DEPLOY_HOST`` in the ``production`` environment, and the deploy key's
   public half in the A1's ``authorized_keys``.
#. Leave the Micro stopped as a spare, or terminate it in the console — it is free either way.

Ten to fifteen minutes; users see a few minutes of downtime between the dump and the first health check.
