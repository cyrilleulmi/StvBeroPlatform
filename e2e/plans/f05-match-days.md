# Test plan F-05 · Match days and call-up

Issue #6 · Requirements AUF-01 to AUF-28 (without notifications, which come with F-06), RR-03, TER-06, SUH-06 ·
Tests: `../tests/f05-spieltage.spec.ts`, backend `backend/src/test/java/ch/stvbero/platform/…/SpieltageTest.java`, `AufgebotRegelnTest.java`

General for every case:

- Preparation: `POST /api/test/reset` (now = Mon 16.11.2026 06:00), then `POST /api/test/anmelden` with `{ "schluessel": "<person>" }` or `/dev-login`.
- Views: mobile 375×812, core cases also desktop 1280×800; new screens once in dark mode.
- After each case: no console errors, no unexpected 4xx/5xx.

IDs: Team 1 `10000000-0000-4000-8000-000000000001`, Team 2 `…002`. Events T01 practice `40000000-0000-4000-8000-000000000001`,
T04 match day Team 1 `…004`, T05 match day Team 2 `…005`. Matches `50000000-0000-4000-8000-0000000000NN` (01, 02 at T04 from swiss unihockey,
03 at T05 entered by hand). People `20000000-0000-4000-8000-0000000000NN` (Petra 02, Anna 05, Lea 06, Mia 07, Nina 09, Kim 11, Eva 12).

## Acceptance criteria

| No. | Case | Steps | Expected |
|---|---|---|---|
| K1 | Seed | `anna` → `/termine/<T04>`; `eva` and `lea` → `/termine/<T05>`; `petra` → `/termine/<T04>/aufgebot` | T04: matches 09:30 Team 1 – UHC Musterdorf (home), 11:45 Unihockey Beispielstadt – Team 1 (away), Anna «noch kein Aufgebot». T05: Eva «aufgeboten», Lea «nicht aufgeboten». Call-up T04: draft, 5 called up (Anna, Lea, Mia, Sara, Jana) |
| K2 | Create match day | `petra` → `/termine/neu` → type «Spieltag», date, start, end, Team 1, venue, Google Maps link, notes, meeting point (time, place), two matches (one away) → «Termin speichern» | On to `/termine/<id>`, «Der Termin ist erfasst.», matches by start time, meeting point, map, notes. Required fields of the matches (opponent) are reported at the field |
| K3 | Event detail | `anna` → `/termine/<T04>` | `spiele` by start time, `treffpunkt` «08:00 · Parkplatz Sporthalle Nord», `spielort-karte` «In Google Maps öffnen» with `target="_blank"` |
| K4 | Selection | `petra` → `/termine/<T04>/aufgebot`; tick Nina (pending) and Kim (declined), untick Lea; reload | Groups Linie 1, Linie 2, Goalies, «Ohne Linie»; response status and comment (Mia «Muss evtl. arbeiten») next to the name; every change saved immediately («Nina Roth ist aufgeboten.»), stays after reload |
| K5 | S9 publish | `anna` → T04 (before); `petra` → call-up → «Veröffentlichen» → dialog «Aufgebot veröffentlichen?» → «Abbrechen», then again → «Veröffentlichen»; `anna`, `kim` → T04 | Before: «noch kein Aufgebot». Cancel changes nothing. Afterwards «veröffentlicht am Mo 16.11. 06:00», Anna «aufgeboten», Kim «nicht aufgeboten». Without a selection, «Veröffentlichen» is disabled with an explanation |
| K6 | S11 coaches only | `anna` → `/termine/<T04>/aufgebot`; API `GET`/`PUT …/aufgebot`, `POST …/veroeffentlichen` as `anna`; `marc` `GET …/T04/aufgebot` | «Keine Berechtigung» (`role="alert"`), API 403. Marc (coach Team 2) 403. Anna sees no link «Aufgebot bearbeiten» in the event |
| K7 | Coaches not selectable | `petra` → call-up T04; API `PUT …/aufgebot` with Petra's ID | 7 checkboxes, Petra missing; API 422 rule AUF-28 |
| K8 | Result | `petra` → T04 → «Resultat für Team 1 – UHC Musterdorf erfassen» → «5-3», then «5:3»; `anna` → T04 | Message at the field for «5-3»; afterwards «Resultat 5:3» in the match, also for Anna. Anna has no button |

## Edge cases (states.md)

| No. | Case | Steps | Expected |
|---|---|---|---|
| G1 | Not a match day | `petra` → `/termine/<T01>/aufgebot`; API `GET …/T01/aufgebot`, `POST …/T01/spiele` | «Nur ein Spieltag hat ein Aufgebot.»; API 409 rule AUF-02 |
| G2 | Over | Clock 22.11.2026 14:00; `petra` → call-up T04 | Checkboxes disabled, notice «abgesagt oder vorbei»; API `PUT` 409 TER-06. Entering a result still works |
| G3 | Publish twice | API `POST …/veroeffentlichen` twice | Second time 409 rule AUF-23 |
| G4 | Imported matches | `petra` → `/termine/<T04>/bearbeiten`; API `PUT …/spiele/<match 02>` with another opponent, `DELETE` | Form: «Von swiss unihockey, nicht änderbar.», no removing; API 409 rule SUH-06 |
| G5 | Edit matches | `marc` → `/termine/<T05>/bearbeiten`: change opponent, add a match, then remove match 1 | The event shows the changed matches by start time |
| G6 | Other day | API `POST …/T04/spiele` with start time on 23.11. | 400 at the field `anspielzeit` |
| G7 | Change type | API `PUT …/T04` with `typ: TRAINING` | 409 rule AUF-01 |
| G8 | Double click | Checkbox twice quickly; «Veröffentlichen» in the dialog twice | All checkboxes disabled during the request, at most one `POST …/veroeffentlichen` |
| G9 | Dark | Call-up screen, event T04/T05, dialogs | Easy to read, status colors and chips recognizable |
| G10 | Move an imported match day (O-48) | `petra` → `/termine/<T04>/bearbeiten` → date 29.11.2026 → save → «Resultat für Team 1 – UHC Musterdorf erfassen» → «2:1»; API `PUT …/T04/spiele/<match 01>` with result | Saving is prevented: message at the field «Datum» «Der Spieltag hat Spiele von swiss unihockey. Er lässt sich nur am selben Tag verschieben; …» (API 422, rule SUH-05, field `beginn`). Match day and matches stay on 22.11., result 2:1 can be saved. |
| G11 | Move a match day entered by hand | `marc` → API `PUT …/T05` with date 29.11. | Match 03 moves along (29.11. 14:00) |
| G12 | Change call-up after publishing | `petra` → publish call-up T04, then tick Kim; `kim` → T04 | Status stays «veröffentlicht», Kim immediately sees «aufgeboten» |
| G13 | Permissions matches/result | `anna`, `marc` → API `PUT …/T04/spiele/<match 01>` | 403; admin `alex` may view and change the call-up |
| G14 | Move an imported match day on the same day | `petra` → `/termine/<T04>/bearbeiten` → date 29.11. → save (rejected) → date back to 22.11., start 09:00 → save → «Resultat für Team 1 – UHC Musterdorf erfassen» → «2:1» (double click on «Resultat speichern») | 200, «Der Termin ist verschoben.», matches stay 09:30/11:45, exactly one `PUT …/spiele/<match 01>` → 200, «Resultat 2:1» stays after reload |
| G15 | Matches for a practice | API `POST …/<T01>/spiele` | 409 «Nur ein Spieltag hat Spiele.», rule AUF-01 |
