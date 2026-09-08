Step 1 — One-time preparation
=============================

Everything on this page is done once and never again: an Oracle account, a key to open the server, a
command-line tool to talk to Oracle, the network rules, and a domain name. Do them in order.

1a. An Oracle Cloud account
---------------------------

Go to https://www.oracle.com/cloud/free/ and create a Free Tier account. Oracle asks for a credit card to prove
you are a person; it is not charged for Always Free resources. During sign-up you pick a **home region** —
the data centre your servers live in. Ours is **US East (Ashburn)**. The region is shown at the top right of
the console and cannot be changed later, so pick one near your users.

After sign-up you land in the **console**, https://cloud.oracle.com — the web page where you manage
everything. The ☰ menu at the top left opens the service list; the two we use are **Compute → Instances**
(servers) and **Networking → Virtual cloud networks** (the network they sit in).

.. note::

   Oracle created a network for you automatically the first time you made an instance. Ours is called
   ``vcn-20260505-0014`` with a subnet ``subnet-20260505-0014``; yours will carry the date you made it. A
   *VCN* ("virtual cloud network") is a private network inside Oracle; a *subnet* is a slice of it that
   servers attach to. You never need to change them, only to open two ports (1d below).

1b. An SSH key pair
-------------------

The server will accept commands only from whoever holds the private half of a key pair. Oracle can generate
one for you on the *Create instance* page ("Generate a key pair for me" → download both files), which is what
we did; the files were saved as ``ssh-key-2026-09-08.key`` (private) and ``.key.pub`` (public). Keep them
somewhere permanent and give them a clear name:

.. code-block:: bash

   mkdir -p ~/.ssh
   cp ~/Downloads/ssh-key-2026-09-08.key      ~/.ssh/sharpen_vm
   cp ~/Downloads/ssh-key-2026-09-08.key.pub  ~/.ssh/sharpen_vm.pub
   chmod 600 ~/.ssh/sharpen_vm          # private keys must be readable by you only, or ssh refuses them
   ls -l ~/.ssh/sharpen_vm*

``~/.ssh`` is the folder where SSH looks for keys; ``~`` is your home folder (``/Users/<you>``). If you
prefer to make a key yourself instead: ``ssh-keygen -t ed25519 -f ~/.ssh/sharpen_vm -N ""`` creates the same
two files.

.. warning::

   The ``.key`` / ``sharpen_vm`` file without ``.pub`` is the secret. Never paste it anywhere, never commit
   it, never email it. The ``.pub`` file is harmless — it is meant to be given to servers.

1c. The OCI command-line tool
-----------------------------

Everything the console does with clicks can also be done by typing, using Oracle's ``oci`` tool. We need it
for one reason: the retry script in :doc:`03-getting-a-server`, which creates the server for us when Oracle
finally has capacity. The tool needs its own key (an *API key*, different from the SSH key) so Oracle knows the
requests come from you.

Install it and run the setup wizard:

.. code-block:: bash

   brew install oci-cli jq        # jq is a small helper that reads the tool's JSON output
   oci setup config

The wizard asks, in order:

#. *Location of config* — press Enter to accept ``~/.oci/config``.
#. *User OCID* — in the console click your profile picture (top right) → **My profile**; the OCID is shown
   on that page with a *Copy* link. An OCID is Oracle's long id string starting ``ocid1.user.oc1..``.
#. *Tenancy OCID* — profile picture → **Tenancy: <name>**; copy the OCID shown there (``ocid1.tenancy.oc1..``).
#. *Region* — type ``us-ashburn-1`` (or pick its number from the list).
#. *Generate a new API signing key pair?* — **Y**. Accept the defaults for directory and name. This creates
   ``~/.oci/oci_api_key.pem`` (private) and ``~/.oci/oci_api_key_public.pem`` (public).

Now tell Oracle about that key. In the console: profile picture → **My profile** → **API keys** (left side)
→ **Add API key** → **Paste a public key** → paste the contents of ``~/.oci/oci_api_key_public.pem`` (print it
with ``cat ~/.oci/oci_api_key_public.pem``) → **Add**. Oracle shows a *fingerprint* like
``ab:12:cd:…``; the wizard already wrote the same fingerprint into ``~/.oci/config``. If the two differ (for
example because you generated the key twice), edit the file so ``fingerprint=`` matches what the console shows.

Prove it works — the tool should print a list of regions instead of an error:

.. code-block:: bash

   oci iam region list | head -5

.. tip::

   A brand-new API key takes a minute or two to become valid everywhere, so ``NotAuthenticated`` right after
   adding it is normal. Wait two minutes and try again. If it still fails, check that ``key_file=`` in
   ``~/.oci/config`` points at the private key that matches the fingerprint you uploaded, and that the file's
   permissions are ``600`` (``chmod 600 ~/.oci/*``).

1d. Open ports 80 and 443 in the network
----------------------------------------

Oracle's network blocks everything except SSH by default. Web traffic needs two more doors.

Console → ☰ → **Networking** → **Virtual cloud networks** → click your VCN → **Security Lists** (left side) →
**Default Security List for vcn-…** → **Security rules** tab → **Add Ingress Rules**. An *ingress* rule
describes traffic allowed *in*. Fill in:

.. list-table::
   :header-rows: 1
   :widths: 25 25 25 25

   * - Source CIDR
     - IP protocol
     - Destination port range
     - Description
   * - ``0.0.0.0/0``
     - TCP
     - ``80``
     - HTTP
   * - ``0.0.0.0/0``
     - TCP
     - ``443``
     - HTTPS

``0.0.0.0/0`` means "from anywhere on the internet", which is what a public website wants. Use **+ Another
Ingress Rule** to add the second one on the same form, then click **Add Ingress Rules** at the bottom. The
list should now show three TCP rules: 22, 80 and 443. Leave the ICMP rules alone; they are Oracle's defaults.

This is done once per VCN, not per server — a future server in the same network inherits it.

1e. A free domain name at DuckDNS
---------------------------------

#. Open https://www.duckdns.org and sign in with Google (your Gmail).
#. In the **sub domain** box type the name you want — we used ``sharpen-ai`` — and click **add domain**.
#. It appears in the table as ``sharpen-ai`` with a **current ip** box. DuckDNS fills that with *your
   laptop's* IP at first; ignore it. We come back to this page in :doc:`04-deploying` to put the server's
   IP there.

You may add up to five names. If yours is taken, pick another (``sharpenai``, ``sharpen-app``) and remember
to use the same name in ``deploy/.env`` later.

.. warning::

   The page also shows a **token** (a long code like ``a1b2c3d4-…``). It is the password for changing where
   your names point. Do not share screenshots of that page.

1f. The settings file
---------------------

On your Mac, in the project folder, create the file the server will need and fill it in now so it is ready:

.. code-block:: bash

   cd ~/codebase/sharpen
   cp deploy/.env.example deploy/.env
   NEW=$(openssl rand -base64 24 | tr -d '/+=')            # a long random password
   sed -i '' "s|^DB_PASSWORD=.*|DB_PASSWORD=$NEW|" deploy/.env
   nano deploy/.env

``nano`` is a simple text editor that runs inside the terminal. Use the arrow keys to move, type to edit,
then **Ctrl+O**, **Enter** to save and **Ctrl+X** to leave. The file should look like this (with your values):

.. code-block:: text

   DOMAIN=sharpen-ai.duckdns.org
   DB_PASSWORD=x7Qm…                      # whatever openssl generated; you never type it anywhere
   ADMIN_EMAIL=you@example.com                 # the account that may read /admin/feedback
   DEMO_DATA=false                            # true would create demo@sharpen.io etc. — not for a public site

``git status`` must **not** list ``deploy/.env``; it is ignored on purpose. Only ``deploy/.env.example``,
which contains no secrets, is in the repository.

1g. The code on GitHub
----------------------

The server downloads Sharpen from GitHub, so the repository must be pushed and, for the simplest setup,
**public**. Check at https://github.com/bhushanladde02/sharpen that the README shows and the latest commit is
there. If it is private, either make it public (*Settings → Danger zone → Change visibility*) or plan to use a
personal access token on the server; public is fine for a pilot whose value is the running service, not the
code.

Checklist before moving on
--------------------------

* ``oci iam region list`` prints regions.
* ``ls ~/.ssh/sharpen_vm.pub`` exists.
* The security list shows TCP 22, 80, 443.
* Your DuckDNS name exists.
* ``deploy/.env`` has the same domain, a long random password, your email, and ``DEMO_DATA=false``.
* The GitHub repository is up to date and public.
