#!/usr/bin/env bash
# GitHub-Hilfsbefehle für die Pipeline-Agenten.
#
# Warum ein Skript: In Claude-Code-Cloud-Sitzungen ist die GitHub-GraphQL-API gesperrt.
# Viele gh-Befehle (gh pr list, gh pr edit, gh issue list, gh pr create) nutzen GraphQL
# und schlagen dort fehl. Dieses Skript verwendet nur die REST-API über «gh api».
#
# Verwendung: scripts/pipeline.sh <befehl> [argumente]   (ohne Argumente: Hilfe)
set -euo pipefail

REPO="${PIPELINE_REPO:-cyrilleulmi/StvBeroPlatform}"
API="repos/$REPO"
SPERRE="agent:aktiv"
SPERRE_MAX_STUNDEN=3

enc() { python3 -c 'import sys,urllib.parse;print(urllib.parse.quote(sys.argv[1],safe=""))' "$1"; }

hilfe() {
  cat <<'EOF'
Befehle:
  prs <label>                    Offene PRs mit Label, älteste zuerst, ohne aktive Sperre: nr<TAB>branch<TAB>titel
  issues <label>                 Offene Issues (keine PRs) mit Label, ohne aktive Sperre: nr<TAB>titel
  hat-label <nr> <label>         Exit 0, wenn Issue/PR das Label hat
  label-von <nr> <label>         GitHub-Login, das das Label zuletzt gesetzt hat
  label-setzen <nr> <label>
  label-entfernen <nr> <label>   (kein Fehler, wenn es fehlt)
  wechseln <nr> <alt> <neu>      Erst alt entfernen, dann neu setzen
  sperren <nr>                   Setzt agent:aktiv
  entsperren <nr>                Entfernt agent:aktiv
  gesperrt <nr>                  Exit 0, wenn agent:aktiv gesetzt und jünger als 3 Stunden
  kommentieren <nr> <datei>      Kommentar aus Markdown-Datei
  pr-erstellen <titel> <branch> <datei>   PR gegen main eröffnen, gibt die Nummer aus
  pr-branch <nr>                 Head-Branch eines PRs
  pr-issue <nr>                  Issue-Nummer aus «Closes #n» im PR
  runde <nr>                     Anzahl Nacharbeitsrunden (Entwicklungs-Kommentare minus 1, min. 0)
  letzter-befund <nr>            Text des neuesten Review- oder Test-Kommentars
  mergebar <nr>                  Exit 0, wenn der PR offen, konfliktfrei und alle Checks grün sind; sonst Grund auf stderr
  mergen <nr> <erwarteter-sha>   Squash-Merge nach main, nur wenn mergebar und Head = erwarteter SHA. Gibt Merge-SHA aus
EOF
}

# Prüft, ob ein PR gemergt werden darf. Gründe gehen auf stderr.
mergebar() {
  local nr="$1" info state mergeable mstate sha draft i
  for i in 1 2 3 4 5 6; do   # GitHub berechnet «mergeable» asynchron
    info=$(gh api "$API/pulls/$nr" --jq '[.state, (.mergeable|tostring), .mergeable_state, .head.sha, (.draft|tostring), .base.ref] | @tsv')
    IFS=$'\t' read -r state mergeable mstate sha draft base <<<"$info"
    [ "$mergeable" != "null" ] && break
    sleep 5
  done
  [ "$state" = "open" ]      || { echo "PR ist nicht offen ($state)" >&2; return 1; }
  [ "$base" = "main" ]       || { echo "PR zielt nicht auf main ($base)" >&2; return 1; }
  [ "$draft" = "false" ]     || { echo "PR ist ein Entwurf" >&2; return 1; }
  [ "$mergeable" = "true" ]  || { echo "PR hat Konflikte mit main (mergeable=$mergeable, state=$mstate)" >&2; return 1; }
  # Checks (CI): alle abgeschlossen und erfolgreich oder übersprungen
  local offen fehl
  offen=$(gh api "$API/commits/$sha/check-runs?per_page=100" --jq '[.check_runs[] | select(.status != "completed")] | length')
  fehl=$(gh api "$API/commits/$sha/check-runs?per_page=100" --jq '[.check_runs[] | select(.status == "completed" and (.conclusion | IN("success","skipped","neutral") | not))] | map(.name) | join(", ")')
  [ "$offen" = "0" ] || { echo "Checks laufen noch ($offen offen)" >&2; return 1; }
  [ -z "$fehl" ]     || { echo "Checks fehlgeschlagen: $fehl" >&2; return 1; }
  echo "$sha"
}

labels_von() { gh api "$API/issues/$1/labels" --jq '.[].name'; }

hat_label() { labels_von "$1" | grep -qx -- "$2"; }

label_von() {
  gh api "$API/issues/$1/events?per_page=100" --paginate \
    --jq "[.[] | select(.event==\"labeled\" and .label.name==\"$2\")] | last | .actor.login // empty" | tail -1
}

gesperrt() {
  hat_label "$1" "$SPERRE" || return 1
  local seit
  seit=$(gh api "$API/issues/$1/events?per_page=100" --paginate \
    --jq "[.[] | select(.event==\"labeled\" and .label.name==\"$SPERRE\")] | last | .created_at // empty" | tail -1)
  [ -z "$seit" ] && return 0
  python3 - "$seit" "$SPERRE_MAX_STUNDEN" <<'PY'
import sys, datetime as d
t = d.datetime.fromisoformat(sys.argv[1].replace('Z', '+00:00'))
alter = (d.datetime.now(d.timezone.utc) - t).total_seconds() / 3600
sys.exit(0 if alter < float(sys.argv[2]) else 1)
PY
}

liste() { # $1 label, $2 pr|issue
  local l; l=$(enc "$1")
  gh api "$API/issues?state=open&labels=$l&sort=created&direction=asc&per_page=100" \
    --jq ".[] | select(${2}) | [.number, .title] | @tsv"
}

cmd="${1:-}"; shift || true
case "$cmd" in
  prs)
    liste "$1" '.pull_request' | while IFS=$'\t' read -r nr titel; do
      gesperrt "$nr" && continue
      printf '%s\t%s\t%s\n' "$nr" "$(gh api "$API/pulls/$nr" --jq .head.ref)" "$titel"
    done ;;
  issues)
    liste "$1" '.pull_request | not' | while IFS=$'\t' read -r nr titel; do
      gesperrt "$nr" && continue
      printf '%s\t%s\n' "$nr" "$titel"
    done ;;
  hat-label) hat_label "$1" "$2" ;;
  label-von) label_von "$1" "$2" ;;
  label-setzen) gh api -X POST "$API/issues/$1/labels" -f "labels[]=$2" --silent ;;
  label-entfernen) gh api -X DELETE "$API/issues/$1/labels/$(enc "$2")" --silent 2>/dev/null || true ;;
  wechseln)
    "$0" label-entfernen "$1" "$2"
    "$0" label-setzen "$1" "$3" ;;
  sperren) "$0" label-setzen "$1" "$SPERRE" ;;
  entsperren) "$0" label-entfernen "$1" "$SPERRE" ;;
  gesperrt) gesperrt "$1" ;;
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
  mergebar) mergebar "$1" >/dev/null ;;
  mergen)
    nr="$1"; erwartet="${2:-}"
    [ -n "$erwartet" ] || { echo "mergen braucht den geprüften Head-SHA als zweites Argument" >&2; exit 2; }
    sha=$(mergebar "$nr") || exit 1
    [ "$sha" = "$erwartet" ] || { echo "Head hat sich seit dem Test geändert ($sha statt $erwartet). Nicht gemergt." >&2; exit 1; }
    titel=$(gh api "$API/pulls/$nr" --jq .title)
    gh api -X PUT "$API/pulls/$nr/merge" -f merge_method=squash -f "sha=$sha" \
      -f "commit_title=$titel (#$nr)" --jq .sha ;;
  ""|-h|--help|hilfe) hilfe ;;
  *) echo "Unbekannter Befehl: $cmd" >&2; hilfe >&2; exit 2 ;;
esac
