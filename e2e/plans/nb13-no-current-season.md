# Test plan follow-up fix #13 · No current season (409 from GET /api/me)

Issue #13 · PR #25 · Requirement SAI-03 · Scenario S23 (test-data.md)

General, for every case:

- Setup: `POST /api/test/reset`, then `DELETE /api/test/current-season` (unless stated otherwise),
  then `POST /api/test/login` with `{ "loginKey": "<person>" }` or via `/dev-login`.
- Clock: default, the clock does not matter here.
- Views: mobile 375×812 (all cases), desktop 1280×800 (K2.1, K2.3). Notice screen once in dark mode.
- After each case: check console errors. The only allowed entry is the browser entry «Failed to load resource … 409»
  for `GET /api/me`. No other 4xx/5xx.

## K1 · API contract (criterion 1)

| Case | Steps | Expected |
|---|---|---|
| K1.1 Contract | Read `docs/api/openapi.yaml`, path `/api/me` `get` | Response `'409'` with `#/components/responses/Conflict` |
| K1.2 Lint | `npx @redocly/cli lint docs/api/openapi.yaml` | No errors, no warnings |
| K1.3 Runtime contract | `GET /q/openapi?format=json`, path `/api/me` | Status codes 200, 401, 409 |
| K1.4 Response 409 | Without current season, as `anna` and as `alex` `GET /api/me` | 409, `application/problem+json`, `title` «Keine aktuelle Saison», `rule` «SAI-03» |
| K1.5 Without session | Without current season, without cookie `GET /api/me` | 401 (not 409) |
| K1.6 Test helper | `DELETE /api/test/current-season`, then `POST /api/test/reset` | 204, afterwards «2026/27» is current again, `GET /api/me` 200 |

## K2 · Notice in the UI (criterion 2)

| Case | Steps | Expected |
|---|---|---|
| K2.1 Deep link | As `anna` open `/teams` | Redirect to `/keine-saison?ziel=%2Fteams`, title «Keine aktuelle Saison · STV Bero», heading «Noch keine aktuelle Saison», `no-season-notice` with explanation, buttons «Nochmals versuchen» and «Abmelden». No blank screen, no navigation |
| K2.2 Start page | As `anna` open `/` | `/keine-saison` (without `ziel`) |
| K2.3 Admin | As `alex` open `/verwaltung/saisons` | Also `/keine-saison?ziel=%2Fverwaltung%2Fsaisons` (assumption O-36) |
| K2.4 Coach via test mode | `/dev-login?ziel=%2Fprofil` → «Petra Keller» | `/keine-saison?ziel=%2Fprofil` with notice |
| K2.5 Retry without season | On the notice «Nochmals versuchen» | Stays on the notice, no error message |
| K2.6 Retry after reset | `POST /api/test/reset`, log in again, «Nochmals versuchen» | Back to `/teams` or `/`, content appears |
| K2.7 Log out | «Abmelden» | `/dev-login`, session ended (`GET /api/session` without person) |
| K2.8 Reload | Reload on `/keine-saison?ziel=%2Fteams` | Notice stays, no further errors |
| K2.9 Back button | Press back after the redirect | No blank screen, no endless loop |
| K2.10 Double click | Double click on «Nochmals versuchen» and on «Abmelden» | No error, no duplicate flow with an error message |
| K2.11 Without login | Without session open `/teams` | Login (`/dev-login?ziel=%2Fteams`), not the season notice |
| K2.12 Dark mode | K2.1 with `prefers-color-scheme: dark` | Text and buttons easy to read, nothing white on white |
| K2.13 With season | Seed (current season exists), as `anna` `/` | Normal start page «Hallo Anna», no redirect (regression) |
| K2.14 Direct access | Seed (current season exists), a) without login, b) as `anna`: open `/keine-saison` directly | No wrong notice: a) login (access «angemeldet», navigation.md), b) on to the target or start page, because there is a current season |
| K2.15 Double click log out | Without current season, as `anna` on the notice double click «Abmelden» | Logged out, no 500 from `POST /api/logout` |

## Result round 0 (at `d3ea152`)

- K1.1–K1.6, K2.1–K2.13 passed (mobile, desktop for K2.1/K2.3, dark mode).
- K2.14 failed: notice «Noch keine aktuelle Saison» also appears without login and with a current season.
- K2.15 failed: the second `POST /api/logout` ends with 500 (`OptimisticLockException` in `MeResource.logout`). Same with «Abmelden» in the navigation on `main`, hence a separate follow-up fix.

## Rework round 1

- K2.14 fixed: `keineSaisonGuard` (`frontend/src/app/kern/keine-saison.guard.ts`) on `/keine-saison`. E2E: `nb13-no-current-season.spec.ts`, block «mit aktueller Saison».
- K2.15 stays outside this PR (follow-up fix #26).

## Result round 1 (at `0eb25ed`)

- Automated: Redocly lint without findings, backend 67 green, E2E 74 green (37 mobile, 37 desktop, with axe).
- K1.1–K1.6 passed (409 as problem details for `anna` and `alex`, 401 without session, runtime contract 200/401/409).
- K2.1–K2.14 passed, mobile; K2.1 and K2.3 also desktop; notice easy to read in light (desktop) and dark (mobile).
- K2.14 (finding round 0) fixed: direct access without login → `/dev-login?ziel=%2Fteams`, with season → target or start page.
- Extra: `?ziel=https://…` and `?ziel=//…` lead to `/`, no redirect to outside.
- Console: only the expected message «409» for `GET /api/me`, no further 4xx/5xx.
- K2.15 outside this PR (#26).
