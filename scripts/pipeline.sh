#!/usr/bin/env bash
# GitHub helper commands for the pipeline agents.
#
# Why a script: in Claude Code cloud sessions the GitHub GraphQL API is blocked.
# Many gh commands (gh pr list, gh pr edit, gh issue list, gh pr create) use GraphQL
# and fail there. This script uses only the REST API via "gh api".
#
# Usage: scripts/pipeline.sh <command> [arguments]   (no arguments: help)
set -euo pipefail

REPO="${PIPELINE_REPO:-cyrilleulmi/StvBeroPlatform}"
API="repos/$REPO"
LOCK="agent:aktiv"
LOCK_MAX_HOURS="${PIPELINE_LOCK_HOURS:-${PIPELINE_SPERRE_STUNDEN:-3}}"   # old variable name still accepted

enc() { python3 -c 'import sys,urllib.parse;print(urllib.parse.quote(sys.argv[1],safe=""))' "$1"; }

usage() {
  cat <<'EOF'
Commands:
  prs <label>                    Open PRs with the label, oldest first, without an active lock: nr<TAB>branch<TAB>title
  issues <label>                 Open issues (no PRs) with the label, without an active lock: nr<TAB>title
  hat-label <nr> <label>         Exit 0 if the issue/PR has the label
  label-von <nr> <label>         GitHub login that last set the label
  label-setzen <nr> <label>
  label-entfernen <nr> <label>   (no error if it is missing)
  wechseln <nr> <old> <new>      Remove old first, then set new
  sperren <nr>                   Sets agent:aktiv
  entsperren <nr>                Removes agent:aktiv
  gesperrt <nr>                  Exit 0 if agent:aktiv is set and younger than 3 hours
  kommentieren <nr> <file>       Comment from a Markdown file
  pr-erstellen <title> <branch> <file>   Open a PR against main, prints the number
  pr-branch <nr>                 Head branch of a PR
  pr-issue <nr>                  Issue number from "Closes #n" in the PR
  runde <nr>                     Number of rework rounds (development comments minus 1, min. 0)
  letzter-befund <nr>            Text of the newest review or test comment
  laufend                        What is currently in the pipeline: open PRs with a pipeline: label (except gemergt)
                                 and issues with status:in-arbeit, pipeline:blockiert or agent:aktiv. Empty = pipeline free
  voraussetzungen <issue>        Exit 0 if all "Voraussetzung: F-xx" issues are closed; otherwise the open ones on stderr
  naechstes-feature              Next issue with status:bereit: follow-up fixes first (oldest), then F issues
                                 (lowest number); without lock/block, prerequisites met
  nachbesserung <pr> <title> <file>  Create an issue typ:nachbesserung + status:bereit (text from file), avoid duplicates by title;
                                 if the PR itself comes from a follow-up fix, without status:bereit (no endless loop)
  beanspruchen <issue>           Sets the lock and a claim comment, waits 20 s. Exit 0 only if this run
                                 holds the oldest claim (prevents two runs from building the same feature)
  anstossen <issue>              Remove status:bereit and set it again: starts the development task
  label-alter <nr> <label>       Minutes since the label was last set (empty if never)
  waechter [--trocken]           Detects stuck steps (aborted runs, lost events) and triggers
                                 them again. --trocken: dry run, only report, change nothing
  mergebar <nr>                  Exit 0 if the PR is open, free of conflicts and all checks are green; otherwise the reason on stderr
  mergen <nr> <expected-sha>     Squash merge into main, only if mergeable and head = expected SHA. Prints the merge SHA
EOF
}

# Minutes since the last "labeled" event for a label (empty if never set)
label_age() {
  local since
  since=$(gh api "$API/issues/$1/events?per_page=100" --paginate \
    --jq "[.[] | select(.event==\"labeled\" and .label.name==\"$2\")] | last | .created_at // empty" | tail -1)
  [ -z "$since" ] && return 0
  python3 -c 'import sys,datetime as d;t=d.datetime.fromisoformat(sys.argv[1].replace("Z","+00:00"));print(int((d.datetime.now(d.timezone.utc)-t).total_seconds()//60))' "$since"
}

# Exit 0 if there was activity in the last ACTIVE_MIN minutes: a push to a claude/ branch
# or a change to the issue/PR. Long runs push intermediate states regularly and so count as alive.
recently_active() {
  local nr="$1" limit
  limit=$(python3 -c 'import sys,datetime as d;print((d.datetime.now(d.timezone.utc)-d.timedelta(minutes=int(sys.argv[1]))).strftime("%Y-%m-%dT%H:%M:%SZ"))' "${ACTIVE_MIN:-${AKTIV_MIN:-60}}")
  local updated; updated=$(gh api "$API/issues/$nr" --jq .updated_at)
  [[ "$updated" > "$limit" ]] && return 0
  local b d
  for b in $(gh api "$API/branches?per_page=100" --jq '.[].name | select(startswith("claude/"))'); do
    d=$(gh api "$API/commits/$(enc "$b")" --jq .commit.committer.date)
    [[ "$d" > "$limit" ]] && return 0
  done
  return 1
}

# Number of watchdog triggers on an item in the last 24 hours
watchdog_triggers_24h() {
  local since
  since=$(python3 -c 'import datetime as d;print((d.datetime.now(d.timezone.utc)-d.timedelta(hours=24)).strftime("%Y-%m-%dT%H:%M:%SZ"))')
  gh api "$API/issues/$1/comments?since=$since&per_page=100" \
    --jq '[.[] | select(.body | startswith("<!-- pipeline:waechter -->"))] | length'
}

# Watchdog: finds stuck steps and triggers them again.
#  - Lock agent:aktiv older than LOCK_MAX_HOURS and no activity for ACTIVE_MIN (push to a claude/ branch,
#    change to the item): the run counts as aborted, remove the lock, trigger the step again
#  - PR with pipeline:review/test/nacharbeit without a lock, label older than WAIT_MIN: event lost, trigger again
#  - Queue: nothing is running, but a feature is ready and not claimed for WAIT_MIN: trigger it
#  - More than MAX_TRIGGERS per item in 24 h: pipeline:blockiert instead of triggering endlessly
watchdog() {
  local dry_run="$1" WAIT_MIN="${WATCHDOG_WAIT_MIN:-${WAECHTER_WARTE_MIN:-45}}" MAX_TRIGGERS=3 actions=0
  run() { if [ "$dry_run" = "--trocken" ]; then echo "  (dry run) $*"; else "$@"; fi; }
  note() { # nr text
    local tmp; tmp=$(mktemp)
    printf '<!-- pipeline:waechter -->\n🛟 **Watchdog:** %s\n' "$2" > "$tmp"
    run gh api -X POST "$API/issues/$1/comments" -F "body=@$tmp" --silent; rm -f "$tmp"
  }
  trigger() { # nr label reason
    local n; n=$(watchdog_triggers_24h "$1")
    if [ "$n" -ge "$MAX_TRIGGERS" ]; then
      echo "#$1: already triggered $n times in 24 h → pipeline:blockiert"
      note "$1" "Step \`$2\` is still stuck ($3). After $n triggers in 24 hours I give up. Please check, then remove \`pipeline:blockiert\` and set \`$2\` again."
      run "$0" label-setzen "$1" "pipeline:blockiert"
    else
      echo "#$1: $3 → $2 triggered again"
      note "$1" "$3. I am triggering \`$2\` again."
      run "$0" label-entfernen "$1" "$2"; [ "$dry_run" = "--trocken" ] || sleep 2
      run "$0" label-setzen "$1" "$2"
    fi
    actions=$((actions+1))
  }

  # 1. PRs in the pipeline
  local nr l age lock
  for l in pipeline:review pipeline:test pipeline:nacharbeit; do
    gh api "$API/issues?state=open&labels=$(enc "$l")&per_page=100" --jq '.[] | select(.pull_request) | .number' |
    while read -r nr; do
      if has_label "$nr" "pipeline:blockiert"; then continue; fi
      if has_label "$nr" "$LOCK"; then
        lock=$(label_age "$nr" "$LOCK")
        if [ -n "$lock" ] && [ "$lock" -ge $((LOCK_MAX_HOURS*60)) ] && ! recently_active "$nr"; then
          run "$0" entsperren "$nr"
          trigger "$nr" "$l" "Run locked for ${lock} minutes, counts as aborted"
        else
          echo "#$nr: $l, agent is working (locked for ${lock:-?} min)"
        fi
      else
        age=$(label_age "$nr" "$l")
        if [ -n "$age" ] && [ "$age" -ge "$WAIT_MIN" ]; then
          trigger "$nr" "$l" "Label \`$l\` set for ${age} minutes without an agent"
        else
          echo "#$nr: $l for ${age:-?} min, still within the time window"
        fi
      fi
    done
  done

  # 2. Issues with an expired lock (development aborted before the PR)
  gh api "$API/issues?state=open&labels=$(enc "$LOCK")&per_page=100" --jq '.[] | select(.pull_request == null) | .number' |
  while read -r nr; do
    lock=$(label_age "$nr" "$LOCK")
    if [ -n "$lock" ] && [ "$lock" -ge $((LOCK_MAX_HOURS*60)) ] && ! recently_active "$nr"; then
      echo "#$nr: locked for ${lock} min and no activity for ${ACTIVE_MIN:-${AKTIV_MIN:-60}} min, development counts as aborted → lock removed"
      note "$nr" "Development was locked for ${lock} minutes and counts as aborted. Lock removed."
      run "$0" entsperren "$nr"
    else
      echo "#$nr: development is running (locked for ${lock:-?} min)"
    fi
  done

  # 3. Queue
  if [ -z "$("$0" laufend)" ]; then
    local next; next=$("$0" naechstes-feature | cut -f1)
    if [ -n "$next" ]; then
      age=$(label_age "$next" "status:bereit")
      if [ -n "$age" ] && [ "$age" -ge "$WAIT_MIN" ]; then
        trigger "$next" "status:bereit" "Feature has been waiting for ${age} minutes, but no development is running"
      else
        echo "#$next: next in line, ready for ${age:-?} min"
      fi
    else
      echo "Queue empty or prerequisites open."
    fi
  else
    echo "Pipeline busy:"; "$0" laufend | sed 's/^/  /'
  fi
}

# Checks whether a PR may be merged. Reasons go to stderr.
mergebar() {
  local nr="$1" info state mergeable mstate sha draft i
  for i in 1 2 3 4 5 6; do   # GitHub computes "mergeable" asynchronously
    info=$(gh api "$API/pulls/$nr" --jq '[.state, (.mergeable|tostring), .mergeable_state, .head.sha, (.draft|tostring), .base.ref] | @tsv')
    IFS=$'\t' read -r state mergeable mstate sha draft base <<<"$info"
    [ "$mergeable" != "null" ] && break
    sleep 5
  done
  [ "$state" = "open" ]      || { echo "PR is not open ($state)" >&2; return 1; }
  [ "$base" = "main" ]       || { echo "PR does not target main ($base)" >&2; return 1; }
  [ "$draft" = "false" ]     || { echo "PR is a draft" >&2; return 1; }
  [ "$mergeable" = "true" ]  || { echo "PR has conflicts with main (mergeable=$mergeable, state=$mstate)" >&2; return 1; }
  # Checks (CI): all completed and successful or skipped
  local pending failed
  pending=$(gh api "$API/commits/$sha/check-runs?per_page=100" --jq '[.check_runs[] | select(.status != "completed")] | length')
  failed=$(gh api "$API/commits/$sha/check-runs?per_page=100" --jq '[.check_runs[] | select(.status == "completed" and (.conclusion | IN("success","skipped","neutral") | not))] | map(.name) | join(", ")')
  [ "$pending" = "0" ] || { echo "Checks still running ($pending open)" >&2; return 1; }
  [ -z "$failed" ]     || { echo "Checks failed: $failed" >&2; return 1; }
  echo "$sha"
}

labels_of() { gh api "$API/issues/$1/labels" --jq '.[].name'; }

has_label() { labels_of "$1" | grep -qx -- "$2"; }

label_set_by() {
  gh api "$API/issues/$1/events?per_page=100" --paginate \
    --jq "[.[] | select(.event==\"labeled\" and .label.name==\"$2\")] | last | .actor.login // empty" | tail -1
}

is_locked() {
  has_label "$1" "$LOCK" || return 1
  local since
  since=$(gh api "$API/issues/$1/events?per_page=100" --paginate \
    --jq "[.[] | select(.event==\"labeled\" and .label.name==\"$LOCK\")] | last | .created_at // empty" | tail -1)
  [ -z "$since" ] && return 0
  python3 - "$since" "$LOCK_MAX_HOURS" <<'PY'
import sys, datetime as d
t = d.datetime.fromisoformat(sys.argv[1].replace('Z', '+00:00'))
age = (d.datetime.now(d.timezone.utc) - t).total_seconds() / 3600
sys.exit(0 if age < float(sys.argv[2]) else 1)
PY
}

list_items() { # $1 label, $2 pr|issue
  local l; l=$(enc "$1")
  gh api "$API/issues?state=open&labels=$l&sort=created&direction=asc&per_page=100" \
    --jq ".[] | select(${2}) | [.number, .title] | @tsv"
}

cmd="${1:-}"; shift || true
case "$cmd" in
  prs)
    list_items "$1" '.pull_request' | while IFS=$'\t' read -r nr title; do
      is_locked "$nr" && continue
      printf '%s\t%s\t%s\n' "$nr" "$(gh api "$API/pulls/$nr" --jq .head.ref)" "$title"
    done ;;
  issues)
    list_items "$1" '.pull_request | not' | while IFS=$'\t' read -r nr title; do
      is_locked "$nr" && continue
      printf '%s\t%s\n' "$nr" "$title"
    done ;;
  hat-label) has_label "$1" "$2" ;;
  label-von) label_set_by "$1" "$2" ;;
  label-setzen) gh api -X POST "$API/issues/$1/labels" -f "labels[]=$2" --silent ;;
  label-entfernen) gh api -X DELETE "$API/issues/$1/labels/$(enc "$2")" --silent 2>/dev/null || true ;;
  wechseln)
    "$0" label-entfernen "$1" "$2"
    "$0" label-setzen "$1" "$3" ;;
  sperren) "$0" label-setzen "$1" "$LOCK" ;;
  entsperren) "$0" label-entfernen "$1" "$LOCK" ;;
  gesperrt) is_locked "$1" ;;
  kommentieren) gh api -X POST "$API/issues/$1/comments" -F "body=@$2" --jq .html_url ;;
  pr-erstellen)
    gh api -X POST "$API/pulls" -f "title=$1" -f "head=$2" -f base=main -F "body=@$3" --jq .number ;;
  pr-branch) gh api "$API/pulls/$1" --jq .head.ref ;;
  pr-issue)
    gh api "$API/pulls/$1" --jq '.body // ""' | grep -oiE '(closes|fixes|resolves) #[0-9]+' | head -1 | grep -oE '[0-9]+' ;;
  runde)
    n=$(gh api "$API/issues/$1/comments?per_page=100" --paginate \
      --jq '[.[] | select(.body | startswith("<!-- pipeline:entwicklung -->"))] | length' | awk '{s+=$1} END {print s+0}')
    echo $(( n > 0 ? n - 1 : 0 )) ;;
  letzter-befund)
    gh api "$API/issues/$1/comments?per_page=100" --paginate \
      --jq '.[] | select((.body | startswith("<!-- pipeline:review -->")) or (.body | startswith("<!-- pipeline:test -->"))) | .body' \
      | awk 'BEGIN{RS="<!-- pipeline:"} NF{last=$0} END{printf "<!-- pipeline:%s", last}' ;;
  laufend)
    gh api "$API/issues?state=open&per_page=100" --jq '.[]
      | {nr: .number, pr: (.pull_request != null), title: .title, l: [.labels[].name]}
      | select( (.pr and any(.l[]; startswith("pipeline:") and . != "pipeline:gemergt"))
             or ((.pr|not) and any(.l[]; . == "status:in-arbeit" or . == "pipeline:blockiert" or . == "agent:aktiv")) )
      | "\(.nr)\t\(if .pr then "PR" else "Issue" end)\t\(.l | map(select(startswith("pipeline:") or startswith("status:"))) | join(","))\t\(.title)"' ;;
  voraussetzungen)
    prereqs=$(gh api "$API/issues/$1" --jq '.body // ""' | grep -m1 -oE 'Voraussetzung:\**[^\n]*' | grep -oE 'F-[0-9]{2}' || true)
    pending=""
    for f in $prereqs; do
      st=$(gh api "$API/issues?state=all&per_page=100" --jq ".[] | select(.pull_request == null and (.title | startswith(\"$f \"))) | .state" | head -1)
      [ "$st" = "closed" ] || pending="$pending $f(${st:-missing})"
    done
    [ -z "$pending" ] || { echo "Open prerequisites:$pending" >&2; exit 1; } ;;
  naechstes-feature)
    # Order: released follow-up fixes (oldest first), then features by F number.
    # Each without a lock, without a block and with prerequisites met.
    {
      gh api "$API/issues?state=open&labels=status%3Abereit&sort=created&direction=asc&per_page=100" \
        --jq '.[] | select(.pull_request == null) | select([.labels[].name] | (index("pipeline:blockiert") | not) and (index("typ:nachbesserung") != null)) | [.number, .title] | @tsv'
      gh api "$API/issues?state=open&labels=status%3Abereit&per_page=100" \
        --jq '.[] | select(.pull_request == null) | select([.labels[].name] | index("pipeline:blockiert") | not) | select(.title | test("^F-[0-9]{2} ")) | [.number, .title] | @tsv' \
        | sort -t$'\t' -k2
    } | while IFS=$'\t' read -r nr title; do
          is_locked "$nr" && continue
          "$0" voraussetzungen "$nr" 2>/dev/null || continue
          printf '%s\t%s\n' "$nr" "$title"; break
        done ;;
  nachbesserung)
    # nachbesserung <pr> <title> <file>: creates an issue typ:nachbesserung, unless an open one with the
    # same title already exists. Prints the issue number. Normal case: with status:bereit, development picks it up
    # automatically after the running PR (E-63). If the PR itself comes from a follow-up fix, it is not
    # released automatically, so that no endless loop of finding and follow-up fix arises.
    pr="$1"; title="Follow-up fix · $2"; file="$3"
    existing=$(gh api "$API/issues?state=open&labels=typ%3Anachbesserung&per_page=100" --jq ".[] | select(.title == \"$title\") | .number" | head -1)
    if [ -n "$existing" ]; then echo "$existing"; exit 0; fi
    source_issue=$("$0" pr-issue "$pr" 2>/dev/null || true)
    release=1
    if [ -n "$source_issue" ] && gh api "$API/issues/$source_issue" --jq '[.labels[].name] | index("typ:nachbesserung") != null' | grep -q true; then release=0; fi
    if [ "$release" = 1 ]; then note_text='Development implements this automatically after the running PR.'
    else note_text='Comes from a follow-up fix and is therefore not released automatically. Setting `status:bereit` puts it in the queue.'; fi
    tmp=$(mktemp); { cat "$file"; printf '\n\n---\nFound in #%s. %s\n' "$pr" "$note_text"; } > "$tmp"
    python3 - "$title" "$tmp" "$release" > "$tmp.json" <<'PY'
import json,sys
labels=["typ:nachbesserung"]+(["status:bereit"] if sys.argv[3]=="1" else [])
print(json.dumps({"title":sys.argv[1],"body":open(sys.argv[2],encoding="utf-8").read(),"labels":labels}))
PY
    gh api -X POST "$API/issues" --input "$tmp.json" --jq .number; rm -f "$tmp" "$tmp.json" ;;
  beanspruchen)
    nr="$1"; token="$(date +%s)-$RANDOM$RANDOM"
    "$0" label-setzen "$nr" "$LOCK"
    tmp=$(mktemp); printf '<!-- pipeline:anspruch %s -->\n🤖 Development claimed this feature.\n' "$token" > "$tmp"
    gh api -X POST "$API/issues/$nr/comments" -F "body=@$tmp" --silent; rm -f "$tmp"
    sleep 20
    since=$(python3 -c 'import datetime as d;print((d.datetime.now(d.timezone.utc)-d.timedelta(minutes=15)).strftime("%Y-%m-%dT%H:%M:%SZ"))')
    first=$(gh api "$API/issues/$nr/comments?since=$since&per_page=100" \
      --jq '[.[] | select(.body | startswith("<!-- pipeline:anspruch "))] | sort_by(.created_at, .id) | first | .body' \
      | grep -oE 'anspruch [^ ]+' | cut -d' ' -f2)
    if [ "$first" = "$token" ]; then echo "claimed"; else echo "Another run was faster ($first)" >&2; exit 1; fi ;;
  anstossen)
    "$0" label-entfernen "$1" "status:bereit"; sleep 2; "$0" label-setzen "$1" "status:bereit" ;;
  label-alter) label_age "$1" "$2" ;;
  waechter) watchdog "${1:-}" ;;
  mergebar) mergebar "$1" >/dev/null ;;
  mergen)
    nr="$1"; expected="${2:-}"
    [ -n "$expected" ] || { echo "mergen needs the tested head SHA as its second argument" >&2; exit 2; }
    sha=$(mergebar "$nr") || exit 1
    [ "$sha" = "$expected" ] || { echo "Head has changed since the test ($sha instead of $expected). Not merged." >&2; exit 1; }
    title=$(gh api "$API/pulls/$nr" --jq .title)
    gh api -X PUT "$API/pulls/$nr/merge" -f merge_method=squash -f "sha=$sha" \
      -f "commit_title=$title (#$nr)" --jq .sha ;;
  ""|-h|--help|hilfe) usage ;;
  *) echo "Unknown command: $cmd" >&2; usage >&2; exit 2 ;;
esac
