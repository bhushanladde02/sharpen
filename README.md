# Sharpen — the AI profile

Sharpen tracks how a person works with AI, at work and at home, and turns it into three things:

1. **A monthly report** — what you used, for what, how much of the thinking stayed yours, and three concrete things to try next month. On screen and as a PDF.
2. **An AI score (0–1000)** that rewards independence, verification and learning — never hours or prompt volume — so the incentive is to stay sharp, not to use more AI.
3. **A public AI profile** (`/p/<handle>`) — a LinkedIn-style page with only the AI-related facts, which companies can browse and compare in the candidates view.

Java 21 · Spring Boot 3.3 · Thymeleaf · Spring Data JPA · Spring Security · H2 (dev) / PostgreSQL (prod) · openhtmltopdf.

[![CI](https://github.com/bhushanladde02/sharpen/actions/workflows/ci.yml/badge.svg)](https://github.com/bhushanladde02/sharpen/actions/workflows/ci.yml)
[![Deploy](https://github.com/bhushanladde02/sharpen/actions/workflows/deploy.yml/badge.svg)](https://github.com/bhushanladde02/sharpen/actions/workflows/deploy.yml)
[![CodeQL](https://github.com/bhushanladde02/sharpen/actions/workflows/codeql.yml/badge.svg)](https://github.com/bhushanladde02/sharpen/actions/workflows/codeql.yml)

## Run it

**IntelliJ IDEA** — open the folder (`File → Open → sharpen/`). The project ships with shared run configurations in `.run/`,
so the run dropdown already offers:

| Configuration | What it does |
|---|---|
| **Sharpen (dev, H2)** | starts the app on http://localhost:8080 with the H2 file database in `./data` and demo accounts seeded |
| **Sharpen (postgres)** | same app against a local PostgreSQL (`sharpen`/`sharpen`), profile `postgres` |
| **Maven package (build + tests)** | `mvn clean package` → `target/sharpen.jar` |
| **All tests** | the JUnit suite |

The project SDK is preset to a JDK named `homebrew-21`; if yours is named differently, pick it once under
*File → Project Structure → Project → SDK*. Dependencies resolve from your local `~/.m2` — nothing new is downloaded.

**Command line**

```bash
mvn spring-boot:run            # http://localhost:8080, H2 file database in ./data, demo accounts seeded
```

Demo accounts (password `demo1234`): `demo@sharpen.io` (individual), `lena@sharpen.io`, `marcus@sharpen.io`, and `hiring@sharpen.io` (company → candidates view).

Production shape:

```bash
docker compose up --build      # PostgreSQL + app, schema applied from src/main/resources/db/schema-postgres.sql
# or
SPRING_PROFILES_ACTIVE=postgres SHARPEN_DB_URL=jdbc:postgresql://host:5432/sharpen \
SHARPEN_DB_USER=… SHARPEN_DB_PASSWORD=… java -jar target/sharpen.jar
```

Tests: `mvn test` (scoring unit tests, import parsers, and an end-to-end MockMvc flow that registers, logs, imports, ingests over the API, generates a report, renders the PDF and checks the public profile and candidates page).

## How usage gets in

| Route | What it captures | Self-assessed? |
|---|---|---|
| **Log a session** form | everything, in ~15 seconds | yes |
| **Browser extension** (`chrome-extension/`) | minutes and prompt count per AI site per day; never the text | no — lands as "needs rating" |
| **Provider usage export** (OpenAI/Anthropic dashboard CSV) | requests and tokens per model per day | no — lands as "needs rating" |
| **Sharpen CSV** (`docs/samples/sessions.csv`) | everything, from a spreadsheet | yes |
| **REST API** `POST /api/v1/sessions` with `X-Api-Key` | whatever the client sends | if all four assessment fields are present |

Only rated sessions count toward the score. Imports show volume immediately but cannot move the score until the person answers the four questions — that keeps the human in the loop and makes the score hard to game with automation.

## The score

Five dimensions, each 0–100, minute-weighted over rated sessions in the window (rolling 90 days on the dashboard and profile; calendar month in reports):

| Dimension | Weight | From |
|---|---|---|
| Independence | 30% | human contribution % |
| Effectiveness | 20% | outcome (1–5) blended with prompts-per-useful-result |
| Verification | 20% | share of sessions where the output was checked |
| Growth | 20% | share of sessions where the person can now do something new |
| Breadth | 10% | distinct task types and tools |

Composite = weighted sum × 10. Fewer than 8 rated sessions → "provisional". More than half of professional minutes in sessions where the model did most of the work → "reliance risk" flag. See `AiScoreService` for the exact formulas; `AiScoreServiceTest` pins the properties (hours never raise the score, long sessions weigh more, unrated sessions are ignored).

## Documentation

Start with **[docs/architecture/how-it-works.rst](docs/architecture/how-it-works.rst)** — the design explained
with flow diagrams: the system on one page, what Spring Boot does at startup, one request end to end, how a
session becomes a score, the monthly report, security, and which Spring Boot features we use and why.

The full requirement set (numbered FR/NFR/SC/… ids), scoring specification, report specification, API,
security/privacy, data model and roadmap live in `docs/` as reStructuredText. Build with
`cd docs && pip install -r requirements.txt && make html`, or read the `.rst` files directly in IntelliJ.

## Layout

```
src/main/java/io/sharpen
  domain/      Person, UsageSession, MonthlyReport, enums
  repo/        Spring Data repositories
  scoring/     AiScore record + AiScoreService (pure, unit-tested)
  service/     PersonService, SessionService, ImportService, StatsService, ReportService, PdfService
  web/         Thymeleaf controllers (dashboard, sessions, import, reports, profile, settings, candidates)
  api/         /api/v1 JSON endpoints for the extension and scripts
  config/      SecurityConfig (form login + API-key chain), DemoDataLoader
src/main/resources
  templates/   layout, nav, fragments (score ring, charts), one template per page, report-pdf (XHTML for the PDF)
  static/css/  sharpen.css — tokens, light/dark, no framework
  db/          schema-postgres.sql
chrome-extension/   MV3 capture extension (background, content, popup)
deploy/             production compose, Caddyfile, VM setup script, deployment guide
docs/               Sphinx (RST) requirements and design docs — `./docs/view.sh`
docs/samples/       import examples for each route
```

## Publishing a pilot

**[docs/deployment/](docs/deployment/01-what-we-are-doing.rst)** is a from-scratch, no-prior-knowledge guide
to the zero-cost public deployment: Oracle Cloud Always Free ARM VM, Docker Compose with PostgreSQL, Caddy for
automatic HTTPS, a free DuckDNS name, the capacity-retry script, day-two operations and troubleshooting, with
diagrams, and the CI/CD pipeline (GitHub Actions: test → smoke → docs, then multi-arch image to GHCR → SSH
rollout with health check and rollback; CodeQL, Dependabot, tagged releases). `deploy/README.md` is the
one-page version for people who already know the tools. The landing page and every
signed-in page show live member/session/report counters (`/api/v1/public/stats`, refreshed every 30 s), there is
a `/feedback` form open to everyone, and the account named in `SHARPEN_ADMIN_EMAIL` can read the inbox at
`/admin/feedback`.

## About this project

Sharpen is a solo, end-to-end build by **Bhushan Ladde** (senior backend/data engineer, 11+ years — Python, Java,
Airflow, PostgreSQL, Docker) made in September 2026 to answer one question: *can the way a person works with AI
be measured in a way that rewards judgement instead of volume?* Everything in this repository is part of that
answer — the requirements (`docs/requirements`), the scoring model and its property tests, the Spring Boot
application, the import pipelines and API, the browser extension, the Docker/Caddy production shape, the Oracle
Cloud deployment and its documentation.

**AI assistance.** Claude (Anthropic) was used throughout as a pair-programmer to speed the work up: drafting
code and Thymeleaf templates from the design, generating the documentation and SVG diagrams, and working through
deployment problems (Oracle capacity, OCI CLI auth, firewall rules) interactively. The product idea, the scoring
model and its weights, the architecture and technology choices, code review, testing, and every deployment
decision are the author's. This is deliberately the kind of AI use the product itself is meant to encourage:
the tool did the typing, the person did the thinking and checked the result.

**A five-minute tour for reviewers**

| If you want to see… | Look at |
|---|---|
| the core idea in code | `scoring/AiScoreService.java` and `AiScoreServiceTest` (properties: hours never raise the score, unrated sessions are ignored, long sessions weigh more) |
| how data gets in without being gameable | `service/ImportService.java`, `service/SessionService.upsertExternal` (idempotent, never overwrites a self-assessment) |
| security design | `config/SecurityConfig.java` — two filter chains: form login for pages, stateless API-key for `/api/**` |
| the end-to-end test | `WebFlowTest` — register → log → import → API ingest → report → PDF → public profile → company view |
| the design explained with diagrams | `docs/architecture/how-it-works.rst` |
| how it runs in production for $0 | `docs/deployment/` and `deploy/` |
| the delivery pipeline | `.github/workflows/` (CI, Deploy, CodeQL, Release) and `deploy/remote-deploy.sh` |

## Release 1 checklist

What is deliberately left out of the prototype and what to add before a public release:

- **Migrations** — add `flyway-core`, move `schema-postgres.sql` to `V1__init.sql`.
- **Email** — verification on sign-up, password reset, and the monthly report as an email (the PDF service already exists).
- **Rate limiting** on `/api/v1/**` and `/register`; put the app behind TLS.
- **Auth for companies** — invite-only company accounts or domain verification, and an audit log of profile views.
- **Extension store listing** — icons, privacy policy, and a settings page; the capture logic is done.
- **Provider connectors** — pull usage from OpenAI/Anthropic admin APIs on a schedule instead of CSV uploads.
- **Caching** — candidates view computes each score on request; cache per person for an hour or store the rolling score on `Person` when sessions change.
- **Observability** — `spring-boot-starter-actuator`, structured logs, and a health endpoint behind auth.
