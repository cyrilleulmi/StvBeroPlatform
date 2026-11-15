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
SPERRE_MAX_STUNDEN="${PIPELINE_SPERRE_STUNDEN:-3}"

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
  laufend                        Was gerade in der Pipeline ist: offene PRs mit pipeline:-Label (ausser gemergt)
                                 und Issues mit status:in-arbeit, pipeline:blockiert oder agent:aktiv. Leer = Pipeline frei
  voraussetzungen <issue>        Exit 0, wenn alle «Voraussetzung: F-xx»-Issues geschlossen sind; sonst offene auf stderr
  naechstes-feature              Kleinstes F-Issue mit status:bereit, ohne Sperre/Blockade, Voraussetzungen erfüllt
  beanspruchen <issue>           Setzt Sperre und Anspruchs-Kommentar, wartet 20 s. Exit 0 nur, wenn dieser Lauf
                                 den ältesten Anspruch hat (verhindert, dass zwei Läufe dasselbe Feature bauen)
  anstossen <issue>              status:bereit entfernen und neu setzen: startet die Entwicklungs-Aufgabe
  label-alter <nr> <label>       Minuten, seit das Label zuletzt gesetzt wurde (leer, wenn nie)
  waechter [--trocken]           Erkennt hängende Schritte (abgebrochene Läufe, verlorene Ereignisse) und stösst
                                 sie neu an. --trocken: nur melden, nichts ändern
  mergebar <nr>                  Exit 0, wenn der PR offen, konfliktfrei und alle Checks grün sind; sonst Grund auf stderr
  mergen <nr> <erwarteter-sha>   Squash-Merge nach main, nur wenn mergebar und Head = erwarteter SHA. Gibt Merge-SHA aus
EOF
}

# Minuten seit dem letzten «labeled»-Ereignis für ein Label (leer, wenn nie gesetzt)
label_alter() {
  local seit
  seit=$(gh api "$API/issues/$1/events?per_page=100" --paginate \
    --jq "[.[] | select(.event==\"labeled\" and .label.name==\"$2\")] | last | .created_at // empty" | tail -1)
  [ -z "$seit" ] && return 0
  python3 -c 'import sys,datetime as d;t=d.datetime.fromisoformat(sys.argv[1].replace("Z","+00:00"));print(int((d.datetime.now(d.timezone.utc)-t).total_seconds()//60))' "$seit"
}

# Exit 0, wenn es in den letzten AKTIV_MIN Minuten Aktivität gab: Push auf einen claude/-Branch
# oder Änderung am Issue/PR. Lange Läufe pushen regelmässig Zwischenstände und gelten so als lebendig.
kuerzlich_aktiv() {
  local nr="$1" grenze
  grenze=$(python3 -c 'import sys,datetime as d;print((d.datetime.now(d.timezone.utc)-d.timedelta(minutes=int(sys.argv[1]))).strftime("%Y-%m-%dT%H:%M:%SZ"))' "${AKTIV_MIN:-60}")
  local upd; upd=$(gh api "$API/issues/$nr" --jq .updated_at)
  [[ "$upd" > "$grenze" ]] && return 0
  local b d
  for b in $(gh api "$API/branches?per_page=100" --jq '.[].name | select(startswith("claude/"))'); do
    d=$(gh api "$API/commits/$(enc "$b")" --jq .commit.committer.date)
    [[ "$d" > "$grenze" ]] && return 0
  done
  return 1
}

# Anzahl Wächter-Anstösse auf einem Eintrag in den letzten 24 Stunden
waechter_anstoesse_24h() {
  local seit
  seit=$(python3 -c 'import datetime as d;print((d.datetime.now(d.timezone.utc)-d.timedelta(hours=24)).strftime("%Y-%m-%dT%H:%M:%SZ"))')
  gh api "$API/issues/$1/comments?since=$seit&per_page=100" \
    --jq '[.[] | select(.body | startswith("<!-- pipeline:waechter -->"))] | length'
}

# Wächter: findet hängende Schritte und stösst sie neu an.
#  - Sperre agent:aktiv älter als SPERRE_MAX_STUNDEN und seit AKTIV_MIN keine Aktivität (Push auf claude/-Branch,
#    Änderung am Eintrag): Lauf gilt als abgebrochen, Sperre weg, Schritt neu anstossen
#  - PR mit pipeline:review/test/nacharbeit ohne Sperre, Label älter als WARTE_MIN: Ereignis verloren, neu anstossen
#  - Warteschlange: nichts läuft, aber ein Feature ist bereit und seit WARTE_MIN nicht beansprucht: anstossen
#  - Mehr als MAX_ANSTOESSE pro Eintrag in 24 h: pipeline:blockiert statt endlos neu anstossen
waechter() {
  local trocken="$1" WARTE_MIN="${WAECHTER_WARTE_MIN:-45}" MAX_ANSTOESSE=3 aktionen=0
  tun() { if [ "$trocken" = "--trocken" ]; then echo "  (trocken) $*"; else "$@"; fi; }
  notiz() { # nr text
    local tmp; tmp=$(mktemp)
    printf '<!-- pipeline:waechter -->\n🛟 **Wächter:** %s\n' "$2" > "$tmp"
    tun gh api -X POST "$API/issues/$1/comments" -F "body=@$tmp" --silent; rm -f "$tmp"
  }
  anstoss() { # nr label grund
    local n; n=$(waechter_anstoesse_24h "$1")
    if [ "$n" -ge "$MAX_ANSTOESSE" ]; then
      echo "#$1: schon $n Mal in 24 h angestossen → pipeline:blockiert"
      notiz "$1" "Schritt \`$2\` hängt weiterhin ($3). Nach $n Anstössen in 24 Stunden gebe ich auf. Bitte prüfen, dann \`pipeline:blockiert\` entfernen und \`$2\` neu setzen."
      tun "$0" label-setzen "$1" "pipeline:blockiert"
    else
      echo "#$1: $3 → $2 neu angestossen"
      notiz "$1" "$3. Ich stosse \`$2\` neu an."
      tun "$0" label-entfernen "$1" "$2"; [ "$trocken" = "--trocken" ] || sleep 2
      tun "$0" label-setzen "$1" "$2"
    fi
    aktionen=$((aktionen+1))
  }

  # 1. PRs in der Pipeline
  local nr l alter sperre
  for l in pipeline:review pipeline:test pipeline:nacharbeit; do
    gh api "$API/issues?state=open&labels=$(enc "$l")&per_page=100" --jq '.[] | select(.pull_request) | .number' |
    while read -r nr; do
      if hat_label "$nr" "pipeline:blockiert"; then continue; fi
      if hat_label "$nr" "$SPERRE"; then
        sperre=$(label_alter "$nr" "$SPERRE")
        if [ -n "$sperre" ] && [ "$sperre" -ge $((SPERRE_MAX_STUNDEN*60)) ] && ! kuerzlich_aktiv "$nr"; then
          tun "$0" entsperren "$nr"
          anstoss "$nr" "$l" "Lauf seit ${sperre} Minuten gesperrt, gilt als abgebrochen"
        else
          echo "#$nr: $l, Agent arbeitet (Sperre seit ${sperre:-?} min)"
        fi
      else
        alter=$(label_alter "$nr" "$l")
        if [ -n "$alter" ] && [ "$alter" -ge "$WARTE_MIN" ]; then
          anstoss "$nr" "$l" "Label \`$l\` seit ${alter} Minuten ohne Agent"
        else
          echo "#$nr: $l seit ${alter:-?} min, noch im Zeitfenster"
        fi
      fi
    done
  done

  # 2. Issues mit abgelaufener Sperre (Entwicklung vor dem PR abgebrochen)
  gh api "$API/issues?state=open&labels=$(enc "$SPERRE")&per_page=100" --jq '.[] | select(.pull_request == null) | .number' |
  while read -r nr; do
    sperre=$(label_alter "$nr" "$SPERRE")
    if [ -n "$sperre" ] && [ "$sperre" -ge $((SPERRE_MAX_STUNDEN*60)) ] && ! kuerzlich_aktiv "$nr"; then
      echo "#$nr: Sperre seit ${sperre} min und seit ${AKTIV_MIN:-60} min keine Aktivität, Entwicklung gilt als abgebrochen → Sperre entfernt"
      notiz "$nr" "Die Entwicklung war seit ${sperre} Minuten gesperrt und gilt als abgebrochen. Sperre entfernt."
      tun "$0" entsperren "$nr"
    else
      echo "#$nr: Entwicklung läuft (Sperre seit ${sperre:-?} min)"
    fi
  done

  # 3. Warteschlange
  if [ -z "$("$0" laufend)" ]; then
    local naechst; naechst=$("$0" naechstes-feature | cut -f1)
    if [ -n "$naechst" ]; then
      alter=$(label_alter "$naechst" "status:bereit")
      if [ -n "$alter" ] && [ "$alter" -ge "$WARTE_MIN" ]; then
        anstoss "$naechst" "status:bereit" "Feature wartet seit ${alter} Minuten, aber keine Entwicklung läuft"
      else
        echo "#$naechst: als nächstes dran, seit ${alter:-?} min bereit"
      fi
    else
      echo "Warteschlange leer oder Voraussetzungen offen."
    fi
  else
    echo "Pipeline belegt:"; "$0" laufend | sed 's/^/  /'
  fi
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
  laufend)
    gh api "$API/issues?state=open&per_page=100" --jq '.[]
      | {nr: .number, pr: (.pull_request != null), titel: .title, l: [.labels[].name]}
      | select( (.pr and any(.l[]; startswith("pipeline:") and . != "pipeline:gemergt"))
             or ((.pr|not) and any(.l[]; . == "status:in-arbeit" or . == "pipeline:blockiert" or . == "agent:aktiv")) )
      | "\(.nr)\t\(if .pr then "PR" else "Issue" end)\t\(.l | map(select(startswith("pipeline:") or startswith("status:"))) | join(","))\t\(.titel)"' ;;
  voraussetzungen)
    vor=$(gh api "$API/issues/$1" --jq '.body // ""' | grep -m1 -oE 'Voraussetzung:\**[^\n]*' | grep -oE 'F-[0-9]{2}' || true)
    offen=""
    for f in $vor; do
      st=$(gh api "$API/issues?state=all&per_page=100" --jq ".[] | select(.pull_request == null and (.title | startswith(\"$f \"))) | .state" | head -1)
      [ "$st" = "closed" ] || offen="$offen $f(${st:-fehlt})"
    done
    [ -z "$offen" ] || { echo "Offene Voraussetzungen:$offen" >&2; exit 1; } ;;
  naechstes-feature)
    gh api "$API/issues?state=open&labels=status%3Abereit&per_page=100" \
      --jq '.[] | select(.pull_request == null) | select([.labels[].name] | index("pipeline:blockiert") | not) | select(.title | test("^F-[0-9]{2} ")) | [.number, .title] | @tsv' \
      | sort -t$'\t' -k2 | while IFS=$'\t' read -r nr titel; do
          gesperrt "$nr" && continue
          "$0" voraussetzungen "$nr" 2>/dev/null || continue
          printf '%s\t%s\n' "$nr" "$titel"; break
        done ;;
  beanspruchen)
    nr="$1"; token="$(date +%s)-$RANDOM$RANDOM"
    "$0" label-setzen "$nr" "$SPERRE"
    tmp=$(mktemp); printf '<!-- pipeline:anspruch %s -->\n🤖 Entwicklung beansprucht dieses Feature.\n' "$token" > "$tmp"
    gh api -X POST "$API/issues/$nr/comments" -F "body=@$tmp" --silent; rm -f "$tmp"
    sleep 20
    seit=$(python3 -c 'import datetime as d;print((d.datetime.now(d.timezone.utc)-d.timedelta(minutes=15)).strftime("%Y-%m-%dT%H:%M:%SZ"))')
    erster=$(gh api "$API/issues/$nr/comments?since=$seit&per_page=100" \
      --jq '[.[] | select(.body | startswith("<!-- pipeline:anspruch "))] | sort_by(.created_at, .id) | first | .body' \
      | grep -oE 'anspruch [^ ]+' | cut -d' ' -f2)
    if [ "$erster" = "$token" ]; then echo "beansprucht"; else echo "Anderer Lauf war schneller ($erster)" >&2; exit 1; fi ;;
  anstossen)
    "$0" label-entfernen "$1" "status:bereit"; sleep 2; "$0" label-setzen "$1" "status:bereit" ;;
  label-alter) label_alter "$1" "$2" ;;
  waechter) waechter "${1:-}" ;;
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
