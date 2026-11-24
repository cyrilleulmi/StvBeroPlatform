Closes #

## What

<!-- One or two sentences: what can you do after this PR? -->

**Feature:** F-xx · **Requirements:** 

## Acceptance criteria

| No. | Criterion | Implementation | Test |
|---|---|---|---|
| 1 | | | `e2e/tests/…` |

## Assumptions

<!-- What was not clear and how it was decided. New open questions also in docs/open-points.md. -->

- 

## Try it out

```bash
scripts/start.sh
```

http://localhost:4200/dev-login → log in as `…` → …

## Definition of Done

<!-- docs/pipeline/definition-of-done.md – tick or explain -->

- [ ] Every acceptance criterion implemented and tested automatically
- [ ] API matches docs/api/openapi.yaml, lint without errors and warnings
- [ ] Permissions checked in the backend, with 403 tests
- [ ] Testability according to docs/testability.md, navigation.md updated
- [ ] Design tokens, light and dark checked, mobile and desktop checked
- [ ] `scripts/test.sh` green (backend with architecture tests and coverage, frontend lint and unit tests, E2E with axe)
- [ ] Tested at the lowest level that shows it; module boundaries hold, no frozen list extended (docs/architecture.md)
- [ ] test-data.md and states.md updated
- [ ] Starts from a fresh clone
