Public profile and company view
===============================

Public profile
--------------

URL: ``/p/{handle}``. Readable without an account when the profile is public.

.. list-table::
   :header-rows: 1
   :widths: 8 92

   * - ID
     - Requirement
   * - PP-1
     - Content is limited to AI-related facts: display name, headline, job title, industry, years of experience,
       location, tools, rolling 90-day score with dimensions and flags, 6-month score trend, month-by-month table
       (score, five dimensions, sessions, hours, work/personal split), total sessions and first-session month,
       number of generated reports.
   * - PP-2
     - No session-level data, notes, prompts or tokens are shown publicly.
   * - PP-3
     - Public by default at registration; a private profile shows a neutral "no public profile here" page to
       everyone except the owner and disappears from the candidates list.
   * - PP-4
     - Handles are lower-case letters, digits and dashes; unique; changing one changes the URL (no redirect from
       the old handle in the prototype).
   * - PP-5
     - The page is the same for owner and visitor, plus *Edit profile* / *Reports* buttons for the owner.

Company view
------------

URL: ``/candidates``. Company accounts only.

.. list-table::
   :header-rows: 1
   :widths: 8 92

   * - ID
     - Requirement
   * - CV-1
     - Lists every public individual with composite score and band, five dimensions, session count, headline,
       title, industry, location, tools, and flags *provisional* and *reliance risk*.
   * - CV-2
     - Text search across name, headline, title, industry, location and tools; case-insensitive substring.
   * - CV-3
     - Sort by score (default), independence, verification, growth, or name. **Hours and prompt volume are never
       a sort key.**
   * - CV-4
     - Scores are computed on request in the prototype; Release 1 should cache the rolling score per person and
       refresh it when sessions change.
   * - CV-5
     - Company accounts have no profile page of their own and are excluded from the list.

Release 1
---------

* Company onboarding gate: invitation code or verified company email domain (CV-6).
* Audit log of which company viewed which profile, visible to the individual (CV-7).
* Saved searches and shortlists (CV-8).
* "Request an introduction" that the individual can accept or ignore — no contact details leak without
  consent (CV-9).
