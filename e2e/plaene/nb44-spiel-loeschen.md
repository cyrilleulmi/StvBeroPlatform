# Testplan Nachbesserung #44 · Nicht mehr gelieferte Spiele aus swiss unihockey löschen

Issue #44 · Datei `e2e/tests/nb44-spiel-loeschen.spec.ts` · Anforderung SUH-06, docs/zustaende.md «Spiel aus swiss unihockey»

Ausgangslage: Datenbank läuft (`scripts/db-start.sh`), Backend Profil `test`/`dev`, Frontend auf Port 4200, `POST /api/test/reset`.

## K1 · Nicht mehr geliefertes Spiel entfernen

| Fall | Schritte | Erwartung |
|---|---|---|
| K1.1 Hinweis und Knopf | `PUT /api/test/swiss-unihockey/spiele` mit `{"suTeamId":900001,"spiele":[Spiel 01]}` → als `alex` `POST /api/teams/10000000-0000-4000-8000-000000000001/swiss-unihockey/abgleich` → als `petra` `/termine/40000000-0000-4000-8000-000000000004/bearbeiten` | Gruppe «Spiel 2»: «swiss unihockey liefert dieses Spiel nicht mehr.» und Knopf «Spiel 2 entfernen». «Spiel 1»: «Von swiss unihockey, nicht änderbar.», kein Knopf |
| K1.2 Entfernen und speichern | «Spiel 2 entfernen» → «Änderungen speichern» | Weiter zu `/termine/…04`, Meldung «Änderungen gespeichert.», Liste «Spiele» nur «09:30 Team 1 – UHC Musterdorf» |
| K1.3 Neu laden | Seite neu laden, `GET /api/termine/…04` | Nur Spiel 01 (suSpielId 5000001) |
| K1.4 Abbrechen | Wie K1.1, «Spiel 2 entfernen», dann «Abbrechen» bzw. wegnavigieren | Spiel 2 bleibt |

## K2 · Noch gelieferte Spiele bleiben geschützt

| Fall | Schritte | Erwartung |
|---|---|---|
| K2.1 Ohne Abgleich | Reset → als `petra` `/termine/…04/bearbeiten` | Beide Spiele «Von swiss unihockey, nicht änderbar.», kein Knopf «Spiel 1/2 entfernen» |

## Regression

| Fall | Schritte | Erwartung |
|---|---|---|
| R1 | `cd e2e && npx playwright test --trace off` | Alle grün (beide Projekte) |
| R2 | F-05 «Marc ändert die Spiele von Termin 05» | Manuelle Spiele weiter entfernbar |

## Ergänzungen Test-Agent

Ausgangslage jeweils: Reset, Simulator `900001` nur Spiel 01, Abgleich als `alex` (wie K1.1), sofern nicht anders angegeben.

### Rollen

| Fall | Person | Schritte | Erwartung |
|---|---|---|---|
| R-1 Admin | `alex` | `/termine/…04/bearbeiten` → «Spiel 2 entfernen» → «Änderungen speichern» | Wie K1.2, Spiel 2 weg |
| R-2 Spielerin | `anna` | `/termine/…04/bearbeiten` | «Keine Berechtigung», kein Formular. API `DELETE /api/termine/…04/spiele/50000000-0000-4000-8000-000000000002` → 403 |
| R-3 Coach fremdes Team | `marc` | wie R-2 | «Keine Berechtigung», API 403 |
| R-4 Noch geliefert, API | `petra` | ohne Abgleich `DELETE …/spiele/…001` | 409 Regel SUH-06, Spiel bleibt |
| R-5 Spielerin sieht Hinweis | `anna` | `/termine/…04` | Spiel 2 mit «swiss unihockey liefert dieses Spiel nicht mehr.», kein Bearbeiten-Link |

### Robustheit

| Fall | Schritte | Erwartung |
|---|---|---|
| X-1 Doppelklick | `petra`, «Spiel 2 entfernen», Doppelklick auf «Änderungen speichern» | Ein Speichervorgang, keine Fehlermeldung, kein 4xx/5xx, Spiel 2 weg |
| X-2 Neuladen nach Entfernen | «Spiel 2 entfernen», Seite neu laden | Spiel 2 wieder da (nichts gespeichert) |
| X-3 Zurück-Taste | «Spiel 2 entfernen», Zurück | Spiel 2 bleibt auf `/termine/…04` |
| X-4 Kombiniert | «Spiel 2 entfernen», zusätzlich Hinweise ändern, speichern | Beides gespeichert |
| X-5 Wieder geliefert nach Löschen | nach K1.2 Simulator zurück auf Spiel 01 und 02, Abgleich | Spiel 02 erscheint wieder (neu importiert), ohne Hinweis «nicht mehr» |

### Ansichten

- K1.1/K1.2 mobil 375×812 und Desktop 1280×800, Formular einmal dunkel (Hinweis und Knopf lesbar).
- Nach jedem Fall: keine Konsolenfehler, keine unerwarteten 4xx/5xx.
