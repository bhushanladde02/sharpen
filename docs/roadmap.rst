Roadmap
=======

Prototype (this repository)
---------------------------

Everything marked *Done* in :doc:`requirements/03-functional`: accounts, session logging and rating, five
ingestion routes, the score, dashboard, monthly report with PDF, public profile, company candidates view, API,
Chrome extension scaffold, Docker shape, tests, this documentation.

Release 1
---------

Ordered by what unblocks a public launch.

1. **Migrations** — Flyway, ``V1__init.sql`` from ``schema-postgres.sql`` (NFR-10).
2. **Email** — verification, password reset, report delivery (FR-5, MR-6).
3. **Edge hardening** — TLS, HSTS, rate limiting (SE-8, SE-9).
4. **Company gating and audit** — invite / domain verification, profile-view log (CV-6, CV-7).
5. **Score caching** — rolling score on ``person``, refreshed on session change (CV-4).
6. **Scheduler lock** — ShedLock or a database lease so the monthly job runs once per cluster.
7. **Export and delete** — full data export and account deletion (PR-5).
8. **Extension store listing** — icons, privacy policy, review submission (FR-43).
9. **Observability** — Actuator behind auth, structured logs (NFR-9).

Release 2 candidates
--------------------

* Provider connectors (OpenAI / Anthropic admin APIs) on a schedule (FR-46).
* Verifiable signals as a sixth score dimension (tests run, sources cited).
* Team roll-ups for companies with opt-in (MR-7).
* Introductions with consent (CV-9).
* Public score methodology page and a versioned score (so a 2027 score is comparable to a 2026 one).
