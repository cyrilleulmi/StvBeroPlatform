# Test plan F-04 · Responses with overview

Issue #5 · Requirements ANM-01 to ANM-12, ANM-20 to ANM-25, ANM-30 to ANM-32, RR-02, RR-03, RR-06, RR-07, TER-06 ·
Tests: `../tests/f04-anmeldung.spec.ts`, backend `backend/src/test/java/ch/stvbero/platform/…/ResponsesTest.java`, `ResponseRulesTest.java`

General for every case:

- Preparation: `POST /api/test/reset` (now = Mon 16.11.2026 06:00), then `POST /api/test/login` with `{ "loginKey": "<person>" }` or `/dev-login`.
- Views: mobile 375×812, core cases also desktop 1280×800; new screens once in dark mode.
- After each case: no console errors, no unexpected 4xx/5xx.

IDs: Team 1 `10000000-0000-4000-8000-000000000001`, Team 2 `…002`. Events `40000000-0000-4000-8000-0000000000NN` (T01 practice Tue 17.11.,
T02 practice Thu 19.11., T03 cancelled, T04 match day Sun 22.11., T07 past). People `20000000-0000-4000-8000-0000000000NN` (Nina 09, Anna 05, Mia 07).

## Acceptance criteria

| No. | Case | Steps | Expected |
|---|---|---|---|
| K1 | Seed | `GET /api/events/<T01>/overview` as `petra`; T02, T04, T05 likewise | Responses as in test-data.md (T01 table, T02 only Anna attending, T04/T05 as described), no `lateCancellation`, no `enteredBy` |
| K2 | S1 overview T01 | `petra` → `/termine/<T01>` | Total «7 dabei · 2 nicht dabei · 1 vielleicht · 2 offen». Team 1: 5/1/0/1, Linie 1 Anna ✓ Lea ✓ Mia ✗, Linie 2 Sara ✓ Nina ?, Goalies Jana ✓, «Ohne Linie» Kim ✓. Team 2: 3/1/1/1, Linie 1 Petra ✓ Eva ~ Lara ✓, Linie 2 Lea ✓ Zoe ✗, Goalies Ella ?. Coach block: Petra ✓ (Team 1), Marc ✗ (Team 2). Sorted by last name |
| K3 | S2 one tap | `nina` → `/termine` → at «Training Di 17.11. 18:30» «Dabei»; then `/termine/<T01>` → «Nicht dabei» → «Überspringen» → «Dabei» | Exactly one `PUT …/responses/me` per tap, no navigation; `data-antwort="ATTENDING"`, `aria-pressed`; counter of the card or overview Team 1 «6 dabei · 0 offen» without reload |
| K4 | S3 maybe | `nina` → T01 → «Vielleicht», comment empty → save; comment only spaces; API `PUT …/me` `{status: MAYBE}` | Dialog stays open, message at the field (`aria-invalid`, `aria-describedby`), no request or a rejected one; API 422 field `comment` rule ANM-04. With comment: saved, «vielleicht» |
| K5 | Declined with reason | `anna` → T01 → «Nicht dabei» → reason «Arzt» → «Grund speichern»; second run «Überspringen» | Response saved immediately; reason optional; Petra sees «Arzt» for Anna |
| K6 | S4 comments | `anna` → T01; `petra` → T01; `marc` → T01; API `GET …/overview` for each | Anna: no comment visible, field `comment` missing in the API. Petra: «Krank» for Mia (Team 1). Marc: comments of Team 2 (Eva «Komme evtl. später»), not Mia's. Mia sees her own comment |
| K7 | S5 late cancellation | `PUT /api/test/clock` 16.11.2026 19:00; `anna` → T01 → «Nicht dabei» | API `lateCancellation: true`; `petra` sees «kurzfristig abgemeldet» for Anna. Before the deadline (06:00) no `lateCancellation` |
| K8 | S7/S8 respond on behalf | `petra` → T01 → «Antwort für Nina Roth eintragen» → «nicht dabei»; `marc` → T01; API `marc` `PUT …/responses/<Anna>`; `anna` `PUT …/responses/<Nina>` | Nina «nicht dabei», «eingetragen von Petra Keller», also after reload. Marc: no button for Anna, API 403. Anna 403 |
| K9 | Start screen | `anna` → `/` | Card `naechster-termin` «Training Di 17.11.» with `meine-antwort`; a tap saves; `alex` (no team): «Keine kommenden Termine» |
| K10 | Reload, cancelled | Reload after K3; `anna` → T03; API `PUT …/T03/responses/me` | Response unchanged; T03 buttons `disabled` with explanation (aria-describedby); API 409 rule TER-06 |

## Edge cases (states.md) and robustness

| No. | Case | Steps | Expected |
|---|---|---|---|
| G1 | Back to PENDING | API `PUT …/me` `{status: PENDING}` | 400 |
| G2 | Past event | API `PUT …/T07/responses/me` as `anna` | 409 |
| G3 | Not involved | `tina` `PUT …/T01/responses/me`; `alex` (admin without team) the same | 403 |
| G4 | Other person / coach for coach | `petra` `PUT …/T01/responses/<Marc>`; `petra` for `tina` (not involved) | 403 or 404 |
| G5 | A change replaces everything | `mia` → T01 «Dabei» | Comment «Krank» gone (Petra no longer sees a comment) |
| G6 | Late cancellation stays / drops | Clock 19:00, `anna` «Nicht dabei» → «Vielleicht» (comment) → «Dabei» | `lateCancellation` stays, drops with «dabei» |
| G7 | Responding yourself after respond on behalf | After K8 `nina` «Dabei» | «eingetragen von» disappears |
| G8 | Double click | «Dabei» twice; «Grund speichern» twice | At most one identical `PUT`, end state correct |
| G9 | Long input | Comment 600 characters without spaces | Cut or message; on mobile no horizontal scrolling |
| G10 | Dark | T01 overview (petra), dialogs, start card | Easy to read, status colors recognizable |
