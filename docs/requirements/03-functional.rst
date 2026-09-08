Functional requirements
=======================

Requirements use *must* for the prototype scope and *should* for Release 1. Each has an identifier for
traceability. Status reflects the prototype in this repository.

Accounts
--------

.. list-table::
   :header-rows: 1
   :widths: 8 62 12 18

   * - ID
     - Requirement
     - Status
     - Where
   * - FR-1
     - A visitor must be able to register with name, email, password and an account type of *Individual* or
       *Company*. Email is unique (case-insensitive).
     - Done
     - ``AuthController``, ``PersonService``
   * - FR-2
     - Passwords must be stored hashed (BCrypt). Sign-in is by email and password with a session cookie.
     - Done
     - ``SecurityConfig``
   * - FR-3
     - Every account must get a unique URL handle derived from the display name, editable later, and a
       random API key that can be rotated.
     - Done
     - ``PersonService``
   * - FR-4
     - An individual must be able to edit the profile fields: name, handle, headline, job title, industry,
       years of experience, location, tools, public/private toggle.
     - Done
     - ``ProfileController``
   * - FR-5
     - Accounts should be able to verify their email and reset a password.
     - Release 1
     - —

Session logging
---------------

.. list-table::
   :header-rows: 1
   :widths: 8 62 12 18

   * - ID
     - Requirement
     - Status
     - Where
   * - FR-10
     - A session record must capture: date, context (*personal* / *professional*), tool (free text with
       suggestions), task type (coding, writing, research, data & analysis, learning, planning, creative,
       admin & email, other), minutes, prompts sent, optional notes.
     - Done
     - ``UsageSession``, ``SessionForm``
   * - FR-11
     - A session must carry a four-part self-assessment: human contribution (0–100 %), output verified
       (yes/no), learned something (yes/no), outcome (1–5).
     - Done
     - ``UsageSession``
   * - FR-12
     - The log form must be completable in about fifteen seconds: sensible defaults, sliders for the two
       numeric answers, no required free text.
     - Done
     - ``session-form.html``
   * - FR-13
     - Sessions must be listable (newest first, paged), editable and deletable by their owner only.
     - Done
     - ``SessionController``
   * - FR-14
     - Sessions that arrive without a self-assessment must be marked *needs rating*, shown distinctly, counted in
       the navigation badge, and rateable in sequence ("save and rate next").
     - Done
     - ``SessionController``, ``sessions.html``

Dashboard
---------

.. list-table::
   :header-rows: 1
   :widths: 8 62 12 18

   * - ID
     - Requirement
     - Status
     - Where
   * - FR-20
     - The dashboard must show the rolling 90-day AI score with its five dimensions, confidence band and flags.
     - Done
     - ``DashboardController``, ``fragments.html``
   * - FR-21
     - It must show the score by month for the last six months, the current month's sessions / hours / prompts /
       active days, and the professional vs personal split.
     - Done
     - ``StatsService.trend``
   * - FR-22
     - It must show the current month by week (stacked professional / personal minutes), by tool and by task type.
     - Done
     - ``MonthSummary``
   * - FR-23
     - It must surface unrated sessions with a one-click path to rate them.
     - Done
     - ``dashboard.html``
   * - FR-24
     - Company accounts must be redirected from the dashboard to the candidates view.
     - Done
     - ``DashboardController``

Monthly report
--------------

See :doc:`05-monthly-report` for the full specification.

.. list-table::
   :header-rows: 1
   :widths: 8 62 12 18

   * - ID
     - Requirement
     - Status
     - Where
   * - FR-30
     - A report must be generated automatically for every individual with sessions, for the month just ended,
       on the 1st of each month.
     - Done
     - ``ReportService`` (cron ``0 0 2 1 * *`` UTC)
   * - FR-31
     - A person must be able to preview any month live and generate (freeze) or regenerate it on demand.
     - Done
     - ``ReportController``
   * - FR-32
     - A generated report must be stored as a snapshot so it does not change when later sessions are edited.
     - Done
     - ``MonthlyReport.payload``
   * - FR-33
     - A report must be downloadable as PDF rendered from the same data.
     - Done
     - ``PdfService``, ``report-pdf.html``
   * - FR-34
     - The report should be emailed to the person when generated.
     - Release 1
     - —

Import and capture
------------------

See :doc:`06-data-ingestion`.

.. list-table::
   :header-rows: 1
   :widths: 8 62 12 18

   * - ID
     - Requirement
     - Status
     - Where
   * - FR-40
     - Users must be able to upload a Sharpen CSV (full record) and have rows created or updated by external id.
     - Done
     - ``ImportService.importCsv``
   * - FR-41
     - Users must be able to upload a provider usage export (OpenAI / Anthropic dashboard CSV) as-is; rows land
       as unrated sessions with estimated minutes.
     - Done
     - ``ImportService.importProviderUsage``
   * - FR-42
     - Users must be able to upload the extension's JSON payload manually.
     - Done
     - ``ImportService.importExtensionJson``
   * - FR-43
     - A browser extension must time visits to AI sites and count prompts, without reading the text, and sync
       to the API with the user's key.
     - Done (scaffold)
     - ``chrome-extension/``
   * - FR-44
     - Re-importing the same data must update, never duplicate (idempotent by ``externalId``).
     - Done
     - ``SessionService.upsertExternal``
   * - FR-45
     - A re-sync must never overwrite a self-assessment the person already entered; only volume fields update.
     - Done
     - ``SessionService.upsertExternal``
   * - FR-46
     - Usage should be pulled from provider admin APIs on a schedule instead of uploaded.
     - Release 1
     - —

Public profile and companies
----------------------------

See :doc:`07-profile-and-companies`.

.. list-table::
   :header-rows: 1
   :widths: 8 62 12 18

   * - ID
     - Requirement
     - Status
     - Where
   * - FR-50
     - Every individual must have a public page at ``/p/{handle}`` when the profile is public; owners can always
       see their own page; anyone else sees a "not available" page when private.
     - Done
     - ``ProfileController``
   * - FR-51
     - The profile must show only AI-related facts: identity line, headline, title/industry/experience/location,
       tools, rolling score with dimensions and flags, 6-month trend and month-by-month table, counts of sessions
       and reports.
     - Done
     - ``profile.html``
   * - FR-52
     - Company accounts must see a candidates list of all public individuals with rolling score, dimensions,
       session count and flags; searchable by text and sortable by score, independence, verification, growth, name.
     - Done
     - ``CandidateController``
   * - FR-53
     - Individuals must not be able to open the candidates view (403).
     - Done
     - ``SecurityConfig``
   * - FR-54
     - Company accounts should be gated (invite or domain verification) and profile views should be audited.
     - Release 1
     - —

API
---

See :doc:`08-api`.

.. list-table::
   :header-rows: 1
   :widths: 8 62 12 18

   * - ID
     - Requirement
     - Status
     - Where
   * - FR-60
     - ``POST /api/v1/sessions`` must accept a batch of sessions authenticated by ``X-Api-Key`` and return
       created / updated / skipped counts with row-level errors.
     - Done
     - ``SessionApiController``
   * - FR-61
     - ``GET /api/v1/me`` and ``GET /api/v1/me/score`` must return the caller's identity and rolling score.
     - Done
     - ``SessionApiController``
   * - FR-62
     - ``GET /api/v1/health`` must be open for monitoring.
     - Done
     - ``SessionApiController``

Community and feedback
----------------------

.. list-table::
   :header-rows: 1
   :widths: 8 62 12 18

   * - ID
     - Requirement
     - Status
     - Where
   * - FR-70
     - The landing page and every signed-in page must show the live number of members, sessions logged and
       reports generated, refreshed without a reload (every 30 s and when the tab regains focus).
     - Done
     - ``CommunityService``, ``/api/v1/public/stats``, ``layout.html``
   * - FR-71
     - Counters are served from a 10-second in-memory cache and invalidated on registration, so a new member
       sees themselves counted immediately while page views never trigger COUNT queries.
     - Done
     - ``CommunityService``
   * - FR-72
     - Anyone, signed in or not, must be able to leave feedback (message, optional 1–5 rating, optional email);
       the page they came from is recorded.
     - Done
     - ``CommunityController``, ``feedback.html``
   * - FR-73
     - The account whose email matches ``sharpen.admin-email`` can read the latest 200 messages and the counters
       at ``/admin/feedback``; everyone else gets 404.
     - Done
     - ``CommunityController``
