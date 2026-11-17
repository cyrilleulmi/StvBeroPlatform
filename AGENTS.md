# Notes for AI coding agents

This repository is developed with the help of AI agents. This file says where the truth is and how work is done.

## Read first

1. [README.md](README.md) – overview and status legend.
2. [docs/glossary.md](docs/glossary.md) – binding vocabulary. Code, API and documents use these English terms; the user interface uses the German terms.
3. [docs/domain-model.md](docs/domain-model.md) – domain terms and how they relate.
4. [docs/requirements/](docs/requirements/) – the requirements with IDs (e.g. `ANM-03`).
5. [docs/design-principles.md](docs/design-principles.md) – binding design.
6. [docs/technology.md](docs/technology.md) – technology and guard rails.
7. [docs/testability.md](docs/testability.md) – binding rules so that test agents can operate the frontend.
8. [docs/api/openapi.yaml](docs/api/openapi.yaml) – API contract (contract first), conventions in [docs/api/README.md](docs/api/README.md).
9. [docs/states.md](docs/states.md), [docs/ui/navigation.md](docs/ui/navigation.md), [docs/test-data.md](docs/test-data.md) – behavior, screens, seed.

## Status and pipeline

Features are built through the [production pipeline](docs/pipeline/README.md): development → review → test → automatic merge by the test agent. A feature is implemented as soon as Cyrille releases the issue with `status:bereit`. Order and acceptance criteria: [docs/pipeline/backlog.md](docs/pipeline/backlog.md).

The roles are skills under `.claude/skills/` (`/pipeline-entwicklung`, `/pipeline-review`, `/pipeline-test`, `/pipeline-waechter`). Shared rules: [docs/pipeline/agent-rules.md](docs/pipeline/agent-rules.md). What every PR must meet: [docs/pipeline/definition-of-done.md](docs/pipeline/definition-of-done.md).

Cloud sessions have no Docker daemon. Database: `scripts/db-start.sh`.

## Handling requirements

- The status decides how binding a requirement is (legend in the README).
- **Decided** and **Accepted**: implement as described.
- **Assumption**: implement, but say in the result that it is an assumption.
- **Open**: make a sensible assumption, mention it in the result and record it in [docs/open-points.md](docs/open-points.md).
- **Later**: do not implement. But build the model so that it can be added later without rework.
- Refer to the requirement IDs in commits, tests and pull requests.
- If a requirement changes, first update the document and record the decision in [docs/decisions.md](docs/decisions.md).

## Way of working

- Plans are only worked out together when this is explicitly requested.
- Otherwise: make sensible assumptions and implement completely, without questions or intermediate stops. No small intermediate steps.
- List the assumptions made briefly with the result.
- Every result runs and can be opened directly (URL or start command), is checked on desktop and mobile, and has seed data to try it out.
- Tests for backend logic.

## Language

- The repository is English (NF-02, NF-40): code, API, database, documents, skills, scripts, commit messages, PR descriptions and agent comments. Use the terms from [docs/glossary.md](docs/glossary.md).
- The user interface is German (Switzerland, i.e. «ss» instead of «ß»), plain language, no jargon. UI text quoted in documents stays German in «…».
- Exceptions that stay German are listed in NF-40 ([docs/requirements/10-non-functional.md](docs/requirements/10-non-functional.md)): UI text, browser routes, IDs, GitHub labels, skill names, `scripts/pipeline.sh` subcommands, comment markers, the `Voraussetzung:` keyword, seed keys and names.
- The switch to English is complete since F-11 (E-68, E-75): backend, database, API, frontend, E2E tests, test IDs, scripts and CI use English identifiers. Only the exceptions of NF-40 stay German.
- Requirements say "player". Which German form the UI shows («Spielerin», «Spieler», …) is still open (see [docs/open-points.md](docs/open-points.md)).
