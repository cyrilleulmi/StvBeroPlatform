# Test plan follow-up fix #40 · Match list in F-05 tests waits for loaded matches

Issue #40 · PR #41 · File `e2e/tests/f05-spieltage.spec.ts` · Requirements AUF-10, AUF-12, AUF-14, AUF-16, AUF-24 (tests only)

General:

- The criteria concern the test file itself, no UI and no API.
- Starting point for all browser cases: database running (`scripts/db-start.sh`), backend profile `test`, frontend on port 4200,
  `POST /api/test/reset`, default "now" Mon 16.11.2026 06:00.

## K1 · All checks of the match list wait automatically (criterion 1)

| Case | Starting point · steps | Expected |
|---|---|---|
| K1.1 Static | `grep -n "spiele(page)" e2e/tests/f05-spieltage.spec.ts` | Every call is inside `await expect.poll(() => spiele(page))`, no `expect(await spiele(page))` anymore |
| K1.2 No fixed-time waits | `grep -n waitForTimeout e2e/tests/f05-spieltage.spec.ts` | No match |
| K1.3 Counter-check delayed response | Locally (do not commit) in the test «F-05 K2», before saving, insert `page.route('**/api/termine/*', …)` with a 2 s delay for GET, run the test | Test green in `mobil` and `desktop` (the check waits until both matches are there) |
| K1.4 Counter-check old state | As K1.3, but line 109 back to `expect(await spiele(page)).toEqual(…)` | Test red with `Received []` – proves that K1.3 triggers the original failure |
| K1.5 Clean up | Discard the changes from K1.3/K1.4 | `git status` clean |

## K2 · Repeated run passes (criterion 2)

| Case | Starting point · steps | Expected |
|---|---|---|
| K2.1 Repetition | `cd e2e && npx playwright test tests/f05-spieltage.spec.ts --repeat-each 20 --trace off` | 560 passed (14 tests × 20 × 2 projects), 0 failed |

## Regression

| Case | Starting point · steps | Expected |
|---|---|---|
| R1 Backend | `cd backend && mvn test` | All green |
| R2 E2E overall | `cd e2e && npx playwright test --trace off` (both projects, incl. axe) | All green, only the known project skips |
| R3 Match day in the browser | Reset → as `petra` via `/dev-login` → `/termine/40000000-0000-4000-8000-000000000004`, mobile 375×812 and desktop 1280×800 | List «Spiele» shows «09:30 Team 1 – UHC Musterdorf» before «11:45 Unihockey Beispielstadt – Team 1»; no console errors |
