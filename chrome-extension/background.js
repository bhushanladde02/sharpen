// Sharpen capture — service worker.
// Keeps one record per (day, tool): active minutes and prompt count. Syncs to the portal once a day
// and on demand. Nothing typed into an AI site is ever read or stored.

const TOOLS = [
  { host: "chatgpt.com", tool: "ChatGPT" },
  { host: "chat.openai.com", tool: "ChatGPT" },
  { host: "claude.ai", tool: "Claude" },
  { host: "gemini.google.com", tool: "Gemini" },
  { host: "copilot.microsoft.com", tool: "Microsoft Copilot" },
  { host: "www.perplexity.ai", tool: "Perplexity" },
  { host: "github.com", path: "/copilot", tool: "GitHub Copilot" }
];

const TICK_MS = 15000;         // how often the active tab is sampled
const IDLE_AFTER_MS = 120000;  // stop counting after two minutes without a prompt or focus change

let active = null;             // { tool, lastSeen }
let lastActivity = 0;

function toolFor(url) {
  try {
    const u = new URL(url);
    const hit = TOOLS.find(t => u.hostname === t.host && (!t.path || u.pathname.startsWith(t.path)));
    return hit ? hit.tool : null;
  } catch { return null; }
}

function today() { return new Date().toISOString().slice(0, 10); }

async function bump(tool, field, amount) {
  const key = `${today()}|${tool}`;
  const { records = {} } = await chrome.storage.local.get("records");
  const r = records[key] || { date: today(), tool, minutes: 0, prompts: 0 };
  r[field] += amount;
  records[key] = r;
  await chrome.storage.local.set({ records });
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

/** Posts every stored record to the portal; records are keyed by externalId so re-sends update, not duplicate. */
async function sync() {
  const { portalUrl, apiKey, context = "PROFESSIONAL", records = {} } = await chrome.storage.local.get(
    ["portalUrl", "apiKey", "context", "records"]);
  if (!portalUrl || !apiKey) return { ok: false, error: "Set the portal URL and API key first." };
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
  if (sessions.length === 0) return { ok: true, sent: 0 };
  try {
    const res = await fetch(`${portalUrl.replace(/\/$/, "")}/api/v1/sessions?source=extension&defaultContext=${context}`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-Api-Key": apiKey },
      body: JSON.stringify({ sessions })
    });
    if (!res.ok) return { ok: false, error: `Portal answered ${res.status}` };
    const result = await res.json();
    // Keep only today's records so the current day keeps accumulating; older ones are now in the portal.
    const keep = Object.fromEntries(Object.entries(records).filter(([k]) => k.startsWith(today())));
    await chrome.storage.local.set({ records: keep, lastSync: new Date().toISOString() });
    return { ok: true, sent: sessions.length, result };
  } catch (e) {
    return { ok: false, error: e.message };
  }
}
