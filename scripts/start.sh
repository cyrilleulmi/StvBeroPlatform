#!/usr/bin/env bash
# Startet die ganze App lokal: Datenbank, Backend (Quarkus, Profil dev) und Frontend (Angular).
#
#   scripts/start.sh            # starten, Ctrl-C beendet Backend und Frontend
#
# Danach: http://localhost:4200 (Anmeldung im Testmodus: http://localhost:4200/dev-login)
# Backend: http://localhost:8080 · Swagger UI: http://localhost:8080/q/swagger-ui
# Logs: .local/backend.log, .local/frontend.log
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOGS="$ROOT/.local"
mkdir -p "$LOGS"

"$ROOT/scripts/db-start.sh"

if [ ! -d "$ROOT/frontend/node_modules" ]; then
  echo "Installiere Frontend-Abhängigkeiten …"
  (cd "$ROOT/frontend" && npm ci --no-audit --no-fund)
fi

PIDS=()
aufraeumen() {
  for pid in "${PIDS[@]}"; do
    kill "$pid" 2>/dev/null || true
  done
  wait 2>/dev/null || true
}
trap aufraeumen EXIT INT TERM

warten() { # url name sekunden
  local url="$1" name="$2" max="$3" i=0
  until curl -fsS -o /dev/null "$url" 2>/dev/null; do
    i=$((i + 1))
    if [ "$i" -ge "$max" ]; then
      echo "$name ist nach $max Sekunden nicht erreichbar. Log: $LOGS" >&2
      exit 1
    fi
    sleep 1
  done
}

if curl -fsS -o /dev/null http://localhost:8080/api/sitzung 2>/dev/null; then
  echo "Backend läuft bereits auf :8080."
else
  echo "Starte Backend (Profil dev) …"
  (cd "$ROOT/backend" && exec mvn -B -q quarkus:dev -Dquarkus.console.enabled=false) >"$LOGS/backend.log" 2>&1 &
  PIDS+=($!)
fi

if curl -fsS -o /dev/null http://localhost:4200 2>/dev/null; then
  echo "Frontend läuft bereits auf :4200."
else
  echo "Starte Frontend …"
  (cd "$ROOT/frontend" && exec npm start -- --port 4200) >"$LOGS/frontend.log" 2>&1 &
  PIDS+=($!)
fi

warten http://localhost:8080/api/sitzung Backend 300
warten http://localhost:4200 Frontend 300

cat <<TEXT

Bereit.
  App:         http://localhost:4200
  Testmodus:   http://localhost:4200/dev-login
  Swagger UI:  http://localhost:8080/q/swagger-ui
Beenden mit Ctrl-C.
TEXT

if [ "${#PIDS[@]}" -gt 0 ]; then
  wait
fi
