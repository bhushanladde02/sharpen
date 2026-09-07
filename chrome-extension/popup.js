const $ = id => document.getElementById(id);

async function load() {
  const { portalUrl = "", apiKey = "", context = "PROFESSIONAL", records = {}, lastSync } =
    await chrome.storage.local.get(["portalUrl", "apiKey", "context", "records", "lastSync"]);
  $("portalUrl").value = portalUrl;
  $("apiKey").value = apiKey;
  $("context").value = context;
  $("status").textContent = lastSync ? `Last sync ${new Date(lastSync).toLocaleString()}` : "Not synced yet.";
  const rows = Object.values(records).sort((a, b) => (b.date + b.tool).localeCompare(a.date + a.tool));
  $("records").innerHTML = rows.length
    ? rows.map(r => `<tr><td>${r.date}</td><td>${r.tool}</td><td class="n">${Math.round(r.minutes)} min</td><td class="n">${r.prompts} prompts</td></tr>`).join("")
    : `<tr><td>No AI sessions captured yet.</td></tr>`;
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
  chrome.runtime.sendMessage({ type: "sync" }, res => {
    $("status").textContent = res && res.ok ? `Synced ${res.sent} session(s).` : `Sync failed: ${res ? res.error : "no response"}`;
    load();
  });
});

load();
