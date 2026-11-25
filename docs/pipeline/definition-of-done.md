# Definition of Done

A PR is done when all items are met. The development agent ticks them in the PR description, the review agent checks them, the test agent checks the behavior.

## Domain

- [ ] Every acceptance criterion of the issue is implemented and has at least one automated test.
- [ ] The affected requirement IDs are named in the PR.
- [ ] Assumptions made are listed in the PR under «Assumptions». New open questions are in [open-points.md](../open-points.md).
- [ ] No requirement with status «Decided» was changed or bypassed.
- [ ] Terms from the [domain model](../domain-model.md) in code, API and UI.

## Backend

- [ ] The API matches [openapi.yaml](../api/openapi.yaml). Changes to the contract are in the same PR, `redocly lint` runs without errors and warnings.
- [ ] Permissions are checked in the backend (RR-07), with a test for the forbidden case (403).
- [ ] Domain rules live in the domain logic, not in the REST layer, and have unit tests.
- [ ] The module boundaries hold ([architecture.md](../architecture.md)): the architecture tests are green. A new dependency between modules is in [modules.json](../../modules.json) and named in the PR.
- [ ] Schema changes as a new Flyway migration. Existing migrations are unchanged (one-time exception: F-10 rewrote them in English, E-74).
- [ ] Test endpoints only in the profiles `dev`/`test`.
- [ ] «Now» comes from the injected clock, never from `LocalDateTime.now()` directly.

## Frontend

- [ ] Rules from [testability.md](../testability.md) followed: semantics, names, states, test IDs, URLs.
- [ ] New routes and fixed test IDs added to [navigation.md](../ui/navigation.md).
- [ ] Design according to [design-principles.md](../design-principles.md): only tokens, no hard-coded colors. Light and dark mode checked.
- [ ] Mobile (375px) and desktop (1280px) checked, touch targets at least 44px.
- [ ] No errors in the browser console.
- [ ] Logic in the frontend (formatting, dates, stores, pure functions) has unit tests (`npm test`). Lint is green, including the module boundaries and size limits.

## Tests and data

- [ ] Every behaviour is tested at the lowest level that can show it ([architecture.md](../architecture.md#tests)): rules as unit tests, endpoints as REST tests, E2E tests for what only the browser shows.
- [ ] `scripts/test.sh` green: backend tests with architecture tests and coverage gate, frontend lint, unit tests and build, E2E tests (every test on `mobile`, the tests tagged `@desktop` on `desktop`), including an axe check without critical findings.
- [ ] New E2E specs are listed in [modules.json](../../modules.json); tests that depend on the desktop layout are tagged `@desktop`.
- [ ] No frozen list was extended and no minimum lowered (class size, file size, complexity, coverage).
- [ ] Seed data covers the feature. Changes to the seed are updated in [test-data.md](../test-data.md), new scenarios added there.
- [ ] Changed behavior is updated in [states.md](../states.md).

## Runnability

- [ ] The app starts with the documented start command from a fresh clone.
- [ ] The PR description says how to try out the feature (start command, URL, test person).
