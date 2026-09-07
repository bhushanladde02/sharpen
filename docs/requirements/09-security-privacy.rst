Security and privacy
====================

Privacy principles
------------------

.. list-table::
   :header-rows: 1
   :widths: 8 92

   * - ID
     - Principle
   * - PR-1
     - **No content, ever.** Sharpen never stores what a person typed into an AI tool or what it answered. The
       extension observes only keystroke *events* (Enter), send-button clicks, focus and time.
   * - PR-2
     - **The person owns visibility.** The profile can be made private at any time; private profiles vanish from
       the candidates list immediately.
   * - PR-3
     - **Minimum public surface.** The public page shows aggregates only (see :doc:`07-profile-and-companies`).
       Session rows, notes, tokens and API keys are never exposed to other accounts.
   * - PR-4
     - **No dark patterns around volume.** Nothing in the product encourages using AI more; the score and every
       insight are volume-neutral.
   * - PR-5
     - **Export and delete.** Release 1 must offer a full export (CSV + JSON) and account deletion that removes
       every session and report.

Security controls (prototype)
-----------------------------

.. list-table::
   :header-rows: 1
   :widths: 8 92

   * - ID
     - Control
   * - SE-1
     - Passwords hashed with BCrypt; no plaintext anywhere.
   * - SE-2
     - Two security filter chains: form login with CSRF for the browser UI; stateless API-key chain for
       ``/api/**`` with CSRF disabled and no session.
   * - SE-3
     - Role separation: ``INDIVIDUAL`` cannot reach ``/candidates``; ``COMPANY`` has no dashboard, sessions or
       reports.
   * - SE-4
     - Every data access is scoped by the signed-in person's id in the repository query (``findByIdAndPersonId``),
       not by trusting ids from the request.
   * - SE-5
     - API keys are 24 random bytes (192 bits) from ``SecureRandom``, prefixed ``shp_``; rotation invalidates the
       old key instantly.
   * - SE-6
     - Uploads capped at 10 MB; CSV parsed in memory with no external process.
   * - SE-7
     - The H2 console is enabled only in the dev profile and disabled in ``postgres``.

Release 1 additions
-------------------

* TLS termination and HSTS at the edge (SE-8).
* Rate limiting on ``/register``, ``/login`` and ``/api/**`` (SE-9).
* Email verification and password reset with expiring tokens (SE-10).
* Company account gating and profile-view audit log (SE-11, see CV-6 / CV-7).
* Dependency scanning in CI and a documented data-retention policy (SE-12).
