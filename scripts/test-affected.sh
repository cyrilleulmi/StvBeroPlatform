#!/usr/bin/env bash
# Runs only the tests a change can break, for the loop while developing. Before a pull request is handed over,
# scripts/test.sh still runs everything, and so does the CI.
#
# The modules and their dependencies come from modules.json (docs/architecture.md): a change in the backend of the
# driver list runs the backend tests and the E2E specs of the driver list, not the 300 others.
#
#   scripts/test-affected.sh                 # everything changed compared with origin/develop, plus uncommitted work
#   scripts/test-affected.sh --base HEAD     # uncommitted work only
#   scripts/test-affected.sh --plan          # only say what would run
#   scripts/test-affected.sh --no-e2e        # backend and frontend only (seconds to a minute)
#
# Even quicker, while typing (they rerun what a saved file touches):
#   cd backend && mvn quarkus:test           # continuous testing of Quarkus
#   cd frontend && npm run test:watch        # Vitest in watch mode
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PLAN_ONLY=false
E2E=true
PASS=()
while [ $# -gt 0 ]; do
  case "$1" in
    --plan) PLAN_ONLY=true ;;
    --no-e2e) E2E=false ;;
    --base) PASS+=("$1" "$2"); shift ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
  shift
done

node "$ROOT/scripts/affected.mjs" ${PASS[@]+"${PASS[@]}"}
[ "$PLAN_ONLY" = true ] && exit 0

PLAN="$(node "$ROOT/scripts/affected.mjs" --json ${PASS[@]+"${PASS[@]}"})"
get() { node -e 'const p = JSON.parse(process.argv[1]); console.log(process.argv[2].split(".").reduce((o, k) => o[k], p))' "$PLAN" "$1"; }

if [ "$(get changed.contract)" = "true" ]; then
  echo "== API contract =="
  npx -y @redocly/cli@2.78.0 lint "$ROOT/docs/api/openapi.yaml"
fi

if [ "$(get backend.run)" = "true" ]; then
  echo "== Backend =="
  [ "${CI:-}" = "true" ] || "$ROOT/scripts/db-start.sh"
  if [ "$(get backend.all)" = "true" ]; then
    (cd "$ROOT/backend" && mvn -B -q test)
  else
    (cd "$ROOT/backend" && mvn -B -q test -Dtest="$(get backend.tests)" -Dsurefire.failIfNoSpecifiedTests=false)
  fi
fi

if [ "$(get frontend.run)" = "true" ]; then
  echo "== Frontend =="
  (cd "$ROOT/frontend" && { [ -d node_modules ] || npm ci --no-audit --no-fund; } && npm run lint && npm test && npm run build)
fi

if [ "$E2E" = true ] && [ "$(get e2e.run)" = "true" ]; then
  echo "== E2E =="
  # shellcheck disable=SC2046 # one argument per spec
  "$ROOT/scripts/e2e.sh" $(node -e 'console.log(JSON.parse(process.argv[1]).e2e.specs.join(" "))' "$PLAN")
fi

echo "Affected tests green."
