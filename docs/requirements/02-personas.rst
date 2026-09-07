Personas and user journeys
==========================

Personas
--------

**Individual — "Priya", senior engineer.**
Uses Claude and Copilot daily at work, ChatGPT at home. Wants proof, for herself and for her next employer,
that she directs the work rather than outsourcing it. Will log sessions if it takes seconds, not minutes.

**Individual — "Marcus", marketing analyst.**
Heavy ChatGPT user, rarely checks the output. Has not noticed how dependent he has become. Needs the report to
tell him plainly, and to give him one thing to change.

**Company — "Northwind Talent", recruiter / hiring manager.**
Screens dozens of candidates. Wants a single comparable number plus the dimensions behind it, filterable by
role, industry and tools. Does not want to read session logs. Does not want to be told how many hours someone
spent prompting.

**Integrator — a developer scripting an import.**
Has a usage export or an internal tool and wants to push sessions in with a key. Expects idempotent upserts and
a JSON error when something is wrong.

Journeys
--------

Individual, first day
~~~~~~~~~~~~~~~~~~~~~

1. Register (name, email, password, account type *Individual*).
2. Land on the dashboard with no data; the empty state links to *Log a session* and *Import*.
3. Log one session: date, context, tool, task type, minutes, prompts, then the four self-assessment questions.
4. Dashboard shows a *provisional* score and the flag explaining that eight rated sessions give full confidence.
5. Settings: fill in headline, title, tools; copy the API key for the extension; profile is public by default.

Individual, end of month
~~~~~~~~~~~~~~~~~~~~~~~~

1. On the 1st, the scheduler freezes last month's report.
2. The person opens *Monthly reports*, reads the brain-active verdict and the three "next month, try" items.
3. Downloads the PDF or shares the profile link.
4. Rates any sessions the extension captured that are still unrated; the score updates immediately.

Company
~~~~~~~

1. Register as *Company*. Sign-in lands on *Candidates*.
2. Search "data engineer Sacramento"; sort by *verification*.
3. Open a profile; read the 6-month trend and the month-by-month table; note flags (*provisional*, *reliance risk*).
