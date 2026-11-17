# Test plan follow-up fix #17 · CSS selectors in E2E tests

Issue #17 · PR #32 · Conventions for E2E tests in `docs/testability.md`

General:

- The criteria concern the test files themselves, no UI and no API.
  The static cases need neither a database nor a login.
- The browser cases (K3) check that the places tested via the helpers are still correct:
  setup `POST /api/test/reset`, login as `anna` via `/dev-login`, mobile 375×812.

## K1 · Exception documented and encapsulated (criterion 1)

| Case | Starting point · steps | Expected |
|---|---|---|
| K1.1 Docs | Read `docs/testability.md`, section "Conventions for E2E tests" | Names as the only exception document elements without a role (`<html>`, `<link>` in the head) and refers to `documentRoot(page)` and `headLink(page, rel)` in `e2e/tests/helpers.ts` |
| K1.2 Helpers exist | `e2e/tests/helpers.ts` | Exports `documentRoot(page)` and `headLink(page, rel)`; the CSS selectors are only there |
| K1.3 Automated | `npx playwright test nb17` | «AK1» green in `mobile` and `desktop` |

## K2 · No CSS selectors outside the exception (criterion 2)

| Case | Starting point · steps | Expected |
|---|---|---|
| K2.1 Current state | `npx playwright test nb17` | «AK2» green; outside `helpers.ts` no match for `locator(`, `$(`, `$$(`, `$eval(`, `waitForSelector(`, `css=`, `xpath=` |
| K2.2 Counter-check `locator` | Append the line `page.locator("html")` in `seed.spec.ts`, run AK2 | AK2 red, message names `seed.spec.ts:<line>: locator() with CSS selector` |
| K2.3 Counter-check chained | As K2.2 with `getByRole('x').locator('y')` | AK2 red, `locator()` |
| K2.4 Counter-check `$eval`, `$$` | As K2.2 with `page.$eval('div', e => e)` and `page.$$('a')` | AK2 red, one match `$()/$$()/$eval()` each |
| K2.5 Counter-check `waitForSelector` | As K2.2 with `page.waitForSelector('x')` | AK2 red, `waitForSelector()` |
| K2.6 Counter-check `xpath=` | As K2.2 with `page.getByText('xpath=//div')` | AK2 red, «CSS- oder XPath-Selektor» |
| K2.7 Clean up | Remove the counter-check lines | `git status` clean, AK2 green again |

## K3 · Converted tests still check the same (regression F-00)

| Case | Starting point · steps | Expected |
|---|---|---|
| K3.1 Dark mode | Reset → as `anna` → `/profil` → radio «Dunkel» | `<html data-theme="dark">`, dark background; F-00 test «Design» green |
| K3.2 Manifest | As `anna` on `/` | `<link rel="manifest">` in the head with `href="manifest.webmanifest"`; F-00 test «PWA» green |
| K3.3 Console | After K3.1/K3.2 | No errors or warnings |
| K3.4 Full run | `npx playwright test` (both projects, incl. axe) | All green |
