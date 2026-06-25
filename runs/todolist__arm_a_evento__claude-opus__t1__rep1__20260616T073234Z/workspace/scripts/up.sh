#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
[ -f .run-env ] && set -a && source .run-env && set +a
SERVICES="${SERVICES:-edge lists items notifications}"
if [ -f .app-pids ]; then
  while read -r pid; do [ -n "$pid" ] && kill "$pid" 2>/dev/null || true; done < .app-pids
  sleep 2
fi
: > .app-pids
for svc in $SERVICES; do
  JAR=$(ls $svc/target/*.jar 2>/dev/null | grep -v '\.original$' | head -1)
  [ -n "$JAR" ] || { echo "no jar for $svc — run 'make build'" >&2; exit 1; }
  portvar=$(echo "${svc}_PORT" | tr '[:lower:]' '[:upper:]'); port=${!portvar}
  nohup java -jar "$JAR" > "$svc.log" 2>&1 &
  echo $! >> .app-pids
  echo "started $svc (pid $!) on $port"
done
# Bundle-readiness gate (grading only). /actuator/health goes UP before the
# Evento bundle is registered and able to handle commands — the framework logs
# "Application Started!" only after projector head-reached + registration. When
# the harness sets READY_REQUIRE_BUNDLE=1 (grading + restore-between-scenarios)
# wait for BOTH health UP and that marker so a not-yet-ready bundle can't 500 the
# first command; the agent's own `make run` leaves it unset and waits on health
# only (no dev-loop / TIME-KPI impact). Services are polled in parallel so the
# wait is the slowest single catch-up, not the sum.
READY_MARKER="Application Started!"
if [ "${READY_REQUIRE_BUNDLE:-}" = "1" ]; then READY_DEADLINE=${READY_DEADLINE_SECONDS:-540}; else READY_DEADLINE=120; fi
_start=$SECONDS
while :; do
  all=1
  for svc in $SERVICES; do
    portvar=$(echo "${svc}_PORT" | tr '[:lower:]' '[:upper:]'); port=${!portvar}
    if ! curl -fsS "http://localhost:$port/actuator/health" 2>/dev/null | grep -q '"UP"'; then all=0; break; fi
    if [ "${READY_REQUIRE_BUNDLE:-}" = "1" ] && ! grep -q "$READY_MARKER" "$svc.log" 2>/dev/null; then all=0; break; fi
  done
  [ "$all" = 1 ] && break
  if [ $((SECONDS - _start)) -ge "$READY_DEADLINE" ]; then
    echo "services not ready within ${READY_DEADLINE}s" >&2
    for svc in $SERVICES; do echo "--- $svc ---" >&2; tail -20 "$svc.log" >&2; done
    exit 1
  fi
  sleep 1
done
echo "all services READY"
