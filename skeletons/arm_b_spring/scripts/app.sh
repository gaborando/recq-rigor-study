#!/usr/bin/env bash
# Start/stop the packaged app. Environment (.run-env) is provided by the harness:
# PORT, DB_URL/DB_USER/DB_PASS and, on the Evento arm, EVENTO_HOST/EVENTO_PORT.
set -euo pipefail
cd "$(dirname "$0")/.."

[ -f .run-env ] && set -a && source .run-env && set +a
PORT="${PORT:-8080}"
JAR=$(ls target/*.jar 2>/dev/null | grep -v '\.original$' | head -1)
PIDFILE=.app.pid

stop() {
  if [ -f "$PIDFILE" ] && kill -0 "$(cat $PIDFILE)" 2>/dev/null; then
    kill "$(cat $PIDFILE)" 2>/dev/null || true
    for _ in $(seq 1 20); do kill -0 "$(cat $PIDFILE)" 2>/dev/null || break; sleep 0.5; done
    kill -9 "$(cat $PIDFILE)" 2>/dev/null || true
  fi
  rm -f "$PIDFILE"
}

start() {
  [ -n "$JAR" ] || { echo "no jar in target/ — run 'make build' first" >&2; exit 1; }
  nohup java -jar "$JAR" > app.log 2>&1 &
  echo $! > "$PIDFILE"
  echo "waiting for http://localhost:$PORT/actuator/health ..."
  for _ in $(seq 1 120); do
    if curl -fsS "http://localhost:$PORT/actuator/health" 2>/dev/null | grep -q '"UP"'; then
      echo "app is UP (pid $(cat $PIDFILE))"; return 0
    fi
    kill -0 "$(cat $PIDFILE)" 2>/dev/null || { echo "app died — see app.log" >&2; tail -40 app.log >&2; exit 1; }
    sleep 1
  done
  echo "app did not become healthy in 120s — see app.log" >&2; tail -40 app.log >&2; exit 1
}

case "${1:-restart}" in
  start) start ;;
  stop) stop ;;
  restart) stop; start ;;
  *) echo "usage: $0 start|stop|restart" >&2; exit 2 ;;
esac
