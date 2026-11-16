# Testplan F-05 · Spieltage und Aufgebot

Issue #6 · Anforderungen AUF-01 bis AUF-28 (ohne Benachrichtigungen, die mit F-06 kommen), RR-03, TER-06, SUH-06 ·
Tests: `../tests/f05-spieltage.spec.ts`, Backend `backend/src/test/java/ch/stvbero/platform/…/SpieltageTest.java`, `AufgebotRegelnTest.java`

Allgemein für jeden Fall:

- Vorbereitung: `POST /api/test/reset` (Jetzt = Mo 16.11.2026 06:00), dann `POST /api/test/anmelden` mit `{ "schluessel": "<person>" }` bzw. `/dev-login`.
- Ansichten: mobil 375×812, zentrale Fälle zusätzlich Desktop 1280×800; neue Screens einmal dunkel.
- Nach jedem Fall: keine Konsolenfehler, keine unerwarteten 4xx/5xx.

IDs: Team 1 `10000000-0000-4000-8000-000000000001`, Team 2 `…002`. Termine T01 Training `40000000-0000-4000-8000-000000000001`,
T04 Spieltag Team 1 `…004`, T05 Spieltag Team 2 `…005`. Spiele `50000000-0000-4000-8000-0000000000NN` (01, 02 bei T04 aus swiss unihockey,
03 bei T05 von Hand). Personen `20000000-0000-4000-8000-0000000000NN` (Petra 02, Anna 05, Lea 06, Mia 07, Nina 09, Kim 11, Eva 12).

## Akzeptanzkriterien

| Nr. | Fall | Schritte | Erwartung |
|---|---|---|---|
| K1 | Seed | `anna` → `/termine/<T04>`; `eva` und `lea` → `/termine/<T05>`; `petra` → `/termine/<T04>/aufgebot` | T04: Spiele 09:30 Team 1 – UHC Musterdorf (Heim), 11:45 Unihockey Beispielstadt – Team 1 (Auswärts), Anna «noch kein Aufgebot». T05: Eva «aufgeboten», Lea «nicht aufgeboten». Aufgebot T04: Entwurf, 5 aufgeboten (Anna, Lea, Mia, Sara, Jana) |
| K2 | Spieltag erfassen | `petra` → `/termine/neu` → Art «Spieltag», Datum, Beginn, Ende, Team 1, Spielort, Google-Maps-Link, Hinweise, Treffpunkt (Zeit, Ort), zwei Spiele (eines Auswärts) → «Termin speichern» | Weiter auf `/termine/<id>`, «Der Termin ist erfasst.», Spiele nach Anspielzeit, Treffpunkt, Karte, Hinweise. Pflichtfelder der Spiele (Gegner) werden beim Feld gemeldet |
| K3 | Termin-Detail | `anna` → `/termine/<T04>` | `spiele` nach Anspielzeit, `treffpunkt` «08:00 · Parkplatz Sporthalle Nord», `spielort-karte` «In Google Maps öffnen» mit `target="_blank"` |
| K4 | Auswahl | `petra` → `/termine/<T04>/aufgebot`; Nina (offen) und Kim (nicht dabei) anhaken, Lea abhaken; neu laden | Gruppen Linie 1, Linie 2, Goalies, Ohne Linie; Antwortstatus und Kommentar (Mia «Muss evtl. arbeiten») neben dem Namen; jede Änderung sofort gespeichert («Nina Roth ist aufgeboten.»), bleibt nach Neuladen |
| K5 | S9 Veröffentlichen | `anna` → T04 (vorher); `petra` → Aufgebot → «Veröffentlichen» → Dialog «Aufgebot veröffentlichen?» → «Abbrechen», dann nochmals → «Veröffentlichen»; `anna`, `kim` → T04 | Vorher «noch kein Aufgebot». Abbrechen ändert nichts. Danach «veröffentlicht am Mo 16.11. 06:00», Anna «aufgeboten», Kim «nicht aufgeboten». Ohne Auswahl ist «Veröffentlichen» gesperrt mit Erklärung |
| K6 | S11 nur Coaches | `anna` → `/termine/<T04>/aufgebot`; API `GET`/`PUT …/aufgebot`, `POST …/veroeffentlichen` als `anna`; `marc` `GET …/T04/aufgebot` | «Keine Berechtigung» (`role="alert"`), API 403. Marc (Coach Team 2) 403. Anna sieht im Termin keinen Link «Aufgebot bearbeiten» |
| K7 | Coaches nicht wählbar | `petra` → Aufgebot T04; API `PUT …/aufgebot` mit Petras ID | 7 Checkboxen, Petra fehlt; API 422 Regel AUF-28 |
| K8 | Resultat | `petra` → T04 → «Resultat für Team 1 – UHC Musterdorf erfassen» → «5-3», dann «5:3»; `anna` → T04 | Meldung beim Feld bei «5-3»; danach «Resultat 5:3» im Spiel, auch für Anna. Anna hat keinen Button |

## Grenzfälle (states.md)

| Nr. | Fall | Schritte | Erwartung |
|---|---|---|---|
| G1 | Kein Spieltag | `petra` → `/termine/<T01>/aufgebot`; API `GET …/T01/aufgebot`, `POST …/T01/spiele` | «Nur ein Spieltag hat ein Aufgebot.»; API 409 Regel AUF-02 |
| G2 | Vorbei | Uhr 22.11.2026 14:00; `petra` → Aufgebot T04 | Checkboxen gesperrt, Hinweis «abgesagt oder vorbei»; API `PUT` 409 TER-06. Resultat erfassen geht weiterhin |
| G3 | Zweimal veröffentlichen | API `POST …/veroeffentlichen` zweimal | zweites Mal 409 Regel AUF-23 |
| G4 | Importierte Spiele | `petra` → `/termine/<T04>/bearbeiten`; API `PUT …/spiele/<Spiel 02>` mit anderem Gegner, `DELETE` | Formular: «Von swiss unihockey, nicht änderbar.», kein Entfernen; API 409 Regel SUH-06 |
| G5 | Spiele bearbeiten | `marc` → `/termine/<T05>/bearbeiten`: Gegner ändern, Spiel hinzufügen, dann Spiel 1 entfernen | Termin zeigt die geänderten Spiele nach Anspielzeit |
| G6 | Anderer Tag | API `POST …/T04/spiele` mit Anspielzeit am 23.11. | 400 beim Feld `anspielzeit` |
| G7 | Typ wechseln | API `PUT …/T04` mit `typ: TRAINING` | 409 Regel AUF-01 |
| G8 | Doppelklick | Checkbox schnell zweimal; «Veröffentlichen» im Dialog doppelt | während der Anfrage alle Checkboxen gesperrt, höchstens ein `POST …/veroeffentlichen` |
| G9 | Dunkel | Aufgebot-Screen, Termin T04/T05, Dialoge | gut lesbar, Statusfarben und Chips erkennbar |
| G10 | Importierten Spieltag verschieben (O-48) | `petra` → `/termine/<T04>/bearbeiten` → Datum 29.11.2026 → speichern → «Resultat für Team 1 – UHC Musterdorf erfassen» → «2:1»; API `PUT …/T04/spiele/<Spiel 01>` mit Resultat | Speichern wird verhindert: Meldung beim Feld «Datum» «Der Spieltag hat Spiele von swiss unihockey. Er lässt sich nur am selben Tag verschieben; …» (API 422, Regel SUH-05, Feld `beginn`). Spieltag und Spiele bleiben am 22.11., Resultat 2:1 lässt sich speichern. |
| G11 | Hand erfassten Spieltag verschieben | `marc` → API `PUT …/T05` mit Datum 29.11. | Spiel 03 zieht mit (29.11. 14:00) |
| G12 | Aufgebot nach Veröffentlichen ändern | `petra` → Aufgebot T04 veröffentlichen, dann Kim anhaken; `kim` → T04 | Status bleibt «veröffentlicht», Kim sieht sofort «aufgeboten» |
| G13 | Rechte Spiele/Resultat | `anna`, `marc` → API `PUT …/T04/spiele/<Spiel 01>` | 403; Admin `alex` darf Aufgebot ansehen und ändern |
| G14 | Importierten Spieltag am selben Tag verschieben | `petra` → `/termine/<T04>/bearbeiten` → Datum 29.11. → speichern (abgelehnt) → Datum zurück auf 22.11., Beginn 09:00 → speichern → «Resultat für Team 1 – UHC Musterdorf erfassen» → «2:1» (Doppelklick auf «Resultat speichern») | 200, «Der Termin ist verschoben.», Spiele bleiben 09:30/11:45, genau ein `PUT …/spiele/<Spiel 01>` → 200, «Resultat 2:1» bleibt nach Neuladen |
| G15 | Spiele bei Training | API `POST …/<T01>/spiele` | 409 «Nur ein Spieltag hat Spiele.», Regel AUF-01 |
