# 06 · swiss unihockey connection

Where possible, match days should be filled with the match data from swiss unihockey instead of being entered by hand.

Status legend: see [README](../../README.md#requirement-status).

## Requirements

| ID | Requirement | Status |
|---|---|---|
| SUH-01 | Where possible, the match data is taken from swiss unihockey. | Decided |
| SUH-02 | An admin links each team once to the corresponding team at swiss unihockey. | Accepted |
| SUH-03 | The matches of the season are imported and synced daily. | Accepted |
| SUH-04 | Matches of the same team on the same day in the same hall are combined into one match day (tournament format, see [05](05-match-days-call-up.md)). | Assumption |
| SUH-05 | If an imported match changes (date, time, location), the match day is updated and everyone affected is notified. | Accepted |
| SUH-06 | Meeting point, notes, responses and call-up are our own data. The sync never overwrites them. | Accepted |
| SUH-07 | The Google Maps link of the venue is set automatically for imported match days. | Accepted |
| SUH-08 | Results are taken over after the match, if they are provided. | Assumption |
| SUH-09 | If the interface cannot be reached or returns unexpected data, the app keeps working normally. The sync is retried later. | Assumption |
| SUH-10 | Match days entered by hand are not touched by the sync. | Accepted |

## What is known about the interface

As of 15.11.2026, read from the public documentation. None of it has been tested against the real interface yet.

- Base URL: `https://api-v2.swissunihockey.ch/api`
- According to the overview, these endpoints, among others, are public: `/api/games`, `/api/teams`, `/api/clubs`, `/api/calendars`, `/api/seasons`.
- Teams of a club: `GET /api/teams?mode=by_club&season=…&club_id=…`
- Matches of a team: `GET /api/games?mode=team&team_id=…&season=…`
- Matches of a club: `GET /api/games?mode=club&club_id=…&season=…`
- The match list contains a column with the hall including a Google Maps link.
- The match detail returns home and away team, result, date, start time and the coordinates of the venue.
- Calendar of a team as iCalendar: `GET /api/calendars?team_id=…`
- The responses are built as generic tables (rows and cells), not as domain objects. Evaluating them therefore needs its own translation layer.

Documentation:

- Overview: https://api-v2.swissunihockey.ch/api/doc/table/overview
- Games: https://api-v2.swissunihockey.ch/api/games/doc
- Teams: https://api-v2.swissunihockey.ch/api/teams/doc
- Calendars: https://api-v2.swissunihockey.ch/api/calendars/doc

## State after F-07

- In the `dev`/`test` profile the app talks to a simulator ([test-data.md](../test-data.md#simulated-swiss-unihockey-data)), in the `prod` profile to the address from `STV_SU_URL` (default `https://api-v2.swissunihockey.ch/api`).
- According to public clients of the interface (as of autumn 2026), `api-v2` now requires credentials. `https://wc.swissunihockey.ch` is free and has the same response format, see [O-20 and O-24](../open-points.md#swiss-unihockey).
- Columns of the match list and the grouping of tournaments: [O-21](../open-points.md#swiss-unihockey).
- The sync is described in [states.md](../states.md#match-from-swiss-unihockey).

## Still to check

| Point | Why |
|---|---|
| Is a key or a registration needed? | The documentation says nothing about it. According to the documentation, some resources are only for registered partners. |
| Which fields exactly does a row of the match list return? | The documentation does not list them for the modes `team` and `club`. |
| How are match days in tournament format represented? | Decides how reliably SUH-04 can group. |
| Club ID of STV Beromünster and team IDs | Needed for the link (SUH-02). |
| Terms of use | Clarify whether regular fetching is allowed for a club app. |
| Is there a newer interface? | An independent WordPress plugin mentions the hosts `wc.swissunihockey.ch` (free) and `office.swissunihockey.ch` (partners). According to other clients, the official web components use `wc.swissunihockey.ch`. Not checked live (O-24). |
