# Testplan Nachbesserung #16 · Ungültiges JSON ergibt Problem Details

Issue #16 · PR #30 · Fehler-Konvention in `docs/api/README.md` (RFC 9457)

Allgemein für jeden Fall:

- Vorbereitung: `POST /api/test/reset`. Wo nötig anmelden mit `POST /api/test/anmelden`
  und `{ "schluessel": "<person>" }`. Uhr: Standard, spielt keine Rolle.
- Anfragen mit `Content-Type: application/json` und dem angegebenen Body (roh, nicht escaped).
- «Problem Details» heisst: Status 400, `Content-Type: application/problem+json`,
  Body mit `title` «Ungültige Anfrage», `status` 400 und
  `detail` «Die Anfrage konnte nicht gelesen werden. Bitte die Eingaben prüfen.»
- Keine Oberfläche betroffen. Ein Fall läuft zusätzlich im Browser (mobil 375×812) über `fetch`
  aus einer angemeldeten Seite; danach Konsole prüfen (nur der erwartete 400).

## K1 · Ungültiges JSON an beliebigem Endpunkt (Kriterium 1)

| Fall | Ausgangslage · Schritte | Erwartung |
|---|---|---|
| K1.1 Test-Endpunkt, abgebrochen | Ohne Sitzung `POST /api/test/anmelden` mit `{"schluessel": ` | Problem Details, ohne `felder` |
| K1.2 Kein JSON | Wie K1.1 mit `nicht json` | Problem Details |
| K1.3 Falscher Typ | Wie K1.1 mit `[1,2]` | Problem Details |
| K1.4 Falsches Datum, Test-Endpunkt | `PUT /api/test/uhr` mit `{"jetzt":"gestern"}` | Problem Details mit `felder[0]` = `{feld: "jetzt", meldung: "Ungültiger Wert."}` |
| K1.5 Fachlicher Endpunkt | Als `alex`: `POST /api/saisons` mit `{"name":"2027/28","von":"2027-08-01","bis":` | Problem Details |
| K1.6 Falsches Datumsformat | Als `alex`: `POST /api/saisons` mit `{"name":"2027/28","von":"irgendwann","bis":"2028-07-31"}` | Problem Details, `felder[0].feld` = `von` |
| K1.7 Falscher Feldtyp | Als `alex`: `POST /api/saisons` mit `{"name":["x"],"von":"2027-08-01","bis":"2028-07-31"}` | Problem Details, `felder[0].feld` = `name` |
| K1.8 PUT | Als `alex`: `PUT /api/teams/10000000-0000-4000-8000-000000000001` mit `{"name":` | Problem Details |
| K1.9 Gültige Anfrage | Als `alex`: `POST /api/saisons` mit `{"name":"2027/28","von":"2027-08-01","bis":"2028-07-31"}` | 201, unverändert |
| K1.10 Browser | Mobil, `/dev-login?ziel=%2Fverwaltung%2Fsaisons`, «Alex Admin», dann `fetch('/api/saisons', POST, '{"name":"2027/28","von":"x"')` | Problem Details; Seite bleibt auf `/verwaltung/saisons`, keine weiteren Konsolenfehler |

## Rollen

| Fall | Ausgangslage · Schritte | Erwartung |
|---|---|---|
| R1 Ohne Sitzung | `POST /api/saisons` mit `{"name":` | 401 (Anmeldung wird vor dem Lesen geprüft) |
| R2 Nicht berechtigt | Als `anna`: `POST /api/saisons` mit `{"name":` | 400 Problem Details (Body wird vor der Rollenprüfung gelesen, verrät nichts) |

## Grenzfälle: Framework-Antworten bleiben unverändert

| Fall | Schritte | Erwartung |
|---|---|---|
| G1 415 | Als `alex`: `POST /api/saisons` mit `Content-Type: text/plain` | 415 wie bisher |
| G2 404 | `GET /api/gibtsnicht` | 404 wie bisher |
| G3 405 | `DELETE /api/saisons` | 405 wie bisher |
| G4 Leerer Body | Als `alex`: `POST /api/saisons` ohne Body | 400 `application/problem+json`, `detail` «Die Anfrage ist unvollständig oder ungültig.» (bestehender Validierungs-Mapper) |
| G5 Leeres Objekt | Als `alex`: `POST /api/saisons` mit `{}` | 400 mit deutschen Feldmeldungen für `name`, `von`, `bis` |
