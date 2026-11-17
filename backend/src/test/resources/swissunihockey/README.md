# swiss unihockey responses for tests

Responses of the match list `GET /games?mode=team&team_id=…&season=…`, as `SuApi` reads them. The tests replay them
through a local HTTP server; the real API is never called in tests (F-07, criterion 2).

**Origin:** When F-07 was built, `api-v2.swissunihockey.ch` could not be reached from the cloud environment (blocked
by the proxy). The files were therefore not recorded live but rebuilt from the format that the documentation and
several public clients of the API show (table `data.regions[].rows[].cells[].text[]`, match ID in
`rows[].link.ids[0]`, columns date/time, venue, home team, away team, result). Team and match IDs are made up. As soon
as the API can be reached, put a real response here and point the test at it (O-21 in docs/open-points.md).

| File | Content |
|---|---|
| `games-team.json` | Normal match list: a tournament with two matches in the same hall, an away match with a result, a row in between without a match |
| `games-relativ.json` | Relative dates «heute» and «morgen», as swiss unihockey delivers them around the current day |
| `games-unerwartet.json` | An error message instead of the table, as with missing access (SUH-09) |
