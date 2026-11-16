# Testplan Nachbesserung #31 · Fehlender Body ergibt deutsche Meldung

Issue #31 · PR #61 · Fehler-Konvention in `docs/api/README.md` (RFC 9457)

Allgemein für jeden Fall:

- Vorbereitung: `POST /api/test/reset`. Wo nötig anmelden mit `POST /api/test/anmelden`
  und `{ "schluessel": "<person>" }`. Uhr: Standard, spielt keine Rolle.
- «Ohne Body» heisst: `Content-Type: application/json`, kein Inhalt (Länge 0).
- «Problem ohne Body» heisst: Status 400, `Content-Type: application/problem+json`,
  `title` «Ungültige Anfrage», `status` 400,
  `detail` «Die Anfrage enthält keine Daten. Bitte die Eingaben prüfen.», kein `felder`
  (oder leer), und die Antwort enthält weder «must not be null» noch «eingabe».
- Keine Oberfläche betroffen. Ein Fall läuft zusätzlich im Browser (mobil 375×812) über `fetch`
  aus einer angemeldeten Seite; danach Konsole prüfen (nur der erwartete 400).

## K1 · Anfrage ohne Body an Endpunkt mit Pflicht-Body (Kriterium 1)

| Fall | Ausgangslage · Schritte | Erwartung |
|---|---|---|
| K1.1 Saison | Als `alex`: `POST /api/saisons` ohne Body | Problem ohne Body |
| K1.2 Test-Anmeldung | Ohne Sitzung: `POST /api/test/anmelden` ohne Body | Problem ohne Body |
| K1.3 JSON `null` | Als `alex`: `POST /api/saisons` mit Body `null` | Problem ohne Body |
| K1.4 Pfad-Parameter und Body | Als `alex`: `PUT /api/teams/10000000-0000-4000-8000-000000000001` ohne Body | Problem ohne Body |
| K1.5 Query-Parameter und Body | Als `alex`: `POST /api/teams?saison=60000000-0000-4000-8000-000000000001` ohne Body | Problem ohne Body |
| K1.6 Zwei Pfad-Parameter | Als `alex`: `PUT /api/teams/10000000-0000-4000-8000-000000000001/mitglieder/20000000-0000-4000-8000-000000000001` ohne Body | Problem ohne Body |
| K1.7 Weitere Endpunkte | Als `alex`: `POST /api/personen`, `POST /api/termine`, `POST /api/serien`, `PUT /api/test/uhr` je ohne Body | jeweils Problem ohne Body |
| K1.8 Spielerin | Als `nina`: `PUT /api/termine/40000000-0000-4000-8000-000000000001/antworten/ich` ohne Body | Problem ohne Body |
| K1.9 Browser | Mobil, `/dev-login`, «Alex Admin», dann `fetch('/api/saisons', {method:'POST', headers:{'Content-Type':'application/json'}})` | Problem ohne Body; nur der erwartete 400 in der Konsole |

## Regression: Feldmeldungen bleiben

| Fall | Schritte | Erwartung |
|---|---|---|
| R1 Fehlendes Feld | Als `alex`: `POST /api/saisons` mit `{"von":"2027-07-01","bis":"2028-06-30"}` | 400, `detail` «Die Anfrage ist unvollständig oder ungültig.», `felder` enthält `name` mit deutscher Meldung |
| R2 Leeres Objekt | Als `alex`: `POST /api/saisons` mit `{}` | 400, deutsche Meldungen für `name`, `von`, `bis` |
| R3 Verschachtelt | Als `alex`: `PUT /api/test/swiss-unihockey/spiele` mit `{"suTeamId":1,"spiele":[null]}` | 400, Feldmeldung (nicht «Die Anfrage enthält keine Daten») |
| R4 Ungültiges JSON | Als `alex`: `POST /api/saisons` mit `{"name":` | 400, `detail` «Die Anfrage konnte nicht gelesen werden. Bitte die Eingaben prüfen.» (#16 unverändert) |
| R5 Gültig | Als `alex`: `POST /api/saisons` mit `{"name":"2027/28","von":"2027-08-01","bis":"2028-07-31"}` | 201 |

## Rollen

| Fall | Schritte | Erwartung |
|---|---|---|
| Ro1 Ohne Sitzung | `POST /api/saisons` ohne Body | 401 (Anmeldung vor dem Lesen) |
| Ro2 Nicht berechtigt | Als `anna`: `POST /api/saisons` ohne Body | 400 Problem ohne Body oder 403; verrät nichts |

## Grenzfälle

| Fall | Schritte | Erwartung |
|---|---|---|
| G1 415 | Als `alex`: `POST /api/saisons` mit `Content-Type: text/plain` ohne Body | 415 wie bisher |
| G2 Body bei Endpunkt ohne Body | Als `alex`: `POST /api/test/reset` ohne Body | 204 wie bisher |

## Ergebnis Runde 0

Alle Fälle bestanden. R3 bestanden (Feldmeldung statt «keine Daten»), dabei aufgefallen: `felder[0].feld` ist
`<list element>` (interner Knotenname). Das ist auf `main` unverändert und betrifft auch
`PUT /api/termine/{id}/aufgebot` mit `{"personIds":[null]}` → eigenes Nachbesserungs-Issue.
