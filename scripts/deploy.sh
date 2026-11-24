#!/usr/bin/env bash
# Deploys the hosted test environments on Fly.io (E-91, docs/deployment.md).
#
#   scripts/deploy.sh check                 # prints configured=true|false (for $GITHUB_OUTPUT)
#   scripts/deploy.sh test                  # test environment of develop: <FLY_APP>.fly.dev
#   scripts/deploy.sh preview <pr>          # preview of a pull request: <FLY_APP>-pr-<pr>.fly.dev
#   scripts/deploy.sh remove-preview <pr>   # destroys the app of a pull request
#   scripts/deploy.sh cleanup               # destroys previews whose pull request is closed (needs gh)
#   scripts/deploy.sh diagnose              # what the token may see (after a failed run; prints no secrets)
#
# Both run the image built with profile preview: club data set with live data from swiss unihockey, /dev-login,
# and a throwaway PostgreSQL inside the container (docker/start.sh). No data is kept between starts.
#
# Environment (GitHub secret and variables, see docs/deployment.md):
#   FLY_API_TOKEN   org token of Fly.io (creates and destroys the preview apps)
#   FLY_APP         name of the test environment app (default stvbero), previews are <FLY_APP>-pr-<nr>
#   FLY_ORG         Fly.io organization (default personal)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FLY_APP="${FLY_APP:-stvbero}"
FLY_ORG="${FLY_ORG:-personal}"

log() { echo "deploy: $*" >&2; }

app_exists() { flyctl status --app "$1" >/dev/null 2>&1; }

deploy_app() { # app
  local app="$1" url="https://$1.fly.dev"
  if ! app_exists "$app"; then
    log "creating Fly app $app in organization $FLY_ORG"
    flyctl apps create "$app" --org "$FLY_ORG"
  fi
  (cd "$ROOT" && flyctl deploy --app "$app" --config fly.toml --local-only --ha=false \
    --env "STV_APP_URL=$url" --wait-timeout 5m)
  smoke_test "$url"
  log "deployed: $url"
  if [ -n "${GITHUB_OUTPUT:-}" ]; then echo "url=$url" >>"$GITHUB_OUTPUT"; fi
}

smoke_test() { # url: the page and the API answer from outside (the machine may need a few seconds to start)
  local url="$1" i
  for i in $(seq 1 30); do
    if curl -fsS --max-time 20 "$url/" | grep -q "<app-root" &&
      curl -sS --max-time 20 -o /dev/null "$url/api/session" -w '%{http_code}' | grep -qE '^(200|204|401)$'; then
      log "smoke test passed: $url answers with the app and the API"
      return 0
    fi
    sleep 5
  done
  log "smoke test failed: $url does not answer with the app"
  return 1
}

remove_app() { # app
  if app_exists "$1"; then
    flyctl apps destroy "$1" --yes
    log "Fly app $1 destroyed"
  fi
}

require() {
  if [ -z "${FLY_API_TOKEN:-}" ]; then
    log "not configured: secret FLY_API_TOKEN is missing (docs/deployment.md)"
    return 1
  fi
}

case "${1:-}" in
  check)
    if require 2>/dev/null; then echo "configured=true"; else echo "configured=false"; fi
    ;;
  test)
    require
    deploy_app "$FLY_APP"
    ;;
  preview)
    require
    deploy_app "$FLY_APP-pr-${2:?pull request number}"
    ;;
  remove-preview)
    require
    remove_app "$FLY_APP-pr-${2:?pull request number}"
    ;;
  cleanup)
    require
    for app in $(flyctl apps list --json | jq -r '.[] | (.Name // .name)' | grep -E "^$FLY_APP-pr-[0-9]+$" || true); do
      pr="${app##*-pr-}"
      state="$(gh pr view "$pr" --json state -q .state 2>/dev/null || echo UNKNOWN)"
      if [ "$state" != "OPEN" ]; then
        log "pull request #$pr is $state"
        remove_app "$app"
      fi
    done
    ;;
  diagnose)
    require
    for cmd in "version" "auth whoami" "orgs list" "apps list"; do
      echo "--- flyctl $cmd"
      # shellcheck disable=SC2086 # the command words are split on purpose
      flyctl $cmd 2>&1 || true
    done
    ;;
  *)
    sed -n '2,9p' "$0" >&2
    exit 2
    ;;
esac
