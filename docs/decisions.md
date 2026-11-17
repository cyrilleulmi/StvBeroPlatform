# Decisions

Log of the decisions taken, newest last. Whoever changes a requirement records the decision here.

**Decided** means: explicitly set by Cyrille. **Accepted** means: proposed and not objected to.

## 15.11.2026 · Foundations

| No. | Decision | Kind |
|---|---|---|
| E-01 | One central, extensible platform with tiles. Angular in the frontend, Quarkus in the backend, PostgreSQL. Mobile first, desktop fully featured. | Decided |
| E-02 | The design does not use club colours but is generic. Light and dark mode are required. | Decided |
| E-03 | The base design is variant 1b «Salbei frisch». | Decided |

## 15.11.2026 · Core features

| No. | Decision | Kind |
|---|---|---|
| E-10 | Main reasons for our own app: practices for several teams at once with a per-team display, and lines with a grouped display. | Decided |
| E-11 | Coaches respond, but are not shown as players. | Decided |
| E-12 | A person can belong to several teams, with one team role each. | Decided |
| E-13 | For now a player belongs to only one line. | Decided |
| E-14 | «Vielleicht» (maybe) stays as a response, with a required comment. | Decided |
| E-15 | Attendance check is a future feature, not for now. | Decided |
| E-16 | Lines per event are not needed. | Decided |
| E-17 | The call-up for matches is part of the core. Where possible with a data connection to swiss unihockey, in any case with the venue via a Google Maps link. | Decided |
| E-18 | A driver list with automatic travel time is a future feature. | Decided |
| E-19 | For now the app is meant for adults. Parent features are kept in mind for later extensions. | Decided |
| E-20 | The teams play in tournament format. A match day combines several matches, with one response and one call-up. | Decided |
| E-21 | The coach may call up anyone in the team. The response status is shown next to them. | Decided |
| E-22 | Called-up players do not have to confirm. Anyone who cannot come after all cancels, and the coach is notified. | Decided |
| E-23 | Lines belong to a team. The rule «at most one line» applies per team. | Accepted |
| E-24 | Dual role in the same event: one response. The person appears in each team in their team role and counts once in the overall total. | Accepted |
| E-25 | The initial status is «offen» (pending). Nobody is attending automatically. | Accepted |
| E-26 | Goalies are a group of their own next to the lines. | Accepted |
| E-27 | The event types practice, match day and other event exist from the start. | Accepted |
| E-28 | Also in the core: series, response deadline with reminder, responding on behalf by the coach, line management, permissions from the team role, seasons. | Accepted |
| E-29 | With «nicht dabei» (declined) the reason is optional. Only coaches see comments. | Accepted |
| E-30 | Later: guests and trial practices, warnings, calendar subscription. | Accepted |
| E-31 | swiss unihockey: an admin links the teams once. Matches are imported and synced daily. Our own data is never overwritten. Match days can also be entered by hand. | Accepted |

## 15.11.2026 · Repository

| No. | Decision | Kind |
|---|---|---|
| E-40 | The repo is called `StvBeroPlatform`. First all requirements go in as files. Nothing is implemented yet. | Decided |

## 15.11.2026 · Production pipeline

| No. | Decision | Kind |
|---|---|---|
| E-50 | Features are built in a pipeline of three agents in the Claude cloud: development, review, test. | Decided |
| E-51 | Testability of the frontend for agents is important during development. This includes API documentation and state diagrams. | Decided |
| E-52 | The agents are Claude cloud tasks with GitHub triggers. The state is kept in labels. The instructions are skills in the repo. | Accepted |
| E-53 | Only Cyrille merges. After rework it always goes back to review. After 3 rounds the PR is blocked. | Accepted |
| E-54 | API contract first with OpenAPI 3.1 in `docs/api/openapi.yaml`. | Accepted |
| E-55 | Testability via the accessibility tree (Playwright MCP), complemented by `data-testid`, fixed seed IDs, test endpoints (reset, clock, login) only in dev/test. | Accepted |
| E-56 | Notifications always exist in the app. E-mail or push are added as channels. | Accepted |
| E-57 | Backend in Java 21 (LTS), migrations with Flyway. Java 25 was planned briefly; Java 21 is preinstalled in the Claude cloud, which keeps the pipeline simpler. | Decided |
| E-58 | The agents merge PRs without Cyrille's involvement if everything is in order. Implementation: after a passed test the test agent merges with a squash merge, only without conflicts, with green CI and exactly the tested state. This replaces E-53 (merge only by Cyrille). | Decided |
| E-59 | All tickets can be released at the same time. The pipeline works through them as a queue: one feature at a time, smallest F-number with fulfilled prerequisites first, after each merge the test agent triggers the next one. | Decided (goal), Accepted (implementation) |
| E-60 | A PR counts as blocked from round 5 (previously round 3). So there are at most 4 rework rounds after the first implementation. Changes the number from E-53. | Decided |
| E-61 | A watchdog (scheduled task, every 2 hours) detects aborted runs and lost events, e.g. after a usage limit, and triggers the pipeline again. | Decided (goal), Accepted (implementation) |
| E-62 | Non-blocking findings from review and test become issues with `typ:nachbesserung` (without `status:bereit`). Released follow-up fixes come before the next feature in the queue. F-08 collects security findings for production operation. | Decided (goal), Accepted (implementation) |
| E-63 | Findings are fixed before the merge: review findings "Must" and "Should" as well as errors and observations from the test send the PR back to rework. So that a PR gets finished, new "Should" findings and observations from round 3 on no longer send it back; like findings in code that was already on `main`, they become follow-up fix issues with `status:bereit` and are implemented automatically before the next feature. Findings from a follow-up fix itself are not released automatically (protection against endless loops). "Notes" stay in the comment. Partly replaces E-62. | Decided |
| E-64 | Push messages to Cyrille only when something went wrong (block, unsolvable merge error, watchdog special case). The normal flow is shown by the status page `status/` on GitHub Pages, live from the GitHub API, with «seit letztem Besuch» (since last visit). | Decided |

## 16.11.2026 · Themes

| No. | Decision | Kind |
|---|---|---|
| E-65 | The base design is far too green for Cyrille. In addition to light/dark/system, the profile offers 20 more themes: 10 after common dark themes (Dracula, Nord, One Dark, Monokai, Solarized Dark, Gruvbox, Tokyo Night, Catppuccin Mocha, GitHub Dark, Night Owl) and 10 of our own (Kiesel, Düne, Lavendel, Gletscher, Beere, Graphit, Mitternacht, Aubergine, Espresso, Tiefsee). They change only the colours; shape, font and spacing stay. «Salbei frisch» stays the default for now, until Cyrille chooses a new default theme (O-53). Extends E-02 and NF-07 with NF-08. | Decided (goal), Accepted (colours, implementation) |
| E-66 | «Düne» gets a dark sister «Düne Dark»: the same warm sand tone, dark, with a light petrol. That makes 21 more themes. | Decided (goal), Accepted (colours) |
| E-67 | After trying them out, three themes remain: «Hell» (colours of «Salbei frisch»), «Düne» and «GitHub Dark». All others are dropped, including the dark fir green («Dunkel») and «Düne Dark». Replaces the lists from E-65 and E-66. Assumption: «System» stays selectable and the default, because the mode must follow the system setting (NF-06); in light mode it shows «Hell», in dark mode «GitHub Dark». | Decided (selection), Accepted («System») |

## 16.11.2026 · Batch 2

| No. | Decision | Kind |
|---|---|---|
| E-68 | The whole repo becomes English: code, database, API, documents, skills, tests. What stays German: the texts of the user interface, the browser addresses (`/termine` etc.), the IDs (`ANM-03`, `E-65`), the GitHub labels, the skill names and the commands of `scripts/pipeline.sh`. Implemented in three steps with top priority: F-09 (glossary, documents, pipeline), F-10 (backend, database, API), F-11 (frontend, E2E). All further features require F-11 and are already written in English. The existing migrations are rewritten, because there is no production data yet. | Decided |
| E-69 | Coaches may do almost everything admins can, across all teams (see players, assign, events, respond on behalf, see comments). Only deleting is restricted. Seasons, the link to swiss unihockey and the admin permission stay with the admins. Changes RR-03, RR-04, RR-06 (F-12). | Decided (goal), Accepted (scope of «deleting», comments, admin areas) |
| E-70 | The attendance check is coming, but can be switched on per event or series and is off by default (F-14). The statistics show responses and, where recorded, attendance; players see their own (F-15). | Decided (optional, off), Accepted (rest) |
| E-71 | Notifications are also sent by e-mail and by push, both (F-19). Closes O-05 and BEN-10. | Decided |
| E-72 | Further features in batch 2: the start page shows at least the next two practices (F-13), warnings such as «kein Goalie» (no goalie) (F-16), team overview with data from swiss unihockey, with a simulator for now (F-17), calendar subscription (F-18), driver list with an automatically calculated route that is credited to the driver (F-20). F-08 (hardening) follows after F-11. Assumptions on the driver list: kilometres only, no money; all team members may drive; map service behind an interface, simulated in test mode. | Decided (goal), Accepted (details) |
| E-73 | F-09 implemented step 1 of E-68: glossary ([glossary.md](glossary.md)), all documents, skills, scripts and templates are English; document files renamed (e.g. `docs/fachmodell.md` → `docs/domain-model.md`). NF-02 changed, NF-40 added. | Decided |
| E-74 | F-10 implemented step 2 of E-68: backend, database and API are English (paths, parameters, schemas, fields, enum values, `rule` and field names in Problem Details, test endpoints, tables and columns, Java packages and classes, configuration keys, session cookie `stv_session`). The Flyway migrations were rewritten instead of adding rename migrations, because there is no production data; local databases need `scripts/db-start.sh reset` once (issue #49). Messages shown to users stay German. Test IDs and browser routes stay German until F-11. | Decided |
