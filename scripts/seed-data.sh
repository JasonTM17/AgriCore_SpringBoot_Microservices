#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SEED_SCRIPT="${SCRIPT_DIR}/seed-data.ps1"

if command -v pwsh >/dev/null 2>&1; then
  exec pwsh -NoLogo -NoProfile -File "${SEED_SCRIPT}" "$@"
fi

if command -v powershell.exe >/dev/null 2>&1; then
  exec powershell.exe -NoLogo -NoProfile -File "${SEED_SCRIPT}" "$@"
fi

echo "PowerShell 7 (pwsh) is required so POSIX and Windows use the same bounded seed implementation." >&2
exit 1
