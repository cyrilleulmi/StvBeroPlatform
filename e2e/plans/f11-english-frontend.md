# Test plan · F-11 English: frontend, E2E tests and status page (NF-40, E-68, E-75)

Issue #50. Pure rename: the app must look and behave exactly as before; the UI and the browser addresses stay German.
Starting point for every UI case: `scripts/db-start.sh`, backend (dev or test profile) on :8080, frontend of the PR on :4200, `POST /api/test/reset`, clock at the seed default (16.11.2026 06:00), log in via `POST /api/test/login {loginKey}` or `/dev-login`.

## Repository checks

| Case | Criterion | Steps | Expected |
|---|---|---|---|
| K1 No German identifiers | 1 | Strip string/template literals and HTML text, then grep `frontend/src`, `e2e/`, `status/index.html` for German stems (`termin`, `antwort`, `aufgebot`, `spieltag`, `linie`, `saison`, `verwaltung`, `benachricht`, `kachel`, `zaehler`, `seite`, …); list folders under `frontend/src/app` | Only the documented exceptions (routes, query parameters, UI text, API values such as `tile-anwesenheit`, old status-page storage keys read for migration) |
| K2 Lint and build | 3 | `npm run lint`, `npm run build` in `frontend/` | «All files pass linting.», build without warnings |
| K3 Automated tests | 4, 6 | `scripts/test.sh` (API lint, `mvn verify`, frontend, E2E `mobile` + `desktop` with axe); CI check «Build und Tests» on the PR | All green; skips unchanged (6); no test removed compared to `main` |
| K4 Docs | 7 | Read `AGENTS.md`, `docs/glossary.md`, `docs/decisions.md` | Switch stated complete; E-68 done; E-75 recorded |

## Visual comparison `main` vs. PR (criterion 2)

Run the `main` frontend on :4300 and the PR frontend on :4200 against the same backend. For each case, a fresh browser context with the stored theme set via `localStorage` `stv-theme` (`main`: `hell`/`duene`/`github-dark`; PR: `light`/`dune`/`github-dark`), log in as the person, open the deep link, wait for network idle. Compare the accessibility tree of `body` (must be identical) and a full-page screenshot (identical or visually equal); record console errors.

Matrix: each route below × themes «Hell», «Düne», «GitHub Dark» × mobile (375×812) and desktop (1280×800).

| Person | Routes |
|---|---|
| none | `/anmelden`, `/dev-login` |
| `nina` | `/`, `/termine`, `/termine/<event 01>`, `/benachrichtigungen` |
| `petra` | `/`, `/termine`, `/termine?ansicht=vergangen`, `/termine/<event 01>`, `/termine/<event 03>` (cancelled), `/termine/<event 04>` (match day), `/termine/neu`, `/termine/neu?art=serie`, `/termine/<event 01>/bearbeiten`, `/termine/<event 04>/bearbeiten`, `/termine/<event 04>/aufgebot`, `/serien/<series 01>`, `/serien/<series 01>/bearbeiten`, `/teams`, `/teams/<team 1>`, `/teams/<team 1>/linien`, `/profil`, `/gibt-es-nicht` |
| `alex` | `/`, `/verwaltung`, `/verwaltung/personen`, `/verwaltung/personen?suche=mei`, `/verwaltung/personen/neu`, `/verwaltung/personen/<person 02>`, `/verwaltung/teams`, `/verwaltung/teams/<team 1>`, `/verwaltung/saisons` |
| `anna` | `/termine/<event 04>/aufgebot` (no permission), `/verwaltung` (no permission) |
| `marc` | `/teams/<team 1>` (no permission) |

## Exploratory cases (Playwright MCP)

Every case on mobile (375×812), central ones also on desktop (1280×800). After every case: no console errors, no unexpected 4xx/5xx.

| Case | Criterion | Person | Steps | Expected |
|---|---|---|---|---|
| U1 Old theme value | 5 | `nina` | Before load set `localStorage` `stv-theme` = `hell`; open `/profil`; repeat with `duene` and `dunkel` | App loads, «System» selected in `theme-switch`, no console errors |
| U2 Theme switch stores English values | 2, 5 | `nina` | `/profil`, choose «Düne», reload; choose «GitHub Dark», reload | Theme kept after reload; `localStorage` `stv-theme` = `dune` / `github-dark`; `html[data-theme]` = `dune` / `github-dark` |
| U3 Respond with renamed test IDs | 2 | `nina` | `/termine` → `event-card-<event 01>` → `response-attending`; reload | `my-response` has `data-response="ATTENDING"`, team counter `data-counter="attending"` +1, survives reload |
| U4 Maybe without comment | 2 | `nina` | Event 01 → «vielleicht», leave comment empty, save | Blocked with error at the field, nothing saved |
| U5 Call-up | 2 | `petra` / `anna` | `/termine/<event 04>/aufgebot`, call up a player, «Veröffentlichen» → confirm; then as `anna` | «<Name> ist aufgeboten.», `call-up-status` published; `anna`: «Keine Berechtigung» |
| U6 Lines | 2 | `petra` / `anna` | `/teams/<team 1>/linien`, move a player with «Linie von <Name>»; as `anna` | Saved immediately; `anna` read-only |
| U7 Notifications | 2 | `nina` | `/benachrichtigungen`, «Alle als gelesen markieren» | `notifications-counter` disappears, «Alle sind als gelesen markiert.» |
| U8 Query parameters stay German | 2 | `petra` / `alex` | `/termine?ansicht=vergangen`, `/termine/neu?art=serie`, `/verwaltung/personen?suche=mei`, `/anmelden?ziel=/teams` (logged out → `/dev-login` → login) | Same view as before; login lands on `/teams` |
| U9 Dark mode | 2 | `petra` | Events list, event detail, call-up in «GitHub Dark» | Readable, no white on white |
| U10 Status page | 1 | – | Open `status/index.html` from the PR in the browser | Renders with German text; no console errors other than network access to GitHub |
