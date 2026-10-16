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
     - **Say it in public, from the code.** ``/privacy`` states what is stored, what is public (profiles are private
       until their owner makes them public; earlier accounts keep their setting), what is never collected, the one cookie, the extension, where the data lives,
       retention (deletion immediate; backups at most 14 days) and the person's rights. The thresholds it quotes
       are read from ``InsightsService``; ``PrivacyController.EFFECTIVE`` is bumped with any change to a promise.
   * - PR-7
     - **No third-party requests.** Rendering a page contacts no host but Sharpen's own: no analytics, no
       advertising, no CDN — the three font families are self-hosted under ``static/fonts`` (SIL OFL 1.1). A test
       asserts that no page references ``googleapis``/``gstatic``. Visitor countries come from a list the server
       downloads monthly, not from a lookup service (SE-14).

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
     - The H2 console is enabled only in the dev profile and disabled in ``postgres`` — and the security rules
       that open ``/h2-console`` (permit-all, no CSRF, frames allowed) exist only when
       ``spring.h2.console.enabled`` is true, so a profile mix-up cannot expose it: in production the path is an
       ordinary protected address that redirects to the login form. A test runs with the console off and checks
       that.
   * - SE-8
     - Behind the proxy, client address and scheme come from forwarded headers only when sent by a private-network
       proxy (Tomcat remote-IP valve); rate limits and visitor counting use that address, so a client-supplied
       ``X-Forwarded-For`` cannot reset a limit. The session cookie is ``Secure`` over https, ``HttpOnly`` and
       ``SameSite=Lax``. Tested against the real server in ``ProxyHeadersTest``.
   * - SE-9
     - Rate limits, all in memory and per instance (``WindowLimiter``: at most *N* per window, bounded map).
       **Sign-in**: ten wrong passwords for one account, or forty from one address, in fifteen minutes lock that
       account or address for the rest of the window (``LoginThrottle``, ``/login?locked``); the count is checked
       *before* the password, so a locked account costs no BCrypt work, and a correct sign-in clears the account's
       count but not the address's. **Sign-up and contact**: honeypot, signed timestamp and a per-address hourly
       limit (``SpamGuard``). **API**: 600 requests a minute per address, then ``429`` with ``Retry-After: 60``,
       before the key is looked up. **PDFs**: the one request that costs real CPU — an account may download 20
       report PDFs in ten minutes, then sees the 429 page (``ReportController.PDF_LIMIT``). Addresses come from SE-8.
   * - SE-13
     - Google/GitHub/LinkedIn sign-in: Spring Security checks state, nonce and (Google, LinkedIn) the ID token's signature, issuer,
       audience and expiry. No automatic merge by email — Sharpen never verified sign-up emails, so merging would
       let someone pre-register a victim's address and keep a password into the account the victim later opens
       with Google; linking needs a signed-in person pressing *Connect* (a POST with CSRF). A password-less account is
       invisible to the password form. Provider access tokens are discarded after sign-in. Redirects after a
       refused sign-in carry only values from Sharpen's own fixed lists — a configured provider id and one of
       ``SocialLoginHandlers.CODES`` — URL-encoded (CodeQL finding on PR #50).
       Each rule has a test that fails when the rule is removed.
   * - SE-14
     - Visitor countries: looked up in memory from DB-IP's list held on the server, so no address is sent to a
       geolocation service, and only the ISO code is stored — never the address. Only literal IPv4/IPv6 addresses are
       parsed (no DNS lookups from attacker-supplied text). The monthly download is capped at 64 MB, must parse
       into at least 100,000 ranges with under 1 % unreadable lines before it replaces the current list, and is
       written to a temporary file and moved into place atomically. Off in development and tests.
   * - SE-15
     - **Content Security Policy** on every page: ``default-src 'self'``; scripts, styles, fonts, images and
       fetches only from Sharpen itself (``img-src`` also ``data:`` for the inline SVG marks); ``object-src
       'none'``; ``base-uri 'self'``; ``frame-ancestors 'self'``; ``form-action 'self'`` plus the three sign-in
       providers, because Chrome applies ``form-action`` to the redirect a form ends at and *Settings → Connect* is a
       form that ends at Google, GitHub or LinkedIn. ``'unsafe-inline'`` is still allowed for the few inline
       scripts and ``style=`` attributes; replacing it with per-request nonces is the next step. Sent by
       ``SecurityConfig``; a test asserts the header.
   * - SE-16
     - **Form tokens are single-use.** The signed timestamp in the sign-up and contact forms carries a random nonce,
       and a token that has been accepted once is refused the second time (``SpamGuard``, "form open too long"), so
       one token cannot be harvested and replayed to make many submissions inside its window.
   * - SE-17
     - **Uploads are checked before they are decoded.** A profile picture's dimensions are read from the file
       header first and anything over 50 million pixels is refused, so a tiny file that declares a huge canvas (a
       decompression bomb) never allocates its canvas. The 10 MB cap (SE-6) stays.
   * - SE-18
     - **Role gates match the account types.** ``/dashboard``, ``/sessions/**``, ``/reports/**`` and ``/import/**``
       require ``INDIVIDUAL`` in the security rules, not only in each controller, so a company account gets a 403
       there rather than relying on every handler to check. Bad input is a 400, not a 500: a malformed month or
       number in an address (``/reports/garbage``) is answered with the plain *That address did not make sense*
       page, and error pages never echo exception messages (``server.error.include-message: never``). CSV exports
       prefix a cell that starts with ``=``, ``+``, ``-``, ``@``, tab or return with ``'`` so a note cannot become a
       formula when the file is opened in a spreadsheet. The share link on a habits-check result is built from the
       configured host, never from the request's ``Host`` header. The Traffic page's ``days`` is clamped to ten
       years.
   * - SE-19
     - **CSRF, and what a failure looks like.** Every browser form carries a session-bound token (Spring's default).
       A form that arrives with no session at all — the tab outlived a deploy, or sat idle past the session's life —
       is not an attack, so it is answered with a redirect to a fresh form and a note (``/login?expired``,
       ``/register?expired``) rather than the 403 page; a form whose session exists but whose token is wrong gets the
       403 (``ExpiredFormHandler``). Both are tested.

Release 1 additions
-------------------

* TLS termination and HSTS at the edge — done (Caddy); the app side of the proxy is SE-8.
* Rate limiting on ``/register``, ``/login``, ``/api/**`` and report PDFs (SE-9) — done, in memory; per-key API
  limits are still to come.
* A nonce-based CSP in place of ``'unsafe-inline'`` (SE-15); binding the admin account to an id rather than an
  email; narrowing the extension's host permissions.
* Email verification and password reset with expiring tokens (SE-10).
* Company account gating and profile-view audit log (SE-11, see CV-6 / CV-7).
* Dependency scanning in CI and a documented data-retention policy (SE-12) — retention is now documented on
  ``/privacy`` (PR-6); dependency scanning is Dependabot.
