Architecture overview
=====================

One Spring Boot application, layered by package. No message broker, no cache, no front-end build step: the
prototype is deliberately the simplest shape that can go to production unchanged.

.. code-block:: text

   browser ──form login/CSRF──▶ web/*Controller ──▶ service/* ──▶ repo/* ──▶ H2 | PostgreSQL
   extension / scripts ──X-Api-Key──▶ api/SessionApiController ──▶ service/ImportService ─┘
                                                   │
                                        scoring/AiScoreService (pure)
                                                   │
                            service/ReportService ──▶ PdfService (openhtmltopdf)
                            @Scheduled 1st of month

Packages
--------

``io.sharpen.domain``
   JPA entities ``Person``, ``UsageSession``, ``MonthlyReport`` and the enums (``AccountType``, ``UsageContext``,
   ``TaskCategory``, ``SessionSource``). Entities are plain classes with explicit accessors; no Lombok.

``io.sharpen.repo``
   Spring Data repositories. Queries are always scoped by ``personId``.

``io.sharpen.scoring``
   ``AiScore`` (record) and ``AiScoreService``. Pure functions of a session list — no Spring dependencies beyond
   ``@Service`` — so the formula is unit-testable and can move to a batch job or a different runtime unchanged.

``io.sharpen.service``
   * ``PersonService`` — registration, current user, handles, API keys.
   * ``SessionService`` — create / upsert / assess / delete / list; the idempotency and "never overwrite an
     assessment" rules live here.
   * ``ImportService`` — CSV and JSON parsing, format detection, provider-export mapping.
   * ``StatsService`` — read-side aggregates: ``MonthSummary`` per month, six-month trend, rolling score.
   * ``ReportService`` — builds ``ReportModel`` (insights, brain-active check, nudges), stores snapshots, runs the
     monthly schedule.
   * ``PdfService`` — renders ``report-pdf.html`` through Thymeleaf into a PDF.

``io.sharpen.web``
   One controller per page group; ``GlobalModelAttributes`` adds ``me``, ``path`` and ``unratedCount`` to every
   model; ``Charts`` computes SVG paths server-side so pages need no charting library.

``io.sharpen.api``
   JSON endpoints for the extension and scripts.

``io.sharpen.config``
   ``SecurityConfig`` (two filter chains), ``ApiKeyAuthFilter``, ``DemoDataLoader`` (seeds three individuals and a
   company when ``sharpen.demo-data=true``).

Request flow: logging a session
-------------------------------

1. ``GET /sessions/new`` renders ``session-form.html`` bound to ``SessionForm`` (Bean Validation on the fields).
2. ``POST /sessions`` validates, converts to ``SessionService.SessionInput`` and calls ``logManual`` → saved with
   ``source=MANUAL, selfAssessed=true``.
3. Redirect to ``/dashboard``; ``StatsService.rollingScore`` reloads the last 90 days and recomputes.

Request flow: extension sync
----------------------------

1. Extension posts ``{sessions:[…]}`` with ``X-Api-Key``.
2. ``ApiKeyAuthFilter`` resolves the key to a ``Person`` and sets a stateless authentication.
3. ``ImportService.importExternal`` → for each row ``SessionService.upsertExternal``: find by
   (person, externalId); create, or update volume only if already assessed.
4. Response with counts; the dashboard badge shows the new unrated sessions.

Rendering
---------

Thymeleaf with a single ``layout.html`` (sidebar + mobile top bar) and ``fragments.html`` for the score ring,
dimension bars, trend line, weekly bars and share lists. All charts are inline SVG or CSS built from numbers the
controller supplies; there is no client-side data fetching. ``app.css`` defines one token set with light and dark
values.

Scheduling
----------

``@EnableScheduling`` on the application class; ``ReportService.generateLastMonthForEveryone`` runs with cron
``0 0 2 1 * *`` (UTC). On a multi-instance deployment this must be guarded (ShedLock or a database lease) —
noted in the roadmap.
