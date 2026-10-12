Roadmap
=======

Prototype (this repository)
---------------------------

Everything marked *Done* in :doc:`requirements/03-functional`: accounts, session logging and rating, five
ingestion routes, the score, dashboard, monthly report with PDF, public profile, company candidates view, API,
Chrome extension scaffold, Docker shape, tests, this documentation.

Making the score worth trusting (from October 2026)
---------------------------------------------------

Two outside reviews in October 2026 agreed: the idea is sound and the build is real, but a score made only of a
person's own answers is a good mirror and a weak credential, and an employer marketplace is premature. The plan,
in order — each step a small, tested change:

#. **Honest framing — done 12 October.** Profiles private until their owner shares them; "self-rated" on every
   score; a *what this score is and isn't* panel on profiles; a candidates-page note not to rank on it; the
   reliance-risk flag only for unchecked model work (so testing generated code is not penalised); a
   *uniform ratings* flag; a home-page section on what the score can and cannot do.
#. **Ownership — done 12 October.** Model-written work the person verified counts 70 % as theirs, so the
   independence part rewards judgement rather than typing everything by hand.
#. **A way in without an account — done 12 October.** The 60-second habits check at ``/check``, with a
   shareable result, as the main route for new visitors (home page, header, footer, Help).
#. **The funnel — done 12 October.** Register signs you in; first-run states; visitor call to action on profiles;
   neutral band names; honest form defaults; accessibility 100. See the pilot log, day 27.
#. **Ten seconds a day.** A once-a-day check-in: rate *one* session (sampled), four taps, pre-filled by the
   extension. Less logging, less fatigue, less temptation to rate everything top.
#. **Evidence.** Optionally attach a link (pull request, document) or one sentence of what was learned, written at
   the time; the profile shows how many sessions carry evidence, separately from the self-rated score.
#. **Something objective.** A short, fixed *spot the AI's mistake* exercise (planted bugs in AI-written code,
   hallucinated APIs), graded by the server and shown as its own result next to the self-rated score.
#. **Measure what matters.** Week-2 return rate on the Traffic page, and ten conversations with real users and
   hiring managers before anything employer-facing is extended.

Release 1
---------

Ordered by what unblocks a public launch.

1. **Migrations** — done (NFR-10): dated scripts in ``db/migrations/`` applied by the deploy script through a
   ``schema_migration`` ledger; Flyway only if the scripts ever need more than "apply once, in order".
2. **Email** — verification, password reset, report delivery (FR-5, MR-6).
3. **Edge hardening** — TLS, HSTS, rate limiting (SE-8, SE-9).
4. **Company gating and audit** — invite / domain verification, profile-view log (CV-6, CV-7).
5. **Score caching** — rolling score on ``person``, refreshed on session change (CV-4).
6. **Scheduler lock** — ShedLock or a database lease so the monthly job runs once per cluster.
7. **Export and delete** — full data export and account deletion (PR-5).
8. **Extension store listing** — icons and review submission (FR-43, FR-43a). The privacy policy the store asks
   for is ``https://sharpenscore.com/privacy`` (PR-6); the history import's disclosure text is in the popup.
9. **Observability** — Actuator behind auth, structured logs (NFR-9).

Release 2 candidates
--------------------

* LinkedIn profile import (headline, positions) — needs LinkedIn partner approval; sign-in with Google, GitHub
  and LinkedIn is done (FR-6).

* Provider connectors (OpenAI / Anthropic admin APIs) on a schedule (FR-46).
* Verifiable signals as a sixth score dimension (tests run, sources cited).
* Team roll-ups for companies with opt-in (MR-7).
* Introductions with consent (CV-9).
* Public score methodology page and a versioned score (so a 2027 score is comparable to a 2026 one).
