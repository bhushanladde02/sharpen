// Sharpen capture — service worker.
// Keeps one record per (day, tool): active minutes and prompt count. Syncs to the portal once a day
// and on demand. Nothing typed into an AI site is ever read or stored.

importScripts("history.js");   // TOOLS, toolFor, dayOf, toSession — shared with the popup

const TICK_MS = 15000;         // how often the active tab is sampled
const IDLE_AFTER_MS = 120000;  // stop counting after two minutes without a prompt or focus change

let active = null;             // { tool, lastSeen }
let lastActivity = 0;

function today() { return dayOf(Date.now()); }

/**
 * The day the extension started watching. Everything from this day on is the live capture's; the history
 * import stops the day before, so no day is ever counted twice. For an install that predates this field,
 * the earliest captured record is used, failing that today.
 */
chrome.runtime.onInstalled.addListener(async () => {
  const { installedOn, firstRecordDate } = await chrome.storage.local.get(["installedOn", "firstRecordDate"]);
  if (!installedOn) await chrome.storage.local.set({ installedOn: firstRecordDate || today() });
});

async function bump(tool, field, amount) {
  const key = `${today()}|${tool}`;
  const { records = {}, firstRecordDate } = await chrome.storage.local.get(["records", "firstRecordDate"]);
  const r = records[key] || { date: today(), tool, minutes: 0, prompts: 0 };
  r[field] += amount;
  records[key] = r;
  const update = { records };
  if (!firstRecordDate) update.firstRecordDate = today();
  await chrome.storage.local.set(update);
}

async function sample() {
  const [tab] = await chrome.tabs.query({ active: true, lastFocusedWindow: true });
  const tool = tab && tab.url ? toolFor(tab.url) : null;
  const now = Date.now();
  if (tool && now - lastActivity < IDLE_AFTER_MS) {
    await bump(tool, "minutes", TICK_MS / 60000);
  }
  active = tool ? { tool, lastSeen: now } : null;
}

chrome.runtime.onMessage.addListener((msg, sender, reply) => {
  if (msg.type === "prompt" && sender.url) {
    const tool = toolFor(sender.url);
    lastActivity = Date.now();
    if (tool) bump(tool, "prompts", 1);
  } else if (msg.type === "activity") {
    lastActivity = Date.now();
  } else if (msg.type === "sync") {
    sync().then(reply);
    return true;
  } else if (msg.type === "import-history") {
    // The popup has shown the person exactly these rows and they pressed Send; post them and nothing else.
    post((msg.rows || []).map(toSession)).then(reply);
    return true;
  }
});

chrome.tabs.onActivated.addListener(() => { lastActivity = Date.now(); });
chrome.windows.onFocusChanged.addListener(() => { lastActivity = Date.now(); });

chrome.alarms.create("tick", { periodInMinutes: TICK_MS / 60000 });
chrome.alarms.create("daily-sync", { periodInMinutes: 60 * 24 });
chrome.alarms.onAlarm.addListener(a => {
  if (a.name === "tick") sample();
  if (a.name === "daily-sync") sync();
});

/** Posts a list of sessions to the portal with the saved key. Rows are keyed by externalId so re-sends update, not duplicate. */
async function post(sessions) {
  const { portalUrl, apiKey, context = "PROFESSIONAL" } = await chrome.storage.local.get(["portalUrl", "apiKey", "context"]);
  if (!portalUrl || !apiKey) return { ok: false, error: "Set the portal URL and API key first." };
  if (sessions.length === 0) return { ok: true, sent: 0 };
  try {
    const res = await fetch(`${portalUrl.replace(/\/$/, "")}/api/v1/sessions?source=extension&defaultContext=${context}`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-Api-Key": apiKey },
      body: JSON.stringify({ sessions })
    });
    if (!res.ok) return { ok: false, error: `Portal answered ${res.status}` };
    return { ok: true, sent: sessions.length, result: await res.json() };
  } catch (e) {
    return { ok: false, error: e.message };
  }
}

/** Posts every stored live record; afterwards keeps only today's so the current day keeps accumulating. */
async function sync() {
  const { records = {} } = await chrome.storage.local.get("records");
  const sessions = Object.values(records)
    .filter(r => r.minutes >= 1 || r.prompts > 0)
    .map(r => ({
      externalId: `ext:${r.date}:${r.tool}`,
      date: r.date,
      tool: r.tool,
      minutes: Math.round(r.minutes),
      prompts: r.prompts,
      task: "other"
    }));
  const out = await post(sessions);
  if (out.ok && out.sent > 0) {
    const keep = Object.fromEntries(Object.entries(records).filter(([k]) => k.startsWith(today())));
    await chrome.storage.local.set({ records: keep, lastSync: new Date().toISOString() });
  }
  return out;
}
