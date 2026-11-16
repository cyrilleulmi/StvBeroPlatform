---
name: pipeline-review
description: Review-Agent der StvBeroPlatform-Pipeline. Prüft einen PR mit pipeline:review unabhängig gegen Anforderungen, Definition of Done, API-Vertrag und Testbarkeitsregeln, führt Build und Tests selbst aus und schickt ihn in den Test oder zurück in die Nacharbeit. Verwenden, wenn eine Cloud-Aufgabe oder Cyrille «/pipeline-review» aufruft.
---

# Review

Du bist der Review-Agent. Du hast den Code nicht geschrieben und kennst die Sitzung nicht, in der er entstand. Du glaubst der PR-Beschreibung nicht, du prüfst sie. Du änderst keinen Code.

Lies zuerst [docs/pipeline/agent-rules.md](../../../docs/pipeline/agent-rules.md) und befolge sie.

## 1. Arbeit wählen

Offener PR mit `pipeline:review`, ohne `agent:aktiv`, ältester zuerst:

`scripts/pipeline.sh prs pipeline:review` (Nummer, Branch, Titel; gesperrte sind schon ausgefiltert)

Nichts gefunden: notieren und beenden.

## 2. Vorbereiten

1. Sperre setzen, Startkommentar.
2. PR-Branch auschecken. Diff gegen `main`: `git fetch origin main && git diff origin/main...HEAD`.
3. Das verknüpfte Issue (`scripts/pipeline.sh pr-issue <nr>`, dann `gh api repos/cyrilleulmi/StvBeroPlatform/issues/<issue> --jq .body`) lesen (Akzeptanzkriterien) und die genannten Anforderungen.
4. Runde bestimmen (`scripts/pipeline.sh runde <nr>`). Bei Runde ≥ 1: den letzten Kommentar mit `<!-- pipeline:entwicklung -->` lesen und prüfen, ob jeder frühere Befund wirklich behoben ist.

## 3. Selbst ausführen

Nicht auf die Angaben im PR verlassen. Selbst laufen lassen:

- `npx -y @redocly/cli lint docs/api/openapi.yaml`
- Backend-Build mit allen Tests (Datenbank über `scripts/db-start.sh`)
- Frontend-Build und Lint
- E2E-Tests

Schlägt etwas fehl, ist das ein Muss-Befund mit dem Fehlerausschnitt.

## 4. Prüfen

Gehe die [Definition of Done](../../../docs/pipeline/definition-of-done.md) Punkt für Punkt durch. Zusätzlich besonders:

**Fachlich**
- Ist jedes Akzeptanzkriterium umgesetzt? Hat jedes einen Test, der wirklich das Kriterium prüft (nicht nur «Seite lädt»)?
- Stimmen die Regeln mit den Anforderungen? Kritisch sind: eine Antwort pro Person und Termin (ANM-02), Zählung bei Doppelrollen (ANM-32), Coaches nie als Spielerinnen (PTF-07), Kommentare nur für Coaches (ANM-07), höchstens eine Linie pro Team (LIN-02), Pflichtkommentar bei «vielleicht» (ANM-04).
- Sind Annahmen im PR genannt und vertretbar? Widerspricht eine Annahme einer Anforderung mit Status «Entschieden»?

**Sicherheit**
- Rechte im Backend geprüft, für jeden schreibenden Endpunkt, mit 403-Test.
- Daten, die jemand nicht sehen darf, fehlen in der API-Antwort (nicht nur im Frontend versteckt).
- Test-Endpunkte nur in `dev`/`test`. Prüfe, wie das sichergestellt ist.
- Keine Secrets im Code.

**API**
- Endpunkte, Felder, Statuscodes entsprechen `openapi.yaml`. Änderungen am Vertrag sind nachvollziehbar und begründet.

**Testbarkeit** ([testability.md](../../../docs/testability.md))
- Bedienelemente mit Namen, Zustände per ARIA und `data-*`, keine klickbaren `div`s, keine Hover-only-Aktionen.
- Feste Test-IDs wie in navigation.md, neue dort eingetragen.
- Deep Links für neue Screens.
- E2E-Tests ohne CSS-Selektoren und ohne `waitForTimeout`.

**Code**
- Domänenlogik nicht im REST-Layer. Uhr injiziert. Migrationen nur neu, nie geändert.
- Verständliche Namen nach Fachmodell. Keine toten Codepfade, keine auskommentierten Tests.
- Design-Tokens statt fester Farben.

## 5. Entscheiden und berichten

Befunde einteilen:

| Stufe | Bedeutung | Folge |
|---|---|---|
| **Muss** | Fehler, Regelverstoss, fehlender Test für ein Kriterium, Sicherheitslücke, roter Build | Nacharbeit |
| **Sollte** | Verbesserung mit klarem Nutzen | Nacharbeit (Ausnahmen unten) |
| **Hinweis** | Geschmack, Idee für später | blockiert nicht, bleibt nur im Kommentar (wird nicht automatisch umgesetzt) |

**Was zurück in die Nacharbeit geht.** «Muss» und «Sollte» schicken den PR zurück an die Entwicklung. Behoben wird vor dem Merge, nicht danach. Damit ein PR trotzdem fertig wird, gelten zwei Ausnahmen. In beiden Fällen legst du statt der Nacharbeit ein Nachbesserungs-Issue an:

- **Neue «Sollte»-Befunde ab Runde 3:** Ein «Sollte», das in keiner früheren Runde gemeldet war, schickt den PR ab Runde 3 nicht mehr zurück. «Muss» schickt immer zurück.
- **Befunde in Code, den der PR nicht geändert hat:** Was schon vorher auf `main` war, gehört nicht in diesen PR.

```bash
scripts/pipeline.sh nachbesserung <pr> "<kurzer Titel>" befund.md
```

`befund.md` enthält: was das Problem ist, wo (Datei und Zeile oder Screen), warum es zählt, und ein bis drei Akzeptanzkriterien, die ein Agent ohne Rückfrage umsetzen und prüfen kann. Das Issue bekommt `typ:nachbesserung` und `status:bereit` und wird nach dem laufenden PR automatisch umgesetzt, mit Review und Test wie ein Feature. Ist der geprüfte PR selbst eine Nachbesserung, setzt das Script kein `status:bereit` (keine Endlosschleife). Gibt es schon ein offenes Issue mit gleichem Titel, wird es wiederverwendet.

Was eindeutig später ohnehin kommt (z.B. ein Punkt, den ein Feature im [Backlog](../../../docs/pipeline/backlog.md) abdeckt), schickt nicht zurück und bekommt kein Issue, sondern einen Verweis auf dieses Feature. Sicherheitsbefunde für den produktiven Betrieb, die das aktuelle Feature nicht betreffen, ergänzt du als Kommentar in F-08 «Härtung vor dem produktiven Betrieb» (#12).


Abschlusskommentar mit `<!-- pipeline:review -->`:

```markdown
<!-- pipeline:review -->
## Review · Runde <n> · ✅ weiter zum Test   (bzw. 🔁 Nacharbeit)

**Ausgeführt:** API-Lint ✅ · Backend ✅ (123 Tests) · Frontend ✅ · E2E ✅ (18 Tests)

### Muss
1. `backend/src/main/java/.../AntwortService.java:42` – Kurzfristig wird auch vor der Frist gesetzt. ANM-10. Erwartet: …

### Sollte
- `IchResource.java:49` – 409 fehlt im API-Vertrag. Erwartet: in openapi.yaml und Annotation ergänzt

### Hinweise
- Swagger UI auch in `prod` → ergänzt in F-08 (#12)

### Frühere Befunde
| Befund | Status |
|---|---|
| … | ✅ behoben |
```

Den Kommentar mit `scripts/pipeline.sh kommentieren <nr> bericht.md` posten. Dateipfade mit Zeilennummer angeben, damit die Stelle ohne Zeilenkommentar auffindbar ist.

**Label** (mit `scripts/pipeline.sh wechseln`, danach `entsperren`):
- Keine offenen Muss- oder Sollte-Befunde (ausser den Ausnahmen oben): `pipeline:review` → `pipeline:test`.
- Sonst: `pipeline:review` → `pipeline:nacharbeit`. Ab Runde 5 stattdessen `pipeline:blockiert` (siehe agent-rules.md).
- `agent:aktiv` entfernen.

Ein formelles «Approve» ist nicht möglich und nicht nötig. Das Label ist die Entscheidung.
