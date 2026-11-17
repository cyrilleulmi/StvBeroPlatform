# Test plan F-07 · swiss unihockey connection

Issue #8 · Requirements SUH-02 to SUH-10, BEN-06, RR-07 · Scenario S12 ·
Tests: `../tests/f07-swiss-unihockey.spec.ts`, backend `backend/src/test/java/ch/stvbero/platform/core/swissunihockey/SwissUnihockeyTest.java`
(sync against the simulator), `…/SyncRulesTest.java` (domain rules), `…/SuApiTest.java` (real client with recorded
responses), `…/core/testsupport/ProdWithoutTestSupportTest.java` (profile prod without simulator).

General, for every case:

- Setup: `POST /api/test/reset` (also resets the simulator: team ID `900001` returns match 01 and 02 of event 04),
  then `POST /api/test/login` with `{ "loginKey": "<person>" }` or `/dev-login`.
- Set the simulator: `PUT /api/test/swiss-unihockey/matches` with `{ "suTeamId": 900001, "unavailable": false, "matches": [ … ] }`
  (fields of a match: `suMatchId`, `startTime`, `homeTeam`, `awayTeam`, `hall`, optional `location`, `mapsUrl`, `latitude`, `longitude`, `result`).
- Trigger a sync: as `alex` under `/verwaltung/teams/<Team>` section «swiss unihockey» → «Jetzt abgleichen»,
  or `POST /api/teams/<Team>/swiss-unihockey/sync`.
- Check notifications via `GET /api/test/notifications` (new ones have IDs that do not start with `90000000`).
- Views: mobile 375×812 and desktop 1280×800, team administration once in dark mode.

IDs: team 1 `10000000-0000-4000-8000-000000000001` (SU `900001`), team 2 `…002` (not linked). Event 04 `40000000-0000-4000-8000-000000000004`,
match 01 `50000000-0000-4000-8000-000000000001` (SU `5000001`), match 02 `…002` (SU `5000002`).

Match 01 in the simulator: `{"suMatchId":5000001,"startTime":"2026-11-22T09:30:00+01:00","homeTeam":"Team 1","awayTeam":"UHC Musterdorf","hall":"Sporthalle Seefeld","location":"Musterdorf"}`,
match 02 the same with `5000002`, `11:45`, home «Unihockey Beispielstadt», away «Team 1».

## Acceptance criteria

| No. | Case | Starting point | Steps | Expected |
|---|---|---|---|---|
| K1 | Link and sync (SUH-02) | Reset, simulator `900002` with one match on 05.12. 13:00, `alex` | `/verwaltung/teams` → team 2 → team ID `900002` → «Verknüpfen» → «Jetzt abgleichen» | The list shows the chip «swiss unihockey» for team 1 (`su-linked-<Team>`). After linking «Verknüpft mit Team-ID 900002.», sync «Abgleich fertig: 1 neu, 0 geändert, 0 nicht mehr bei swiss unihockey, 0 unverändert.» Match day Sat 05.12. appears for `eva` |
| K1b | Input validation | as K1 | «abc» or empty → «Verknüpfen» | «Bitte eine Team-ID eingeben (nur Ziffern).», field `aria-invalid`. API: `0` → 400, ID already in use (`900001` for team 2) → 409 rule SUH-02 |
| K1c | Unlink | Reset, `alex` | Team 1 → «Verknüpfung lösen» | «Die Verknüpfung ist gelöst. Bisherige Spieltage bleiben.», «Nicht verknüpft.», sync button gone; API sync 409 |
| K1d | Admins only (RR-07) | Reset, `petra` | `/verwaltung/teams/<Team 1>`; API PUT/POST | «Keine Berechtigung», API 403; without login 401 |
| K2 | Simulator vs. real interface | Reset | Sync team 1 | «0 neu, 0 geändert, 0 nicht mehr …, 2 unverändert». Profile prod: backend test `ProdWithoutTestSupportTest` (no simulator, URL `https://api-v2.swissunihockey.ch/api`), real client: `SuApiTest` with files under `backend/src/test/resources/swissunihockey` |
| K3 | Building match days (SUH-04, SUH-07) | Simulator: match 01, 02 and two matches on 06.12. 10:00 and 12:30 in «Sporthalle Nord, Beromünster» | Sync; as `anna` events → match day Sun 06.12. | Exactly one match day with «10:00 Team 1 – UHC Drei» and «12:30 UHC Vier – Team 1», Google Maps link `…query=Sporthalle+Nord+Berom%C3%BCnster`, start 10:00, end 13:45 (last start time + 75 min.). A match in another hall or on another day: its own match day. Second sync: nothing new |
| K3b | New match for an existing match day | Simulator: match 01, 02 and a third on 22.11. 14:00 in Sporthalle Seefeld | Sync | Event 04 has three matches, end 15:15 |
| K4 | S12 rescheduling (SUH-05, SUH-06, BEN-06) | Simulator: match 02 at 12:15 | Sync; `petra` opens event 04; `anna` and `eva` open `/benachrichtigungen` | «1 geändert, 1 unverändert». Match 02 «12:15 Unihockey Beispielstadt – Team 1», meeting point «08:00 · Parkplatz Sporthalle Nord», note, responses and call-up (draft Anna, Lea, Mia, Sara, Jana) unchanged. All 8 members of team 1 (incl. Petra) receive `MATCH_CHANGED` «Spieltag So 22.11. wurde geändert: Spiel gegen Unihockey Beispielstadt neu um 12:15.», Eva (team 2) does not. Second sync: none more |
| K4b | First match rescheduled | Simulator: match 01 at 10:00 | Sync | Event 04 starts at 10:00, counts as «verschoben», meeting point stays 08:00 |
| K4c | Different day | Simulator: match 02 on 29.11. 11:45 in Turnhalle Weid | Sync | Match 02 moves to a new match day Sun 29.11., event 04 keeps match 01. Text «… neu am So 29.11. 11:45, neu in Turnhalle Weid.» |
| K4d | Result (SUH-08) | Simulator: match 01 with `"result":"5:3"` | Sync | Match 01 shows «Resultat 5:3», no notification |
| K5 | Match missing | Simulator only match 01 | Sync; `petra` opens event 04 | «1 nicht mehr bei swiss unihockey». Match 02 stays with «swiss unihockey liefert dieses Spiel nicht mehr.» and can now be deleted. If it is delivered again, the marker disappears |
| K6 | Unreachable (SUH-09) | Simulator `unavailable: true` | Sync | Message (`role=alert`) «swiss unihockey ist im Moment nicht erreichbar. Es wurde nichts geändert. Der nächste Abgleich versucht es wieder.», API 502 rule SUH-09. Event 04 unchanged, no notification, events and teams load normally |
| K7 | Daily job (SUH-03) | Code | `SyncJob` (`@Scheduled`, `stv.swiss-unihockey.sync.cron`, default 04:30), backend test `suh03DailyJob…` | The job syncs all linked teams of the current season; one failing team does not hold up the others |
| K8 | Open questions | Docs | `docs/open-points.md` O-20 to O-24, `docs/requirements/06-swiss-unihockey.md` | Research status updated |

## Edge cases

| No. | Case | Steps | Expected |
|---|---|---|---|
| G1 | Manually entered match days (SUH-10) | Link team 2 with `900002`, simulator returns a match on 28.11. in Turnhalle Weid (like event 05) | New match day, event 05 unchanged |
| G2 | No current season | `DELETE /api/test/current-season`, sync | 409 rule SAI-03 |
| G3 | Double click «Jetzt abgleichen» | Tap twice quickly | One sync, button locked while running |
| G4 | Archive | `/verwaltung/teams/<Team>?saison=<2025/26>` | Section «swiss unihockey» missing (read-only) |
| G5 | Whole match day rescheduled (SUH-04, SUH-06, rework round 1) | Simulator: match 01 on 29.11. 09:30 and match 02 on 29.11. 11:45, both «Sporthalle Seefeld»; delivered once in this order, once in reverse order. Sync; `petra` opens event 04 | «0 neu, 2 geändert», only event 04 affected. Event 04: Sun 29.11. 09:30–13:00, «Dieser Termin wurde verschoben.», both matches, meeting point «08:00 · Parkplatz Sporthalle Nord», note, responses («4 dabei · 1 nicht dabei · 1 vielleicht · 1 offen») and call-up draft stay. No additional match day. One notification per member «Spieltag So 22.11. wurde geändert: Spiel gegen … neu am So 29.11. …». Second sync: «2 unverändert» |
| G6 | Match delivered again | Simulator only match 01, sync; then match 01 and 02, sync | Second sync «1 geändert», `missingAtSu` `false` for both matches |
| G7 | Roles API | `petra` (coach team 1), `anna` (player), `marc` (coach team 2): `PUT …/swiss-unihockey`, `POST …/sync`; without login `POST …/sync` | 403 for all three, 401 without login |
| G8 | Invalid team ID API | `PUT …/swiss-unihockey` with `0`, `-5`, `99999999999999` | 400 with field `suTeamId` |
| G9 | Reload | Team 1 «Verknüpfung lösen», reload the page | «Nicht verknüpft.» stays |
| G10 | Responses on an imported match day | K3, as `anna` match day Sun 06.12. → «Dabei» | Button pressed, «1 dabei» |

## Test run round 1 (test agent, at 8046fa4)

Automated: backend 265 ✅, E2E 264 ✅ (mobile and desktop, incl. axe), 6 skipped by project.

Exploratory with Playwright MCP: K1–K6 and G1–G10 passed. Mobile 375×812: K1, K1b, K1c, K1d, K3, K5, K6, G3, G4, G9, G10. Desktop 1280×800: K4, G5. Dark mode: `/verwaltung/teams/:id` with section «swiss unihockey» easy to read. K2, K3b, K4b–K4d, G2, G7, G8 via the API. K7 and K8: backend test `suh03DailyJob…` and docs O-20 to O-24 respectively.

Follow-up fix: a match that swiss unihockey no longer delivers cannot be deleted in `/termine/:id/bearbeiten` (only via `DELETE /api/events/{eventId}/matches/{matchId}`). The form comes from F-05, see the follow-up fix issue.
