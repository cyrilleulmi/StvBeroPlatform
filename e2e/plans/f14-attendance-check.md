# Test plan F-14 · Attendance check per event

Issue #53 · File `e2e/tests/f14-attendance-check.spec.ts` · Requirements PRS-01 to PRS-10 (`docs/requirements/11-attendance-check.md`) · Scenarios S41–S43 (docs/test-data.md)

Starting point for every case: database running (`scripts/db-start.sh`), backend profile `dev`/`test`, frontend on port 4200, `POST /api/test/reset`, clock at default (Mon 16.11.2026 06:00) unless stated, login via `/dev-login` or `POST /api/test/login`. Mobile 375×812 and desktop 1280×800.

IDs: event 01 `40000000-0000-4000-8000-000000000001` (switch off), 09 `…009` (Thu 12.11., switch on, nothing recorded), 10 `…010` (Thu 05.11., recorded by Petra). Team 1 `10000000-0000-4000-8000-000000000001`.

Test IDs: `event-attendance-check`, `attendance-section`, `attendance-recorded`, `attendance-record`, `my-attendance` (`data-attendance`), `attendance-list`, `attendance-state`, `attendance-person-<personId>` (`data-response`, `data-attendance`), `attendance-summary`, `attendance-unsaved`, `attendance-save`.

| Criterion | Case | Steps | Expected |
|---|---|---|---|
| 1 PRS-01 | Switch off by default | As `petra` `/termine/neu`, also «Serie (jede Woche)» | Checkbox «Präsenz erfassen» unticked, hint per kind |
| 1 PRS-01 | Series passes it on | Create a Friday series 20.11.–04.12. for Team 1 with the switch | All 3 events have `attendanceCheck: true`; «Serie bearbeiten» shows it ticked |
| 2 PRS-02 | Not before the start | Create an event on 20.11. with the switch | Section «Präsenz» only with «Die Präsenz lässt sich ab Beginn des Termins erfassen.», no link; API 409 PRS-02 |
| 2, 3, 5 | List of event 09 | As `petra` event 09 → «Präsenz erfassen» | Groups Linie 1, Linie 2, Goalies, Ohne Linie; Anna «anwesend», Mia/Nina/Kim «abwesend»; «4 anwesend · 3 abwesend · 0 entschuldigt»; Petra not listed; axe clean |
| 3 | Nothing saved before «Präsenz speichern» | Set Mia to «entschuldigt», reload | «Noch nicht gespeichert.» before reload, afterwards Mia «abwesend» again, `attendanceRecorded: false` |
| 4 PRS-04 | Save and correct | Clock 12.11. 21:00, save; clock 13.11. 08:00, `marc` corrects Sara | «Zuletzt gespeichert von Petra Keller am Do 12.11. 21:00.», then «… Marc Huber am Fr 13.11. 08:00.»; Mia's response stays «nicht dabei» |
| 6 PRS-06 | Own attendance only | As `sara` event 10 | «Deine Präsenz»: «Der Coach hat dich als «abwesend» erfasst.»; no section «Präsenz»; `/termine/…010/praesenz` «Keine Berechtigung», API 403 |
| 7 PRS-07 | Switch off | Clock 17.11. 19:00, `petra` event 01 | No section «Präsenz»; `/praesenz` «Für diesen Termin lässt sich keine Präsenz erfassen.»; API 409 PRS-07 |
| 8 PRS-08 | Switch off keeps data | Event with switch, clock into the event, record Anna «entschuldigt», untick in «Termin bearbeiten» | Dialog «Präsenz ausschalten?»: «Abbrechen» saves nothing, «Ausschalten und speichern» saves; data still there after switching on again |
| 9 PRS-09 | Cancelled | Event with switch cancelled, clock after start | No section «Präsenz», API 409 PRS-09 |
| 10 | Seed | As `alex` event 10 → `/praesenz` | «Zuletzt gespeichert von Petra Keller am Do 05.11. 21:00.», «4 anwesend · 2 abwesend · 1 entschuldigt», Sara «abwesend» with response «dabei» |

## Additional cases (test agent, round 0)

| Criterion | Case | Steps | Expected |
|---|---|---|---|
| 1 PRS-01 | Single event deviates from the series | Create a Friday series 20.11.–04.12. with the switch via UI; untick it on the 27.11. event; in «Serie bearbeiten» switch off, then on again | Series off: all 3 events off. Series on again: 20.11. and 04.12. on, 27.11. stays off |
| 1 PRS-01 | Default via API | `POST /api/events` without `attendanceCheck` | `attendanceCheck: false` |
| 2 PRS-02 | Exact start | Clock 1 s before start → `PUT …/attendance`; clock at start | 409 PRS-02, then 200 |
| 3 | Shared event of two teams | Clock 17.11. 19:00, switch on for event 01 (Team 1 + Team 2), open `/praesenz` as `petra` | Groups are recognizable per team (team heading as in the overview); Lea (in both teams) has one value in both places; Petra listed as player of Team 2 only |
| 3 | Double click on «Präsenz speichern» | Change Mia, double click | Exactly one `PUT`, «Die Präsenz ist gespeichert.» |
| 4 PRS-04 | Correction by a coach of another team | Reset, clock 16.11. 08:00, `marc` sets Sara «anwesend» on event 10 | «Noch nicht gespeichert.» after the change, disappears when changed back; after saving «Zuletzt gespeichert von Marc Huber am Mo 16.11. 08:00.», «5 anwesend · 1 abwesend · 1 entschuldigt» |
| 5 PRS-05 | Invalid entries | `PUT` with Petra (coach), unknown person, duplicate person, unknown status, empty body | 422 PRS-05, 422 PRS-05, 400, 400, 400 |
| 6 PRS-06 | Players without permission | `mia`, `anna`: `GET`/`PUT …/attendance` and `/praesenz` | 403, «Keine Berechtigung», no 403 requests from the UI itself; overview contains no attendance |
| 8 PRS-08 | Hidden for the player while off | After «Ausschalten und speichern», as `anna` | `myAttendance` absent; after switching on again «entschuldigt» is back |
| 9 PRS-09 | Cancelled in the UI | Cancel a series event with the switch, clock after start | No section «Präsenz», `/praesenz` «Für diesen Termin lässt sich keine Präsenz erfassen.» |
| – | Anonymous | `GET …/attendance` without session | 401 |
| – | Dark mode | `/praesenz` on mobile in dark mode | Readable, selected states distinguishable |
