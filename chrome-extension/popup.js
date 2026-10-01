const $ = id => document.getElementById(id);
const esc = s => String(s).replace(/[&<>"]/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));

async function load() {
  const { portalUrl = "", apiKey = "", context = "PROFESSIONAL", records = {}, lastSync, installedOn, historyImportedOn } =
    await chrome.storage.local.get(["portalUrl", "apiKey", "context", "records", "lastSync", "installedOn", "historyImportedOn"]);
  $("portalUrl").value = portalUrl;
  $("apiKey").value = apiKey;
  $("context").value = context;
  $("status").textContent = lastSync ? `Last sync ${new Date(lastSync).toLocaleString()}` : "Not synced yet.";
  const rows = Object.values(records).sort((a, b) => (b.date + b.tool).localeCompare(a.date + a.tool));
  $("records").innerHTML = rows.length
    ? rows.map(r => `<tr><td>${esc(r.date)}</td><td>${esc(r.tool)}</td><td class="n">${Math.round(r.minutes)} min</td><td class="n">${r.prompts} prompts</td></tr>`).join("")
    : `<tr><td>No AI sessions captured yet.</td></tr>`;

  // The consent screen says exactly which sites are read — the same list the live capture watches.
  $("import-sites").innerHTML = [...new Set(TOOLS.map(t => t.host + (t.path || "")))].map(h => `<li>${esc(h)}</li>`).join("");
  if (installedOn) $("installedOn").textContent = installedOn;
  if (historyImportedOn) {
    $("import-explain").hidden = true;
    $("import-done").hidden = false;
    $("import-done-text").textContent = `History imported on ${historyImportedOn}. Running it again only updates the same rows.`;
    $("import-done").insertAdjacentHTML("beforeend", '<button id="import-again" class="secondary">Preview again</button>');
    $("import-again").addEventListener("click", () => { $("import-done").hidden = true; $("import-explain").hidden = false; });
  }
}

$("save").addEventListener("click", async () => {
  await chrome.storage.local.set({
    portalUrl: $("portalUrl").value.trim(),
    apiKey: $("apiKey").value.trim(),
    context: $("context").value
  });
  $("status").textContent = "Saved.";
});

$("sync").addEventListener("click", () => {
  $("status").textContent = "Syncing…";
  chrome.runtime.sendMessage({ type: "sync" }, async res => {
    await load();   // refresh the table first, so the outcome below is not overwritten by the "last sync" line
    $("status").textContent = res && res.ok ? `Synced ${res.sent} session(s).` : `Sync failed: ${res ? res.error : "no response"}`;
  });
});

// ---- Consented history import -------------------------------------------------------------------------

let previewRows = [];

/** Hands the history permission back; an extension that has finished reading has no reason to keep it. */
async function releaseHistory() {
  try { await chrome.permissions.remove({ permissions: ["history"] }); } catch (e) { /* not held, or not removable */ }
}

/** Visit times for watched sites only — every other history entry is discarded unread, URL and all. */
async function readVisits(installedOn) {
  const startTime = Date.now() - HISTORY_DAYS * 86400000;
  const items = await chrome.history.search({ text: "", startTime, maxResults: 100000 });
  const visits = [];
  for (const item of items) {
    if (!item.url || !toolFor(item.url)) continue;
    for (const v of await chrome.history.getVisits({ url: item.url })) {
      if (v.visitTime >= startTime) visits.push({ url: item.url, time: v.visitTime });
    }
  }
  return estimateSessions(visits, toolFor, installedOn);
}

$("import-preview-btn").addEventListener("click", async () => {
  // permissions.request must be the first thing the click does, or Chrome does not count it as a user gesture.
  const granted = await chrome.permissions.request({ permissions: ["history"] });
  if (!granted) { $("import-summary").textContent = ""; $("status").textContent = "No history access given — nothing was read."; return; }
  const { installedOn } = await chrome.storage.local.get("installedOn");
  const before = installedOn || dayOf(Date.now());
  try {
    previewRows = await readVisits(before);
  } catch (e) {
    await releaseHistory();
    $("status").textContent = `Could not read history: ${e.message}`;
    return;
  }
  const minutes = previewRows.reduce((s, r) => s + r.minutes, 0);
  $("import-summary").innerHTML = previewRows.length
    ? `<b>${previewRows.length}</b> day-and-tool rows before ${esc(before)}, about <b>${minutes}</b> minutes in all. This is everything that would be sent:`
    : `No visits to the watched AI sites were found in the 90 days before ${esc(before)}. Nothing to send.`;
  $("import-preview").innerHTML = previewRows.length
    ? `<table><tr><th>Date</th><th>Tool</th><th class="n">Min</th><th class="n">Visits</th></tr>` +
      previewRows.map(r => `<tr><td>${esc(r.date)}</td><td>${esc(r.tool)}</td><td class="n">${r.minutes}</td><td class="n">${r.visits}</td></tr>`).join("") + `</table>`
    : "";
  $("import-send").disabled = previewRows.length === 0;
  $("import-explain").hidden = true;
  $("import-result").hidden = false;
});

$("import-cancel").addEventListener("click", async () => {
  previewRows = [];
  await releaseHistory();
  $("import-result").hidden = true;
  $("import-explain").hidden = false;
  $("status").textContent = "Import cancelled — nothing was sent and history access was given back.";
});

$("import-send").addEventListener("click", () => {
  $("import-send").disabled = true;
  $("status").textContent = "Sending…";
  chrome.runtime.sendMessage({ type: "import-history", rows: previewRows }, async res => {
    await releaseHistory();
    if (res && res.ok) {
      const when = dayOf(Date.now());
      await chrome.storage.local.set({ historyImportedOn: when });
      $("import-result").hidden = true;
      $("import-done").hidden = false;
      $("import-done-text").textContent = `Sent ${res.sent} estimated session(s). Rate them on the portal under Sessions → needs rating. History access has been given back.`;
      $("status").textContent = `Imported ${res.sent} session(s) from history.`;
      previewRows = [];
    } else {
      $("import-send").disabled = false;   // the rows are still on screen; Send can be tried again
      $("status").textContent = `Import failed: ${res ? res.error : "no response"}. Nothing was stored; history access was given back.`;
    }
  });
});

load();
