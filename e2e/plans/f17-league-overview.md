# Test plan F-17 · Team overview with data from swiss unihockey

Issue #56, PR #71. Requirements SUH-01, SUH-02, SUH-03, SUH-09 (SUH extended), RR-07; assumptions O-59, O-60. Seed «League of Team 1» and scenarios S52–S55 from [test-data.md](../../docs/test-data.md), rules «League data» in [states.md](../../docs/states.md). Default clock: Mon 16.11.2026 06:00.

Before every case: `POST /api/test/reset`, log in via `POST /api/test/login` or `/dev-login`. Viewports: mobile 375×812 (all cases), desktop 1280×800 (cases 1, 2, 3, 4, 6). After every case: no console errors, no unexpected 4xx/5xx.

IDs: Team 1 `10000000-0000-4000-8000-000000000001`, Team 2 `…0002`, event 04 `40000000-0000-4000-8000-000000000004`. Team 1 is linked with SU team ID `900001`.

## 1. Section «Liga» and hint for teams without a link (criterion 1; S55)

1. As `anna` open `/teams/<Team 1>`.
   - Expect: section `team-league` «Liga» with «Tabelle, kommende Spiele und Resultate von swiss unihockey.» and link «Liga ansehen» (`team-league-link`) to `/teams/<Team 1>/liga`.
2. As `marc` open `/teams/<Team 2>`.
   - Expect: «Dieses Team ist nicht mit swiss unihockey verknüpft. Darum gibt es keine Ligadaten.», no admin link.
3. As `alex` open `/teams/<Team 2>` and `/teams/<Team 2>/liga`.
   - Expect: same hint plus link «In der Verwaltung verknüpfen» to `/verwaltung/teams/<Team 2>`; following it opens the team in the administration.
4. As `marc` deep link `/teams/<Team 2>/liga` → `league-not-linked`, no admin link, no «Neu laden».
5. Archive: as `alex` `/teams/<Team 1>?saison=<2025/26>` → no section «Liga».

## 2. Table (criteria 2 and 11; S52)

1. As `anna` open `/teams/<Team 1>/liga`.
   - Expect: table «Tabelle» with columns «Rang», «Team», «Punkte», «Spiele», «Siege», «Unentschieden», «Niederlagen», «Tore»; eight rows with the values of the seed table.
   - Own row `league-row-2` has `data-own="true"`, chip «Unser Team» (`league-own-team`), bold and a bar (not only colour).
2. At 375px: rank, team and points are visible without scrolling the page; the remaining columns scroll inside the card; the page itself has no horizontal scroll.
3. Desktop 1280px: all columns visible.

## 3. «Kommende Spiele» (criterion 3)

1. As `anna` on the league page.
   - Expect: four entries in this order: Sun 22.11.2026 · 09:30 «Heim gegen UHC Musterdorf», 11:45 «Auswärts bei Unihockey Beispielstadt», Sat 12.12.2026 · 14:00, Sun 10.01.2027 · 10:00; hall and place each.
   - The two matches of 22.11. are links «Zum Spieltag» to `/termine/<event 04>`; the other two are not links.
2. Click the first one → event 04 opens; back button returns to the league page.
3. Clock edge: `PUT /api/test/clock` 22.11.2026 10:40 → match 5000001 still upcoming; 10:50 → gone (75 minutes after start).

## 4. «Letzte Resultate» (criterion 4)

1. As `anna`: five entries, latest first: 15.11. «Team 1 – Unihockey Bergdorf 6:5» «Sieg», 07.11. «UHC Musterdorf – Team 1 6:3» «Niederlage», 25.10. «UHC Waldberg – Team 1 2:5» «Sieg», 17.10. «Team 1 – Floorball Seetal 4:4» «Unentschieden», 04.10. «UH Talmatt – Team 1 3:6» «Sieg». Match of 19.09. not shown. `data-outcome` matches.

## 5. League, group and «Stand» (criterion 5)

1. Heading «Herren Kleinfeld 3. Liga · Gruppe 12», «Stand: Mo 16.11.2026, 04:30», «Die Daten werden jeden Morgen neu geladen.»

## 6. Cache and manual reload (criteria 6 and 7; S53)

1. As `anna` open the league page, then `PUT /api/test/swiss-unihockey/matches` with another `league` for `900001`, reload the page.
   - Expect: still the seed data (the page reads only the cache).
2. As `alex` «Neu laden».
   - Expect: «Die Ligadaten sind neu geladen.» (`role="status"`), new league/group/table, «Stand: Mo 16.11.2026, 06:00»; after reload unchanged.
3. Roles: `petra` (coach) and `anna` see no «Neu laden»; `POST /api/teams/<Team 1>/league/refresh` as `petra` → 403; as `alex` for Team 2 → 409.
4. Robustness: double click on «Neu laden» → one success, no 5xx; button disabled while loading.
5. Contract: `league` in the test endpoint as documented in `openapi.yaml` (`SuLeague`, `SuStanding`).

## 7. swiss unihockey not reachable (criterion 9; S54)

1. Simulator `{"suTeamId":900001,"matches":[],"unavailable":true}`; as `alex` «Neu laden».
   - Expect: error «swiss unihockey ist im Moment nicht erreichbar. …» (`role="alert"`, API 502), table stays, notice `league-stale` «swiss unihockey war am Mo 16.11.2026, 06:00 nicht erreichbar. Angezeigt wird der Stand vom Mo 16.11.2026, 04:30.»; persists after reload, also for `anna`.
   - Rest of the app works: `/`, `/termine`, `/teams/<Team 1>` load normally.
2. Then simulator reachable again, «Neu laden» → notice gone, new «Stand».
3. Never loaded: link Team 2 with an SU team ID in the administration (as `alex`) → league page shows `league-never-loaded` «Es sind noch keine Daten von swiss unihockey geladen.»; with the simulator unavailable and «Neu laden» additionally «Der letzte Versuch am … hat nicht geklappt …».

## 8. Who sees the page (criterion 10)

1. `anna`, `kim` (players), `petra` (coach of Team 1), `marc` (coach of another team, F-12), `alex` (admin) → page visible.
2. `eva` (Team 2 only) → «Keine Berechtigung»; `GET /api/teams/<Team 1>/league` → 403.
3. `vera` (Team 1 only in 2025/26) → «Keine Berechtigung».

## 9. Dark mode

1. League page of Team 1 and the not-linked hint in dark mode, mobile: own row, chips «Unser Team», «Sieg/Unentschieden/Niederlage», stale notice readable, nothing white on white.

## Result round 0 (head `6ff3fb7`)

All cases passed except one observation in case 7.3: after a failed «Neu laden» on a team that was never loaded, the alert reads «… Es werden weiterhin die zuletzt geladenen Daten angezeigt.» although there are no loaded data (`LeagueService.UNAVAILABLE`, 502 detail).

## Result round 1 (head `81667d1`)

Backend 374 ✅, E2E 410 passed (6 skipped as on main) ✅. Case 7.3 fixed: a failed «Neu laden» on a never-loaded team (Team 2 linked with `900002`, simulator unavailable) shows «swiss unihockey ist im Moment nicht erreichbar. Bitte später nochmals versuchen.» (API 502 detail the same); a team with loaded data (Team 1) still gets «… Es werden weiterhin die zuletzt geladenen Daten angezeigt.» together with the stale notice. Regression of cases 1–6 and 8–9 on mobile and desktop, light and dark: no change.
