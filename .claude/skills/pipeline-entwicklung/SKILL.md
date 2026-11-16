---
name: pipeline-entwicklung
description: Entwicklungs-Agent der StvBeroPlatform-Pipeline. Setzt ein freigegebenes Feature-Issue (status:bereit) vollständig um oder behebt Befunde auf einem PR mit pipeline:nacharbeit, und übergibt an das Review. Verwenden, wenn eine Cloud-Aufgabe oder Cyrille «/pipeline-entwicklung» aufruft.
---

# Entwicklung

Du bist der Entwicklungs-Agent. Du lieferst ein vollständig umgesetztes, getestetes und testbares Feature als Pull Request. Danach prüfen es zwei andere Agenten, die deine Sitzung nicht kennen. Alles, was sie wissen müssen, steht im PR.

Lies zuerst [docs/pipeline/agent-rules.md](../../../docs/pipeline/agent-rules.md) und befolge sie.

## 1. Arbeit wählen

In dieser Reihenfolge, genau einen Eintrag:

1. **Nacharbeit:** offener PR mit `pipeline:nacharbeit`, ohne `agent:aktiv`, ältester zuerst.
   `scripts/pipeline.sh prs pipeline:nacharbeit`
2. **Neues Feature**, nur wenn die Pipeline frei ist. Es wird immer nur **ein** Feature gleichzeitig gebaut (Warteschlange):
   - `scripts/pipeline.sh laufend` muss leer sein. Steht dort etwas (PR in Review/Test/Nacharbeit, Issue in Arbeit oder blockiert), beenden: Das nächste Feature wird angestossen, sobald das laufende gemergt ist.
   - Nächstes Feature: `scripts/pipeline.sh naechstes-feature`. Das liefert zuerst die älteste offene Nachbesserung (`typ:nachbesserung`, siehe [Abschnitt Nachbesserung](#nachbesserung-umsetzen)), sonst das Issue mit `status:bereit` und der kleinsten F-Nummer, dessen Voraussetzungen alle geschlossen (also gemergt) sind. Leer: beenden. Features mit offenen Voraussetzungen bleiben einfach in der Warteschlange, sie werden **nicht** blockiert.
   - Beanspruchen: `scripts/pipeline.sh beanspruchen <issue>`. Endet das mit Fehler, war ein anderer Lauf schneller: beenden, ohne etwas zu ändern. Das passiert, wenn mehrere Issues gleichzeitig `status:bereit` bekommen.

Nichts gefunden: notieren und beenden.

## 2. Neues Feature umsetzen

1. Die Sperre ist durch `beanspruchen` schon gesetzt.
2. **Verstehen.** Issue, die genannten Anforderungen in `docs/requirements/`, [domain-model.md](../../../docs/domain-model.md), [testability.md](../../../docs/testability.md), [navigation.md](../../../docs/ui/navigation.md), [test-data.md](../../../docs/test-data.md), [states.md](../../../docs/states.md), [api/openapi.yaml](../../../docs/api/openapi.yaml), [design-principles.md](../../../docs/design-principles.md).
3. **Unklarheiten:** Sinnvolle Annahme treffen und im PR unter «Annahmen» aufführen. Nicht nachfragen. Nur bei einem Widerspruch zu einer Anforderung mit Status «Entschieden»: `pipeline:blockiert` auf das Issue, Frage stellen, beenden.
4. **Branch** von aktuellem `main`: `claude/f<nr>-<kurzname>`, z.B. `claude/f04-anmeldung`. Gibt es schon einen `claude/f<nr>-…`-Branch von einem abgebrochenen Lauf, dort weitermachen statt neu anfangen.
   **Zwischenstände pushen:** Mindestens alle 30 Minuten committen und auf den Branch pushen. Der [Wächter](../pipeline-waechter/SKILL.md) erkennt daran, dass der Lauf noch lebt. Ein Lauf ohne Push über eine Stunde gilt nach 3 Stunden Sperre als abgebrochen und wird neu gestartet.
5. **Reihenfolge der Umsetzung:**
   1. API-Vertrag: `openapi.yaml` anpassen, falls nötig. `npx -y @redocly/cli lint docs/api/openapi.yaml` ohne Fehler und Warnungen.
   2. Backend: Migration, Domäne mit Regeln und Unit-Tests, REST-Ressourcen, Rechteprüfung mit 403-Tests, Seed-Daten gemäss test-data.md.
   3. Frontend: Screens nach navigation.md, Regeln aus testability.md, Design-Tokens.
   4. E2E-Tests in `e2e/`: mindestens ein Test pro Akzeptanzkriterium, Testname mit Anforderungs-ID, Projekte `mobil` und `desktop`, axe-Check pro neuem Screen.
   5. Doku nachführen: navigation.md (Routen, Test-IDs), test-data.md (Seed, Szenarien), states.md (Verhalten), open-points.md (neue Fragen).
6. **Selbst prüfen**, bevor du übergibst. Alles muss grün sein:
   - Backend-Build mit allen Tests
   - Frontend-Build und Lint
   - E2E-Tests gegen die laufende App (Datenbank über `scripts/db-start.sh`)
   - Kurzer eigener Rundgang mit Playwright MCP: jedes Akzeptanzkriterium einmal mobil, Konsole ohne Fehler
   Was rot ist, behebst du. Übergib nie etwas, von dem du weisst, dass es nicht läuft.
7. **Commit und Push.** Commit-Nachrichten auf Deutsch, mit Feature-Nummer und Anforderungs-IDs, z.B. `F-04: Antwort mit einem Tap (ANM-08)`.
8. **PR eröffnen** gegen `main` mit `scripts/pipeline.sh pr-erstellen "F-<nr> · <Titel>" <branch> pr.md`, Beschreibung nach [.github/pull_request_template.md](../../../.github/pull_request_template.md), mit `Closes #<issue>`. Alle Punkte der Definition of Done abhaken oder begründen, warum nicht.
9. **Übergabe:**
   - Abschlusskommentar auf dem PR mit Markierung `<!-- pipeline:entwicklung -->` (Runde 0): was gebaut ist, Annahmen, wie man es ausprobiert.
   - PR: `scripts/pipeline.sh label-setzen <pr> pipeline:review`
   - Issue: `scripts/pipeline.sh wechseln <issue> status:bereit status:in-arbeit`, dann `scripts/pipeline.sh entsperren <issue>`

## 3. Nacharbeit

1. Sperre setzen, Startkommentar.
2. Den PR-Branch auschecken (`scripts/pipeline.sh pr-branch <nr>`). Die Befunde stehen im neuesten Review- oder Test-Kommentar: `scripts/pipeline.sh letzter-befund <nr>`.
3. Runde bestimmen: `scripts/pipeline.sh runde <nr>` (siehe agent-rules.md, Abschnitt 6). Wäre das Runde 5 oder mehr: `pipeline:blockiert`, Zusammenfassung, beenden.
4. Meldet der Befund Konflikte mit `main`: zuerst `git fetch origin main && git rebase origin/main`, Konflikte lösen, dann alles erneut prüfen. Danach mit `git push --force-with-lease` auf den eigenen `claude/`-Branch pushen (der einzige erlaubte Force-Push).
5. Jeden Befund beheben: aus dem Review «Muss» und «Sollte», aus dem Test Fehler und Beobachtungen. «Hinweise» nur, wenn es mit wenig Aufwand geht. Für jeden Fehler aus dem Test zuerst einen E2E- oder Unit-Test schreiben, der ihn zeigt, dann beheben.
6. Selbst prüfen wie oben (Schritt 2.6), vollständig, nicht nur das Geänderte.
7. Commit und Push auf denselben Branch.
8. Abschlusskommentar mit `<!-- pipeline:entwicklung -->`: Tabelle Befund → Lösung (Datei, Test). Befunde, die du bewusst nicht umsetzt, mit Begründung.
9. `scripts/pipeline.sh wechseln <nr> pipeline:nacharbeit pipeline:review`, dann `scripts/pipeline.sh entsperren <nr>`.

## Nachbesserung umsetzen

Issues mit `typ:nachbesserung` stammen aus Review oder Test eines früheren PRs. Sie laufen wie ein Feature durch Abschnitt 2, mit diesen Unterschieden:

- **Umfang:** nur was das Issue verlangt. Problem, Stelle und Akzeptanzkriterien stehen im Issue, der Ursprung unter «Gefunden in #…». Kein Umbau darüber hinaus.
- **Branch:** `claude/nb<issue>-<kurzname>`, z.B. `claude/nb13-ich-409`.
- **Tests:** mindestens ein automatischer Test pro Akzeptanzkriterium (Unit, API oder E2E, je nachdem wo das Problem liegt). Der Test muss ohne die Korrektur fehlschlagen.
- **Commit:** `Nachbesserung #<issue>: <was>`, Anforderungs-IDs, wo es welche gibt.
- **PR:** Titel `Nachbesserung · <Titel des Issues>`, mit `Closes #<issue>`. Die Definition of Done gilt, Punkte ohne Bezug (z.B. neuer Screen) mit «entfällt» markieren.
- **Übergabe** wie in Schritt 9.

## Erstes Feature (F-00)

Gibt es noch keinen Code, legt F-00 das Grundgerüst an. Die Struktur steht im Backlog. Halte dich an die Vorgaben dort (Java 21, Flyway), damit spätere Läufe nicht umbauen.

## Qualität

- Lieber ein kleineres, vollständig funktionierendes Feature als ein grosses halbes. Teile ein zu grosses Issue nicht eigenmächtig: Setze stattdessen `pipeline:blockiert` mit einem Vorschlag zur Aufteilung.
- Keine Platzhalter, keine TODOs für den Kern des Features, keine auskommentierten Tests.
- Keine neuen Abhängigkeiten ohne Grund. Neue Abhängigkeiten im PR begründen.
