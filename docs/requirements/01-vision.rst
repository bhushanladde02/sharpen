Vision and goals
================

Problem
-------

AI assistants are becoming the default way people write, code, research and plan. Two things get lost:

* **The person's own capability.** When the model does the first draft every time, the person stops practising
  the skill. Over years that makes people less able to judge, correct and improve what AI produces — which
  is exactly the human contribution AI needs in order to keep improving.
* **Any evidence of how someone actually works with AI.** Résumés say "proficient with AI tools". Nobody can tell
  whether a candidate leads the work and checks the output, or pastes whatever the model returns.

Sharpen makes both visible, to the person first and, if they choose, to employers.

Goals
-----

.. list-table::
   :header-rows: 1
   :widths: 6 44 50

   * - #
     - Goal
     - How the product serves it
   * - G1
     - Keep the human brain active and sharp, so people remain able to contribute beyond what AI produces.
     - The score rewards *human contribution*, *verification* and *learning*; it never rewards volume. The monthly
       report has a "brain-active check" and concrete nudges for the next month.
   * - G2
     - Help each person use AI more effectively.
     - Per-session self-assessment, effectiveness (outcome per prompt), breakdown by tool and task type, and
       rule-based insights.
   * - G3
     - Give companies an honest per-person AI score so they can hire people who can work on their own, and spend
       fewer tokens on people who cannot.
     - Company accounts browse public profiles, ranked and sortable by dimension; volume is displayed but never
       used to rank.
   * - G4
     - Be the "LinkedIn of AI": a profile that carries only AI-related facts — no feed, no endorsements, no noise.
     - The public profile shows headline, title, industry, experience, tools, score, trend and month-by-month
       table, and nothing else.

Non-goals (prototype)
---------------------

* Capturing prompt or response *content*. Sharpen records time, counts and self-assessment only.
* Replacing a résumé or LinkedIn. The profile is a supplement that links out.
* Ranking people by how much AI they use. More usage is neutral by design.
* Automated "AI detection" of a person's work. All judgement of quality comes from the person's own
  self-assessment plus, later, verifiable signals (tests run, sources cited).

Success criteria for the prototype
----------------------------------

* A new user can register, log a session and see a provisional score in under two minutes.
* A month of usage produces a report a person would be willing to send to a manager.
* A company user can shortlist candidates on the score dimensions without reading a single session.
* Every ingestion route (form, extension, provider export, CSV, API) lands data that flows into the same score.
