Non-functional requirements
===========================

.. list-table::
   :header-rows: 1
   :widths: 8 62 30

   * - ID
     - Requirement
     - Prototype status
   * - NFR-1
     - **Stack.** Java 21, Spring Boot 3.3.x, Thymeleaf server-rendered UI, Spring Data JPA, Spring Security.
       No JavaScript framework; the only client-side JS is two slider labels and the extension.
     - Met
   * - NFR-2
     - **Databases.** H2 file database for development (zero setup); PostgreSQL for production via the
       ``postgres`` profile with ``ddl-auto=validate`` against a checked-in schema.
     - Met
   * - NFR-3
     - **Efficiency.** One query per page for session data, grouped in memory; indexes on
       ``(person_id, occurred_on)`` and ``(person_id, external_id)``; batch inserts enabled; Thymeleaf cache on in
       production; HTTP compression on. Target: dashboard under 100 ms at 10 000 sessions per person on a
       laptop.
     - Met (not yet load-tested)
   * - NFR-4
     - **Build.** ``mvn clean package`` produces one runnable jar; the build and tests run offline against a
       populated local Maven repository.
     - Met
   * - NFR-5
     - **Tests.** Unit tests for scoring and parsers; an end-to-end MockMvc flow covering register → log → import
       → API → report → PDF → profile → candidates. Tests must pass before merge.
     - Met (13 tests)
   * - NFR-6
     - **Deployability.** Multi-stage Dockerfile and a ``docker-compose.yml`` with PostgreSQL; all secrets via
       environment variables (``SHARPEN_DB_URL``, ``SHARPEN_DB_USER``, ``SHARPEN_DB_PASSWORD``).
     - Met
   * - NFR-7
     - **Accessibility and theming.** Semantic HTML, visible focus states, ``prefers-reduced-motion`` respected,
       light and dark themes from one token set, no colour-only encoding (chips carry text).
     - Met
   * - NFR-8
     - **Responsiveness.** Sidebar collapses to a top bar below 860 px; tables scroll horizontally inside their
       container; the page body never scrolls sideways.
     - Met
   * - NFR-9
     - **Observability.** Structured logs, health endpoint, and Actuator metrics behind authentication.
     - Release 1
   * - NFR-10
     - **Migrations.** Flyway with versioned SQL; the prototype's ``schema-postgres.sql`` becomes ``V1__init.sql``.
     - Release 1
   * - NFR-11
     - **Time.** All timestamps stored in UTC; session dates are calendar dates without a zone (the person's own
       day). The monthly job runs at 02:00 UTC.
     - Met
   * - NFR-12
     - **Documentation.** This RST set is the requirement of record; every FR/NFR id is stable once published.
     - Met
