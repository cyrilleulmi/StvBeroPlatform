#!/usr/bin/env bash
# Führt alle Tests aus und endet mit Exit-Code 0, wenn alles grün ist:
#   1. API-Vertrag (redocly lint)
#   2. Backend-Tests (inkl. Vergleich mit docs/api/openapi.yaml)
#   3. Frontend-Lint und -Build
#   4. E2E-Tests (Playwright, Projekte mobil und desktop, mit axe)
#
# Backend und Frontend für die E2E-Tests startet Playwright selbst, falls sie nicht schon laufen.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

if [ "${CI:-}" != "true" ]; then
  "$ROOT/scripts/db-start.sh"
fi

echo "== API-Vertrag =="
npx -y @redocly/cli@2.78.0 lint "$ROOT/docs/api/openapi.yaml"

echo "== Backend =="
(cd "$ROOT/backend" && mvn -B verify)

echo "== Frontend =="
(cd "$ROOT/frontend" && { [ -d node_modules ] || npm ci --no-audit --no-fund; } && npm run lint && npm run build)

echo "== E2E =="
(cd "$ROOT/e2e" && { [ -d node_modules ] || npm ci --no-audit --no-fund; } && npx playwright test --trace off)

echo "Alle Tests grün."
