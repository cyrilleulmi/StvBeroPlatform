#!/usr/bin/env bash
# Starts a local PostgreSQL instance without Docker.
#
# Why: Claude cloud sessions have no Docker daemon. Quarkus Dev Services
# and Testcontainers do not work there. This script uses the
# PostgreSQL binaries that ship with the image, and it is idempotent.
#
# Result: database "stv", user "stv", password "stv" on localhost:${PGPORT:-5432}
#
# Usage:
#   scripts/db-start.sh          # start (creates the cluster the first time)
#   scripts/db-start.sh stop     # stop
#   scripts/db-start.sh reset    # delete the cluster and create it again
#
# After F-10 (issue #49) run 'reset' once on a database created before F-10:
# the Flyway migrations were rewritten with English names (no production data).
set -euo pipefail

PGPORT="${PGPORT:-5432}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DATA="${PGDATA_DIR:-$ROOT/.local/pgdata}"
LOG="$ROOT/.local/postgres.log"

# Find the PostgreSQL binaries (Ubuntu path first, then PATH)
PGBIN=""
for v in 18 17 16 15; do
  if [ -x "/usr/lib/postgresql/$v/bin/pg_ctl" ]; then PGBIN="/usr/lib/postgresql/$v/bin"; break; fi
done
if [ -z "$PGBIN" ] && command -v pg_ctl >/dev/null 2>&1; then PGBIN="$(dirname "$(command -v pg_ctl)")"; fi
if [ -z "$PGBIN" ]; then
  echo "PostgreSQL not found. Locally: 'docker compose up -d db' or install PostgreSQL." >&2
  exit 1
fi

# As root, PostgreSQL must run as a different user.
run() {
  if [ "$(id -u)" = "0" ]; then
    su postgres -s /bin/bash -c "$*"
  else
    bash -c "$*"
  fi
}

prepare_dirs() {
  mkdir -p "$ROOT/.local"
  if [ "$(id -u)" = "0" ]; then
    mkdir -p "$DATA"
    chown -R postgres:postgres "$ROOT/.local"
    chmod 700 "$DATA"
  fi
}

start() {
  prepare_dirs
  if [ ! -f "$DATA/PG_VERSION" ]; then
    echo "Creating PostgreSQL cluster: $DATA"
    run "'$PGBIN/initdb' -D '$DATA' -U postgres --auth=trust --encoding=UTF8 --locale=C.UTF-8 >/dev/null"
  fi
  if run "'$PGBIN/pg_ctl' -D '$DATA' status" >/dev/null 2>&1; then
    echo "PostgreSQL is already running."
  else
    # No fsync: this cluster only holds seed data that every reset builds again, and the tests reset it hundreds of
    # times (a third less time per reset). Never do this with data that must survive a crash.
    run "'$PGBIN/pg_ctl' -D '$DATA' -l '$LOG' -o '-p $PGPORT -k /tmp -c listen_addresses=localhost -c fsync=off -c synchronous_commit=off -c full_page_writes=off' -w start" >/dev/null
    echo "PostgreSQL started on localhost:$PGPORT"
  fi
  # Create user and database idempotently
  run "'$PGBIN/psql' -h localhost -p $PGPORT -U postgres -tAc \"SELECT 1 FROM pg_roles WHERE rolname='stv'\"" | grep -q 1 \
    || run "'$PGBIN/psql' -h localhost -p $PGPORT -U postgres -qc \"CREATE ROLE stv LOGIN PASSWORD 'stv' CREATEDB\""
  run "'$PGBIN/psql' -h localhost -p $PGPORT -U postgres -tAc \"SELECT 1 FROM pg_database WHERE datname='stv'\"" | grep -q 1 \
    || run "'$PGBIN/psql' -h localhost -p $PGPORT -U postgres -qc \"CREATE DATABASE stv OWNER stv\""
  echo "Ready: jdbc:postgresql://localhost:$PGPORT/stv (user stv, password stv)"
}

stop() {
  if [ -f "$DATA/PG_VERSION" ]; then
    run "'$PGBIN/pg_ctl' -D '$DATA' -m fast stop" >/dev/null 2>&1 || true
    echo "PostgreSQL stopped."
  fi
}

case "${1:-start}" in
  start) start ;;
  stop) stop ;;
  reset) stop; rm -rf "$DATA"; start ;;
  *) echo "Unknown: $1 (start|stop|reset)" >&2; exit 2 ;;
esac
