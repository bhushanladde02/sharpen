Data model
==========

Three tables. The PostgreSQL DDL is in ``src/main/resources/db/schema-postgres.sql``; H2 is created by Hibernate
in development.

person
------

.. list-table::
   :header-rows: 1
   :widths: 22 18 60

   * - Column
     - Type
     - Notes
   * - id
     - bigserial PK
     -
   * - email
     - varchar(190)
     - unique, lower-cased; login name
   * - password_hash
     - varchar(100)
     - BCrypt
   * - display_name
     - varchar(120)
     -
   * - handle
     - varchar(60)
     - unique; public URL slug
   * - account_type
     - varchar(20)
     - ``INDIVIDUAL`` / ``COMPANY``
   * - headline, job_title, industry, location
     - varchar
     - profile text, optional
   * - years_experience
     - integer
     - optional
   * - primary_tools
     - varchar(300)
     - comma-separated, maintained by the person
   * - public_profile
     - boolean
     - default true
   * - api_key
     - varchar(64)
     - unique; ``shp_`` + 48 hex chars
   * - created_at
     - timestamptz
     -

usage_session
-------------

.. list-table::
   :header-rows: 1
   :widths: 22 18 60

   * - Column
     - Type
     - Notes
   * - id
     - bigserial PK
     -
   * - person_id
     - bigint FK
     - cascade delete
   * - occurred_on
     - date
     - the person's calendar day; no time of day
   * - context
     - varchar(20)
     - ``PERSONAL`` / ``PROFESSIONAL``
   * - tool
     - varchar(60)
     - free text ("Claude", "ChatGPT", …)
   * - task_category
     - varchar(20)
     - ``CODING`` … ``OTHER``
   * - duration_minutes, prompt_count
     - integer
     - volume; may be estimated for imports
   * - human_contribution_pct
     - integer
     - 0–100; assessment
   * - verified_output, learned_something
     - boolean
     - assessment
   * - outcome
     - integer
     - 1–5; assessment
   * - source
     - varchar(20)
     - ``MANUAL`` / ``EXTENSION`` / ``API_IMPORT``
   * - self_assessed
     - boolean
     - false until the person rates the session; gates the score
   * - external_id
     - varchar(120)
     - unique per person; idempotency key for imports
   * - tokens_in, tokens_out
     - bigint
     - optional, from provider exports
   * - notes
     - varchar(500)
     - optional
   * - created_at
     - timestamptz
     -

Indexes: ``(person_id, occurred_on)`` for every window query; unique ``(person_id, external_id)``.

monthly_report
--------------

.. list-table::
   :header-rows: 1
   :widths: 22 18 60

   * - Column
     - Type
     - Notes
   * - id
     - bigserial PK
     -
   * - person_id
     - bigint FK
     - cascade delete
   * - year_month
     - varchar(7)
     - ``yyyy-MM``; unique with person_id
   * - ai_score, independence, effectiveness, verification, growth, breadth
     - integer
     - denormalised for listing without parsing the payload
   * - session_count, total_minutes
     - integer
     -
   * - payload
     - text
     - the full ``ReportModel`` as JSON
   * - generated_at
     - timestamptz
     -

Derived (not stored)
--------------------

* ``AiScore`` — computed from a session list on request (rolling window) or at generation time (report).
* ``MonthSummary`` — per-month aggregate: minutes, prompts, tokens, active days, per-tool and per-task shares,
  weekly bars, sources, plus the month's ``AiScore``.
* ``ReportModel`` — ``MonthSummary`` for the month and the previous month, deltas, brain-active panel, insights,
  nudges. Stored only inside ``monthly_report.payload``.

Release 1 candidates
--------------------

* ``person.rolling_score`` + ``rolling_score_at`` — cached for the candidates list (CV-4).
* ``profile_view`` (viewer_id, person_id, viewed_at) — audit log (CV-7).
* ``email_token`` — verification and reset (FR-5).
