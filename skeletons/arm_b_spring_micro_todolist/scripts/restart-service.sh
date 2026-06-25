#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
[ -f .run-env ] && set -a && source .run-env && set +a
svc="$1"
pkill -f "$svc/target/" 2>/dev/null || true
# Drain the old instance FULLY before restarting. A broker-based bundle
# (Evento/Axon) stays registered with its server until its JVM exits; starting
# the replacement while the old one is still shutting down (graceful shutdown
# under load can take ~10s) creates a two-instance overlap — reconnect/supersede
# churn and routing to the dying instance — that intermittently destabilises
# crash recovery. Wait for the old process to exit, then force-kill stragglers.
for _ in $(seq 1 60); do pgrep -f "$svc/target/" >/dev/null 2>&1 || break; sleep 0.5; done
pkill -9 -f "$svc/target/" 2>/dev/null || true
sleep 0.5
if [ "${2:-}" = "stop" ]; then echo "$svc stopped"; exit 0; fi
JAR=$(ls $svc/target/*.jar 2>/dev/null | grep -v '\.original$' | head -1)
portvar=$(echo "${svc}_PORT" | tr '[:lower:]' '[:upper:]'); port=${!portvar}
# Prune dead PIDs before appending: a stale PID can be recycled by the OS and
# then SIGTERM'd by a later up.sh/down.sh, killing an unrelated process.
if [ -f .app-pids ]; then
  _tmp="$(mktemp)"
  while read -r _p; do [ -n "$_p" ] && kill -0 "$_p" 2>/dev/null && printf '%s\n' "$_p" >> "$_tmp"; done < .app-pids
  mv "$_tmp" .app-pids
fi
nohup java -jar "$JAR" > "$svc.log" 2>&1 &
echo $! >> .app-pids
for _ in $(seq 1 120); do
  curl -fsS "http://localhost:$port/actuator/health" 2>/dev/null | grep -q '"UP"' && { echo "$svc UP"; exit 0; }
  sleep 1
done
echo "$svc did not recover" >&2; exit 1
