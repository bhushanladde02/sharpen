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
   * - PR-6
     - **Say it in public, from the code.** ``/privacy`` states what is stored, what is public (profiles are public
       by default for individuals), what is never collected, the one cookie, the extension, where the data lives,
       retention (deletion immediate; backups at most 14 days) and the person's rights. The thresholds it quotes
       are read from ``InsightsService``; ``PrivacyController.EFFECTIVE`` is bumped with any change to a promise.
   * - PR-7
     - **No third-party requests.** Rendering a page contacts no host but Sharpen's own: no analytics, no
       advertising, no CDN — the three font families are self-hosted under ``static/fonts`` (SIL OFL 1.1). A test
       asserts that no page references ``googleapis``/``gstatic``.

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
   * - SE-8
     - Behind the proxy, client address and scheme come from forwarded headers only when sent by a private-network
       proxy (Tomcat remote-IP valve); rate limits and visitor counting use that address, so a client-supplied
       ``X-Forwarded-For`` cannot reset a limit. The session cookie is ``Secure`` over https, ``HttpOnly`` and
       ``SameSite=Lax``. Tested against the real server in ``ProxyHeadersTest``.
   * - SE-13
     - Google/GitHub/LinkedIn sign-in: Spring Security checks state, nonce and (Google, LinkedIn) the ID token's signature, issuer,
       audience and expiry. No automatic merge by email — Sharpen never verified sign-up emails, so merging would
       let someone pre-register a victim's address and keep a password into the account the victim later opens
       with Google; linking needs a signed-in person pressing *Connect* (a POST with CSRF). A password-less account is
       invisible to the password form. Provider access tokens are discarded after sign-in. Redirects after a
       refused sign-in carry only values from Sharpen's own fixed lists — a configured provider id and one of
       ``SocialLoginHandlers.CODES`` — URL-encoded (CodeQL finding on PR #50).
       Each rule has a test that fails when the rule is removed.

Release 1 additions
-------------------

* TLS termination and HSTS at the edge — done (Caddy); the app side of the proxy is SE-8.
* Rate limiting on ``/register``, ``/login`` and ``/api/**`` (SE-9) — ``/register`` and the contact form are done (``SpamGuard``).
* Email verification and password reset with expiring tokens (SE-10).
* Company account gating and profile-view audit log (SE-11, see CV-6 / CV-7).
* Dependency scanning in CI and a documented data-retention policy (SE-12) — retention is now documented on
  ``/privacy`` (PR-6); dependency scanning is Dependabot.
