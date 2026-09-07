HTTP API
========

Base path ``/api/v1``. JSON in and out. Stateless; no cookies.

Authentication
--------------

Send the account's API key in ``X-Api-Key: shp_…`` (or ``Authorization: Bearer shp_…``). Keys are issued at
registration and rotated from *Settings*. A missing or unknown key yields ``401``. Every endpoint is scoped
to the key's owner; there is no cross-account access.

Endpoints
---------

``GET /health``
   Open. ``{"status":"ok"}``.

``GET /me``
   ``{"handle": "...", "displayName": "...", "accountType": "INDIVIDUAL"}``

``GET /me/score``
   The rolling 90-day ``AiScore``:

   .. code-block:: json

      {
        "composite": 721, "independence": 61, "effectiveness": 79, "verification": 82,
        "growth": 58, "breadth": 100, "sessionCount": 70, "assessedCount": 68,
        "confidence": "FULL", "relianceRisk": false,
        "flags": [{"kind": "assess", "message": "2 imported sessions not rated yet — …"}]
      }

``POST /sessions?source=extension|import&defaultContext=PROFESSIONAL|PERSONAL``
   Upsert a batch. Body:

   .. code-block:: json

      {"sessions": [
        {"externalId": "ext:2026-09-02:Claude", "date": "2026-09-02", "tool": "Claude",
         "task": "coding", "context": "professional", "minutes": 42, "prompts": 7,
         "humanPct": 60, "verified": true, "learned": false, "outcome": 4,
         "notes": "optional", "tokensIn": 5000, "tokensOut": 1200}
      ]}

   Only ``date`` is required; ``externalId`` is strongly recommended (without it every call creates a new row).
   A row is self-assessed when ``humanPct``, ``verified``, ``learned`` and ``outcome`` are all present.

   Response ``200``:

   .. code-block:: json

      {"created": 1, "updated": 0, "skipped": 0, "errors": []}

   Errors are per row (``"Row 3: missing or unreadable date"``); the call still succeeds for the other rows.

Conventions
-----------

* Dates: ``yyyy-MM-dd`` preferred; ISO date-time and epoch accepted.
* Enumerations are case-insensitive on input and upper-case on output.
* Release 1 adds rate limiting per key, ``GET /sessions`` for export, and ``GET /reports/{yyyy-MM}`` returning the
  stored report JSON.
