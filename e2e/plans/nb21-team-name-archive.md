# Testplan Nachbesserung #21 · Teamname pro Saison, Archiv behält den Namen

Issue #21 · PR #33 · Anforderungen SAI-02, SAI-04, RR-07

Allgemein für jeden Fall:

- Vorbereitung: `POST /api/test/reset`, anmelden mit `POST /api/test/anmelden` und
  `{ "schluessel": "<person>" }`. Uhr: Standard (16.11.2026), spielt keine Rolle.
- IDs: Team 1 `10000000-0000-4000-8000-000000000001`, Team 2 `…0002`,
  Saison 2025/26 (Archiv) `60000000-0000-4000-8000-000000000001`, 2026/27 (aktuell) `…0002`,
  Vera `20000000-0000-4000-8000-000000000017`, Petra `…0002`.
- Nach jedem Browser-Fall: keine Konsolenfehler, keine unerwarteten 4xx/5xx.

## K1 · Umbenennen in der aktuellen Saison lässt das Archiv unverändert

| Fall | Ausgangslage · Schritte | Erwartung |
|---|---|---|
| K1.1 API umbenennen | `alex`: `PUT /api/teams/<T1>` mit `{"name":"  Damen 1  "}` | 200, `name` «Damen 1» (getrimmt) |
| K1.2 Archiv Detail | danach `GET /api/teams/<T1>?saison=<2025/26>` | `name` «Team 1», Vera als Spielerin |
| K1.3 Aktuell Detail | `GET /api/teams/<T1>` | `name` «Damen 1» |
| K1.4 Listen | `GET /api/teams?saison=<2025/26>` bzw. ohne Saison | Archiv: «Team 1»; aktuell: «Damen 1», «Team 2», «Team 3» (nach Name sortiert) |
| K1.5 Eigene Teams | `petra`: `GET /api/ich` | Zugehörigkeit Team 1 heisst «Damen 1» |
| K1.6 Personendetail | `alex`: `GET /api/personen/<Vera>` | Zugehörigkeit 2025/26 heisst «Team 1» |
| K1.7 Browser mobil | 375×812, `alex`, `/verwaltung/teams/<T1>`, Teamname «Damen 1», «Umbenennen» doppelt klicken | Status «Name gespeichert.», Überschrift «Damen 1», genau ein `PUT …?saison=<2026/27>` |
| K1.8 Archiv im Browser | `/teams/<T1>?saison=<2025/26>` und `/verwaltung/teams/<T1>?saison=<2025/26>` | Überschrift «Team 1», Hinweis «Saison 2025/26 im Archiv: nur lesend.», kein Umbenennen-Formular |
| K1.9 Archivliste | «‹ Alle Teams» → `/verwaltung/teams?saison=<2025/26>` | nur «Team 1» |
| K1.10 Personendetail Browser | `/verwaltung/personen/<Vera>` | Link «Team 1» mit Saison 2025/26 |
| K1.11 Rolle Coach | `petra`, `/teams` | «Damen 1 · Coach», «Team 2 · Spielerin» |
| K1.12 Desktop | 1280×800, `alex`, `/verwaltung/teams/<T1>`, Name «Herren Plausch» + Enter, dann Saisonwahl Archiv ↔ aktuell | Archiv «Team 1», aktuell «Herren Plausch», «Team 2», «Team 3» |
| K1.13 Darkmode | mobil, dunkel, `/verwaltung/teams/<T1>` | alles lesbar |

## K2 · Backend-Test vorhanden

| Fall | Schritte | Erwartung |
|---|---|---|
| K2.1 | `cd backend && mvn test` | `TeamsTest.sai02UmbenennenVeraendertDasArchivNicht`, `sai02ArchivLaesstSichNichtUmbenennen`, `SaisonsTest.sai02TeamnameGiltProSaison` grün |

## Grenzfälle

| Fall | Ausgangslage · Schritte | Erwartung |
|---|---|---|
| G1 Archiv umbenennen | `alex`: `PUT /api/teams/<T1>?saison=<2025/26>` | 409 «Saison im Archiv», `regel` SAI-02, Name bleibt «Team 1» |
| G2 Team nicht in Saison | `PUT /api/teams/<T2>?saison=<2025/26>` | 404 «Dieses Team gibt es in der Saison 2025/26 nicht.» |
| G3 Unbekannte Saison | `?saison=60000000-0000-4000-8000-000000000099` | 404 «Diese Saison gibt es nicht.» |
| G4 Leerer Name | `{"name":"   "}` (API und Formular) | 400 «Bitte einen Namen eingeben.»; im Formular Fehlermeldung am Feld |
| G5 Länge | 61 Zeichen / 60 Zeichen | 400 «Höchstens 60 Zeichen.» / 200 |
| G6 Doppelt gleichzeitig | zwei `PUT` parallel | beide 200, kein 5xx im Backend-Log |
| G7 Übernahme | Team 1 → «Damen 1», dann `POST /api/saisons` 2027/28 mit `uebernehmenAus` 2026/27 | 2027/28 hat «Damen 1», «Team 2», «Team 3» |
| G8 Umbenennen nur in Folgesaison | danach `PUT /api/teams/<T1>?saison=<2027/28>` «Damen Elite» | 2025/26 «Team 1», 2026/27 «Damen 1», 2027/28 «Damen Elite» |
| G9 Aufnahme in neue Saison | Team «Alt» in 2026/27 anlegen, umbenennen zu «Alt umbenannt», Person in 2027/28 aufnehmen | Team in 2027/28 heisst «Alt umbenannt» |
| G10 Migration | V1+V2 von `main` mit Daten (Team in zwei Saisons), dann V3 | `team_saison.name` aus `team.name` übernommen, `team.name` entfernt |

## Rollen

| Fall | Schritte | Erwartung |
|---|---|---|
| R1 Spielerin | `anna`: `PUT /api/teams/<T1>` | 403 RR-07 |
| R2 Coach | `petra`: `PUT /api/teams/<T1>` | 403 RR-07 |
| R3 Ohne Sitzung | `PUT /api/teams/<T1>` | 401 |
