#!/usr/bin/env bash
# Deploys the hosted test environments on Fly.io with a Neon database (E-83, docs/deployment.md).
#
#   scripts/deploy.sh check                 # prints configured=true|false (for $GITHUB_OUTPUT)
#   scripts/deploy.sh test                  # test environment of main: <FLY_APP>.fly.dev, Neon default branch
#   scripts/deploy.sh preview <pr>          # preview of a pull request: <FLY_APP>-pr-<pr>.fly.dev, branch preview/pr-<pr>
#   scripts/deploy.sh remove-preview <pr>   # destroys the app and the branch of a pull request
#   scripts/deploy.sh cleanup               # removes previews whose pull request is closed (needs gh)
#
# Both environments run the image built with profile preview: seed data, /dev-login, simulators. Every start
# empties the database and builds it again (application.properties, %preview), so no data is kept.
#
# Environment (GitHub secrets and variables, see docs/deployment.md):
#   FLY_API_TOKEN     org token of Fly.io (creates and destroys the preview apps)
#   NEON_API_KEY      API key of Neon
#   NEON_PROJECT_ID   the Neon project; its default branch is the database of the test environment
#   FLY_APP           name of the test environment app (default stvbero), previews are <FLY_APP>-pr-<nr>
#   FLY_ORG           Fly.io organization (default personal)
#   NEON_DATABASE     database name (default neondb), NEON_ROLE role name (default neondb_owner)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FLY_APP="${FLY_APP:-stvbero}"
FLY_ORG="${FLY_ORG:-personal}"
NEON_DATABASE="${NEON_DATABASE:-neondb}"
NEON_ROLE="${NEON_ROLE:-neondb_owner}"
NEON_API="https://console.neon.tech/api/v2"

log() { echo "deploy: $*" >&2; }

neon() { # method path [json body]
  local method="$1" path="$2" body="${3:-}"
  local args=(-fsS -X "$method" -H "Authorization: Bearer $NEON_API_KEY" -H "Accept: application/json")
  if [ -n "$body" ]; then args+=(-H "Content-Type: application/json" -d "$body"); fi
  curl "${args[@]}" "$NEON_API/projects/$NEON_PROJECT_ID$path"
}

branch_id() { # name -> id or empty
  neon GET "/branches" | jq -r --arg n "$1" '.branches[] | select(.name == $n) | .id' | head -n 1
}

default_branch_id() {
  neon GET "/branches" | jq -r '.branches[] | select(.default == true) | .id' | head -n 1
}

wait_for_branch() { # id: waits until the operations of the branch are finished
  local i
  for i in $(seq 1 60); do
    if [ "$(neon GET "/branches/$1" | jq -r '.branch.current_state')" = "ready" ]; then return 0; fi
    sleep 2
  done
  log "branch $1 is not ready after 2 minutes"
  return 1
}

create_branch() { # name -> id (from the default branch, with a compute)
  local id
  id="$(neon POST "/branches" "{\"branch\":{\"name\":\"$1\"},\"endpoints\":[{\"type\":\"read_write\"}]}" \
    | jq -r '.branch.id')" || {
    log "Neon refused the branch $1. Is the limit of 10 branches reached? Close old pull requests or run cleanup."
    return 1
  }
  wait_for_branch "$id"
  echo "$id"
}

delete_branch() { # name
  local id
  id="$(branch_id "$1")"
  if [ -n "$id" ]; then
    neon DELETE "/branches/$id" >/dev/null
    log "Neon branch $1 deleted"
  fi
}

# Writes the secrets of the database of a branch to the app (staged, the following deploy applies them).
# The values never appear in the log: masked for GitHub Actions and passed to flyctl on stdin.
set_database_secrets() { # app branch-id url
  local app="$1" id="$2" url="$3" uri
  uri="$(neon GET "/connection_uri?branch_id=$id&database_name=$NEON_DATABASE&role_name=$NEON_ROLE" | jq -r '.uri')"
  python3 - "$uri" "$url" <<'PY' >"$RUNNER_SECRETS"
import sys, urllib.parse
uri = urllib.parse.urlsplit(sys.argv[1])
user = urllib.parse.unquote(uri.username or "")
password = urllib.parse.unquote(uri.password or "")
print(f"STV_DB_URL=jdbc:postgresql://{uri.hostname}{uri.path}?sslmode=require")
print(f"STV_DB_USER={user}")
print(f"STV_DB_PASSWORD={password}")
print(f"STV_APP_URL={sys.argv[2]}")
PY
  if [ -n "${GITHUB_ACTIONS:-}" ]; then echo "::add-mask::$(sed -n 's/^STV_DB_PASSWORD=//p' "$RUNNER_SECRETS")"; fi
  flyctl secrets import --stage --app "$app" <"$RUNNER_SECRETS" >/dev/null
  rm -f "$RUNNER_SECRETS"
}

ensure_app() { # app
  if flyctl status --app "$1" >/dev/null 2>&1; then return 0; fi
  log "creating Fly app $1 in organization $FLY_ORG"
  flyctl apps create "$1" --org "$FLY_ORG" >/dev/null
}

deploy_app() { # app branch-id
  local app="$1" url="https://$1.fly.dev"
  if [ -z "$2" ] || [ "$2" = "null" ]; then log "no Neon branch for $app"; return 1; fi
  ensure_app "$app"
  set_database_secrets "$app" "$2" "$url"
  (cd "$ROOT" && flyctl deploy --app "$app" --config fly.toml --local-only --ha=false --wait-timeout 5m)
  log "deployed: $url"
  if [ -n "${GITHUB_OUTPUT:-}" ]; then echo "url=$url" >>"$GITHUB_OUTPUT"; fi
}

require() {
  local missing=()
  for v in FLY_API_TOKEN NEON_API_KEY NEON_PROJECT_ID; do
    if [ -z "${!v:-}" ]; then missing+=("$v"); fi
  done
  if [ "${#missing[@]}" -gt 0 ]; then
    log "not configured, missing: ${missing[*]} (docs/deployment.md)"
    return 1
  fi
}

RUNNER_SECRETS="$(mktemp)"
trap 'rm -f "$RUNNER_SECRETS"' EXIT

case "${1:-}" in
  check)
    if require 2>/dev/null; then echo "configured=true"; else echo "configured=false"; fi
    ;;
  test)
    require
    id="$(default_branch_id)"
    deploy_app "$FLY_APP" "$id"
    ;;
  preview)
    require
    pr="${2:?pull request number}"
    name="preview/pr-$pr"
    # A fresh branch on every push: the app empties it on start anyway, and so no old compute settings stay.
    delete_branch "$name"
    id="$(create_branch "$name")"
    deploy_app "$FLY_APP-pr-$pr" "$id"
    ;;
  remove-preview)
    require
    pr="${2:?pull request number}"
    if flyctl status --app "$FLY_APP-pr-$pr" >/dev/null 2>&1; then
      flyctl apps destroy "$FLY_APP-pr-$pr" --yes >/dev/null
      log "Fly app $FLY_APP-pr-$pr destroyed"
    fi
    delete_branch "preview/pr-$pr"
    ;;
  cleanup)
    require
    for pr in $(neon GET "/branches" | jq -r '.branches[].name' | sed -n 's#^preview/pr-\([0-9]\+\)$#\1#p'); do
      state="$(gh pr view "$pr" --json state -q .state 2>/dev/null || echo UNKNOWN)"
      if [ "$state" != "OPEN" ]; then
        log "pull request #$pr is $state: removing its preview"
        "$0" remove-preview "$pr"
      fi
    done
    ;;
  *)
    sed -n '2,8p' "$0" >&2
    exit 2
    ;;
esac
