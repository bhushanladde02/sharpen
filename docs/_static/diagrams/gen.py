#!/usr/bin/env python3
"""Generates the architecture diagrams as SVG. Run from docs/_static/diagrams: python3 gen.py

Hand-placed boxes and arrows on a fixed palette, so every diagram in the docs looks like the same system.
Edit the specs below and re-run; the SVGs are committed so readers never need this script.
"""
from pathlib import Path

INK, INK2, INK3 = "#12171f", "#4a5665", "#7c8794"
LINE, SURF, SURF2 = "#d9dfe8", "#ffffff", "#eceff4"
ACCENT, ACCENT_SOFT = "#2857d9", "#e6ecfc"
GOOD, GOOD_SOFT = "#0f7a2b", "#e2f4e6"
WARN, WARN_SOFT = "#9a6400", "#fbf0d3"
ORANGE, ORANGE_SOFT = "#c2410c", "#fde8dc"
FONT = "Manrope, ui-sans-serif, system-ui, -apple-system, 'Segoe UI', Helvetica, Arial, sans-serif"
MONO = "'IBM Plex Mono', ui-monospace, SFMono-Regular, Menlo, Consolas, monospace"


class Svg:
    def __init__(self, w, h):
        self.w, self.h, self.parts = w, h, []

    def box(self, x, y, w, h, title, sub=None, fill=SURF, stroke=LINE, color=INK, mono=False, r=10, bold=True):
        self.parts.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="{fill}" stroke="{stroke}" stroke-width="1.2"/>')
        cx = x + w / 2
        lines = title.split("\n")
        subs = wrap(sub, w - 16, 6.3) if sub else []
        for ln in lines:
            if len(ln) * (7.6 if bold else 7.0) > w - 12:
                print(f"  ! title too wide for box: {ln!r}")
        if len(lines) * 17 + len(subs) * 14 > h - 6:
            print(f"  ! text taller than box: {title!r} ({len(lines)}+{len(subs)} lines in {h}px)")
        total = len(lines) * 17 + len(subs) * 14
        ty = y + h / 2 - total / 2 + 13
        for ln in lines:
            self.parts.append(f'<text x="{cx}" y="{ty}" text-anchor="middle" font-family="{MONO if mono else FONT}" font-size="13.5" font-weight="{700 if bold else 500}" fill="{color}">{esc(ln)}</text>')
            ty += 17
        for s in subs:
            self.parts.append(f'<text x="{cx}" y="{ty}" text-anchor="middle" font-family="{FONT}" font-size="11.5" fill="{INK2}">{esc(s)}</text>')
            ty += 14

    def group(self, x, y, w, h, label, fill=SURF2, stroke=LINE, dash=False):
        d = ' stroke-dasharray="6 4"' if dash else ""
        self.parts.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="14" fill="{fill}" stroke="{stroke}" stroke-width="1.2"{d}/>')
        self.parts.append(f'<text x="{x + 14}" y="{y + 20}" font-family="{FONT}" font-size="11" font-weight="800" letter-spacing="1.2" fill="{INK3}">{esc(label.upper())}</text>')

    def arrow(self, x1, y1, x2, y2, label=None, color=INK2, dashed=False, lx=None, ly=None, curve=None):
        d = ' stroke-dasharray="5 4"' if dashed else ""
        if curve:
            path = f"M{x1} {y1} Q{curve[0]} {curve[1]} {x2} {y2}"
        else:
            path = f"M{x1} {y1} L{x2} {y2}"
        self.parts.append(f'<path d="{path}" fill="none" stroke="{color}" stroke-width="1.6" marker-end="url(#arrow)"{d}/>')
        if label:
            mx, my = (lx if lx is not None else (x1 + x2) / 2), (ly if ly is not None else (y1 + y2) / 2 - 6)
            tw = len(label) * 6.6 + 10
            self.parts.append(f'<rect x="{mx - tw / 2}" y="{my - 11}" width="{tw}" height="16" rx="4" fill="{SURF}" opacity=".92"/>')
            self.parts.append(f'<text x="{mx}" y="{my + 1}" text-anchor="middle" font-family="{FONT}" font-size="11" fill="{INK2}">{esc(label)}</text>')

    def text(self, x, y, s, size=12, color=INK2, anchor="start", weight=500, mono=False):
        self.parts.append(f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-family="{MONO if mono else FONT}" font-size="{size}" font-weight="{weight}" fill="{color}">{esc(s)}</text>')

    def step(self, x, y, n):
        self.parts.append(f'<circle cx="{x}" cy="{y}" r="11" fill="{ACCENT}"/>')
        self.parts.append(f'<text x="{x}" y="{y + 4}" text-anchor="middle" font-family="{FONT}" font-size="11.5" font-weight="800" fill="#fff">{n}</text>')

    def save(self, name):
        head = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {self.w} {self.h}" width="{self.w}" height="{self.h}" '
                f'font-family="{FONT}" role="img">'
                f'<defs><marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse">'
                f'<path d="M0 0 L10 5 L0 10 z" fill="{INK2}"/></marker></defs>'
                f'<rect width="{self.w}" height="{self.h}" fill="#f4f5f8" rx="16"/>')
        Path(name).write_text(head + "\n".join(self.parts) + "</svg>\n")
        print("wrote", name)


def esc(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def wrap(text, width_px, char_px):
    """Wrap at word boundaries so a line never exceeds width_px; explicit newlines are kept."""
    out = []
    for para in text.split("\n"):
        line = ""
        for word in para.split(" "):
            cand = (line + " " + word).strip()
            if len(cand) * char_px > width_px and line:
                out.append(line)
                line = word
            else:
                line = cand
        out.append(line)
    return out



# ------------------------------------------------------------------ 1. big picture
s = Svg(1000, 540)
s.text(20, 30, "Sharpen — the whole system on one page", 15, INK, weight=800)

s.group(20, 50, 210, 470, "People and clients")
s.box(38, 90, 174, 66, "Browser", "individual or company; HTML pages, form login")
s.box(38, 176, 174, 66, "Chrome extension", "times AI sites, counts prompts; POST /api/v1/sessions")
s.box(38, 262, 174, 66, "Scripts / imports", "usage exports, CSV, any HTTP client with a key")
s.box(38, 348, 174, 66, "Recruiter", "reads /p/{handle} and /candidates")

s.group(258, 50, 500, 470, "One Spring Boot application (sharpen.jar)")
s.box(276, 90, 110, 52, "Tomcat :8080", "embedded", fill=SURF)
s.box(398, 90, 110, 52, "Spring\nSecurity", None, fill=ACCENT_SOFT, stroke=ACCENT)
s.box(520, 90, 105, 52, "Spring MVC", "controllers", fill=ACCENT_SOFT, stroke=ACCENT)
s.box(637, 90, 103, 52, "Thymeleaf", "HTML views", fill=ACCENT_SOFT, stroke=ACCENT)
s.box(276, 168, 464, 56, "Services", "Person · Session · Import · Stats · Report · Pdf — the business rules, @Transactional")
s.box(276, 248, 222, 58, "AiScoreService", "pure scoring, no I/O, unit-tested", fill=GOOD_SOFT, stroke=GOOD)
s.box(518, 248, 222, 58, "@Scheduled job", "1st of month 02:00 UTC: freeze last month's reports", fill=WARN_SOFT, stroke=WARN)
s.box(276, 330, 464, 52, "Spring Data JPA repositories", "PersonRepository · UsageSessionRepository · MonthlyReportRepository")
s.box(276, 406, 222, 52, "openhtmltopdf", "report-pdf.html → PDF")
s.box(518, 406, 222, 52, "Hibernate + HikariCP", "ORM and connection pool")
s.text(508, 492, "Also: Jackson (JSON) · Bean Validation · profiles dev (H2) / postgres · hashed static files", 11, INK3, anchor="middle")

s.group(786, 50, 194, 470, "Storage")
s.box(804, 110, 158, 70, "H2 (file)", "development; ./data/sharpen.mv.db")
s.box(804, 210, 158, 70, "PostgreSQL", "production profile; schema-postgres.sql")
s.box(804, 320, 158, 92, "3 tables", "person\nusage_session\nmonthly_report", mono=True, bold=False)

s.arrow(212, 123, 274, 108, "HTTPS", lx=243, ly=100)
s.arrow(212, 209, 274, 114, "X-Api-Key", lx=232, ly=170)
s.arrow(212, 295, 274, 120)
s.arrow(212, 381, 274, 126)
s.arrow(386, 116, 396, 116)
s.arrow(508, 116, 518, 116)
s.arrow(625, 116, 635, 116)
s.arrow(575, 142, 575, 166)
s.arrow(387, 224, 387, 246)
s.arrow(629, 224, 629, 246)
s.arrow(508, 306, 508, 328)
s.arrow(629, 382, 629, 404)
s.arrow(740, 432, 802, 370, "JDBC", lx=772, ly=392)
s.save("01-big-picture.svg")

# ------------------------------------------------------------------ 2. one request through Spring Boot
s = Svg(1000, 580)
s.text(20, 30, "What happens when the browser asks for GET /dashboard", 15, INK, weight=800)
y = 70
cols = [
    ("Embedded Tomcat", "accepts the HTTP request on :8080 and hands it to the servlet filter chain", SURF, LINE),
    ("Spring Security filter chain", "session cookie → who is this? CSRF check on POST; role check (/candidates needs COMPANY)", ACCENT_SOFT, ACCENT),
    ("DispatcherServlet", "matches the URL to a handler method: @GetMapping(\"/dashboard\")", ACCENT_SOFT, ACCENT),
    ("DashboardController", "asks services for the model; @ControllerAdvice adds me, path, unratedCount to every page", SURF, LINE),
    ("StatsService · SessionService", "@Transactional(readOnly): one query for the 90-day window, grouped in memory", SURF, LINE),
    ("AiScoreService", "pure computation over the session list → AiScore record (composite, 5 dimensions, flags)", GOOD_SOFT, GOOD),
    ("UsageSessionRepository → Hibernate → DB", "a derived query method becomes SQL; HikariCP lends a pooled connection", SURF, LINE),
    ("Thymeleaf", "renders dashboard.html with the model; fragments draw the ring, bars and trend as inline SVG", ACCENT_SOFT, ACCENT),
    ("Response", "one HTML document plus one cached CSS file; no client-side data fetching", SURF, LINE),
]
for i, (t, sub, fill, stroke) in enumerate(cols):
    s.step(40, y + 22, i + 1)
    s.box(62, y, 400, 46, t, None, fill=fill, stroke=stroke)
    for j, ln in enumerate(wrap(sub, 500, 6.3)):
        s.text(480, y + 19 + j * 14, ln, 11.5, INK2)
    if i < len(cols) - 1:
        s.arrow(262, y + 46, 262, y + 54)
    y += 54
s.save("02-request-flow.svg")

# ------------------------------------------------------------------ 3. from a session to a score
s = Svg(1000, 480)
s.text(20, 30, "From usage to a score: five ways in, one table, one formula", 15, INK, weight=800)

s.group(20, 50, 230, 400, "Ingestion routes")
routes = [("Log form", "self-assessed", GOOD_SOFT, GOOD), ("Sharpen CSV", "assessed if complete", GOOD_SOFT, GOOD),
          ("Provider usage export", "needs rating", WARN_SOFT, WARN), ("Chrome extension", "needs rating", WARN_SOFT, WARN),
          ("REST API", "either", SURF, LINE)]
for i, (t, sub, f, st) in enumerate(routes):
    s.box(38, 82 + i * 70, 194, 54, t, sub, fill=f, stroke=st)
    s.arrow(232, 109 + i * 70, 288, 200 + (i - 2) * 6)

s.box(290, 140, 230, 120, "SessionService", "logManual / upsertExternal: idempotent by (person, externalId); never overwrites a rating; selfAssessed = all 4 answers present", fill=ACCENT_SOFT, stroke=ACCENT)
s.arrow(520, 200, 566, 200)
s.box(568, 140, 180, 120, "usage_session", "one row per session: volume + 4 assessment fields + self_assessed flag", mono=True, bold=False)
s.arrow(748, 200, 794, 200)
s.box(796, 130, 184, 140, "AiScoreService", "only self_assessed rows; minute-weighted (5–240); 5 dimensions × weights → 0–1000 + flags", fill=GOOD_SOFT, stroke=GOOD)

s.box(290, 320, 230, 62, "Rating screen", "the person answers the 4 questions for \"needs rating\" rows", fill=WARN_SOFT, stroke=WARN)
s.arrow(405, 320, 405, 262, "assess()", lx=445, ly=295)
s.arrow(658, 260, 658, 318, "unrated?", lx=690, ly=292)
s.arrow(568, 351, 522, 351)
s.box(568, 320, 180, 62, "Dashboard badge", "count of unrated sessions", bold=False)

s.box(796, 320, 184, 76, "Windows", "dashboard/profile: 90 days; report: calendar month", bold=False)
s.arrow(888, 270, 888, 318)
s.text(500, 440, "Design rule: volume (minutes, prompts, tokens) is stored and shown, but only rated sessions can move the score.", 11.5, INK2, anchor="middle")
s.save("03-session-to-score.svg")

# ------------------------------------------------------------------ 4. monthly report
s = Svg(1000, 340)
s.text(20, 30, "Monthly report: built live, frozen on demand or on the 1st, rendered twice", 15, INK, weight=800)
s.box(30, 90, 190, 74, "@Scheduled", "cron 0 0 2 1 * * (UTC); every individual with sessions that month", fill=WARN_SOFT, stroke=WARN)
s.box(30, 194, 190, 74, "Person clicks Generate", "any month since the first session", fill=SURF)
s.arrow(220, 127, 268, 158)
s.arrow(220, 231, 268, 182)
s.box(270, 120, 210, 100, "ReportService.build()", "MonthSummary for this month and last; deltas; brain-active check; insights; 3 nudges", fill=ACCENT_SOFT, stroke=ACCENT)
s.arrow(480, 170, 528, 170)
s.box(530, 120, 200, 100, "monthly_report", "payload = ReportModel as JSON + score columns for listing; unique (person, month)", mono=True, bold=False)
s.arrow(730, 150, 778, 116)
s.arrow(730, 190, 778, 226)
s.box(780, 76, 200, 78, "Screen", "report.html — Thymeleaf page, same fragments as the dashboard")
s.box(780, 186, 200, 78, "PDF", "report-pdf.html — openhtmltopdf, strict XHTML, A4, ~5 KB")
s.text(500, 305, "A live preview (not stored) uses the same build(); the page is labelled so nobody mistakes it for a frozen report.", 11.5, INK2, anchor="middle")
s.save("04-monthly-report.svg")

# ------------------------------------------------------------------ 5. security
s = Svg(1000, 300)
s.text(20, 30, "Two security filter chains, chosen by URL", 15, INK, weight=800)
s.box(30, 110, 150, 60, "Request", "any URL")
s.arrow(180, 128, 248, 95, "/api/**", lx=212, ly=98)
s.arrow(180, 152, 248, 205, "everything else", lx=200, ly=262)
s.box(250, 60, 330, 70, "API chain  @Order(1)", "ApiKeyAuthFilter reads X-Api-Key → Person; stateless; CSRF off; 401 on failure", fill=ACCENT_SOFT, stroke=ACCENT)
s.box(250, 170, 330, 70, "Web chain  @Order(2)", "form login → session cookie; CSRF on; /, /login, /register, /p/**, /css/** are open", fill=ACCENT_SOFT, stroke=ACCENT)
s.arrow(580, 95, 638, 95)
s.arrow(580, 205, 638, 205)
s.box(640, 60, 340, 70, "ROLE_INDIVIDUAL / ROLE_COMPANY", "derived from account_type; the same Person object either way", bold=False)
s.box(640, 170, 340, 70, "Data scoping in queries", "findByIdAndPersonId(...): a user can only reach rows that carry their own id", bold=False)
s.text(500, 278, "Passwords: BCrypt. API keys: 192 random bits, rotatable from Settings.", 11.5, INK2, anchor="middle")
s.save("05-security.svg")

# ------------------------------------------------------------------ 6. startup
s = Svg(1000, 260)
s.text(20, 30, "What Spring Boot does at startup (java -jar sharpen.jar)", 15, INK, weight=800)
steps = [("Read config", "application.yml plus the active profile"), ("Auto-configure", "looks at the jars on the classpath and wires web, JPA, security, Thymeleaf"),
         ("Component scan", "io.sharpen.* classes become beans; constructor injection"), ("Data + JPA", "HikariCP pool; Hibernate maps entities; ddl update / validate"),
         ("Security chains", "the two SecurityFilterChain beans"), ("Tomcat :8080", "embedded server starts and listens"), ("Runner", "ApplicationRunner: DemoDataLoader seeds accounts when demo-data=true")]
x = 26
for i, (t, sub) in enumerate(steps):
    s.box(x, 70, 126, 110, t, sub, fill=ACCENT_SOFT if i in (1, 2) else SURF, stroke=ACCENT if i in (1, 2) else LINE)
    if i < len(steps) - 1:
        s.arrow(x + 126, 125, x + 137, 125)
    x += 138
s.text(500, 225, "No XML, no web.xml, no application server to install: the jar contains the server and the configuration.", 11.5, INK2, anchor="middle")
s.save("06-startup.svg")

# ------------------------------------------------------------------ 7. deployment: where things live
s = Svg(1000, 480)
s.text(20, 30, "Where Sharpen runs in production: one free Oracle VM, three containers, one domain name", 15, INK, weight=800)

s.box(30, 120, 170, 90, "A visitor", "opens https://sharpen-ai.duckdns.org in any browser")
s.arrow(200, 150, 240, 112, "1. which IP?", lx=190, ly=118)
s.box(242, 66, 170, 70, "DuckDNS", "free DNS: the name points at the VM's public IP", fill=SURF)
s.arrow(412, 101, 468, 101, "IP", lx=440, ly=94)
s.arrow(200, 180, 468, 250, "2. HTTPS to that IP, port 443", lx=300, ly=240)

s.group(466, 50, 514, 410, "Oracle Cloud, region Ashburn - Always Free tier")
s.box(484, 176, 478, 44, "VCN security list", "lets in only ports 22 (SSH), 80 (HTTP) and 443 (HTTPS)", fill=WARN_SOFT, stroke=WARN)
s.group(484, 234, 478, 214, "VM: VM.Standard.A1.Flex, Ubuntu 24.04, Docker", fill=SURF)
s.box(500, 278, 140, 104, "caddy", "web server; fetches and renews the HTTPS certificate itself; forwards to the app", fill=ACCENT_SOFT, stroke=ACCENT)
s.arrow(640, 330, 662, 330)
s.box(664, 278, 140, 104, "app", "sharpen.jar in a container; Spring Boot, profile postgres", fill=GOOD_SOFT, stroke=GOOD)
s.arrow(804, 330, 826, 330)
s.box(828, 278, 118, 104, "db", "PostgreSQL 16; data in a Docker volume, survives restarts", fill=SURF)
s.text(723, 404, "caddy talks to app on port 8080 and app to db on 5432 - inside the VM only", 11, INK2, anchor="middle")
s.text(723, 420, "All three start together from deploy/docker-compose.prod.yml", 11, INK2, anchor="middle")
s.text(723, 436, "Settings (domain, DB password, admin email) live in deploy/.env, never in git", 11, INK2, anchor="middle")
s.arrow(570, 220, 570, 276)

s.box(30, 320, 170, 90, "You", "ssh -i key ubuntu@IP to manage it; git pull to update", fill=SURF)
s.arrow(200, 350, 468, 210, "SSH, port 22", lx=300, ly=292)
s.save("07-deployment.svg")

# ------------------------------------------------------------------ 8. deployment: the steps in order
s = Svg(1000, 300)
s.text(20, 30, "Deploying, step by step — each box is one section of the deployment guide", 15, INK, weight=800)
steps = [("Prepare", "Oracle account, SSH key, OCI CLI, open ports 80/443, DuckDNS name"),
         ("Get a server", "create an Ampere A1 VM; if \"out of capacity\", let the retry script hunt for you"),
         ("Point the name", "put the VM's public IP into DuckDNS"),
         ("Prepare the VM", "ssh in; setup-vm.sh installs Docker and the firewall"),
         ("Deploy", "git clone; fill deploy/.env; docker compose up"),
         ("Check", "the site opens with a padlock; register the admin account"),
         ("Day two", "git pull + compose up to update; nightly backup; read /admin/feedback")]
x = 26
for i, (t, sub) in enumerate(steps):
    s.step(x + 63, 62, i + 1)
    s.box(x, 80, 126, 140, t, sub, fill=ACCENT_SOFT if i == 4 else SURF, stroke=ACCENT if i == 4 else LINE)
    if i < len(steps) - 1:
        s.arrow(x + 126, 150, x + 137, 150)
    x += 138
s.text(500, 262, "Only step 2 can take a while (Oracle's free ARM pool is often full). Everything else is about an hour the first time.", 11.5, INK2, anchor="middle")
s.save("08-deploy-steps.svg")
