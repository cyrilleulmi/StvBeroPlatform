#!/usr/bin/env bash
# Runs the E2E tests in parallel on stacks of their own, next to an app that may be running on :4200/:8080.
#
# The E2E tests reset the database before every test, so tests that share a database cannot run at the same time,
# and run against the app you are trying out by hand they would wipe your data. This script builds the app once
# (the Angular build inside the backend, as in the container) and starts one backend with its own database per
# worker. Playwright spreads the test files over the workers (e2e/tests/helpers.ts sends each to its stack).
#
#   scripts/e2e.sh                                # fast run: every test on mobile, the @desktop tests on desktop
#   scripts/e2e.sh --full                         # every test on mobile and on desktop
#   scripts/e2e.sh tests/f04-responses.spec.ts    # other arguments go to «playwright test»
#   scripts/e2e.sh --workers 2 --project=mobile
#   scripts/e2e.sh --no-build tests/seed.spec.ts  # reuse the last build
#
#   stack i   backend http://localhost:<8180+i> (profile dev, test data set, simulators), database stv_e2e_<i>
#
# Workers: --workers or E2E_WORKERS; default: CPU cores minus one, at least 1, at most 4. First port:
# E2E_BASE_PORT. Logs: .local/e2e-backend-<i>.log. Everything is stopped when the tests are done.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOGS="$ROOT/.local"
BASE_PORT="${E2E_BASE_PORT:-8180}"
PGPORT="${PGPORT:-5432}"
FULL=false
BUILD=true
WORKERS="${E2E_WORKERS:-}"
ARGS=()
while [ $# -gt 0 ]; do
  case "$1" in
    --full) FULL=true ;;
    --no-build) BUILD=false ;;
    --workers) WORKERS="$2"; shift ;;
    --workers=*) WORKERS="${1#--workers=}" ;;
    *) ARGS+=("$1") ;;
  esac
  shift
done
if [ -z "$WORKERS" ]; then
  CORES="$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 2)"
  WORKERS=$((CORES - 1))
  [ "$WORKERS" -lt 1 ] && WORKERS=1
  [ "$WORKERS" -gt 4 ] && WORKERS=4
fi
mkdir -p "$LOGS"

if [ "${CI:-}" != "true" ]; then
  "$ROOT/scripts/db-start.sh"
fi

# ---------------------------------------------------------------- databases
sql() { # runs one statement as the user stv on the maintenance database, prints the result
  if command -v psql >/dev/null 2>&1; then
    PGPASSWORD="${STV_DB_PASSWORD:-stv}" psql -h localhost -p "$PGPORT" -U "${STV_DB_USER:-stv}" -d postgres -tAc "$1"
  else
    # PostgreSQL in a container (e.g. «stv-db»): use the psql inside it
    docker exec "${E2E_DB_CONTAINER:-stv-db}" psql -U "${STV_DB_USER:-stv}" -d postgres -tAc "$1"
  fi
}
for i in $(seq 0 $((WORKERS - 1))); do
  if [ "$(sql "select 1 from pg_database where datname = 'stv_e2e_$i'")" != "1" ]; then
    echo "Creating database stv_e2e_$i …"
    sql "create database stv_e2e_$i owner ${STV_DB_USER:-stv}" >/dev/null
  fi
done

# ---------------------------------------------------------------- stop what this script started
PIDS=()
stop_port() { # on Windows a killed shell leaves its java child behind: stop whoever listens
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
  for i in $(seq 0 $((WORKERS - 1))); do
    stop_port $((BASE_PORT + i))
  done
  wait 2>/dev/null || true
}
trap cleanup EXIT INT TERM

for i in $(seq 0 $((WORKERS - 1))); do
  if curl -s -o /dev/null "http://localhost:$((BASE_PORT + i))"; then
    echo "Port $((BASE_PORT + i)) is already in use. Choose another first port with E2E_BASE_PORT." >&2
    PIDS=()
    trap - EXIT INT TERM
    exit 1
  fi
done

# ---------------------------------------------------------------- build: frontend into the backend
# Build configuration «preview» (test mode with /dev-login) and backend profile dev (test endpoints). Both go
# into folders of their own (frontend/dist-e2e, backend/target-e2e), so a running «ng serve», «quarkus:dev» or
# test run of the same sources is left alone.
if [ "$BUILD" = true ]; then
  [ -d "$ROOT/frontend/node_modules" ] || (cd "$ROOT/frontend" && npm ci --no-audit --no-fund)
  echo "Building the frontend for the E2E tests …"
  (cd "$ROOT/frontend" && npx ng build --configuration preview --output-path dist-e2e >"$LOGS/e2e-frontend-build.log" 2>&1) \
    || { cat "$LOGS/e2e-frontend-build.log" >&2; exit 1; }
  echo "Building the backend for the E2E tests …"
  (cd "$ROOT/backend" && mvn -B -q package -Pe2e -Dmaven.test.skip=true -Dquarkus.profile=dev)
fi
JAR="$ROOT/backend/target-e2e/quarkus-app/quarkus-run.jar"
[ -f "$JAR" ] || { echo "No build found ($JAR). Run without --no-build once." >&2; exit 1; }

# ---------------------------------------------------------------- one backend per worker
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
STACKS=""
echo "Starting $WORKERS backend(s) from :$BASE_PORT (databases stv_e2e_0 … stv_e2e_$((WORKERS - 1)), test data set) …"
for i in $(seq 0 $((WORKERS - 1))); do
  port=$((BASE_PORT + i))
  (cd "$ROOT/backend" && STV_SIMULATION=true STV_DB_URL="jdbc:postgresql://localhost:$PGPORT/stv_e2e_$i" \
    STV_APP_URL="http://localhost:$port" \
    exec java -Dquarkus.http.port="$port" -jar "$JAR") >"$LOGS/e2e-backend-$i.log" 2>&1 &
  PIDS+=($!)
  STACKS="${STACKS:+$STACKS,}http://localhost:$port"
done
for i in $(seq 0 $((WORKERS - 1))); do
  wait_for "http://localhost:$((BASE_PORT + i))/api/test/clock" "Backend $i" 180
done

# ---------------------------------------------------------------- tests
cd "$ROOT/e2e"
[ -d node_modules ] || npm ci --no-audit --no-fund
E2E_STACKS="$STACKS" E2E_FULL="$FULL" npx playwright test --trace off ${ARGS[@]+"${ARGS[@]}"}
