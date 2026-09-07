#!/usr/bin/env bash
# Build the Sharpen documentation and open it in the browser.
# First run creates a virtualenv and installs Sphinx; later runs just rebuild what changed.
#
#   ./docs/view.sh            build (if needed) and open
#   ./docs/view.sh --clean    rebuild from scratch, then open
#   ./docs/view.sh --no-open  build only (CI / headless)
set -euo pipefail

cd "$(dirname "$0")"
VENV=.venv
OUT=_build/html/index.html
OPEN=1
CLEAN=0

for arg in "$@"; do
  case "$arg" in
    --clean)   CLEAN=1 ;;
    --no-open) OPEN=0 ;;
    -h|--help) sed -n '2,8p' "$0"; exit 0 ;;
    *) echo "Unknown option: $arg" >&2; exit 2 ;;
  esac
done

# 1. Python
PY=$(command -v python3 || true)
if [ -z "$PY" ]; then
  echo "python3 is required. On macOS: brew install python  (or install from python.org)" >&2
  exit 1
fi

# 2. Virtualenv with Sphinx (created once, reused after)
if [ ! -x "$VENV/bin/sphinx-build" ]; then
  echo "Setting up a local Python environment for Sphinx (one time)..."
  "$PY" -m venv "$VENV"
  "$VENV/bin/pip" install --quiet --upgrade pip
  "$VENV/bin/pip" install --quiet -r requirements.txt
fi

# 3. Build (warnings are errors, so a broken link or bad table fails loudly)
if [ "$CLEAN" = 1 ]; then rm -rf _build; fi
"$VENV/bin/sphinx-build" -q -W --keep-going -b html . _build/html
echo "Built: $(pwd)/$OUT"

# 4. Open
if [ "$OPEN" = 1 ]; then
  case "$(uname -s)" in
    Darwin) open "$OUT" ;;
    Linux)  xdg-open "$OUT" >/dev/null 2>&1 || echo "Open $OUT in a browser." ;;
    *)      echo "Open $OUT in a browser." ;;
  esac
fi
