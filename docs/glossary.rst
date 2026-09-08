Glossary
========

.. glossary::

   AI score
      The 0–1000 composite of the five dimensions over a window. See :doc:`requirements/04-scoring`.

   Assessment / self-assessment
      The four answers a person gives per session: human contribution %, output verified, learned something,
      outcome 1–5. Only assessed sessions count toward the score.

   Band
      The label for a composite: Expert, Strong, Developing, Dependent, At risk.

   Brain-active check
      The report panel that states how much of the month's work stayed human, with a one-sentence verdict.

   Breadth
      Spread across task types and tools; 10 % of the score.

   Context
      Whether a session was personal or professional.

   Effectiveness
      Outcome quality per prompt spent; 20 % of the score.

   External id
      A stable identifier supplied by an import or the extension so re-sends update instead of duplicate.

   Growth
      Share of sessions where the person can now do something they could not before; 20 % of the score.

   Handle
      The slug in the public URL ``/p/{handle}``.

   Human contribution
      0–100 %: how much of the finished work came from the person rather than the model.

   Independence
      Weighted mean of human contribution; 30 % of the score.

   Needs rating
      A session that arrived without an assessment (extension, provider export, partial CSV). Shown with a
      warning tint and excluded from the score until rated.

   Provisional
      A score based on fewer than eight rated sessions.

   Reliance risk
      Flag set when more than half of professional minutes were spent in sessions where the model did most of
      the work (human contribution below 30 %).

   Rolling score
      The score over the last 90 days ending today; what the dashboard, profile and candidates show.

   Session
      One block of AI usage: a chat, a coding session, an hour with Copilot.

   Verification
      Share of sessions where the output was checked against a source, a test, or the person's own judgement;
      20 % of the score.

Deployment terms
----------------

.. glossary::

   Availability domain (AD)
      One physical building inside an Oracle region. Ashburn has three (AD-1..3). Free ARM capacity differs
      between them, so the retry script tries all three.

   Always Free
      The part of Oracle Cloud's Free Tier that never expires and is never billed: one Ampere A1 VM (up to
      4 cores / 24 GB), two E2.1.Micro VMs, 200 GB block storage, 10 TB egress a month.

   Caddy
      The web server in front of the app. Terminates HTTPS with a Let's Encrypt certificate it obtains and
      renews by itself, and forwards requests to ``app:8080``.

   Container / Docker / Compose
      A container is a sealed box with one program and everything it needs. Docker runs containers; Docker
      Compose starts a set of them from one file (``deploy/docker-compose.prod.yml``).

   DNS / DuckDNS
      The system that turns a name into an IP address. DuckDNS is a free provider of ``*.duckdns.org`` names.

   Instance / VM / server
      A rented computer in a data centre. Oracle calls it an instance; the shape (size) we use is
      ``VM.Standard.A1.Flex``.

   OCI CLI
      Oracle's command-line tool (``oci``). Authenticated with an API key pair whose public half is registered
      under *My profile → API keys*.

   OCID
      Oracle's long identifier for anything (user, tenancy, instance, subnet), e.g. ``ocid1.tenancy.oc1..aaaa…``.

   Out of capacity
      Oracle's error when no free ARM machine is spare in that AD right now. Not a quota or billing problem;
      retrying later succeeds.

   Port
      A number that selects one service on a computer: 22 SSH, 80 HTTP, 443 HTTPS, 8080 the app inside the
      VM, 5432 PostgreSQL inside the VM.

   Public IP
      The address of the server on the internet; assigned when the instance is created and kept for its life.

   Security list
      Oracle's network firewall for a VCN: which ports may receive traffic from where. Ours allows 22, 80
      and 443 from anywhere.

   SSH / key pair
      Encrypted remote login. The private key stays on your Mac; the public key is placed on the server when
      it is created.

   Tenancy
      Your whole Oracle account; also the name of its root compartment, where all our resources live.

   VCN / subnet
      Virtual cloud network: a private network inside Oracle. A subnet is the part of it instances attach to.

   Volume
      Disk space that outlives a container. The database's files and Caddy's certificates live in volumes.
