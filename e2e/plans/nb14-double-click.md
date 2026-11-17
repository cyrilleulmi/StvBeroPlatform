# Test plan follow-up fix #14 · Lock double clicks (write actions)

Issue #14 · PR #27 · Rule "Forms" in `docs/states.md`, state "Action running" in `docs/testability.md`

General, for every case:

- Setup: `POST /api/test/reset`, then log in via `/dev-login` or `POST /api/test/login`
  with `{ "loginKey": "<person>" }`. Clock: default, does not matter.
- To make sure the second click lands in the running request, the response of the checked
  request is held back (Playwright `page.route`, released after the checks). Requests are counted
  by method and path.
- "Locked" means: button `disabled`. "Running" means: additionally `aria-busy="true"`.
  After the response `aria-busy` is removed again.
- Views: mobile 375×812 (all cases), desktop 1280×800 (K1.1, K2.1, K2.4). `/dev-login`
  once in dark mode while a login is running.
- After each case: check console errors and unexpected 4xx/5xx.

## K1 · Logging in at `/dev-login` (criterion 1)

| Case | Starting point · steps | Expected |
|---|---|---|
| K1.1 Double click | Without session `/dev-login`, hold back the response of `POST /api/test/login`, double click «Anna Muster» | «Anna Muster» `disabled` + `aria-busy="true"`; «Petra Keller» `disabled`, without `aria-busy`. After release: `/`, «Hallo Anna». Exactly 1 `POST /api/test/login` |
| K1.2 Session | After K1.1 `GET /api/session` | One logged-in person: Anna |
| K1.3 Without throttling | Double click «Petra Keller» at normal speed | Exactly 1 login, start page «Hallo Petra», no console errors |
| K1.4 Error | Answer `POST /api/test/login` with 500, click «Anna Muster» | Error message visible, then all people usable again, no `aria-busy`. A second attempt without error logs in |
| K1.5 With target | `/dev-login?ziel=%2Fprofil`, double click «Alex Admin» | 1 login, lands on `/profil` |
| K1.6 Back button | After K1.1 press back | No crash, no second login |
| K1.7 Dark mode | K1.1 with `prefers-color-scheme: dark`, screenshot while the login is running | Locked people dimmed but readable; nothing white on white |

## K2 · Other write actions (criterion 2)

| Case | Starting point · steps | Expected |
|---|---|---|
| K2.1 Log out profile | As `anna` `/profil`, hold back `POST /api/logout`, double click «Abmelden» | Button `disabled` + `aria-busy`. Then `/dev-login`, exactly 1 `POST /api/logout`, no 500 |
| K2.2 Log out without season | `DELETE /api/test/current-season`, as `anna` `/teams` → `/keine-saison`, double click «Abmelden» | As K2.1. «Nochmals versuchen» stays usable (not a write action) |
| K2.3 Rename | As `alex` `/verwaltung/teams/10000000-0000-4000-8000-000000000001`, change the name, hold back `PUT /api/teams/…`, double click «Umbenennen» | «Umbenennen» `disabled` + `aria-busy`; «Aufnehmen» and all «… entfernen» `disabled` without `aria-busy`. Exactly 1 `PUT`, name changed after reload |
| K2.4 Remove | As K2.3, double click «Anna Muster entfernen» (hold back `DELETE …/members/…`) | Only this button `aria-busy`, others `disabled`. Exactly 1 `DELETE`, no 404/409 from a second request, Anna no longer in the team |
| K2.5 Add | As K2.3, choose a person, double click «Aufnehmen» | Only «Aufnehmen» `aria-busy`. Exactly 1 `POST …/members`, no 409 PTF-04 from a second request |
| K2.6 Change team role | As K2.3, change the team role of a person, hold back the request | Select fields and buttons `disabled`, no button `aria-busy`. 1 request |
| K2.7 Create season | As `alex` `/verwaltung/saisons`, fill in 2027/28, double click «Saison anlegen» | Only «Saison anlegen» `aria-busy`. Exactly 1 `POST /api/seasons`, no 409 from a duplicate |
| K2.8 Activate season | As K2.7, then double click «Zur aktuellen Saison machen» | Only this button `aria-busy`, «Saison anlegen» `disabled` without `aria-busy`. 1 `POST …/activate` |
| K2.9 Create team | As `alex` `/verwaltung/teams`, enter a name, double click «anlegen» | 1 `POST /api/teams`, button `aria-busy` |
| K2.10 Save person | As `alex` `/verwaltung/personen/neu`, fill in the fields, double click «Speichern» | 1 `POST /api/persons`, button `aria-busy`, no duplicate person in the list |
| K2.11 Error | Answer an administration action with 500 | Error message, all buttons usable again, no `aria-busy` |
| K2.12 Roles | `anna` opens `/verwaltung/teams/…` | No access (regression, unchanged from F-01) |

## Regression

| Case | Steps | Expected |
|---|---|---|
| R1 Normal use | Single click on all actions above without throttling | Works as before, buttons usable again afterwards |
| R2 Automated | `e2e/tests/nb14-doppelklick.spec.ts` and all other E2E tests | Green, mobile and desktop, axe without findings |

## Result round 1 (at `b5962d6`)

- Automated: backend 67 ✅, E2E 90 ✅ (mobile + desktop, axe), API lint and frontend build ✅.
- K1.1–K1.7 passed (mobile, desktop for K1.1, dark mode). Exactly 1 `POST /api/test/login` each.
- K2.1–K2.12 passed: exactly 1 request each, `aria-busy` only on the clicked button,
  others `disabled` without `aria-busy`, usable again after an error. Desktop for K2.1 and K2.4.
- Console: only expected entries (simulated 500, 409 from `GET /api/me` without season).
