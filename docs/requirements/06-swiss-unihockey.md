# 06 · swiss unihockey connection

Where possible, match days should be filled with the match data from swiss unihockey instead of being entered by hand.

Status legend: see [README](../../README.md#requirement-status).

## Requirements

| ID | Requirement | Status |
|---|---|---|
| SUH-01 | Where possible, the match data is taken from swiss unihockey. | Decided |
| SUH-02 | A coach or an admin links a team to the corresponding team at swiss unihockey, in a dialog that offers the teams of the club at swiss unihockey («Team verknüpfen»). The team ID can also be typed by hand. Linking loads the matches and the league right away. (Changed with E-84: before, only admins linked, by typing the ID.) | Decided |
| SUH-03 | The matches of the season are imported and synced daily. | Accepted |
| SUH-04 | Matches of the same team on the same day in the same hall are combined into one match day (tournament format, see [05](05-match-days-call-up.md)). | Assumption |
| SUH-05 | If an imported match changes (date, time, location), the match day is updated and everyone affected is notified. | Accepted |
| SUH-06 | Meeting point, notes, responses and call-up are our own data. The sync never overwrites them. | Accepted |
| SUH-07 | The Google Maps link of the venue is set automatically for imported match days. | Accepted |
| SUH-08 | Results are taken over after the match, if they are provided. | Assumption |
| SUH-09 | If the interface cannot be reached or returns unexpected data, the app keeps working normally. The sync is retried later. | Assumption |
| SUH-10 | Match days entered by hand are not touched by the sync. | Accepted |
| SUH-11 | A link applies to one season. A team can have several links in a season, and other ones in the next season: the team at swiss unihockey changes from season to season. The sync takes the matches of all links; a team at swiss unihockey belongs to at most one team per season. The league page shows the first link. | Decided (several links), Assumption (league of the first link) |
| SUH-12 | If swiss unihockey changes a match (time, hall, opponent, home/away) or no longer delivers it, coaches and admins see a warning on the match day: in the event list, in the overview and on the match itself with the values before and after. The change is applied right away (SUH-05); the warning stays until a coach of the team or an admin marks it «Gesehen». A match that swiss unihockey lists as «Abgesagt» counts as no longer delivered. | Decided |
| SUH-13 | If the last sync of a link failed, the app says so: on the link («Letzter Abgleich am … fehlgeschlagen») and as a hint on the start page of coaches and admins. Everything else keeps working (SUH-09). | Decided |
| SUH-14 | Responses of swiss unihockey can be cached for a while (`STV_SU_CACHE_DIR`, `STV_SU_CACHE_TTL`), so that restarts in development do not ask the free interface each time. Off in production. | Assumption |
| SUH-15 | People are never synced with swiss unihockey: players and coaches are managed in the app only. | Decided |
| SUH-16 | The page «swiss unihockey» (`/swissunihockey`, tile on the start page, link on the team) shows for every own team (coaches and admins: every team of the current season) its links to swiss unihockey with a link to the team page at swissunihockey.ch («Auf swissunihockey.ch ansehen») and to the league in the app (`/teams/:id/liga`). Coaches and admins link, unlink and sync right there (SUH-02, SUH-11). A team without a link says to players «Nicht verbunden. Frag den Coach zum Einrichten.» | Decided |

## What is known about the interface

As of 21.11.2026, checked live against the real interface.

- **Free:** `https://wc.swissunihockey.ch`, the interface of the official web components. No key. Only some endpoints are allowed: `/seasons`, `/clubs`, `/clubs/:id/statistics`, `/teams` (by club), `/teams/:id`, `/teams/:id/statistics`, `/groups`, `/games` (modes `team`, `club`, `list`, `current`, `direct`), `/games/:id`, `/games/:id/summary`, `/rankings`, `/cups`. The app uses this address by default (E-83).
- **Partners:** `https://office.swissunihockey.ch/api/legacy` with key and secret (token from `POST /api/legacy/bo/session/auth`). Needed for leagues, match events, players, top scorers, national players and the official calendar. Access is requested from the IT of swiss unihockey (`it@swissunihockey.ch`). The app does not need it.
- **Old address:** `https://api-v2.swissunihockey.ch/api` answers every data endpoint with 401, although its documentation still calls them public.
- Teams of a club: `GET /teams?mode=by_club&season=…&club_id=…`, a «dropdown» with `entries[].text` and `entries[].set_in_context.team_id`. The club is «Eintracht Beromünster», club ID `417058`, with ten teams in 2026/27.
- Matches of a team: `GET /games?mode=team&team_id=…&season=…&games_per_page=500`. One row per match: date and time, hall and town with the coordinates of the hall (`link.x`, `link.y`, no map address), home team, away team, result. Cancelled matches have «Abgesagt» instead of date and time and «???» as the hall.
- League table of a team: `GET /rankings?team_id=…&season=…`. The title is e.g. «Tabelle Damen KF 1. Liga Gruppe 2 2026/27 per 21.11.2026»; the app reduces it to league and group.
- The season of swiss unihockey 2026/27 has matches from June 2026 (cup).
- The responses are built as generic tables (rows and cells), not as domain objects. Evaluating them therefore needs its own translation layer.

Recorded responses for the tests: `backend/src/test/resources/swissunihockey/live-*.json`.

Documentation:

- Overview of the APIs: https://api-v2.swissunihockey.ch/api/doc
- Partner interface: https://office.swissunihockey.ch/api/legacy/doc

## State after the batch «club data»

- In production the app talks to the address from `STV_SU_URL` (default `https://wc.swissunihockey.ch`), with the club from `STV_SU_CLUB_ID` (default `417058`).
- In the `dev` profile it does the same with the club data set, and talks to a simulator with the test data set; the `test` profile always uses the simulator ([test-data.md](../test-data.md#data-sets)).
- The sync is described in [states.md](../states.md#match-from-swiss-unihockey).

## Still to check

| Point | Why |
|---|---|
| Terms of use | No terms found for using the free interface directly. Clarify with swiss unihockey whether regular fetching is allowed for a club app (O-23). |
| How are match days in tournament format represented? | The interface has no grouping; the app groups by day and hall (SUH-04). Works with the real data of the ten teams. |
