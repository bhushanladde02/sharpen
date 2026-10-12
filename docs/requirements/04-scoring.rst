The AI score
============

The score is the product. Everything else exists to feed it or display it. This page is the normative
specification; ``AiScoreService`` implements it and ``AiScoreServiceTest`` pins the properties below.

Principles
----------

.. list-table::
   :header-rows: 1
   :widths: 8 92

   * - ID
     - Principle
   * - SC-1
     - **Only self-assessed sessions count.** Imported or captured usage is visible as volume but cannot move the
       score until the person answers the four questions. This keeps a human in the loop and makes the score
       hard to inflate with automation.
   * - SC-2
     - **Volume never raises the score.** Every dimension is a rate or a weighted mean. Logging twice as many
       hours with the same behaviour yields the same score.
   * - SC-3
     - **Sessions are minute-weighted.** Weight = minutes, floored at 5 and capped at 240, so a quick question still
       counts and one marathon cannot dominate a month.
   * - SC-4
     - **The score is explainable.** Every number on the page can be traced to a formula on this page; there is no
       machine-learned component in the prototype.
   * - SC-5
     - **Windows.** Dashboard, public profile and candidates use a rolling 90-day window ending today. Reports use
       the calendar month.

Dimensions
----------

Each dimension is 0–100.

.. list-table::
   :header-rows: 1
   :widths: 18 10 72

   * - Dimension
     - Weight
     - Definition
   * - Independence
     - 0.30
     - Weighted mean of *human contribution %* — how much of the finished work came from the person.
   * - Effectiveness
     - 0.20
     - ``0.7 × outcome + 0.3 × promptEfficiency`` where outcome maps 1–5 to 0–100 and prompt efficiency is
       ``100 / (1 + (prompts/outcome − 1)/4)``, clamped 0–100; a session with unknown prompt count scores a
       neutral 60.
   * - Verification
     - 0.20
     - Weighted share of sessions where *output verified* is true.
   * - Growth
     - 0.20
     - Weighted share of sessions where *learned something* is true.
   * - Breadth
     - 0.10
     - ``(2 × min(1, distinctTaskTypes/6) + min(1, distinctTools/3)) / 3 × 100``.

Composite
---------

``composite = round(Σ weight × dimension)`` → 0–100, the same scale as each dimension (the weights add up to 1).

Bands: **Expert** ≥ 80 · **Strong** ≥ 65 · **Developing** ≥ 50 · **Dependent** ≥ 35 · **At risk** below.

Until 12 October 2026 the composite was ten times larger (0–1000, bands at 800/650/500/350). The extra digit
suggested a precision the answers behind it do not have, and the profile page said "of 1000" next to dimensions
out of 100. Every number is now out of 100. Monthly reports stored before the change are converted once at
start-up (``ReportService.upgradeStoredReportsToScale100``: the two composites, the change between them, the
*Score up/down* insight and ``monthly_report.ai_score``) and marked ``"scale": 100`` so it never happens twice; the
rest of a frozen report was already on its final scale.

Confidence and flags
--------------------

* **Confidence.** *None* with no rated sessions (no score is shown); *Low / provisional* with fewer than 8 rated
  sessions; *Full* otherwise.
* **Reliance risk.** Set when more than half of *professional* weighted minutes were in sessions with human
  contribution below 30 %.
* **Flags** are short sentences shown beside the score: provisional, unrated sessions present, reliance risk,
  verification below 50, growth below 30, strength (independence and verification both ≥ 70), narrow usage
  (breadth below 40 with ≥ 5 sessions).

Worked example
--------------

Ten professional sessions of 45 minutes, human contribution 85 %, all verified, half learned, outcome 5, prompts
4, spread over six task types and three tools:

* Independence 85 · Effectiveness ≈ 0.7×100 + 0.3×92 ≈ 98 · Verification 100 · Growth 50 · Breadth 100
* Composite ≈ 10 × (25.5 + 19.5 + 20 + 10 + 10) = **850 → Expert**.

Eight professional sessions of 30 minutes, human contribution 15 %, never verified, never learned, outcome 4:

* Independence 15 · Effectiveness ≈ 0.7×75 + 0.3×100 ≈ 82 · Verification 0 · Growth 0 · Breadth ≈ 22
* Composite ≈ 10 × (4.5 + 16.5 + 0 + 0 + 2.2) ≈ **232 → At risk**, with the reliance-risk flag.

Open questions for Release 1
----------------------------

.. todo::
   Weights are a product decision, not a technical one. Decide whether verification should outweigh
   effectiveness for hiring use, and whether personal sessions should carry a lower weight in the
   employer-facing score.

.. todo::
   Consider verifiable signals (tests run, sources cited, diffs kept) as a sixth dimension once the extension can
   observe them, so the score is not purely self-reported.
