Sharpen for companies — a short introduction
=============================================

*One page for a hiring manager, a team lead or an HR partner who has never heard of Sharpen. Copy it into an
email, or point people at the live site.*

The problem
-----------

Everyone on your shortlist "uses AI". That tells you nothing. Two engineers can both spend three hours a day
with Copilot or ChatGPT: one is directing the tool, checking what comes back and learning as they go; the other
is pasting in the task and shipping whatever appears. Their résumés look identical. The difference shows up
six months after the hire, in production.

What Sharpen is
---------------

Sharpen is a small web service where an individual keeps a log of how they work with AI — at work and at home —
and gets back three things:

* **A monthly report** of what they used, for what, and how much of the thinking stayed theirs.
* **An AI score (0–1000)** that rewards *independence, verification and learning*, never hours or prompt
  volume. Using AI more does not raise it; using it well does.
* **A public AI profile** — a LinkedIn-style page with only the AI-related facts, which they can share with an
  employer.

For companies there is a fourth thing: a **candidates view** that lists every public profile with its score,
the five dimensions behind it, session count and warning flags, searchable and sortable.

What the score measures
-----------------------

Each logged session carries a four-question self-assessment: how much of the result was the person's own
contribution (0–100 %), whether they verified the output, whether they learned something they could not do
before, and how good the outcome was. From these, over a rolling 90-day window, Sharpen computes:

.. list-table::
   :header-rows: 1
   :widths: 22 12 66

   * - Dimension
     - Weight
     - Question it answers
   * - Independence
     - 30 %
     - How much of the work was the person's own judgement rather than the model's?
   * - Effectiveness
     - 20 %
     - Did they get good outcomes without a hundred prompts to get there?
   * - Verification
     - 20 %
     - Do they check what the model says before acting on it?
   * - Growth
     - 20 %
     - Are they learning, or outsourcing the same thing every week?
   * - Breadth
     - 10 %
     - Do they use AI across different kinds of work and tools?

Two flags matter more than the number. **Provisional** means fewer than eight rated sessions — not enough
evidence yet. **Reliance risk** means that in more than half of their professional AI time the model did most
of the work; that is the person you want to ask harder questions in the interview.

Why it is hard to fake
----------------------

Only sessions the person has rated count. Usage imported automatically — from a browser extension or a
provider's usage export — shows up as volume but cannot move the score until the four questions are answered,
one session at a time. There is no way to bulk-upload a good score, and logging more sessions of low-quality
work lowers it rather than raising it. The score is self-reported, like a résumé — but unlike a résumé it is
structured, dated, consistent across candidates, and the incentives point toward honesty: claiming 100 %
human contribution on every session is exactly what a reliance-risk pattern looks like when the effectiveness
and verification numbers do not back it up.

What you can do with it today
-----------------------------

#. Create a free **company account** on the site. Company accounts land on the candidates view.
#. Search by name or headline, sort by score, independence, verification or growth, and open any profile.
#. Ask candidates for their Sharpen profile link the same way you ask for a GitHub or LinkedIn URL. A
   candidate with a private profile can make it public in one click, or send you a PDF of their monthly
   report instead.
#. Use the numbers as interview prompts, not as a gate: "Your verification score is high — what do you check,
   and what have you caught?"

What it is not
--------------

Sharpen is a working prototype in a free pilot. It does not monitor anyone: nothing is recorded unless the
person logs it, the browser extension records only minutes and prompt counts (never text), and every profile
is private until its owner makes it public. It is not a certification, and a score of 800 is not a hiring
decision — it is a structured, comparable signal about a habit that otherwise goes unmeasured.

Try it
------

The pilot is free and needs no card: ``https://sharpenscore.com``. Register as a company, browse the
candidates view, and use the *Feedback* link on any page to tell us what would make this useful in your hiring
process — that feedback is what decides what gets built next.
