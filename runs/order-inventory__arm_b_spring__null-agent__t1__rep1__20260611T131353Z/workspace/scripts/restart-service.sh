#!/usr/bin/env bash
# restart-service.sh <name>  — kill+restart one service (chaos: crash/recover).
set -euo pipefail
cd "$(dirname "$0")/.."
[ -f .run-env ] && set -a && source .run-env && set +a
svc="$1"
# kill any java running that service's jar
pkill -f "$svc/target/" 2>/dev/null || true
sleep 1
if [ "${2:-}" = "stop" ]; then echo "$svc stopped"; exit 0; fi
JAR=$(ls $svc/target/*.jar 2>/dev/null | grep -v '\.original$' | head -1)
portvar=$(echo "${svc}_PORT" | tr '[:lower:]' '[:upper:]'); port=${!portvar}
nohup java -jar "$JAR" > "$svc.log" 2>&1 &
echo $! >> .app-pids
for _ in $(seq 1 120); do
  curl -fsS "http://localhost:$port/actuator/health" 2>/dev/null | grep -q '"UP"' && { echo "$svc UP"; exit 0; }
  sleep 1
done
echo "$svc did not recover" >&2; exit 1
