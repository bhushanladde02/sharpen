Step 2 — Getting a server
=========================

This is the one step that Oracle, not you, controls the timing of. Try the console first (two minutes); if it
says *out of capacity*, hand the job to the retry script and get on with your day.

2a. Try the console
-------------------

Console → ☰ → **Compute** → **Instances** → **Create instance**. The form is long; only these fields matter,
everything else stays at its default.

.. list-table::
   :header-rows: 1
   :widths: 22 78

   * - Field
     - What to choose
   * - **Name**
     - ``sharpen``. (The retry script looks for this exact name to know whether a server already exists.)
   * - **Placement**
     - Any availability domain, AD-1, AD-2 or AD-3. An *availability domain* is one building in the region;
       capacity differs between them, so if one is full try the others.
   * - **Image**
     - Click *Change image* → **Canonical Ubuntu** → version **24.04**. Ubuntu is the Linux flavour our setup
       script expects.
   * - **Shape**
     - Click *Change shape* → **Ampere** → ``VM.Standard.A1.Flex``. Set **1** OCPU and **6** GB memory (2 and
       12 also work; both are inside the free 4/24 quota). The form shows *Always Free-eligible* next to the
       shape — if it does not, you picked a paid one.
   * - **Networking**
     - *Select existing virtual cloud network* → your VCN and its public subnet. **Assign a public IPv4
       address** must be *on*, or nobody can reach the server.
   * - **Add SSH keys**
     - *Upload public key file* → choose ``~/.ssh/sharpen_vm.pub``. (Press **⌘⇧.** in the file dialog to see
       hidden folders like ``.ssh``.) Or *Paste public key* and paste the one line from that file.
   * - **Boot volume**
     - Default (about 47 GB). Always Free allows 200 GB in total across all volumes.

Click **Create**. One of two things happens:

* The instance page appears with a yellow *PROVISIONING* box that turns green *RUNNING* after a minute. Copy
  the **Public IP address** shown on that page and go to :doc:`04-deploying`.
* A red error: *Out of capacity for shape VM.Standard.A1.Flex in availability domain …*. This is not about
  your account or money; Oracle simply has no free ARM machine spare in that building right now. Try the
  other two ADs from the Placement section. If all three fail, continue below.

.. note::

   Only the ARM shape suffers from this. The free tier also includes two tiny x86 machines
   (``VM.Standard.E2.1.Micro``, 1 GB memory) that are almost always available. 1 GB is tight but workable
   with a trimmed configuration — :doc:`08-small-vm` launches on one today while the A1 hunt continues.

2b. Let the retry script wait for you
-------------------------------------

``deploy/oci-retry-a1.sh`` asks Oracle to create the server every couple of minutes, in every availability
domain, at every size your quota allows, until one succeeds. It then waits for the server to boot and prints
its public IP. It cannot create anything billable: the shape is Always Free and a Free Tier account rejects
anything else.

**What it does, in plain words:** look up your subnet and the newest Ubuntu 24.04 ARM image; list the ADs; make
sure no server called ``sharpen`` exists already; then loop — for each AD, for each size — asking Oracle to
launch. *Out of capacity* → say so and move on. Network hiccup → wait 30 seconds and retry. *LimitExceeded* →
that size is bigger than your quota, drop it from the rotation. Anything else → stop and show the error,
because it is something you need to fix.

Run it on your Mac
^^^^^^^^^^^^^^^^^^

.. code-block:: bash

   cd ~/codebase/sharpen
   SSH_PUB=~/.ssh/sharpen_vm.pub caffeinate -i bash deploy/oci-retry-a1.sh

``SSH_PUB=…`` tells it which public key to put on the new server. ``caffeinate -i`` stops the Mac from
going to sleep while the script runs — but it does **not** stop sleep when you close the lid, which is why the
next section exists. The output looks like:

.. code-block:: text

   Looking up resources in compartment ocid1.tenancy.oc1..aaaaaaaa...
   Image: Canonical-Ubuntu-24.04-aarch64-2026.08.25-0
   Availability domains: RUzw:US-ASHBURN-AD-1 RUzw:US-ASHBURN-AD-2 RUzw:US-ASHBURN-AD-3
   18:36:03  attempt 1  RUzw:US-ASHBURN-AD-1  1 OCPU/6 GB ... out of capacity
   18:37:52  attempt 2  RUzw:US-ASHBURN-AD-1  2 OCPU/12 GB ... out of capacity
   18:39:41  attempt 3  RUzw:US-ASHBURN-AD-2  1 OCPU/6 GB ... transient error (1 in a row) — will retry
   ...
   09:12:07  attempt 188  RUzw:US-ASHBURN-AD-3  1 OCPU/6 GB ... CREATED
   Instance ocid1.instance.oc1.iad.an... — waiting for it to run...
   Public IP: 150.136.x.x
   Next: ssh ubuntu@150.136.x.x

Stop it any time with **Ctrl+C**; it is safe to start again, because it checks for an existing ``sharpen``
server first.

Run it on a machine that never sleeps (recommended)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Capacity tends to appear at night, exactly when a laptop is closed. Any always-on Linux box works; we used
the small Oracle VM that already runs another workload (its public IP is ``<bot-vm-ip>`` below, user ``ubuntu``). The steps: copy
the script, your OCI credentials and the SSH public key to that machine, install the ``oci`` tool there, and
start the script in the background.

On your Mac (``K`` is the private key that opens *that* machine, not the new one):

.. code-block:: bash

   K="$HOME/.ssh/<key-for-that-machine>"     # the private key that machine was created with
   chmod 600 "$K"
   scp -i "$K" ~/codebase/sharpen/deploy/oci-retry-a1.sh ~/.ssh/sharpen_vm.pub ubuntu@<bot-vm-ip>:~
   scp -i "$K" -r ~/.oci ubuntu@<bot-vm-ip>:~
   ssh -i "$K" ubuntu@<bot-vm-ip>

``scp`` is "secure copy": it moves files to the server over SSH. The last line logs you in; the prompt
becomes ``ubuntu@instance-…:~$``. Now, **on that machine**:

.. code-block:: bash

   # point the config at the key file's new location and lock the permissions
   sed -i "s#^key_file=.*#key_file=$HOME/.oci/sharpen_api_key.pem#" ~/.oci/config
   chmod 700 ~/.oci && chmod 600 ~/.oci/*
   # install jq and the oci tool (in its own Python environment so it cannot disturb anything else)
   sudo apt-get update -qq && sudo apt-get install -y -qq jq python3-venv
   python3 -m venv ~/ocienv && ~/ocienv/bin/pip install -q oci-cli
   echo 'export PATH=$HOME/ocienv/bin:$PATH' >> ~/.bashrc && export PATH=$HOME/ocienv/bin:$PATH
   oci iam region list | head -3         # must print JSON, not an error

The ``pip install`` takes several minutes on a small machine and prints nothing meanwhile; that is normal.
Ubuntu may pop up a purple *Pending kernel upgrade* / *Which services should be restarted?* screen during
``apt-get`` — press **Tab** until *<Ok>* is highlighted and **Enter**; the defaults are safe.

Start the script so it keeps running after you log out:

.. code-block:: bash

   SIZES="1:6 2:12" SSH_PUB=~/sharpen_vm.pub nohup bash ~/oci-retry-a1.sh > ~/retry.log 2>&1 &
   sleep 30; tail -3 ~/retry.log
   exit

``nohup … &`` means "run in the background and do not stop when I disconnect"; everything it prints goes to
``retry.log``. ``SIZES`` lists the sizes to rotate through as ``cores:gigabytes``; ours is limited to two
because this tenancy's A1 quota is 2 cores per availability domain (see below).

From then on, check from your Mac whenever you like:

.. code-block:: bash

   ssh -i "$K" ubuntu@<bot-vm-ip> 'tail -3 retry.log'

To stop it: ``ssh -i "$K" ubuntu@<bot-vm-ip> 'pkill -f oci-retry-a1.sh'``.

Reading the log
^^^^^^^^^^^^^^^

.. list-table::
   :header-rows: 1
   :widths: 30 70

   * - Line ends with
     - Meaning
   * - ``out of capacity``
     - Normal. Oracle had nothing spare in that AD at that size. The script continues.
   * - ``transient error (n in a row) — will retry``
     - The call to Oracle timed out or was throttled. Harmless unless it reaches 10 in a row, which points to
       a network problem on the machine running the script.
   * - ``limit exceeded — dropping 4 OCPU/24 GB``
     - That size is above your account's quota; the script keeps going with the smaller sizes. To see the
       quota: console → ☰ → **Governance & Administration** → **Limits, Quotas and Usage**, service
       *Compute*, resource *Cores for Standard.A1 based VM and BM instances*. A brand-new Free Tier account
       may show 2 instead of 4; Oracle usually raises it on request from that page.
   * - ``error:`` followed by ``(not a capacity error — fix the cause before retrying)``
     - Something is wrong with the setup (wrong subnet name, expired API key, …). The lines above it say
       what. Fix it and start the script again.
   * - ``CREATED`` … ``Public IP: …``
     - Done. Copy the IP and go to :doc:`04-deploying`.

How long does it take? Anywhere from minutes to a few days. Weekend and early-morning US-Eastern hours have
the best odds. Two sizes × three ADs at two-minute spacing is polite enough that Oracle does not throttle
you; do not shorten ``INTERVAL`` below 60.
