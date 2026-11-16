# Testplan F-07 · Anbindung swiss unihockey

Issue #8 · Anforderungen SUH-02 bis SUH-10, BEN-06, RR-07 · Szenario S12 ·
Tests: `../tests/f07-swiss-unihockey.spec.ts`, Backend `backend/src/test/java/ch/stvbero/platform/kern/swissunihockey/SwissUnihockeyTest.java`
(Abgleich gegen den Simulator), `…/AbgleichRegelnTest.java` (Fachregeln), `…/SuApiTest.java` (echter Client mit aufgezeichneten
Antworten), `…/kern/testhilfen/ProdOhneTesthilfenTest.java` (Profil prod ohne Simulator).

Allgemein für jeden Fall:

- Vorbereitung: `POST /api/test/reset` (setzt auch den Simulator zurück: Team-ID `900001` liefert Spiel 01 und 02 von Termin 04),
  dann `POST /api/test/anmelden` mit `{ "schluessel": "<person>" }` bzw. `/dev-login`.
- Simulator vorgeben: `PUT /api/test/swiss-unihockey/spiele` mit `{ "suTeamId": 900001, "nichtErreichbar": false, "spiele": [ … ] }`
  (Felder eines Spiels: `suSpielId`, `anspielzeit`, `heimteam`, `gastteam`, `halle`, optional `ort`, `mapsUrl`, `breite`, `laenge`, `resultat`).
- Abgleich auslösen: als `alex` unter `/verwaltung/teams/<Team>` Abschnitt «swiss unihockey» → «Jetzt abgleichen»,
  oder `POST /api/teams/<Team>/swiss-unihockey/abgleich`.
- Benachrichtigungen prüfen über `GET /api/test/benachrichtigungen` (neue haben IDs, die nicht mit `90000000` beginnen).
- Ansichten: mobil 375×812 und Desktop 1280×800, die Teamverwaltung einmal dunkel.

IDs: Team 1 `10000000-0000-4000-8000-000000000001` (SU `900001`), Team 2 `…002` (nicht verknüpft). Termin 04 `40000000-0000-4000-8000-000000000004`,
Spiel 01 `50000000-0000-4000-8000-000000000001` (SU `5000001`), Spiel 02 `…002` (SU `5000002`).

Spiel 01 im Simulator: `{"suSpielId":5000001,"anspielzeit":"2026-11-22T09:30:00+01:00","heimteam":"Team 1","gastteam":"UHC Musterdorf","halle":"Sporthalle Seefeld","ort":"Musterdorf"}`,
Spiel 02 gleich mit `5000002`, `11:45`, Heim «Unihockey Beispielstadt», Gast «Team 1».

## Akzeptanzkriterien

| Nr. | Fall | Ausgangslage | Schritte | Erwartung |
|---|---|---|---|---|
| K1 | Verknüpfen und abgleichen (SUH-02) | Reset, Simulator `900002` mit einem Spiel am 05.12. 13:00, `alex` | `/verwaltung/teams` → Team 2 → Team-ID `900002` → «Verknüpfen» → «Jetzt abgleichen» | Liste zeigt bei Team 1 den Chip «swiss unihockey» (`su-verknuepft-<Team>`). Nach dem Verknüpfen «Verknüpft mit Team-ID 900002.», Abgleich «Abgleich fertig: 1 neu, 0 geändert, 0 nicht mehr bei swiss unihockey, 0 unverändert.» Spieltag Sa 05.12. erscheint bei `eva` |
| K1b | Eingabe prüfen | wie K1 | «abc» oder leer → «Verknüpfen» | «Bitte eine Team-ID eingeben (nur Ziffern).», Feld `aria-invalid`. API: `0` → 400, schon vergebene ID (`900001` bei Team 2) → 409 Regel SUH-02 |
| K1c | Lösen | Reset, `alex` | Team 1 → «Verknüpfung lösen» | «Die Verknüpfung ist gelöst. Bisherige Spieltage bleiben.», «Nicht verknüpft.», Abgleich-Knopf weg; API-Abgleich 409 |
| K1d | Nur Admins (RR-07) | Reset, `petra` | `/verwaltung/teams/<Team 1>`; API PUT/POST | «Keine Berechtigung», API 403; ohne Anmeldung 401 |
| K2 | Simulator vs. echte Schnittstelle | Reset | Abgleich Team 1 | «0 neu, 0 geändert, 0 nicht mehr …, 2 unverändert». Profil prod: Backend-Test `ProdOhneTesthilfenTest` (kein Simulator, URL `https://api-v2.swissunihockey.ch/api`), echter Client: `SuApiTest` mit Dateien unter `backend/src/test/resources/swissunihockey` |
| K3 | Spieltage bilden (SUH-04, SUH-07) | Simulator: Spiel 01, 02 und zwei Spiele am 06.12. 10:00 und 12:30 in «Sporthalle Nord, Beromünster» | Abgleich; als `anna` Termine → Spieltag So 06.12. | Genau ein Spieltag mit «10:00 Team 1 – UHC Drei» und «12:30 UHC Vier – Team 1», Google-Maps-Link `…query=Sporthalle+Nord+Berom%C3%BCnster`, Beginn 10:00, Ende 13:45 (letzte Anspielzeit + 75 Min.). Spiel in anderer Halle oder an anderem Tag: eigener Spieltag. Zweiter Abgleich: nichts neu |
| K3b | Neues Spiel zu bestehendem Spieltag | Simulator: Spiel 01, 02 und ein drittes am 22.11. 14:00 in Sporthalle Seefeld | Abgleich | Termin 04 hat drei Spiele, Ende 15:15 |
| K4 | S12 Verschiebung (SUH-05, SUH-06, BEN-06) | Simulator: Spiel 02 auf 12:15 | Abgleich; `petra` öffnet Termin 04; `anna` und `eva` öffnen `/benachrichtigungen` | «1 geändert, 1 unverändert». Spiel 02 «12:15 Unihockey Beispielstadt – Team 1», Treffpunkt «08:00 · Parkplatz Sporthalle Nord», Hinweis, Antworten und Aufgebot (Entwurf Anna, Lea, Mia, Sara, Jana) unverändert. Alle 8 Mitglieder von Team 1 (inkl. Petra) erhalten `SPIEL_GEAENDERT` «Spieltag So 22.11. wurde geändert: Spiel gegen Unihockey Beispielstadt neu um 12:15.», Eva (Team 2) nicht. Zweiter Abgleich: keine weitere |
| K4b | Erstes Spiel verschoben | Simulator: Spiel 01 auf 10:00 | Abgleich | Termin 04 beginnt 10:00, gilt als «verschoben», Treffpunkt bleibt 08:00 |
| K4c | Anderer Tag | Simulator: Spiel 02 am 29.11. 11:45 in Turnhalle Weid | Abgleich | Spiel 02 wandert in einen neuen Spieltag So 29.11., Termin 04 behält Spiel 01. Text «… neu am So 29.11. 11:45, neu in Turnhalle Weid.» |
| K4d | Resultat (SUH-08) | Simulator: Spiel 01 mit `"resultat":"5:3"` | Abgleich | Spiel 01 zeigt «Resultat 5:3», keine Benachrichtigung |
| K5 | Spiel fehlt | Simulator nur Spiel 01 | Abgleich; `petra` öffnet Termin 04 | «1 nicht mehr bei swiss unihockey». Spiel 02 bleibt mit «swiss unihockey liefert dieses Spiel nicht mehr.» und lässt sich jetzt löschen. Wird es wieder geliefert, verschwindet die Markierung |
| K6 | Nicht erreichbar (SUH-09) | Simulator `nichtErreichbar: true` | Abgleich | Meldung (`role=alert`) «swiss unihockey ist im Moment nicht erreichbar. Es wurde nichts geändert. Der nächste Abgleich versucht es wieder.», API 502 Regel SUH-09. Termin 04 unverändert, keine Benachrichtigung, Termine und Teams laden normal |
| K7 | Täglicher Job (SUH-03) | Code | `AbgleichJob` (`@Scheduled`, `stv.swiss-unihockey.abgleich.cron`, Standard 04:30), Backend-Test `suh03TaeglicherJob…` | Job gleicht alle verknüpften Teams der aktuellen Saison ab; ein gestörtes Team hält die anderen nicht auf |
| K8 | Offene Fragen | Doku | `docs/offene-punkte.md` O-20 bis O-24, `docs/anforderungen/06-swiss-unihockey.md` | Stand der Recherche nachgeführt |

## Grenzfälle

| Nr. | Fall | Schritte | Erwartung |
|---|---|---|---|
| G1 | Von Hand erfasste Spieltage (SUH-10) | Team 2 mit `900002` verknüpfen, Simulator liefert ein Spiel am 28.11. in Turnhalle Weid (wie Termin 05) | Neuer Spieltag, Termin 05 unverändert |
| G2 | Keine aktuelle Saison | `DELETE /api/test/aktuelle-saison`, Abgleich | 409 Regel SAI-03 |
| G3 | Doppelklick «Jetzt abgleichen» | zweimal schnell tippen | Ein Abgleich, Knopf während des Laufs gesperrt |
| G4 | Archiv | `/verwaltung/teams/<Team>?saison=<2025/26>` | Abschnitt «swiss unihockey» fehlt (nur lesend) |
| G5 | Ganzer Spieltag verschoben (SUH-04, SUH-06, Nacharbeit Runde 1) | Simulator: Spiel 01 am 29.11. 09:30 und Spiel 02 am 29.11. 11:45, beide «Sporthalle Seefeld»; einmal in dieser, einmal in umgekehrter Reihenfolge geliefert. Abgleich; `petra` öffnet Termin 04 | «0 neu, 2 geändert», betroffen nur Termin 04. Termin 04: So 29.11. 09:30–13:00, «Dieser Termin wurde verschoben.», beide Spiele, Treffpunkt «08:00 · Parkplatz Sporthalle Nord», Hinweis, Antworten (4 dabei · 1 nicht dabei · 1 vielleicht · 1 offen) und Aufgebot-Entwurf bleiben. Kein zusätzlicher Spieltag. Eine Benachrichtigung pro Mitglied «Spieltag So 22.11. wurde geändert: Spiel gegen … neu am So 29.11. …». Zweiter Abgleich: «2 unverändert» |
| G6 | Spiel wieder geliefert | Simulator nur Spiel 01, Abgleich; dann Spiel 01 und 02, Abgleich | Zweiter Abgleich «1 geändert», `beiSuNichtMehrVorhanden` bei beiden Spielen `false` |
| G7 | Rollen API | `petra` (Coach Team 1), `anna` (Spielerin), `marc` (Coach Team 2): `PUT …/swiss-unihockey`, `POST …/abgleich`; ohne Anmeldung `POST …/abgleich` | 403 für alle drei, 401 ohne Anmeldung |
| G8 | Ungültige Team-ID API | `PUT …/swiss-unihockey` mit `0`, `-5`, `99999999999999` | 400 mit Feld `suTeamId` |
| G9 | Neu laden | Team 1 «Verknüpfung lösen», Seite neu laden | «Nicht verknüpft.» bleibt |
| G10 | Antworten auf importiertem Spieltag | K3, als `anna` Spieltag So 06.12. → «Dabei» | Knopf gedrückt, «1 dabei» |

## Testlauf Runde 1 (Test-Agent, Stand 8046fa4)

Automatisch: Backend 265 ✅, E2E 264 ✅ (mobil und desktop, inkl. axe), 6 projektbedingt übersprungen.

Explorativ mit Playwright MCP: K1–K6 und G1–G10 bestanden. Mobil 375×812: K1, K1b, K1c, K1d, K3, K5, K6, G3, G4, G9, G10. Desktop 1280×800: K4, G5. Dunkelmodus: `/verwaltung/teams/:id` mit Abschnitt «swiss unihockey» gut lesbar. K2, K3b, K4b–K4d, G2, G7, G8 über die API. K7 und K8: Backend-Test `suh03TaeglicherJob…` bzw. Doku O-20 bis O-24.

Nachbesserung: Ein Spiel, das swiss unihockey nicht mehr liefert, lässt sich in `/termine/:id/bearbeiten` nicht löschen (nur über `DELETE /api/termine/{id}/spiele/{spielId}`). Das Formular stammt aus F-05, siehe Nachbesserungs-Issue.
