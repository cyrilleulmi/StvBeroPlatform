# Test plan follow-up fix #26 · Logging out is idempotent

Issue #26 · PR #60 · Rule "Logging out" in `docs/states.md` (F-00)

General, for every case:

- Setup: `POST /api/test/reset`, clear cookies, log in via `/dev-login` (click on the person)
  or `POST /api/test/login` with `{ "loginKey": "<person>" }`. Clock: default, does not matter.
- "Without season": additionally `DELETE /api/test/current-season`, then `/teams` → redirects to `/keine-saison`.
- Counted are all responses of `POST /api/logout` (status) and all responses ≥ 500.
- After each case: check console errors and `pageerror`. The only allowed entry is the browser entry
  «Failed to load resource … 409» from `GET /api/me` without a current season (follow-up fix #13).
- Views: mobile 375×812 (all cases), desktop 1280×800 (K2.1–K2.6). Dark mode once on mobile (K2.7).

## K1 · `POST /api/logout` is idempotent (criterion 1)

| Case | Starting point · steps | Expected |
|---|---|---|
| K1.1 One after the other | Log in as `anna`, `POST /api/logout` twice with the same cookie | Both 204, both `Set-Cookie: stv_session=;…Max-Age=0`. Then `GET /api/me` → 401 |
| K1.2 At the same time | Log in as `anna`, 20 simultaneous `POST /api/logout` with the same cookie | All 204, no 500, no `OptimisticLockException` in the backend log. Then `GET /api/me` → 401 |
| K1.3 Own session only | Two sessions `anna` (A, B) and one `petra`; log out twice with A | `GET /api/me`: A → 401, B → 200, petra → 200 |
| K1.4 Invalid cookie | `POST /api/logout` with an empty cookie, with a 3000-character token, with `x' OR '1'='1` | 204 each. Other sessions stay valid |

## K2 · Logging out in the UI (criterion 2)

| Case | Starting point · steps | Expected |
|---|---|---|
| K2.1 Profile double click | As `anna` `/profil`, double click «Abmelden» | `/dev-login`, exactly 1 `POST /api/logout` (204), no 5xx, no console errors |
| K2.2 Profile twice quickly | As K2.1, click and immediately a second click (forced) | As K2.1 |
| K2.3 Profile two tabs | As `anna` `/profil` in two tabs, «Abmelden» in both at the same time | Both tabs `/dev-login`, 2 × 204, no 5xx, no console errors in either tab |
| K2.4 Without season double click | Without season, as `anna` `/teams` → `/keine-saison`, double click «Abmelden» | As K2.1 (409 from `/api/me` allowed) |
| K2.5 Without season quickly | As K2.4 with two quick clicks | As K2.4 |
| K2.6 Without season two tabs | As K2.3, on `/keine-saison` | As K2.3 (409 from `/api/me` allowed) |
| K2.7 Dark mode | K2.1 and K2.4 with `prefers-color-scheme: dark`, screenshot after logging out | `/dev-login` readable, nothing white on white |
| K2.8 Reload, back | After K2.1–K2.6 reload, then back button | Stays on or lands on `/dev-login`, `GET /api/session` → `signedIn: false` |
| K2.9 Roles | K2.1 as `alex` and `petra`, K2.4 as `alex` | As K2.1 (logging out is the same for everyone, public endpoint) |

## Result (round 0, PR #60, at 101e672)

- Automated: backend 267 ✅ (incl. `AuthTest` simultaneous/twice), E2E 286 ✅ (6 skipped as before).
- K1.1–K1.4 passed: 20 × 204 simultaneously, then 401; only the own session deleted; invalid cookies 204.
- K2.1–K2.9 passed on mobile and desktop: always `/dev-login`, no 5xx, no console errors except the allowed 409 from `/api/me`.
