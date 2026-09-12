Steps 3–6 — Putting Sharpen on the server
=========================================

You have a public IP (from the console or from the retry script's ``Public IP:`` line). Everything below takes
about twenty minutes. In the commands, replace ``<public-ip>`` with that number.

Step 3 — Point the name at the server
-------------------------------------

Open https://www.duckdns.org (signed in), find your name in the table, put ``<public-ip>`` into its
**current ip** box and click **update ip**. The *changed* column resets to "0 seconds ago".

Check that the internet agrees — on your Mac:

.. code-block:: bash

   dig +short sharpen-ai.duckdns.org      # prints the IP you just entered

If it prints nothing or an old value, wait a minute; DuckDNS updates are fast but not instant.

Step 4 — Prepare the server
---------------------------

Log in for the first time:

.. code-block:: bash

   ssh -i ~/.ssh/sharpen_vm ubuntu@<public-ip>

SSH asks *The authenticity of host … can't be established. Are you sure you want to continue connecting?* —
type ``yes``. It is remembering the server's identity so it can warn you if it ever changes. The prompt becomes
``ubuntu@sharpen:~$``: you are on the server. (``ubuntu`` is the user Canonical's image creates; the key you
uploaded at creation time is what lets you in.)

Run the one-time setup script straight from GitHub:

.. code-block:: bash

   curl -fsSL https://raw.githubusercontent.com/bhushanladde02/sharpen/main/deploy/setup-vm.sh | bash

It takes two or three minutes and does four things, all of which you could do by hand:

#. ``apt-get install`` a few basics (``git``, ``curl``, ``ufw``).
#. Installs **Docker** from Docker's own repository and adds ``ubuntu`` to the ``docker`` group so you can
   use it without ``sudo``.
#. Turns on **ufw**, the server's own firewall, allowing only SSH, 80 and 443 — the same three ports as the
   Oracle security list. Two firewalls, same rules; either alone would block, so both must agree.
#. Fixes an Oracle quirk: their Ubuntu image ships an ``iptables`` rule that rejects 80/443 even when ufw
   allows them. The script inserts accept rules ahead of it and saves them so they survive reboots.

When it prints *Done. Log out and back in*, do exactly that — group membership only applies to new logins:

.. code-block:: bash

   exit
   ssh -i ~/.ssh/sharpen_vm ubuntu@<public-ip>
   docker ps          # should print an empty table with a header, not "permission denied"

Step 5 — Deploy
---------------

Still on the server, fetch the code:

.. code-block:: bash

   git clone https://github.com/bhushanladde02/sharpen.git
   cd sharpen

Now the settings file. It is not in git (on purpose), so copy the one you prepared on your Mac. Open a
**second terminal tab on your Mac** and run:

.. code-block:: bash

   scp -i ~/.ssh/sharpen_vm ~/codebase/sharpen/deploy/.env ubuntu@<public-ip>:~/sharpen/deploy/.env

Back on the server, confirm it arrived and start everything:

.. code-block:: bash

   cat deploy/.env                          # DOMAIN, DB_PASSWORD, ADMIN_EMAIL, DEMO_DATA — four lines
   docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env up -d --build

What that command means: ``docker compose`` reads the description file (``-f``) and the settings
(``--env-file``); ``up`` creates and starts the three containers; ``-d`` ("detached") returns the prompt to
you while they keep running; ``--build`` compiles Sharpen first.

The first run downloads the PostgreSQL and Caddy images and builds the Sharpen image, which includes running
Maven inside Docker. On a 1-core ARM machine this takes **five to ten minutes**; the screen scrolls with
download progress and Maven output. It is finished when you see three lines like ``✔ Container deploy-db-1
Started``. Watch the application come up:

.. code-block:: bash

   docker compose -f deploy/docker-compose.prod.yml logs -f app

``logs -f`` follows the app's output live. The line you are waiting for is
``Started SharpenApplication in 12.3 seconds``. Press **Ctrl+C** to stop watching (the app keeps running).

.. tip::

   Save typing for every later command: ``alias dc='docker compose -f ~/sharpen/deploy/docker-compose.prod.yml
   --env-file ~/sharpen/deploy/.env'`` — put that line in ``~/.bashrc`` on the server and ``dc logs -f app``,
   ``dc ps``, ``dc restart app`` all work.

Step 6 — Check
--------------

On any device, open ``https://sharpenscore.com``. The **first** visit can take up to a minute: Caddy sees
the domain for the first time, asks Let's Encrypt for a certificate (Let's Encrypt connects back to port 80 to
verify you control the name — this is why 80 must be open too), installs it, and only then serves the page.
Refresh if the browser gives up early. After that it is instant, with the padlock.

What to verify:

* The landing page shows the live counters (members, sessions, reports) — all zero on a fresh database.
* **Register** with the ``ADMIN_EMAIL`` address from ``.env``. That account, and only that one, can open
  ``https://sharpenscore.com/admin/feedback``.
* Log a session, open the dashboard, generate a report, download its PDF. If all of that works on the server,
  the deployment is complete.

Congratulations — it is live. Send the link to a few people and read what they write on ``/feedback``.

If the page does not load
-------------------------

Work through these in order; each one rules something out.

#. **Is the server up?** ``ssh -i ~/.ssh/sharpen_vm ubuntu@<public-ip>`` works → yes.
#. **Are the containers running?** On the server, ``docker compose -f deploy/docker-compose.prod.yml ps``
   should show ``db``, ``app`` and ``caddy`` all *Up*. If ``app`` is restarting, ``… logs app | tail -50``
   shows why — usually a wrong ``DB_PASSWORD`` (the database was created with the first password it saw;
   see :doc:`05-day-two` to reset) or the JVM ran out of memory on a 1 GB machine.
#. **Does the app answer inside the server?** ``curl -s localhost:80 | head -3`` on the server: HTML → Caddy
   and the app are fine, the problem is outside (DNS or the Oracle firewall). Nothing or *connection
   refused* → Caddy is not up; ``… logs caddy``.
#. **Does the name resolve?** ``dig +short sharpen-ai.duckdns.org`` on your Mac must print the server's IP.
#. **Is port 80/443 open from outside?** ``nc -zv <public-ip> 443`` on your Mac. *Connection refused* or a
   timeout with the app running means a firewall: re-check the security list (:doc:`02-one-time-setup`, 1d)
   and run ``sudo ufw status`` and ``sudo iptables -L INPUT -n --line-numbers | head`` on the server — the
   ``ACCEPT`` lines for 80 and 443 must come *before* the ``REJECT … icmp-host-prohibited`` line.
#. **Certificate errors?** ``… logs caddy | grep -i -E "error|acme"``. The common cause is DNS pointing at
   the wrong IP when Caddy asked; fix DNS, then ``… restart caddy``.
