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
- [ ] Schema changes as a new Flyway migration. Existing migrations are unchanged (one-time exception: F-10 rewrote them in English, E-74).
- [ ] Test endpoints only in the profiles `dev`/`test`.
- [ ] «Now» comes from the injected clock, never from `LocalDateTime.now()` directly.

## Frontend

- [ ] Rules from [testability.md](../testability.md) followed: semantics, names, states, test IDs, URLs.
- [ ] New routes and fixed test IDs added to [navigation.md](../ui/navigation.md).
- [ ] Design according to [design-principles.md](../design-principles.md): only tokens, no hard-coded colors. Light and dark mode checked.
- [ ] Mobile (375px) and desktop (1280px) checked, touch targets at least 44px.
- [ ] No errors in the browser console.

## Tests and data

- [ ] Backend tests green.
- [ ] E2E tests (`e2e/`) green in the projects `mobile` and `desktop`, including an axe check without critical findings.
- [ ] Seed data covers the feature. Changes to the seed are updated in [test-data.md](../test-data.md), new scenarios added there.
- [ ] Changed behavior is updated in [states.md](../states.md).

## Runnability

- [ ] The app starts with the documented start command from a fresh clone.
- [ ] The PR description says how to try out the feature (start command, URL, test person).
