# Test plan · F-10 English: backend, database and API (NF-40, E-68, E-74)

Issue #49. Pure rename: the app must behave exactly as before, the UI stays German. Most criteria are repository and API checks; criterion 6 is an exploratory walkthrough of every screen.
Starting point for every UI case: `scripts/db-start.sh reset`, `scripts/start.sh`, `POST /api/test/reset`, clock at the seed default (16.11.2026 06:00), log in via `/dev-login`.

## Repository and API

| Case | Steps | Expected |
|---|---|---|
| K1 Contract lint (crit. 1) | `npx -y @redocly/cli@2.78.0 lint docs/api/openapi.yaml`; collect all paths, parameter names, schema names, property names and enum values | Valid, 0 warnings; every identifier English (e.g. `/api/events`, `Event`, `ATTENDING`, `MATCH_DAY`) |
| K2 Contract test (crit. 2) | Backend suite | `OpenApiContractTest` green |
| K3 No German identifiers (crit. 3) | `find backend/src -type d`; grep Java/SQL/properties for German domain words outside string literals | Packages English; hits only in Javadoc quoting UI text («noch kein Aufgebot», `/termine`, `kachel-<id>`) |
| K4 Fresh DB and seed (crit. 4) | `scripts/db-start.sh reset`, backend tests, start app; `GET /api/events/40000000-0000-4000-8000-000000000001/overview` as `petra` | Migrates without errors; counters as in test-data.md |
| K5 Tests (crit. 5) | `mvn test` in `backend`; `npx playwright test` in `e2e` (projects `mobil`, `desktop`, incl. axe) | All green, skips unchanged (6) |
| K6 German messages (crit. 7) | As `petra`/`anna` via API: invalid event input (400), call-up as `anna` (403), unknown event id (404), respond to cancelled event 03 (409), «MAYBE» without comment (422) | `title`/`detail` German («Ungültige Anfrage», «Dafür hast du keine Berechtigung.», «Diesen Termin gibt es nicht.», «Termin abgesagt», «Kommentar fehlt»); `rule` and `fields[].field` English |
| K7 Test support endpoints | `GET /api/test/clock`, `POST /api/test/login {loginKey}`, `GET /api/test/notifications` | English paths respond; old `/api/test/uhr` returns 404 |

## Exploratory walkthrough (crit. 6)

Every screen on mobile (375×812), central ones also on desktop (1280×800). After every case: no console errors, no unexpected 4xx/5xx.

| Case | Person | Steps | Expected |
|---|---|---|---|
| U1 Start | `nina` | `/` | Greeting, next event with response buttons, tiles |
| U2 Events list | `petra` | `/termine`, switch «Vergangen», filter team | Months, cancelled event 03 with coral date tile and «Abgesagt», counters |
| U3 Respond | `nina` | `/termine` → «dabei» on event 01; reload | `data-antwort` shows the new response, counter +1, survives reload |
| U4 Maybe without comment | `nina` | Event 01 → «vielleicht» without comment | Blocked with «Kommentar fehlt» (ANM-04), nothing saved |
| U5 Event detail and overview | `petra` | `/termine/40000000-0000-4000-8000-000000000001` | Details, overview by team and line, coach block, comments |
| U6 On-behalf response | `petra` | Overview → set «vielleicht» for a player without comment, then with comment | Field error at the comment, then saved |
| U7 Create/edit/cancel event | `petra` | `/termine/neu` single event; edit; «Termin absagen» with reason | Saved, shows «Abgesagt: <Grund>» |
| U8 Series | `petra` | `/serien/70000000-0000-4000-8000-000000000001`, add exception | Exception listed |
| U9 Match day and call-up | `petra` | `/termine/40000000-0000-4000-8000-000000000004/aufgebot`, call up a player, publish | «<Name> ist aufgeboten.», status published; player gets a notification |
| U10 Call-up forbidden | `anna` | Same URL | «Keine Berechtigung» |
| U11 Teams and lines | `petra` | `/teams`, `/teams/<team1>`, `/teams/<team1>/linien`, move a player | Saved immediately |
| U12 Notifications | `nina` | `/benachrichtigungen`, click one, «Alle als gelesen markieren» | Marked read, opens event, feedback text |
| U13 Administration | `alex` | `/verwaltung/personen` (search), person edit, `/verwaltung/teams/<team1>` incl. «Jetzt abgleichen», `/verwaltung/saisons` | All load and save; sync result as `role="status"` |
| U14 Administration forbidden | `anna` | `/verwaltung` | «Keine Berechtigung» |
| U15 Profile, theme, logout | `nina` | `/profil`, choose «GitHub Dark», log out | Theme stays, logout leads to login |
| U16 Dark mode | `petra` | Events list, event detail, call-up in «GitHub Dark» | Readable, no white on white |
| U17 Robustness | `nina` | Double click on «dabei»; reload mid-flow; back button | One response, consistent state |
