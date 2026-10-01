// Sharpen capture — the consented history import.
// Turns a list of browser-history visits to AI sites into one estimated session per (day, tool), the same
// shape the live capture produces. Pure functions only: nothing here touches chrome.* or the network, so the
// rules below are the whole story of what is read and what is sent.
//
// What is read: visit times (and nothing else) for URLs on the AI sites the extension already watches, in the
// 90 days before the extension was installed. The live capture covers everything from installation onwards,
// so the two never count the same day twice.
// What is sent: date, tool, estimated minutes, a note saying it is an estimate. Never a URL, a title, or a
// prompt count (history does not know how many prompts were sent, so none is claimed).

/** The AI sites the extension watches — the only hosts the live capture times and the only ones the import reads. */
const TOOLS = [
  { host: "chatgpt.com", tool: "ChatGPT" },
  { host: "chat.openai.com", tool: "ChatGPT" },
  { host: "claude.ai", tool: "Claude" },
  { host: "gemini.google.com", tool: "Gemini" },
  { host: "copilot.microsoft.com", tool: "Microsoft Copilot" },
  { host: "www.perplexity.ai", tool: "Perplexity" },
  { host: "github.com", path: "/copilot", tool: "GitHub Copilot" }
];

const HISTORY_DAYS = 90;             // how far back the import looks
const SITTING_GAP_MS = 10 * 60000;   // visits more than ten minutes apart are separate sittings

/** Which tool a URL belongs to, or null for every other page on the web. */
function toolFor(url) {
  try {
    const u = new URL(url);
    const hit = TOOLS.find(t => u.hostname === t.host && (!t.path || u.pathname.startsWith(t.path)));
    return hit ? hit.tool : null;
  } catch { return null; }
}

/** UTC calendar day of an epoch-millisecond time, matching how the live capture keys its records. */
function dayOf(ms) { return new Date(ms).toISOString().slice(0, 10); }

/**
 * @param visits   [{ url, time }] — every visit to a watched site, in any order
 * @param toolFor  url → tool name or null (the same function the service worker uses)
 * @param before   "YYYY-MM-DD": only days strictly before this one count (the install day and after belong
 *                 to the live capture)
 * @returns rows sorted newest first: { date, tool, minutes, visits, sittings }
 */
function estimateSessions(visits, toolFor, before) {
  const since = Date.now() - HISTORY_DAYS * 86400000;
  const byKey = new Map();
  for (const v of visits) {
    if (!v || typeof v.time !== "number" || v.time < since) continue;
    const tool = toolFor(v.url);
    if (!tool) continue;
    const date = dayOf(v.time);
    if (before && date >= before) continue;
    const key = `${date}|${tool}`;
    if (!byKey.has(key)) byKey.set(key, { date, tool, times: [] });
    byKey.get(key).times.push(v.time);
  }
  const rows = [];
  for (const { date, tool, times } of byKey.values()) {
    times.sort((a, b) => a - b);
    let minutes = 0, sittings = 0, start = times[0], last = times[0];
    for (let i = 1; i <= times.length; i++) {
      if (i === times.length || times[i] - last > SITTING_GAP_MS) {
        // One sitting: the time between its first and last page, plus a minute for the last page itself —
        // a single page open with no further navigation counts as one minute, never more.
        minutes += Math.round((last - start) / 60000) + 1;
        sittings++;
        if (i < times.length) { start = times[i]; last = times[i]; }
      } else {
        last = times[i];
      }
    }
    rows.push({ date, tool, minutes, visits: times.length, sittings });
  }
  rows.sort((a, b) => (b.date + a.tool).localeCompare(a.date + b.tool));
  return rows;
}

/** The exact payload the portal receives for an estimated row; kept here so the preview and the send agree. */
function toSession(row) {
  return {
    externalId: `hist:${row.date}:${row.tool}`,
    date: row.date,
    tool: row.tool,
    minutes: row.minutes,
    task: "other",
    notes: `Estimated from browser history: ${row.visits} page visit${row.visits === 1 ? "" : "s"} in ` +
           `${row.sittings} sitting${row.sittings === 1 ? "" : "s"}. Prompt count not known.`
  };
}

if (typeof module !== "undefined") module.exports = { TOOLS, toolFor, estimateSessions, toSession, dayOf, HISTORY_DAYS, SITTING_GAP_MS };
