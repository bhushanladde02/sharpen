// Sharpen capture — content script.
// Detects that a prompt was sent (Enter in the composer or a click on a send button). The text itself is never read.

function isComposer(el) {
  if (!el) return false;
  const tag = el.tagName;
  return tag === "TEXTAREA" || el.isContentEditable || (tag === "INPUT" && el.type === "text");
}

function looksLikeSend(el) {
  const target = el.closest && el.closest("button, [role=button]");
  if (!target) return false;
  const label = ((target.getAttribute("aria-label") || "") + " " + (target.getAttribute("data-testid") || "") + " " + target.textContent).toLowerCase();
  return /send|submit|run/.test(label);
}

document.addEventListener("keydown", e => {
  if (e.key === "Enter" && !e.shiftKey && isComposer(e.target)) {
    chrome.runtime.sendMessage({ type: "prompt" });
  } else if (isComposer(e.target)) {
    chrome.runtime.sendMessage({ type: "activity" });
  }
}, true);

document.addEventListener("click", e => {
  if (looksLikeSend(e.target)) chrome.runtime.sendMessage({ type: "prompt" });
  else chrome.runtime.sendMessage({ type: "activity" });
}, true);

document.addEventListener("scroll", () => chrome.runtime.sendMessage({ type: "activity" }), { passive: true, capture: true });
