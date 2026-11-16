# Test plan follow-up fix #21 · Team name per season, archive keeps the name

Issue #21 · PR #33 · Requirements SAI-02, SAI-04, RR-07

General, for every case:

- Setup: `POST /api/test/reset`, log in with `POST /api/test/anmelden` and
  `{ "schluessel": "<person>" }`. Clock: default (16.11.2026), does not matter.
- IDs: team 1 `10000000-0000-4000-8000-000000000001`, team 2 `…0002`,
  season 2025/26 (archive) `60000000-0000-4000-8000-000000000001`, 2026/27 (current) `…0002`,
  Vera `20000000-0000-4000-8000-000000000017`, Petra `…0002`.
- After each browser case: no console errors, no unexpected 4xx/5xx.

## K1 · Renaming in the current season leaves the archive unchanged

| Case | Starting point · steps | Expected |
|---|---|---|
| K1.1 API rename | `alex`: `PUT /api/teams/<T1>` with `{"name":"  Damen 1  "}` | 200, `name` «Damen 1» (trimmed) |
| K1.2 Archive detail | then `GET /api/teams/<T1>?saison=<2025/26>` | `name` «Team 1», Vera as player |
| K1.3 Current detail | `GET /api/teams/<T1>` | `name` «Damen 1» |
| K1.4 Lists | `GET /api/teams?saison=<2025/26>` or without season | Archive: «Team 1»; current: «Damen 1», «Team 2», «Team 3» (sorted by name) |
| K1.5 Own teams | `petra`: `GET /api/ich` | Membership team 1 is called «Damen 1» |
| K1.6 Person detail | `alex`: `GET /api/personen/<Vera>` | Membership 2025/26 is called «Team 1» |
| K1.7 Browser mobile | 375×812, `alex`, `/verwaltung/teams/<T1>`, team name «Damen 1», double click «Umbenennen» | Status «Name gespeichert.», heading «Damen 1», exactly one `PUT …?saison=<2026/27>` |
| K1.8 Archive in the browser | `/teams/<T1>?saison=<2025/26>` and `/verwaltung/teams/<T1>?saison=<2025/26>` | Heading «Team 1», notice «Saison 2025/26 im Archiv: nur lesend.», no rename form |
| K1.9 Archive list | «‹ Alle Teams» → `/verwaltung/teams?saison=<2025/26>` | only «Team 1» |
| K1.10 Person detail browser | `/verwaltung/personen/<Vera>` | Link «Team 1» with season 2025/26 |
| K1.11 Team role coach | `petra`, `/teams` | «Damen 1 · Coach», «Team 2 · Spielerin» |
| K1.12 Desktop | 1280×800, `alex`, `/verwaltung/teams/<T1>`, name «Herren Plausch» + Enter, then season selection archive ↔ current | Archive «Team 1», current «Herren Plausch», «Team 2», «Team 3» |
| K1.13 Dark mode | mobile, dark, `/verwaltung/teams/<T1>` | everything readable |

## K2 · Backend test exists

| Case | Steps | Expected |
|---|---|---|
| K2.1 | `cd backend && mvn test` | `TeamsTest.sai02UmbenennenVeraendertDasArchivNicht`, `sai02ArchivLaesstSichNichtUmbenennen`, `SaisonsTest.sai02TeamnameGiltProSaison` green |

## Edge cases

| Case | Starting point · steps | Expected |
|---|---|---|
| G1 Rename archive | `alex`: `PUT /api/teams/<T1>?saison=<2025/26>` | 409 «Saison im Archiv», `regel` SAI-02, name stays «Team 1» |
| G2 Team not in season | `PUT /api/teams/<T2>?saison=<2025/26>` | 404 «Dieses Team gibt es in der Saison 2025/26 nicht.» |
| G3 Unknown season | `?saison=60000000-0000-4000-8000-000000000099` | 404 «Diese Saison gibt es nicht.» |
| G4 Empty name | `{"name":"   "}` (API and form) | 400 «Bitte einen Namen eingeben.»; in the form an error message at the field |
| G5 Length | 61 characters / 60 characters | 400 «Höchstens 60 Zeichen.» / 200 |
| G6 Twice at the same time | two `PUT` in parallel | both 200, no 5xx in the backend log |
| G7 Carry-over | Team 1 → «Damen 1», then `POST /api/saisons` 2027/28 with `uebernehmenAus` 2026/27 | 2027/28 has «Damen 1», «Team 2», «Team 3» |
| G8 Rename only in the following season | then `PUT /api/teams/<T1>?saison=<2027/28>` «Damen Elite» | 2025/26 «Team 1», 2026/27 «Damen 1», 2027/28 «Damen Elite» |
| G9 Adding to a new season | Create team «Alt» in 2026/27, rename it to «Alt umbenannt», add a person in 2027/28 | The team in 2027/28 is called «Alt umbenannt» |
| G10 Migration | V1+V2 from `main` with data (team in two seasons), then V3 | `team_saison.name` taken from `team.name`, `team.name` removed |

## Roles

| Case | Steps | Expected |
|---|---|---|
| R1 Player | `anna`: `PUT /api/teams/<T1>` | 403 RR-07 |
| R2 Coach | `petra`: `PUT /api/teams/<T1>` | 403 RR-07 |
| R3 Without session | `PUT /api/teams/<T1>` | 401 |
