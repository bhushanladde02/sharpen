Data ingestion
==============

Five routes bring usage in. All of them end in the same ``usage_session`` row and the same score; the only
difference is which fields are known on arrival.

.. list-table::
   :header-rows: 1
   :widths: 22 36 14 28

   * - Route
     - Carries
     - Self-assessed
     - Source tag
   * - Log form
     - everything
     - yes
     - ``MANUAL``
   * - Sharpen CSV
     - everything (rows may omit assessment fields)
     - if all four present
     - ``API_IMPORT``
   * - Provider usage export
     - date, model, requests, tokens
     - no
     - ``API_IMPORT``
   * - Browser extension
     - date, site → tool, minutes, prompts
     - no
     - ``EXTENSION``
   * - REST API
     - whatever the client sends
     - if all four present
     - ``EXTENSION`` or ``API_IMPORT``

Common rules
------------

.. list-table::
   :header-rows: 1
   :widths: 8 92

   * - ID
     - Requirement
   * - DI-1
     - Every external row must carry a stable ``externalId``; the pair (person, externalId) is unique. Re-sending
       updates the existing row (idempotent).
   * - DI-2
     - An update to an already self-assessed row changes volume fields only (minutes, prompts, tokens); the four
       assessment answers are never overwritten by an import.
   * - DI-3
     - Rows missing the assessment land as *needs rating* and are excluded from the score until rated.
   * - DI-4
     - Rows without a context take the default the user chose for the upload (professional by default).
   * - DI-5
     - Dates are accepted as ``yyyy-MM-dd``, ISO date-time, or epoch seconds/milliseconds; unreadable dates skip
       the row with a row-numbered error.
   * - DI-6
     - Task types are matched loosely (``code`` → coding, ``Data & analysis`` → analysis, ``email`` → admin);
       unknown values become *other*.
   * - DI-7
     - The import result reports created, updated, skipped, and a list of row-level errors; the page offers a
       direct link to rate what arrived.

Sharpen CSV
-----------

Header row required; column order free; names case-insensitive. Detected by the presence of ``human_pct``.

.. code-block:: text

   date,context,tool,task,minutes,prompts,human_pct,verified,learned,outcome,notes,external_id
   2026-09-01,professional,Claude,coding,45,6,60,yes,no,4,Refactored the DAG factory,sample-1

``verified`` / ``learned`` accept yes/no, true/false, 1/0. ``tokens_in`` and ``tokens_out`` are optional.
Example: ``docs/samples/sessions.csv``.

Provider usage export
---------------------

Any CSV with a date column plus some of ``model``, ``n_requests`` / ``requests``, ``n_context_tokens_total`` /
``input_tokens``, ``n_generated_tokens_total`` / ``output_tokens``. One row per day per model becomes one
session:

* tool derived from the model name (``gpt-*`` → OpenAI API, ``claude-*`` → Claude API, ``gemini-*`` → Gemini API)
* minutes estimated at **3 per request** until the person rates the session (the estimate is noted in ``notes``)
* ``externalId`` = ``usage:<date>:<model>``

Example: ``docs/samples/openai-usage-export.csv``.

Browser extension
-----------------

Chrome Manifest V3, in ``chrome-extension/``.

.. list-table::
   :header-rows: 1
   :widths: 8 92

   * - ID
     - Requirement
   * - EX-1
     - Recognised sites: chatgpt.com, chat.openai.com, claude.ai, gemini.google.com, copilot.microsoft.com,
       perplexity.ai, github.com/copilot. Adding a site is one line in ``TOOLS``.
   * - EX-2
     - Active time is sampled every 15 s while a recognised tab is focused and there was activity (keystroke,
       click, scroll, tab switch) within the last 2 minutes.
   * - EX-3
     - A prompt is counted on Enter (without Shift) in a composer or a click on a send/submit button. The text is
       never read, stored or transmitted.
   * - EX-4
     - Records are aggregated per (day, tool) and synced daily and on demand to ``POST /api/v1/sessions`` with
       ``externalId = ext:<date>:<tool>``; the popup shows what has been captured and the last sync time.
   * - EX-5
     - Settings: portal URL, API key, default context. Stored in ``chrome.storage.local`` only.

Manual upload of the extension payload (``docs/samples/extension-payload.json``) is supported for users who do
not want the extension to talk to the server directly.
