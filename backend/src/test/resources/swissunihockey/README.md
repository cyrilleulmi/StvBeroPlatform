# swiss unihockey responses for tests

Responses of the match list `GET /games?mode=team&team_id=…&season=…` and of the league table
`GET /rankings?team_id=…&season=…` (F-17), as `SuApi` reads them. The tests replay them
through a local HTTP server; the real API is never called in tests (F-07, criterion 2).

**Origin:** When F-07 was built, `api-v2.swissunihockey.ch` could not be reached from the cloud environment (blocked
by the proxy). The files were therefore not recorded live but rebuilt from the format that the documentation and
several public clients of the API show (table `data.regions[].rows[].cells[].text[]`, match ID in
`rows[].link.ids[0]`, columns date/time, venue, home team, away team, result). Team and match IDs are made up. As soon
as the API can be reached, put a real response here and point the test at it (O-21 in docs/open-points.md).

| File | Content |
|---|---|
| `games-team.json` | Normal match list: a tournament with two matches in the same hall, an away match with a result, a row in between without a match |
| `games-relative.json` | Relative dates «heute» and «morgen», as swiss unihockey delivers them around the current day |
| `games-unerwartet.json` | An error message instead of the table, as with missing access (SUH-09) |
| `rankings-team.json` | League table (F-17) with league and group, team IDs in `data.team.id` or in the row link, an image column |
| `rankings-overtime.json` | Table of a league with overtime («SnV», «NnV»), group inside the title, no team IDs |
| `rankings-unerwartet.json` | A table without rank, team and points columns (SUH-09) |

The rankings files were not recorded live either (F-17, O-59): the endpoint and its columns follow the format of the
match list and the column abbreviations shown on swissunihockey.ch.
