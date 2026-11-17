# Test plan F-02 · Lines

Issue #3 · Requirements LIN-01 to LIN-12, RR-03, RR-07 · Tests: `../tests/f02-lines.spec.ts`

General for every case:

- Preparation: `POST /api/test/reset`, then `POST /api/test/login` with `{ "loginKey": "<person>" }`.
- Views: mobile 375×812 and desktop 1280×800, light and dark. Drag & drop only on desktop; on mobile via the select.
- After each case: no console errors.

IDs: Team 1 `10000000-0000-4000-8000-000000000001`, Team 2 `…002`. Groups `30000000-0000-4000-8000-0000000000NN`
(01 T1 Linie 1, 02 T1 Linie 2, 03 T1 Goalies, 04 T2 Linie 1, 05 T2 Linie 2, 06 T2 Goalies, 07 T3 Linie 1, 08 T3 Goalies).

| No. | Case | Steps | Expected |
|---|---|---|---|
| K1 | Seed | `petra` → `/teams/<T1>/linien` | Order Linie 1 (Mia Frei, Lea Graf, Anna Muster), Linie 2 (Sara Brun, Nina Roth), «Linie hinzufügen», Goalies (Jana Wyss), «Ohne Linie» (Kim Baumann). `/teams/<T1>`: lists «Linie 1», «Linie 2», «Goalies», «Ohne Linie», link «Linien einteilen» |
| K2.1 | Add | «Linie hinzufügen» | Status «Linie 3 ist hinzugefügt.», Linie 3 before the goalies |
| K2.2 | Rename | «Linie 3 umbenennen» → «Powerplay» → «Speichern»; empty name | «Neuer Name: Powerplay.»; empty: field error «Bitte einen Namen eingeben.» |
| K2.3 | Order | «Powerplay nach oben» twice | Powerplay at the top; «Linie 1 nach oben» was disabled |
| K2.4 | Delete | «Linie 2 löschen» → dialog «Linie 2 löschen?» → «Abbrechen», then «Löschen» | Cancel changes nothing. Delete: Sara and Nina under «Ohne Linie» (S24) |
| K3.1 | Select | `assignment-<Kim>` → «Linie 2» (S14) | Status «Kim Baumann ist jetzt in Linie 2.», saved immediately (same after reload), focus stays in the field |
| K3.2 | Drag & drop | Drag Anna by the handle onto «Goalies» | Status «Anna Muster ist jetzt in Goalies.», same after reload |
| K4 | One group per team | Anna → «Linie 2» (S15) | Anna only in Linie 2 |
| K5 | Two teams | `alex`: Lea in T1 Linie 1, T2 Linie 2; Lea in T1 → Goalies | T2 unchanged, the select in T2 shows only groups of T2 |
| K6 | Goalies | Goalies without buttons; API `DELETE …/groups/<03>` | 409, rule LIN-05 (S25) |
| K7 | Permissions | `anna` → `/teams/<T1>/linien`; `petra` → `/teams/<T2>/linien`; `marc` → `/teams/<T1>/linien` | Anna and Petra (player in T2): notice «Nur Coaches des Teams können die Einteilung ändern.», no controls. Marc: «Keine Berechtigung», API 403 (S26) |

## Addition by the test agent (round 0)

Edge cases from states.md («Linien») and robustness. Preparation as above.

| No. | Case | Steps | Expected |
|---|---|---|---|
| G1 | Duplicate name (LIN-09), E2E `F-02 G1` | `petra`, mobile → «Linie hinzufügen» → «Linie 3 umbenennen» → «Linie 1» (also «LINIE 1», «goalies») → «Speichern» | 409 rule LIN-09; message «In diesem Team gibt es schon eine Gruppe «Linie 1».» **visible at the name field**, focus stays in the field, name unchanged |
| G2 | Name too long | API `POST …/groups` with 300 characters; field «Name von …» | 400 with field error; field has `maxlength="30"` |
| G3 | Goalies cannot be changed (S25) | API `PUT …/groups/<03>` `{ name: "X" }`, `DELETE …/groups/<03>` | 409 each, rule LIN-05 |
| G4 | Coach cannot be assigned (LIN-10) | `petra` API `PUT /api/teams/<T1>/assignments/<Petra>` `{ groupId: <01> }` | 409, rule LIN-10 |
| G5 | Group of another team | API `PUT /api/teams/<T1>/assignments/<Lea>` `{ groupId: <05> }` | 404 |
| G6 | Write permissions | `anna` and `marc`: `POST …/groups` `{ name: "Test" }`, `PUT …/groups/<01>`, `DELETE …/groups/<01>`, `PUT …/assignments/<Lea>` | 403 each; `GET …/groups` anna 200, marc 403 |
| G7 | Focus when moving, E2E `F-02 G7` | Keyboard: focus on «Linie 2 nach unten» (with Linie 3), Enter | Linie 2 is at the bottom. Focus stays on a control of this line (e.g. «Linie 2 nach oben»), not on `body` |
| G8 | Double click | «Linie hinzufügen» twice; «Löschen» in the dialog twice; «Speichern» when renaming twice | Exactly one new line, one deletion or one change, no error message |
| G9 | Back/forward | After «Linie hinzufügen» back button, then forward | State from the server, Linie 3 stays |
| G10 | Archive | `alex` → `/teams/<T1>?saison=<2025/26>` | Archive notice, «Ohne Linie: Vera Studer», no link «Linien einteilen» |
| G11 | Dark mode | `/teams/<T1>/linien` with rename field error and delete dialog, `/teams/<T1>` | Everything readable, sufficient contrast |

## Retest by the test agent (round 1)

Checked on `ec9c568`, preparation as above.

| No. | Case | Steps | Expected |
|---|---|---|---|
| R1.1 | G1 mobile | `petra`, 375×812 → «Linie hinzufügen» → «Linie 3 umbenennen» → «LINIE 1», then «goalies» (with Enter) → «Speichern» | Message at the field (`aria-invalid`, `aria-describedby`) in the visible area, focus in the field, no message at the top of the page |
| R1.2 | G1 correction | After R1.1 «Powerplay» → «Speichern», reload | «Neuer Name: Powerplay.», field error gone, focus on «Powerplay umbenennen», name stays after reload |
| R1.3 | G1 cancel | After the error «Abbrechen», «Linie 3 umbenennen» again | Field with «Linie 3», without the old error; focus after cancel on «Linie 3 umbenennen» |
| R1.4 | G1 double click | Desktop dark, «Linie 2» → double click «Speichern» | Only one request (409), message at the field easy to read |
| R1.5 | G7 keyboard | Enter on «Linie 2 nach unten» (at the bottom); Enter on «Linie 3 nach oben» (at the top), Enter, space bar | At the edge, focus moves to the opposite button of the same line; in the middle it stays; never on `body` |
| R1.6 | Permissions (regression) | API `GET`/`PUT …/groups/<01>`, `DELETE …/groups/<03>` as `anna`, `marc`, `petra` | anna 200/403/403, marc 403/403/403, petra 200/200/409 |
