# Test plan · F-15 Attendance statistics (STA-01 to STA-11)

Screens `/statistik`, `/statistik/:personId`, `/profil/statistik`; API `GET /api/statistics`, `GET /api/statistics/persons/{personId}` (`docs/requirements/12-statistics.md`, `docs/ui/navigation.md`, scenarios S44–S47 in `docs/test-data.md`).
Tests: `../tests/f15-statistics.spec.ts`, backend `StatisticsTest`, `StatisticsRulesTest`.

General for every case: `POST /api/test/reset`, clock as seeded (17.11.2026) unless stated, sign in via `POST /api/test/login`. Mobile (375×812) for every case, desktop (1280×800) for T1–T8. After every case: no console errors, no unexpected 4xx/5xx.

| Case | Start (person, clock) | Steps | Expected |
|---|---|---|---|
| T1 Reachable (STA-01) | `petra` | `/termine` → link «Statistik» (`statistics-link`); `/teams/<Team 1>` → «Statistik» | Both open `/statistik` (from the team page with `?team=<Team 1>`), `h1` «Statistik», subtitle «Team 1 · Saison 2026/27» |
| T2 Reachable for other coach and admin (STA-01, E-69) | `marc`, `alex` | `/teams/<Team 1>` → «Statistik»; `/statistik` | Statistics of Team 1 visible (marc coaches only Team 2) |
| T3 Default and filters in the address (STA-02) | `petra` | `/statistik`; set «Von» 01.11.2026, «Bis» 15.11.2026; «Art» «Spieltage»; «Team» Team 2; reload; «Ganze Saison» | Defaults: Team 1, whole season; every filter appears in the URL (`von`, `bis`, `art=spieltag`, `team`), survives reload, «Ganze Saison» removes `von`/`bis` |
| T4 Seed numbers Anna (STA-03, STA-04, STA-09, S44) | `petra` | `/statistik` row `statistics-person-<Anna>`; base `statistics-base` | Anna 26 · 2 · 1 · 0 · 23 · 8 % · 1 · 1 · 0 · 0 · 100 %; «26 Termine im Zeitraum. Die Präsenzquote beruht auf 1 Termin mit erfasster Präsenz.» Period 01.11.–15.11.: 4 · 2 · 1 · 0 · 1 · 50 % · 1. «Spieltage»: 0 events, rates «–» |
| T5 Groups, sorting, average (STA-05) | `petra` | Inspect group cards; «Sortieren nach» «Zusagequote», «Präsenzquote», «Name» | Cards Linie 1, Linie 2, Goalies, «Ohne Linie» (Kim); order changes within groups (highest first, without rate last), `?sortierung=` in URL; card «Teamschnitt» with one row, counts with one decimal |
| T6 Events of a player (STA-06, S45) | `petra` | Click «Anna Muster» | `/statistik/<Anna>` with filters kept, 26 events newest first: Do 12.11. «dabei», Di 10.11. «nicht dabei» + «kurzfristig abgemeldet», Do 05.11. «dabei» + «Präsenz: anwesend», then «offen»; «Zur Statistik» keeps filters |
| T7 What counts (STA-07, S46) | `petra`; clock 25.11.2026 07:00 | `/statistik/<Kim>`; set clock, reload | Kim 13 events, «Im Team seit 01.10.2026.»; on 25.11. 29 events for Anna-type full members (17.11., 19.11., match day 22.11. added, cancelled 24.11. left out) |
| T8 Own numbers only (STA-08, S47) | `anna` | `/profil` → «Statistik ansehen»; `/statistik`; `/termine`; `GET /api/statistics/persons/<Mia>`; `GET /api/statistics?team=<Team 1>`; `/statistik/<Mia>` | «Meine Statistik» with the same numbers as T4; `/statistik` «Keine Berechtigung», no `statistics-link` in `/termine`; API 403 for both; screen «Keine Berechtigung» |
| T9 Player in two teams (STA-08) | `lea` | `/profil/statistik`, switch «Team» | Only Team 1 and Team 2 offered, numbers per team |
| T10 No team as player | `alex` | `/profil` and `/profil/statistik` | No link «Statistik ansehen»; page says «Du spielst in dieser Saison in keinem Team.» |
| T11 Invalid period (states.md) | `petra` | API `from` after `to`; `from` 2026-06-01 `to` 2026-08-01; UI «Von» after «Bis» | 400 with field error, UI shows a German message, no 500 |
| T12 Unknown person | `petra` | `/statistik/<random uuid>`, API same | 404 / «nicht gefunden»-style message, no crash; as `anna` 403 (not 404) |
| T13 Mobile layout (STA-10) | `petra` | 375px, `document.scrollingElement.scrollWidth` | No horizontal page scroll; table scrolls inside its card, name column stays visible |
| T14 Dark mode | `petra`, `anna` | «GitHub Dark» on `/statistik`, `/statistik/<Anna>`, `/profil/statistik` | Readable, no white on white, sticky name column has a background |
| T15 Robustness | `petra` | Back button after opening a player, reload on `/statistik/<Anna>?von=…`, rapid filter changes | Filters restored, no stale or mixed results, no errors |
