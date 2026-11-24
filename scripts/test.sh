#!/usr/bin/env bash
# Runs all tests and exits with code 0 when everything is green (docs/architecture.md):
#   1. modules.json is complete; API contract (redocly lint)
#   2. Backend: unit tests, REST tests, architecture tests and the coverage gate (mvn verify -Pcoverage)
#   3. Frontend: lint (with module boundaries and size limits), unit tests, build
#   4. E2E tests (Playwright with axe) in parallel on stacks of their own (scripts/e2e.sh)
#
#   scripts/test.sh          # E2E fast run: every test on mobile, the @desktop tests on desktop
#   scripts/test.sh --full   # E2E full run: every test on mobile and on desktop (the CI after every merge)
#
# While developing, scripts/test-affected.sh runs only what a change can break.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
E2E_ARGS=()
for arg in "$@"; do
  case "$arg" in
    --full) E2E_ARGS+=("--full") ;;
    *) echo "Unknown argument: $arg" >&2; exit 2 ;;
  esac
done

if [ "${CI:-}" != "true" ]; then
  "$ROOT/scripts/db-start.sh"
fi

echo "== Modules and API contract =="
node "$ROOT/scripts/affected.mjs" --check
npx -y @redocly/cli@2.78.0 lint "$ROOT/docs/api/openapi.yaml"

echo "== Backend =="
(cd "$ROOT/backend" && mvn -B verify -Pcoverage)

echo "== Frontend =="
(cd "$ROOT/frontend" && { [ -d node_modules ] || npm ci --no-audit --no-fund; } && npm run lint && npm test && npm run build)

echo "== E2E =="
"$ROOT/scripts/e2e.sh" ${E2E_ARGS[@]+"${E2E_ARGS[@]}"}

echo "All tests green."
