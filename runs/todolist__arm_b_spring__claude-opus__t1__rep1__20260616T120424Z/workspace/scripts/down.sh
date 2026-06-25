#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
[ -f .app-pids ] || exit 0
while read -r pid; do [ -n "$pid" ] && kill "$pid" 2>/dev/null || true; done < .app-pids
sleep 2
while read -r pid; do kill -9 "$pid" 2>/dev/null || true; done < .app-pids
rm -f .app-pids
