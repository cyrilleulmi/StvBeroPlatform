# Test plan F-00 · Foundation

Issue #1, PR #11. Requirements: NF-01–NF-24, RR-05.

**General starting point:** `POST /api/test/reset` (seed F-00: seasons, people 01–17 with account), default clock (Mon 16.11.2026 06:00). App: frontend `http://localhost:4200` (development build), backend `http://localhost:8080`.
Viewports: mobile 375×812, desktop 1280×800. After each case: console without errors, no unexpected 4xx/5xx.

## K1 · `scripts/start.sh`

1. Run `scripts/start.sh` from the repo state (DB, backend profile `dev`, frontend).
2. Expected: message «Bereit.», `http://localhost:4200` answers 200, `GET http://localhost:4200/api/session` (proxy) answers 200 with `signedIn: false`.

## K2 · `scripts/test.sh`

1. Run `scripts/test.sh`.
2. Expected: API lint without findings, backend tests green, frontend lint and build green, E2E mobile + desktop (with axe) green, exit code 0.

## K3 · `/dev-login`

Starting point: reset, not signed in.

1. Open `/dev-login`.
2. Expected: all 17 seed people are visible (test ID `dev-login-person-<schluessel>`, e.g. `dev-login-person-petra`), with name. Banner `test-mode-notice` visible.
3. Click «Anna Muster».
4. Expected: URL `/`, greeting with «Anna». Still signed in after reload.
5. Edge case: `POST /api/test/login {loginKey: "gibtsnicht"}` → 400 or 404 (Problem Details), no session. Empty body → 400.

## K4 · Start page and tiles

Starting point: reset, signed in as `anna`.

1. Open `/`.
2. Expected: heading with «Anna». Tiles match `GET /api/tiles`; tile `tile-anwesenheit` visible.
3. Tap the tile «Anwesenheit».
4. Expected: URL `/termine`, empty state with text (no blank screen).
5. Role: as `alex` (admin), compare `GET /api/tiles` and the start page.

## K5 · Navigation (RR-05)

1. Mobile as `anna`: `nav` «Hauptnavigation» at the bottom, entries «Start», «Termine», «Teams», «Profil» (no «Verwaltung»). Active entry `aria-current="page"`, changes when navigating.
2. Desktop as `anna`: navigation on the left.
3. As `alex`: additionally «Verwaltung», `/verwaltung` shows an empty state.
4. As `anna`: `/verwaltung` via deep link → «Keine Berechtigung» (`role="alert"`).
5. Edge case 1023px vs. 1024px: navigation switches from bottom to left.

## K6 · Design tokens and theme

1. As `anna` open `/profil`, switcher `theme-switch` with «Hell»/«Dunkel»/«System» (since E-90; E-67 to E-89 «Hell»/«Düne»/«GitHub Dark»/«System»).
2. Choose «Dunkel» → `<html data-theme="dark">`, screen dark; reload → stays dark.
3. «Hell» → light, stays after reload. «System» with `prefers-color-scheme: dark` → dark (Dunkel).
4. Every screen (`/dev-login`, `/`, `/termine`, `/teams`, `/profil`, `/verwaltung`, not found) in dark mode: readable, nothing white on white.
5. CSS custom properties from design-principles.md present on `:root` (e.g. `--primary`).

## K7 · PWA

1. `/manifest.webmanifest` delivers a valid manifest (name, icons 192/512, start_url, display standalone).
2. Service worker `sw.js` is registered (`navigator.serviceWorker.getRegistration()`).

## K8 · Test endpoints

1. Profile dev: `POST /api/test/reset` 204, `PUT /api/test/clock` 204, `POST /api/test/login` 204 with cookie.
2. Profile prod: automated test `ProdWithoutTestSupportTest` (404, not in OpenAPI).

## K9 · OpenAPI comparison

`OpenApiContractTest` runs green in the backend tests.

## K10 · Clock

1. `PUT /api/test/clock` to 2027-01-05T10:00 → `GET /api/me` still delivers consistent data; backend test `settingClockChangesNowInWholeBackend` green.
2. Invalid clock (`{}` or text) → 400.

## K11 · CI

Checks on the PR: workflow `ci.yml` green.

## K12 · Redirect back

Starting point: reset, not signed in.

1. Open `/profil` → redirect to `/dev-login?ziel=%2Fprofil`.
2. Sign in as `petra` → URL `/profil`.
3. Same for `/termine`, `/teams`, `/verwaltung` (as `alex`), unknown route.
4. Edge case open redirect: `/dev-login?ziel=https://example.com` or `?ziel=//example.com` → `/` after sign-in.
5. Sign out in the profile → `/anmelden` or `/dev-login`; the back button shows no protected content.

## Robustness

- Double click on a person in `/dev-login`: exactly one sign-in, no errors.
- Reload on `/profil` after a theme change.
- Back button after sign-in.
- Unknown route while signed in: «Diese Seite gibt es nicht.» with link «Zur Startseite».
