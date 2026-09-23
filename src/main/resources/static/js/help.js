/*
 * Sharpen Help — a small, rule-based guide that lives in the corner of every page.
 *
 * Deliberately not an AI chatbot: no model, no API, no cost, nothing typed here leaves the browser. It matches
 * the visitor's words against a fixed list of topics and answers from that list; anything else gets an honest
 * "I can't answer that" plus the Contact form. There is no customer-service team behind it and it says so.
 *
 * The conversation is held in memory only — no cookies, no storage, gone on reload.
 */
(function () {
  'use strict';

  var root = document.getElementById('help-widget');
  if (!root) return;

  var signedIn = root.getAttribute('data-signed-in') === 'true';
  var company = root.getAttribute('data-company') === 'true';
  var handle = root.getAttribute('data-handle') || 'your-handle';
  var contact = '<a href="/feedback?from=help">Contact form</a>';

  // ---- the knowledge base ---------------------------------------------------------------------------------
  // keys: words or phrases (lower-case). A match on any key scores the topic; longer keys score more, so
  // "customer representative" beats "customer" alone. The best topic above the threshold answers.
  var topics = [
    {
      id: 'enroll', chip: 'How do I enrol?',
      keys: ['enrol', 'enroll', 'register', 'registration', 'sign up', 'signup', 'join', 'create account', 'create an account',
             'new account', 'get started', 'getting started', 'how to start', 'how do i start', 'begin', 'onboard', 'account'],
      answer: function () {
        if (signedIn) {
          return '<p>You already have an account and are signed in. Next step: <a href="/sessions/new">log a session</a> — ' +
                 'tool, what you did, how long, how much of the thinking was yours. It takes about 15 seconds.</p>' +
                 '<p>After a few sessions your AI score appears on the <a href="/dashboard">dashboard</a>; ' +
                 'after a month you get your first report.</p>';
        }
        return '<p>Enrolling is free and takes under a minute:</p>' +
               '<ol><li>Click <a href="/register">Create account</a>.</li>' +
               '<li>Enter your first and last name (middle is optional), email and a password (8 or more characters). Choose <b>Individual</b> (you track your own AI use) ' +
               'or <b>Company</b> (you hire and want the candidates view).</li>' +
               '<li><a href="/login">Sign in</a> — your dashboard opens.</li>' +
               '<li>Log your first AI session with the <b>+</b> button: tool, what you did, how long, how much of the thinking was yours.</li></ol>' +
               '<p>No email verification, no card. After a few sessions your AI score appears; after a month, your first monthly report.</p>';
      }
    },
    {
      id: 'pdf', chip: 'How do I get the PDF report?',
      keys: ['pdf', 'report', 'reports', 'download', 'monthly report', 'print', 'export', 'generate', 'save report'],
      answer: function () {
        var steps = '<ol><li>Open <a href="/reports">Reports</a> in the menu.</li>' +
                    '<li>Pick a month. <b>Preview</b> shows it with live numbers; <b>Generate</b> freezes the numbers for that month.</li>' +
                    '<li>On the report page click <b>Download PDF</b>. The file is named <code>sharpen-' + handle + '-YYYY-MM.pdf</code>.</li></ol>' +
                    '<p>Direct address for any month: <code>/reports/YYYY-MM.pdf</code>, for example <code>/reports/2026-09.pdf</code>. ' +
                    'Reports are also generated automatically on the 1st for the month just ended. The PDF is built from exactly the same page you see on screen.</p>';
        if (!signedIn) steps = '<p>Reports are personal, so you need to be <a href="/login">signed in</a> first. Then:</p>' + steps;
        return steps;
      }
    },
    {
      id: 'human', chip: 'Can I talk to someone?',
      keys: ['human', 'person', 'agent', 'representative', 'rep', 'customer service', 'customer support', 'customer care', 'customer representative',
             'support team', 'help desk', 'helpdesk', 'call', 'phone', 'telephone', 'number', 'chat with', 'talk to', 'speak to', 'speak with',
             'live chat', 'live agent', 'real person', 'someone', 'operator', 'email you', 'contact', 'reach you', 'complaint', 'complain'],
      answer: function () {
        return '<p><b>There is no customer-service team and no live representative.</b> Sharpen is built and run by one person, and I am a small ' +
               'automated guide — not a person, and not an AI.</p>' +
               '<p>For anything I cannot answer, use the ' + contact + ' on the website. Every message is read and answered by the author; ' +
               'include the email you registered with if it is about your account.</p>';
      }
    },
    {
      id: 'score', chip: 'What is the AI score?',
      keys: ['score', 'points', '1000', 'band', 'independence', 'effectiveness', 'verification', 'growth', 'breadth', 'provisional',
             'how is it calculated', 'calculated', 'rating', 'strong', 'sharp'],
      answer: function () {
        return '<p>The AI score runs from 0 to 1000 and rewards <b>how</b> you work with AI, never how much. Five parts: ' +
               '<b>independence</b> (how much of the thinking stayed yours), <b>effectiveness</b> (did it help), <b>verification</b> ' +
               '(did you check the output), <b>growth</b> (are you learning) and <b>breadth</b> (range of tasks and tools).</p>' +
               '<p>It is computed over the last 90 days and is marked <i>provisional</i> until you have logged enough sessions. ' +
               'Logging more hours does not raise it; verifying and doing your own thinking does.' +
               (signedIn ? ' Yours is on the <a href="/dashboard">dashboard</a>.' : ' <a href="/#how">More on the landing page.</a>') + '</p>';
      }
    },
    {
      id: 'public', chip: 'Public profile',
      keys: ['public', 'profile', 'handle', 'url', 'my link', 'share', 'linkedin', 'visible', 'hide', 'private', 'directory', 'listed', 'employer', 'employers',
             'my name', 'change name', 'change my name', 'rename', 'first name', 'last name', 'middle name', 'surname', 'spelling'],
      answer: function () {
        if (company) return '<p>Company accounts have no public profile; you see everyone else\'s in <a href="/candidates">Candidates</a>.</p>';
        return '<p>Your name (first, middle, last), headline, title and everything else on the profile is edited under <a href="/settings">Settings</a>.</p>' +
               '<p>Your profile is private until you switch it on: <a href="/settings">Settings</a> → tick <b>Public profile</b> → Save. ' +
               'It then appears at <code>/p/' + handle + '</code> and in the <a href="/p">public directory</a>, and companies can find you. ' +
               'Untick it any time to make it private again.</p>' +
               '<p>Only AI-related facts you chose are shown — score, trend, tools, headline, title, industry, location. Never your sessions or email.</p>';
      }
    },
    {
      id: 'picture', chip: 'Profile picture',
      keys: ['picture', 'photo', 'avatar', 'image', 'upload picture', 'initials'],
      answer: function () {
        return '<p><a href="/settings">Settings</a> → <b>Picture</b> → choose a JPEG, PNG or WebP up to 8 MB. It is cropped to a square and shown on your ' +
               'profile, in the directory and in the sidebar. <b>Remove</b> goes back to your initials.</p>';
      }
    },
    {
      id: 'tools', chip: 'Tools not showing',
      keys: ['tool', 'tools', 'claude', 'chatgpt', 'copilot', 'gemini', 'perplexity', 'not showing', 'not reflecting', 'not listed', 'missing tool'],
      answer: function () {
        return '<p>The tools on your profile and in the directory come from the sessions you log — the tool you pick on each session, most-used first. ' +
               'A tool appears as soon as one session names it.</p>' +
               '<p><i>Other tools you use</i> in <a href="/settings">Settings</a> is only for tools you have not logged yet; they are added after the logged ones.</p>';
      }
    },
    {
      id: 'import', chip: 'Import / extension',
      keys: ['import', 'csv', 'spreadsheet', 'extension', 'chrome', 'browser extension', 'api', 'api key', 'automatic', 'automatically', 'json', 'usage export'],
      answer: function () {
        return '<p>Three ways to bring sessions in, all on <a href="/import">Import</a>:</p>' +
               '<ul><li><b>Browser extension</b> (in <code>chrome-extension/</code> of the source): times your visits to ChatGPT, Claude, Gemini, Copilot and Perplexity and posts them once a day with your API key from <a href="/settings">Settings</a>. Nothing you type is captured.</li>' +
               '<li><b>Provider usage export</b> — upload the file your AI provider gives you.</li>' +
               '<li><b>Sharpen CSV</b> — a spreadsheet in the format shown on the page; the CSV from Settings → Your data is already in it.</li></ul>' +
               '<p>Imported sessions show as <i>needs rating</i> until you answer the four questions about them.</p>';
      }
    },
    {
      id: 'privacy', chip: 'Privacy & my data',
      keys: ['privacy', 'private data', 'my data', 'cookie', 'cookies', 'tracking', 'track me', 'gdpr', 'delete account', 'delete my account',
             'remove account', 'remove my data', 'sell', 'who can see', 'secure', 'security',
             'export', 'download my data', 'backup', 'back up', 'take my data', 'get my data', 'leave'],
      answer: function () {
        return '<p>Sharpen stores only what you log or type. No third-party analytics, no advertising, no cookies beyond the one that keeps you signed in; ' +
               'page views are counted server-side without identifying anyone.</p>' +
               '<p>Your sessions, email and reports are never public. The public profile, if you switch it on, shows only the AI-related facts you chose.</p>' +
               '<p><b>Taking your data out:</b> <a href="/settings">Settings</a> → <b>Your data</b> → <b>Sessions (CSV)</b> (opens in a spreadsheet and ' +
               'imports into another Sharpen account) or <b>Everything (JSON)</b> (profile, sessions, reports). Instant, no request needed.</p>' +
               '<p>To delete your account and everything in it, send a message from the ' + contact + ' using the email you registered with; ' +
               'it is done by hand within a few days.</p>';
      }
    },
    {
      id: 'cost', chip: 'Is it free?',
      keys: ['free', 'price', 'pricing', 'cost', 'pay', 'payment', 'subscription', 'plan', 'premium', 'money', 'charge', 'open source', 'license'],
      answer: function () {
        return '<p>Yes — free, with no paid tier. Sharpen is open source under AGPL-3.0; the code is on ' +
               '<a href="https://github.com/bhushanladde02/sharpen" target="_blank" rel="noopener">GitHub</a>.</p>';
      }
    },
    {
      id: 'company', chip: 'For companies',
      keys: ['company', 'companies', 'hire', 'hiring', 'recruit', 'recruiter', 'candidates', 'candidate', 'team'],
      answer: function () {
        return '<p>Create a <b>Company</b> account at <a href="/register">Create account</a>. You get the <a href="/candidates">Candidates</a> view — ' +
               'everyone with a public profile, ranked by AI score, filterable by tool, industry and score — and each candidate\'s full public profile.</p>';
      }
    },
    {
      id: 'password', chip: 'Forgot password',
      keys: ['password', 'forgot', 'reset', 'locked out', 'cannot sign in', "can't sign in", 'cannot log in', "can't log in", 'login problem', 'wrong password'],
      answer: function () {
        return '<p>Password reset by email is not built yet. Send a message from the ' + contact + ' with the email you registered under and the author ' +
               'resets it by hand, usually the same day.</p>' +
               '<p>To change a password you still know: <a href="/settings">Settings</a>.</p>';
      }
    },
    {
      id: 'login', chip: null,
      keys: ['sign in', 'signin', 'log in', 'login', 'logout', 'sign out'],
      answer: function () {
        return signedIn ? '<p>You are signed in. <b>Sign out</b> is at the bottom of the menu.</p>'
                        : '<p><a href="/login">Sign in here</a>. No account yet? <a href="/register">Create one</a> — it is free.</p>';
      }
    },
    {
      id: 'hello', chip: null,
      keys: ['hello', 'hi', 'hey', 'good morning', 'good evening', 'thanks', 'thank you', 'ok', 'okay', 'bye'],
      answer: function (q) {
        return /thank|bye/.test(q) ? '<p>You are welcome. Anything else, just ask — or use the ' + contact + '.</p>'
                                    : '<p>Hello. Ask me how to enrol, how to get your PDF report, or pick a question below.</p>';
      }
    }
  ];

  var fallback = function () {
    return '<p>I am a small automated guide and can only answer questions about using Sharpen — I did not understand that one.</p>' +
           '<p>Try one of the questions below, or ask a real person through the ' + contact + ' (there is no live support line; the author reads every message).</p>';
  };

  // ---- matching -------------------------------------------------------------------------------------------
  function normalise(s) { return ' ' + s.toLowerCase().replace(/[^a-z0-9'+ ]+/g, ' ').replace(/\s+/g, ' ').trim() + ' '; }
  function best(question) {
    var q = normalise(question), top = null, topScore = 0;
    topics.forEach(function (t) {
      var score = 0;
      t.keys.forEach(function (k) {
        if (q.indexOf(' ' + k + ' ') !== -1) score += 1 + k.split(' ').length; // phrases outrank single words
      });
      if (score > topScore) { topScore = score; top = t; }
    });
    return topScore > 0 ? top : null;
  }

  // ---- UI -------------------------------------------------------------------------------------------------
  var launcher = root.querySelector('.help-launcher'), panel = root.querySelector('.help-panel'),
      log = root.querySelector('.help-log'), form = root.querySelector('.help-form'), input = form.querySelector('input'),
      chips = root.querySelector('.help-chips'), closeBtn = root.querySelector('.help-close');

  function say(html, who) {
    var m = document.createElement('div'); m.className = 'help-msg ' + who; m.innerHTML = html;
    log.appendChild(m); log.scrollTop = log.scrollHeight; return m;
  }
  function ask(text, fromChip) {
    if (!text.trim()) return;
    say('<p></p>', 'me').querySelector('p').textContent = text;
    var t = best(text);
    say(t ? t.answer(normalise(text)) : fallback(), 'bot');
    if (!fromChip) input.value = '';
    input.focus();
  }
  function open() {
    panel.hidden = false; launcher.setAttribute('aria-expanded', 'true'); root.classList.add('open');
    if (!log.childElementCount) {
      say('<p>Hi — I am <b>Sharpen Help</b>, a small automated guide (not a person, not an AI). ' +
          'I can explain how to enrol, how to get your PDF report and where things are. ' +
          'There is no live customer support; for anything else use the ' + contact + '.</p>', 'bot');
    }
    setTimeout(function () { input.focus(); }, 50);
  }
  function close() { panel.hidden = true; launcher.setAttribute('aria-expanded', 'false'); root.classList.remove('open'); launcher.focus(); }

  topics.forEach(function (t) {
    if (!t.chip) return;
    var b = document.createElement('button'); b.type = 'button'; b.className = 'help-chip'; b.textContent = t.chip;
    b.addEventListener('click', function () { ask(t.chip, true); });
    chips.appendChild(b);
  });
  launcher.addEventListener('click', function () { panel.hidden ? open() : close(); });
  closeBtn.addEventListener('click', close);
  form.addEventListener('submit', function (e) { e.preventDefault(); ask(input.value, false); });
  document.addEventListener('keydown', function (e) { if (e.key === 'Escape' && !panel.hidden) close(); });
  // Any link with data-help-open (footer "Help") opens the panel instead of navigating.
  Array.prototype.forEach.call(document.querySelectorAll('[data-help-open]'), function (a) {
    a.addEventListener('click', function (e) { e.preventDefault(); open(); });
  });
  // "?topic" in the address opens the panel on that topic (used by links in emails and docs): /?help=pdf
  try {
    var want = new URLSearchParams(location.search).get('help');
    if (want) { open(); var t = topics.filter(function (x) { return x.id === want; })[0]; if (t) ask(t.chip || want, true); }
  } catch (e) {}
})();
