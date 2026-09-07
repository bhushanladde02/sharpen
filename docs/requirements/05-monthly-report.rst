Monthly report
==============

Purpose
-------

The report is the monthly moment of honesty: what you used, for what, how much of the thinking stayed yours,
and what to change. It is the same document on screen and as a PDF, and once generated it is frozen.

Generation
----------

.. list-table::
   :header-rows: 1
   :widths: 8 92

   * - ID
     - Requirement
   * - MR-1
     - A scheduled job runs at 02:00 UTC on the 1st and generates the previous month for every individual
       account that has at least one session in that month.
   * - MR-2
     - Generation is idempotent per (person, month): regenerating replaces the stored snapshot.
   * - MR-3
     - Any month from the first session onward can be previewed live; a live preview is clearly labelled as not
       stored.
   * - MR-4
     - The stored payload is the full report model as JSON, so the page and the PDF re-render without
       recomputation and survive later changes to sessions or to the scoring formula.
   * - MR-5
     - The PDF is A4, rendered from an XHTML template with the same numbers; file name
       ``sharpen-<handle>-<yyyy-MM>.pdf``.

Content
-------

In order, top to bottom:

1. **Header** — month, person, headline, profile URL; whether the report is generated or a live preview.
2. **AI score** — composite, band, confidence, five dimension bars, delta versus the previous month.
3. **The month in numbers** — sessions, hours, prompts (and tokens when imports carried them), active days, each
   with delta versus the previous month; professional vs personal split; minutes per week stacked by context.
4. **Brain-active check** — a one-sentence verdict, human share of the work (human minutes ÷ total rated
   minutes), output-checked %, learned-something %, rated ÷ total sessions.

   Verdicts: *Sharp* (human ≥ 60 % and verified ≥ 60 %), *Balanced* (human ≥ 40 %), otherwise *Leaning on the
   model* with an instruction to reclaim one task type.
5. **Tools / task types / sources** — minutes share per tool (top 6) and per task type; count of sessions per
   source (logged, extension, import).
6. **What stood out** — rule-based insights: score change ≥ 25 points either way; dominant tool; dominant task
   type and whether the spread is good; lopsided professional/personal balance (≥ 80 %); all score flags except
   the provisional one.
7. **Next month, try** — up to three nudges chosen by the weakest dimensions:

   * independence < 60 → write the first draft yourself for 15 minutes, then use AI to review
   * verification < 70 → end every session by checking one claim or running one test
   * growth < 50 → once a week ask for an explanation, then redo the step by hand
   * breadth < 50 → try one task type you have not logged
   * effectiveness < 60 → front-load goal, constraints and format in the first prompt
   * human share < 40 % → pick one recurring task and do it fully unassisted this month
   * nothing weak → keep the pattern and keep logging
8. **Footer** — the weights, the "only rated sessions count, hours never raise the score" statement, generation
   date.

Release 1
---------

* Email delivery of the PDF on generation (MR-6).
* A team roll-up for company accounts that opt in (MR-7) — aggregate only, never per-person without consent.
