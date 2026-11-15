#!/usr/bin/env bash
# Startet eine lokale PostgreSQL-Instanz ohne Docker.
#
# Warum: In Claude-Cloud-Sitzungen läuft kein Docker-Daemon. Quarkus Dev Services
# und Testcontainers funktionieren dort nicht. Dieses Skript nutzt die
# PostgreSQL-Binaries, die im Image vorhanden sind, und ist idempotent.
#
# Ergebnis: Datenbank "stv", Benutzer "stv", Passwort "stv" auf localhost:${PGPORT:-5432}
#
# Verwendung:
#   scripts/db-start.sh          # starten (legt den Cluster beim ersten Mal an)
#   scripts/db-start.sh stop     # stoppen
#   scripts/db-start.sh reset    # Cluster löschen und neu anlegen
set -euo pipefail

PGPORT="${PGPORT:-5432}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DATA="${PGDATA_DIR:-$ROOT/.local/pgdata}"
LOG="$ROOT/.local/postgres.log"

# PostgreSQL-Binaries finden (Ubuntu-Pfad zuerst, sonst PATH)
PGBIN=""
for v in 18 17 16 15; do
  if [ -x "/usr/lib/postgresql/$v/bin/pg_ctl" ]; then PGBIN="/usr/lib/postgresql/$v/bin"; break; fi
done
if [ -z "$PGBIN" ] && command -v pg_ctl >/dev/null 2>&1; then PGBIN="$(dirname "$(command -v pg_ctl)")"; fi
if [ -z "$PGBIN" ]; then
  echo "PostgreSQL nicht gefunden. Lokal: 'docker compose up -d db' oder PostgreSQL installieren." >&2
  exit 1
fi

# Als root muss PostgreSQL unter einem anderen Benutzer laufen.
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
    echo "Lege PostgreSQL-Cluster an: $DATA"
    run "'$PGBIN/initdb' -D '$DATA' -U postgres --auth=trust --encoding=UTF8 --locale=C.UTF-8 >/dev/null"
  fi
  if run "'$PGBIN/pg_ctl' -D '$DATA' status" >/dev/null 2>&1; then
    echo "PostgreSQL läuft bereits."
  else
    run "'$PGBIN/pg_ctl' -D '$DATA' -l '$LOG' -o '-p $PGPORT -k /tmp -c listen_addresses=localhost' -w start" >/dev/null
    echo "PostgreSQL gestartet auf localhost:$PGPORT"
  fi
  # Benutzer und Datenbank idempotent anlegen
  run "'$PGBIN/psql' -h localhost -p $PGPORT -U postgres -tAc \"SELECT 1 FROM pg_roles WHERE rolname='stv'\"" | grep -q 1 \
    || run "'$PGBIN/psql' -h localhost -p $PGPORT -U postgres -qc \"CREATE ROLE stv LOGIN PASSWORD 'stv' CREATEDB\""
  run "'$PGBIN/psql' -h localhost -p $PGPORT -U postgres -tAc \"SELECT 1 FROM pg_database WHERE datname='stv'\"" | grep -q 1 \
    || run "'$PGBIN/psql' -h localhost -p $PGPORT -U postgres -qc \"CREATE DATABASE stv OWNER stv\""
  echo "Bereit: jdbc:postgresql://localhost:$PGPORT/stv (Benutzer stv, Passwort stv)"
}

stop() {
  if [ -f "$DATA/PG_VERSION" ]; then
    run "'$PGBIN/pg_ctl' -D '$DATA' -m fast stop" >/dev/null 2>&1 || true
    echo "PostgreSQL gestoppt."
  fi
}

case "${1:-start}" in
  start) start ;;
  stop) stop ;;
  reset) stop; rm -rf "$DATA"; start ;;
  *) echo "Unbekannt: $1 (start|stop|reset)" >&2; exit 2 ;;
esac
