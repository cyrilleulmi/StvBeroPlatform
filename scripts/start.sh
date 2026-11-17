#!/usr/bin/env bash
# Starts the whole app locally: database, backend (Quarkus, dev profile) and frontend (Angular).
#
#   scripts/start.sh            # start; Ctrl-C stops backend and frontend
#
# Then: http://localhost:4200 (sign-in in test mode: http://localhost:4200/dev-login)
# Backend: http://localhost:8080 · Swagger UI: http://localhost:8080/q/swagger-ui
# Logs: .local/backend.log, .local/frontend.log
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOGS="$ROOT/.local"
mkdir -p "$LOGS"

"$ROOT/scripts/db-start.sh"

if [ ! -d "$ROOT/frontend/node_modules" ]; then
  echo "Installing frontend dependencies …"
  (cd "$ROOT/frontend" && npm ci --no-audit --no-fund)
fi

PIDS=()
cleanup() {
  for pid in "${PIDS[@]}"; do
    kill "$pid" 2>/dev/null || true
  done
  wait 2>/dev/null || true
}
trap cleanup EXIT INT TERM

wait_for() { # url name seconds
  local url="$1" name="$2" max="$3" i=0
  until curl -fsS -o /dev/null "$url" 2>/dev/null; do
    i=$((i + 1))
    if [ "$i" -ge "$max" ]; then
      echo "$name is not reachable after $max seconds. Logs: $LOGS" >&2
      exit 1
    fi
    sleep 1
  done
}

if curl -fsS -o /dev/null http://localhost:8080/api/session 2>/dev/null; then
  echo "Backend is already running on :8080."
else
  echo "Starting backend (dev profile) …"
  (cd "$ROOT/backend" && exec mvn -B -q quarkus:dev -Dquarkus.console.enabled=false) >"$LOGS/backend.log" 2>&1 &
  PIDS+=($!)
fi

if curl -fsS -o /dev/null http://localhost:4200 2>/dev/null; then
  echo "Frontend is already running on :4200."
else
  echo "Starting frontend …"
  (cd "$ROOT/frontend" && exec npm start -- --port 4200) >"$LOGS/frontend.log" 2>&1 &
  PIDS+=($!)
fi

wait_for http://localhost:8080/api/session Backend 300
wait_for http://localhost:4200 Frontend 300

cat <<TEXT

Ready.
  App:         http://localhost:4200
  Test mode:   http://localhost:4200/dev-login
  Swagger UI:  http://localhost:8080/q/swagger-ui
Stop with Ctrl-C.
TEXT

if [ "${#PIDS[@]}" -gt 0 ]; then
  wait
fi
