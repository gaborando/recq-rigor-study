#!/usr/bin/env bash
# Start all micro services on harness-assigned ports; wait each /actuator/health.
set -euo pipefail
cd "$(dirname "$0")/.."
[ -f .run-env ] && set -a && source .run-env && set +a
SERVICES="edge orders inventory customers"
: > .app-pids
for svc in $SERVICES; do
  JAR=$(ls $svc/target/*.jar 2>/dev/null | grep -v '\.original$' | head -1)
  [ -n "$JAR" ] || { echo "no jar for $svc — run 'make build'" >&2; exit 1; }
  portvar=$(echo "${svc}_PORT" | tr '[:lower:]' '[:upper:]'); port=${!portvar}
  nohup java -jar "$JAR" > "$svc.log" 2>&1 &
  echo $! >> .app-pids
  echo "started $svc (pid $!) on $port"
done
for svc in $SERVICES; do
  portvar=$(echo "${svc}_PORT" | tr '[:lower:]' '[:upper:]'); port=${!portvar}
  ok=0
  for _ in $(seq 1 120); do
    if curl -fsS "http://localhost:$port/actuator/health" 2>/dev/null | grep -q '"UP"'; then ok=1; break; fi
    sleep 1
  done
  [ "$ok" = 1 ] || { echo "$svc did not become healthy" >&2; tail -30 "$svc.log" >&2; exit 1; }
  echo "$svc UP on $port"
done
echo "all services UP"
