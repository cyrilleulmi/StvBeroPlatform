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
