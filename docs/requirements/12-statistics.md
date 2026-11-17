# 12 · Attendance statistics

Coaches look up how reliably players respond and attend; players see their own numbers (F-15, decision E-78). The statistics only read: they count responses ([ANM](04-responses.md)) and, where recorded, attendance ([PRS](11-attendance-check.md)). Out of scope: export and charts over time.

Status legend: see [README](../../README.md#requirement-status).

## Screen and filters

| ID | Requirement | Status |
|---|---|---|
| STA-01 | Coaches (of any team, E-69) and admins have the screen «Statistik» (`/statistik`). It is reachable from the events of the tile «Anwesenheit» (`/termine`) and from a team page (`/teams/:id`). Everyone else gets «Keine Berechtigung», the API 403. | Decided |
| STA-02 | Filters, all in the address: team (all teams of the season, F-12; default the first team the person coaches, otherwise the first by name), period (default the whole current season, custom from/to within one season; the season follows from «von»), event type (all, practices, match days). | Decided (filters), Assumption (defaults, one season per period) |

## Numbers

| ID | Requirement | Status |
|---|---|---|
| STA-03 | Per player one row: number of events, «dabei», «nicht dabei», «vielleicht», «offen» (current response), share of «dabei» in percent (whole percent, half up) and number of late cancellations (ANM-10). | Decided |
| STA-04 | Where attendance was recorded (F-14), the row also shows «anwesend», «abwesend», «entschuldigt» and the attendance rate («anwesend» of all recorded values, «entschuldigt» included in the base). It is based only on events with the check switched on and recorded attendance. The screen says on how many events the rate is based. Without a base the rate shows «–». | Decided (criteria), Assumption (formula) |
| STA-05 | Rows are grouped by line (lines, goalies, «Ohne Linie», as in the team page) and sortable by name, by «Zusagequote» and by «Präsenzquote» (highest first, without a rate last; `?sortierung=`). A summary row «Teamschnitt» shows the team average: the mean per player, counts with one decimal, rates as the mean of the players that have one. | Decided (criteria), Assumption (average) |
| STA-06 | A click on a player opens her list of events in the period (`/statistik/:personId`), newest first, with response, late cancellation and, where recorded, attendance. | Decided |
| STA-07 | Only past events count (start before «now» of the backend clock). Cancelled events are left out. A player counts only for events from the day she joined the team. The day of joining is stored per team membership: the day she was added, or the start of the season (seed, carry-over, adding to a planned season). | Decided (criteria), Assumption (day of joining) |

## Visibility

| ID | Requirement | Status |
|---|---|---|
| STA-08 | Players see the same numbers for themselves only, under their profile («Meine Statistik», `/profil/statistik`), per team they play in. The API answers 403 for other people's statistics. | Decided |
| STA-09 | The numbers match the seed: scenarios S44–S47 in [test-data.md](../test-data.md) with expected values for Anna and Kim, checked by a backend test and an E2E test. | Decided |
| STA-10 | Mobile first: at 375px the screen has no horizontal page scrolling; each group is a table that scrolls sideways inside its card, the name column stays visible. | Decided |
| STA-11 | Requirements are written as `STA-xx`. Export (e.g. for J+S) and charts over time are not part of it (PRS-11). | Decided |
