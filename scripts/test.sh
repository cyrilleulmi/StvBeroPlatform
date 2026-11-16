#!/usr/bin/env bash
# Runs all tests and exits with code 0 when everything is green:
#   1. API contract (redocly lint)
#   2. Backend tests (including the comparison with docs/api/openapi.yaml)
#   3. Frontend lint and build
#   4. E2E tests (Playwright, projects mobile and desktop, with axe)
#
# Playwright starts the backend and frontend for the E2E tests itself, if they are not already running.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

if [ "${CI:-}" != "true" ]; then
  "$ROOT/scripts/db-start.sh"
fi

echo "== API contract =="
npx -y @redocly/cli@2.78.0 lint "$ROOT/docs/api/openapi.yaml"

echo "== Backend =="
(cd "$ROOT/backend" && mvn -B verify)

echo "== Frontend =="
(cd "$ROOT/frontend" && { [ -d node_modules ] || npm ci --no-audit --no-fund; } && npm run lint && npm run build)

echo "== E2E =="
(cd "$ROOT/e2e" && { [ -d node_modules ] || npm ci --no-audit --no-fund; } && npx playwright test --trace off)

echo "All tests green."
