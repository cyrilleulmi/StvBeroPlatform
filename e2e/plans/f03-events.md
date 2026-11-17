# Test plan F-03 · Events and series

Issue #4 · Requirements TER-01 to TER-11, RR-05, RR-06, RR-07 · Tests: `../tests/f03-termine.spec.ts`,
backend `backend/src/test/java/ch/stvbero/platform/core/event/`

General for every case:

- Preparation: `POST /api/test/reset` (now = Mon 16.11.2026 06:00), then `POST /api/test/login` with `{ "loginKey": "<person>" }`.
- Views: mobile 375×812 and desktop 1280×800, light and dark.
- After each case: no console errors.

IDs: Team 1 `10000000-0000-4000-8000-000000000001`, Team 2 `…002`, Team 3 `…003`. Events `40000000-0000-4000-8000-0000000000NN`,
series `70000000-0000-4000-8000-0000000000NN`.

| No. | Case | Steps | Expected |
|---|---|---|---|
| K1 | Seed | `anna` → `/termine`; `petra` → `/serien/<01>` | List starts with practice Tue 17.11. 18:30 (T01), Thu 19.11. 19:00 (T02), match day Sun 22.11. 09:30 (T04), Tue 24.11. «abgesagt · Halle besetzt» (T03). Series 01: «Jeden Dienstag» 18:30–20:00, exception «Weihnachtsferien: 21.12.2026 – 03.01.2027»; no practices on 22.12. and 29.12. |
| K2 | Own teams | `tina` → `/termine`; `alex` → `/termine` | Tina: no T01, first entry Wed 18.11. 18:00, T06 visible. Alex (no team): «Keine kommenden Termine» |
| K3 | Event for two teams | `petra` → «Termin erfassen», date 20.11., 18:00–19:30, Team 1 + Team 2 → «Termin speichern» | Detail page «Der Termin ist erfasst.»; appears for `anna` (Team 1) and `eva` (Team 2), not for `tina` |
| K3.1 | Permissions | `marc`: `POST /api/events` only Team 1; `anna` → `/termine/neu` | 403; the form offers Marc only Team 2. Anna: «Keine Berechtigung», no link «Termin erfassen» |
| K4 | Series | `petra` → `/termine/neu` → «Serie (jede Woche)», Monday 19:30–21:00, 16.11.–14.12. | `?art=serie` in the URL; series page «Jeden Montag, 19:30–21:00»; list Team 1: Mon 16.11., 23.11., 30.11., 07.12., 14.12. |
| K5 | Exception | `petra` → `/serien/<01>`, exception 30.11.–06.12. «Hallenreinigung» | Status «Ausnahme «Hallenreinigung» hinzugefügt.»; Tue 01.12. is missing from the list. With a response (from F-04): the event is cancelled, reason «Hallenreinigung» |
| K6 | Move | `petra` → T02 → «Termin bearbeiten» → date 20.11. | Notice «gilt nur für diesen Termin»; «Der Termin ist verschoben.»; list: Fri 20.11. 19:00 «verschoben», Thu 26.11. unchanged; series 02 still «Jeden Donnerstag, 19:00–20:30» |
| K7 | Cancelled (S13) | `anna` → T03 | «Abgesagt: Halle besetzt», «Antworten ist nicht mehr möglich.», no «Termin bearbeiten» |
| K7.1 | Cancel | `petra` → edit T01 → «Termin absagen», without reason, then «Halle gesperrt» | Without reason: field error. Then «Der Termin ist abgesagt.», status with reason |
| K8 | Response deadline | T01; new event 21.11. 10:00 with 48 hours; series 01 with 12 hours | «Mo 16.11. 18:30 (24 Stunden vor Beginn)»; «Do 19.11. 10:00 (48 …)»; T01 afterwards «Di 17.11. 06:30 (12 …)» |
| K9 | Filter | `petra` → team «Team 2», then «Vergangen», reload | URL `?team=<T2>&ansicht=vergangen`, T07 visible, selection stays after reload |

## Addition by the test agent (round 0)

Edge cases from states.md (forbidden transitions) and robustness. Starting point as above (reset, now = Mon 16.11.2026 06:00).

| No. | Case | Steps | Expected |
|---|---|---|---|
| G1 | API permissions | `anna`: `POST /api/events` (Team 1), `PUT /api/events/<T01>`, `POST /api/events/<T01>/cancel`, `PUT /api/series/<01>`, `POST /api/series/<01>/exceptions`; `petra`: `POST /api/events` only Team 3; `tina`: `GET /api/events/<T01>`, `GET /api/series/<01>` | 403 each with `rule` RR-07 |
| G2 | RR-06 | `marc` (coach Team 2): `POST /api/events/<T01>/cancel` `{ "reason": "Halle zu" }` | 200, T01 cancelled |
| G3 | Cancelled is final | `petra`: cancel or change T03 again (API); `/termine/<T03>/bearbeiten` | 409 TER-06; the page shows «Dieser Termin ist abgesagt oder vorbei und lässt sich nicht mehr ändern.», no «Termin bearbeiten» in the detail |
| G4 | Invalid input, event | `petra` → `/termine/neu`: title only spaces; end 09:00 before start 10:00; date 10.11.2026; no team; response deadline empty, -1, 337 | Field errors in German at the field: «Bitte einen Titel angeben.», «Das Ende muss nach dem Beginn liegen.», «Der Termin liegt in der Vergangenheit.», «Bitte mindestens ein Team wählen.», «Bitte eine Zahl angeben.»; for 337 a German message (at most 336 hours). URL stays `/termine/neu` |
| G5 | Invalid input, series | `/serien/<01>/bearbeiten` response deadline 400; API series longer than one year; exception without data and with "to" before "from" | German field errors; 400 «Eine Serie dauert höchstens ein Jahr.»; «Bitte ein Datum angeben.»/«Bitte einen Grund angeben.»; «Das Ende der Ausnahme muss nach ihrem Beginn liegen.» |
| G6 | Exception with response | Series 01, event 08.12. gets a response (from F-04 through the app, before that directly in the DB); exception 07.12.–13.12. «Turnier» | List: Tue 08.12. «abgesagt · Turnier»; detail «Abgesagt: Turnier» |
| G7 | Double click | Double click «Termin speichern» and «Absagen» in the dialog | Exactly one `POST` |
| G8 | Reload | Reload after moving (K6) and filter (K9); back button after «Vergangen» | State stays; back leads to `?team=<T2>` |
| G9 | Series of other teams | `anna` (only Team 1) and `marc` (only Team 2) → `/serien/<01>` | «Teams: Team 1 + Team 2» (all teams of the series, not only their own) |
| G10 | Edit series without data loss | `marc` → `/serien/<01>/bearbeiten` → save without changes | Series keeps Team 1 + Team 2 |
| G11 | Long input | Title, place, notes with 600 characters without spaces (e.g. a pasted link) | Cut to the maximum length; detail page on mobile without horizontal scrolling |
| G12 | Filter other team | `petra` → `/termine?team=<Team 3>` | The «Team» select shows what is filtered (or the filter is ignored); display and list match |
| G13 | Dark and desktop | `/termine`, `/termine/<T03>`, `/termine/neu?art=serie`, `/serien/<01>`, dialog «Termin absagen» dark, 375 and 1280 | Easy to read, nothing white on white |

## Rework round 1

Automated in `e2e/tests/f03-termine.spec.ts` (section «Befunde aus dem Test») and `backend/.../EventsTest.java`.

| Finding | Case | Test |
|---|---|---|
| Bug 1 / G9 | `anna`, `marc` → `/serien/<01>` shows «Team 1 + Team 2» | E2E «Serie nennt alle ihre Teams», `ter11SeriesNamesAllTeamsIncludingOthers` |
| Observation 1 / G4, G5 | Response deadline 337, 400 and -1 in the form (event and series), API 337 | E2E «Antwortfrist ausserhalb 0 bis 336 Stunden», `ter10ResponseDeadlineAtMostTwoWeeksWithGermanMessage` |
| Observation 2 / G11 | Long title, place, notes without spaces, detail page | E2E «Lange Wörter … brechen um» (mobile and desktop) |
| Observation 3 / G12 | `petra` → `/termine?team=<Team 3>`: select «Team 3» | E2E K9 (end) |

## Retest round 1 (test agent)

Starting point as above. Checks the findings from round 0 again and the criteria K1 to K9 as regression.

| No. | Case | Steps | Expected |
|---|---|---|---|
| R1 | Series of other teams (G9) | `anna`, `marc` → `/serien/<01>`; `tina` → `/serien/<01>` | anna and marc: «Teams: Team 1 + Team 2». tina: «Keine Berechtigung», API 403 |
| R2 | Response deadline in the form (G4, G5) | `petra` → `/termine/neu`, response deadline 337, -1, 2.5, empty → «Termin speichern»; `/serien/<01>/bearbeiten` 400 | «Höchstens 336 Stunden (14 Tage).», «Mindestens 0 Stunden.», «Bitte eine ganze Zahl angeben.»; field `aria-invalid="true"` and focused; no `POST` |
| R3 | Response deadline and required fields, API | `POST /api/events` with `responseDeadlineHours` 337 or -1, empty title, notes 501 characters, `teamIds: []` | 400, `fields[].message` in German |
| R4 | Long words (G11) | Event with title, place, notes from a 320-character link without spaces; detail, list, edit at 375 px | `scrollWidth` 375, text wraps, title cut to 80 characters |
| R5 | Filter other team (G12) | `petra` → `/termine?team=<Team 3>` mobile and desktop | Select shows «Team 3», list only «Teamessen» |
| R6 | Regression K1–K9 | as K1–K9, mobile and desktop; double click on «Termin speichern» and «Absagen» | as above; exactly one `POST` each |
| R7 | Dark mode | `/serien/<01>` (anna, mobile), response deadline field error (mobile), `/termine?team=<Team 3>` (desktop) | Easy to read, error text recognizable |
