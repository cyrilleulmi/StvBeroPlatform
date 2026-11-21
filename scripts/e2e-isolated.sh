#!/usr/bin/env bash
# Runs the E2E tests on a stack of their own, next to an app that is already running on :4200/:8080.
#
# The E2E tests reset the database before every test. Run against the app you are trying out by hand, they
# would wipe your data over and over. This script gives them their own database, backend and frontend:
#
#   database  stv_e2e                 (created if missing, on the same PostgreSQL as the app)
#   backend   http://localhost:8180   (packaged build with the dev profile, test data set and simulators)
#   frontend  http://localhost:4210   (ng serve, proxying /api to :8180)
#
#   scripts/e2e-isolated.sh                         # all tests
#   scripts/e2e-isolated.sh tests/club-data.spec.ts # arguments go to «playwright test»
#
# Ports and database: E2E_BACKEND_PORT, E2E_FRONTEND_PORT, E2E_DB. Logs: .local/e2e-backend.log,
# .local/e2e-frontend.log. Backend and frontend are stopped when the tests are done.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOGS="$ROOT/.local"
BACKEND_PORT="${E2E_BACKEND_PORT:-8180}"
FRONTEND_PORT="${E2E_FRONTEND_PORT:-4210}"
DB="${E2E_DB:-stv_e2e}"
PGPORT="${PGPORT:-5432}"
mkdir -p "$LOGS"

# ---------------------------------------------------------------- database
sql() { # runs one statement as the user stv on the maintenance database, prints the result
  if command -v psql >/dev/null 2>&1; then
    PGPASSWORD="${STV_DB_PASSWORD:-stv}" psql -h localhost -p "$PGPORT" -U "${STV_DB_USER:-stv}" -d postgres -tAc "$1"
  else
    # PostgreSQL in a container (e.g. «stv-db»): use the psql inside it
    docker exec "${E2E_DB_CONTAINER:-stv-db}" psql -U "${STV_DB_USER:-stv}" -d postgres -tAc "$1"
  fi
}
if [ "$(sql "select 1 from pg_database where datname = '$DB'")" != "1" ]; then
  echo "Creating database $DB …"
  sql "create database $DB owner ${STV_DB_USER:-stv}" >/dev/null
fi

# ---------------------------------------------------------------- stop what this script started
PIDS=()
stop_port() { # on Windows a killed shell leaves its java or node child behind: stop whoever listens
  if command -v taskkill >/dev/null 2>&1; then
    for pid in $(netstat -ano 2>/dev/null | grep -E ":$1 " | grep LISTEN | awk '{print $NF}' | sort -u); do
      taskkill //PID "$pid" //T //F >/dev/null 2>&1 || true
    done
  fi
}
cleanup() {
  for pid in "${PIDS[@]}"; do
    kill "$pid" 2>/dev/null || true
  done
  stop_port "$BACKEND_PORT"
  stop_port "$FRONTEND_PORT"
  wait 2>/dev/null || true
}
trap cleanup EXIT INT TERM

for port in "$BACKEND_PORT" "$FRONTEND_PORT"; do
  if curl -s -o /dev/null "http://localhost:$port"; then
    echo "Port $port is already in use. Choose others with E2E_BACKEND_PORT and E2E_FRONTEND_PORT." >&2
    PIDS=()
    trap - EXIT INT TERM
    exit 1
  fi
done

wait_for() { # url name seconds
  local i=0
  until curl -fsS -o /dev/null "$1" 2>/dev/null; do
    i=$((i + 1))
    if [ "$i" -ge "$3" ]; then
      echo "$2 is not reachable after $3 seconds. Logs: $LOGS" >&2
      exit 1
    fi
    sleep 1
  done
}

# ---------------------------------------------------------------- backend
# A packaged build with the dev profile (test endpoints included), in its own folder, so that a running
# «quarkus:dev» of the same sources is left alone.
echo "Building the backend for the E2E tests …"
(cd "$ROOT/backend" && mvn -B -q package -Dmaven.test.skip=true -Dquarkus.profile=dev \
  -Dquarkus.package.output-directory=e2e-app)
echo "Starting backend on :$BACKEND_PORT (database $DB, test data set) …"
(cd "$ROOT/backend" && STV_SIMULATION=true STV_DB_URL="jdbc:postgresql://localhost:$PGPORT/$DB" \
  exec java -Dquarkus.http.port="$BACKEND_PORT" -jar target/e2e-app/quarkus-run.jar) \
  >"$LOGS/e2e-backend.log" 2>&1 &
PIDS+=($!)

# ---------------------------------------------------------------- frontend
cat >"$LOGS/proxy.e2e.conf.json" <<JSON
{ "/api": { "target": "http://localhost:$BACKEND_PORT", "secure": false } }
JSON
if [ ! -d "$ROOT/frontend/node_modules" ]; then
  (cd "$ROOT/frontend" && npm ci --no-audit --no-fund)
fi
echo "Starting frontend on :$FRONTEND_PORT …"
(cd "$ROOT/frontend" && exec npx ng serve --port "$FRONTEND_PORT" --proxy-config ../.local/proxy.e2e.conf.json) \
  >"$LOGS/e2e-frontend.log" 2>&1 &
PIDS+=($!)

wait_for "http://localhost:$BACKEND_PORT/api/test/clock" "Backend" 180
wait_for "http://localhost:$FRONTEND_PORT" "Frontend" 300

# ---------------------------------------------------------------- tests
cd "$ROOT/e2e"
[ -d node_modules ] || npm ci --no-audit --no-fund
BASE_URL="http://localhost:$FRONTEND_PORT" E2E_NO_WEBSERVER=true npx playwright test --trace off "$@"
