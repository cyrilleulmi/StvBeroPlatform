# StvBeroPlatform

Central web platform for floorball (unihockey) at STV Beromünster. It brings together the organizational tools that are scattered today (lists, chats, spreadsheets) in one place – simple enough that players, coaches and helpers can use it without instructions.

> **Status 16.11.2026:** Requirements and the [production pipeline](docs/pipeline/README.md) are in place. The foundation (F-00) and the core features are built. More features are created through the pipeline as soon as an issue gets `status:bereit`. The repository is written in English, the switch was completed with F-11; the user interface stays German (NF-02, NF-40).

## Start and test

```bash
scripts/start.sh   # start database, backend (Quarkus, profile dev) and frontend
scripts/test.sh    # everything: API lint, backend tests with architecture and coverage, frontend lint/unit tests/build, E2E tests
scripts/test-affected.sh  # while developing: only the tests the change can break (modules.json)
scripts/e2e.sh     # E2E tests in parallel on stacks of their own (from :8180), next to a running app
python3 scripts/check-links.py   # check relative links in all Markdown files
```

Then: http://localhost:4200 – in test mode, log in as a seed person via http://localhost:4200/dev-login ([test-data.md](docs/test-data.md)). Swagger UI: http://localhost:8080/q/swagger-ui.

The app starts with the **club data set**: the ten real teams of the club with their matches loaded live from swiss unihockey (needs internet; without it the app still runs and says that swiss unihockey could not be reached), and invented people. The automated tests switch to the fixed **test data set** with simulators; `/dev-login` shows which one is loaded and switches between them. `STV_SIMULATION=true scripts/start.sh` starts with the test data set.

Prerequisites: Java 21, Maven, Node 22, PostgreSQL binaries (or a database `stv` on `localhost:5432`, user/password `stv`), Google Chrome for the E2E tests.

After F-10 a local database created before F-10 must be reset once with `scripts/db-start.sh reset`: the Flyway migrations were rewritten in English instead of adding rename migrations, because there is no production data (issue #49, E-74).

| Folder | Content |
|---|---|
| `backend/` | Quarkus, Java 21. One package per module under `ch.stvbero.platform.<module>`, shared core under `ch.stvbero.platform.core` ([docs/architecture.md](docs/architecture.md)). Migrations: `src/main/resources/db/migration` (Flyway) |
| `frontend/` | Angular (standalone, signals). One folder per module, the pages compose them; unit tests with Vitest |
| `e2e/` | Playwright Test (`tests/`, `plans/`): every test on `mobile`, the tests tagged `@desktop` also on `desktop` |
| `modules.json` | The modules and what each may depend on; read by the architecture test, ESLint and `test-affected.sh` |
| `scripts/` | `db-start.sh`, `start.sh`, `test.sh`, `test-affected.sh`, `e2e.sh`, `pipeline.sh`, `check-links.py`, `deploy.sh` |
| `Dockerfile`, `docker/`, `fly.toml` | Container and Fly.io app of the hosted test environments, see [docs/deployment.md](docs/deployment.md) |

## Why our own app?

Standard apps do not cover two things that are central for us:

1. **Practices with several teams.** A practice can belong to several teams at the same time. The responses show the members grouped by team right away.
2. **Lines.** Players are assigned to lines. Responses are shown grouped by line right away.

In addition: coaches respond as well, but do not appear as players. And a person can belong to several teams, each with its own team role (e.g. coach in team 1, player in team 2).

## Contents

| Document | Content |
|---|---|
| [docs/glossary.md](docs/glossary.md) | Binding vocabulary German → English |
| [docs/project-context.md](docs/project-context.md) | Goal, basic principles, tiles, way of working |
| [docs/domain-model.md](docs/domain-model.md) | Terms and how they relate |
| [docs/requirements/](docs/requirements/) | Requirements for the core, one file per topic |
| [docs/more-tiles.md](docs/more-tiles.md) | Planned tiles outside the core |
| [docs/later-features.md](docs/later-features.md) | Deliberately postponed and dropped features |
| [docs/design-principles.md](docs/design-principles.md) | Basic design «Salbei frisch»: colors, shapes, typography |
| [docs/technology.md](docs/technology.md) | Technology decisions and architecture guard rails |
| [docs/architecture.md](docs/architecture.md) | Modules and their dependencies, test levels, the checks that fail the build |
| [docs/reviews/](docs/reviews/2026-11-modularization.md) | Reviews of the project: what was measured, what was changed and why |
| [docs/open-points.md](docs/open-points.md) | Unresolved questions |
| [docs/decisions.md](docs/decisions.md) | Log of the decisions made |
| [docs/pipeline/](docs/pipeline/README.md) | Production pipeline: flow, setup, backlog, definition of done |
| [docs/api/](docs/api/README.md) | API contract (OpenAPI 3.1) and conventions |
| [docs/states.md](docs/states.md) | State and sequence diagrams |
| [docs/ui/navigation.md](docs/ui/navigation.md) | Routes, screens, fixed test IDs |
| [docs/testability.md](docs/testability.md) | Rules so that test agents can operate the frontend |
| [docs/test-data.md](docs/test-data.md) | Seed data with fixed IDs and test scenarios |
| [AGENTS.md](AGENTS.md) | Notes for AI coding agents working in this repository |

### Requirements for the core

| No. | Topic | Prefix |
|---|---|---|
| 01 | [People, teams, team roles](docs/requirements/01-people-teams-roles.md) | PTF |
| 02 | [Lines](docs/requirements/02-lines.md) | LIN |
| 03 | [Events](docs/requirements/03-events.md) | TER |
| 04 | [Responses](docs/requirements/04-responses.md) | ANM |
| 05 | [Match days and call-up](docs/requirements/05-match-days-call-up.md) | AUF |
| 06 | [swiss unihockey integration](docs/requirements/06-swiss-unihockey.md) | SUH |
| 07 | [Roles and permissions](docs/requirements/07-roles-permissions.md) | RR |
| 08 | [Notifications](docs/requirements/08-notifications.md) | BEN |
| 09 | [Seasons](docs/requirements/09-seasons.md) | SAI |
| 10 | [Non-functional requirements](docs/requirements/10-non-functional.md) | NF |
| 11 | [Attendance check](docs/requirements/11-attendance-check.md) | PRS |
| 12 | [Attendance statistics](docs/requirements/12-statistics.md) | STA |
| 13 | [Calendar subscription](docs/requirements/13-calendar.md) | CAL |
| 14 | [Driver list](docs/requirements/14-driver-list.md) | DRV |
| 15 | [Timeline of a match day](docs/requirements/15-timeline.md) | TML |
| 16 | [Event variants](docs/requirements/16-event-variants.md) | VAR |
| 17 | [Calendar view](docs/requirements/17-calendar-view.md) | KAL |
| 18 | [Absences](docs/requirements/18-absences.md) | ABS |

The prefixes are the original German abbreviations and stay unchanged, so that old commits, PRs and issues remain traceable.

## Requirement status

Every requirement has a status. It shows how binding it is. Until F-09 the German values were used; they mean the same.

| Status | German (until F-09) | Meaning |
|---|---|---|
| **Decided** | Entschieden | Explicitly set this way by Cyrille. |
| **Accepted** | Angenommen | Proposal from the joint elaboration that was not contradicted. Applies until revoked. |
| **Assumption** | Annahme | Not discussed yet. A sensible assumption so that implementation is not blocked. May be questioned. |
| **Open** | Offen | Must be clarified before or during implementation. |
| **Later** | Später | Deliberately postponed. Do not implement, but do not build the model against it. |
