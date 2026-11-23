# 17 · Calendar view

A month calendar in the app (E-92), next to the list of events. Not to be confused with the calendar subscription ([13](13-calendar.md)), which brings the events into the phone's own calendar.

Status legend: see [README](../../README.md#requirement-status).

| ID | Requirement | Status |
|---|---|---|
| KAL-01 | `/termine/kalender` («Kalender», link from `/termine`) shows a month grid Monday to Sunday with the events of the chosen teams, past and upcoming together, and below it (side by side from 1400px) the events of the chosen day with time, title, place, teams, variant and the own response. Today comes from the backend clock. Month and day are in the address (`?monat=2026-11&tag=2026-11-17`); without them the current month and today. On a phone the grid shows one dot per event in the colour of its variant, from 768px time and title. | Decided (asked for), Assumption (layout) |
| KAL-02 | The teams shown can be chosen (one toggle per team, `?teams=<id>,<id>`); at least one stays chosen. «Meine Teams» goes back to the default. | Assumption |
| KAL-03 | A player in several teams sees all her teams together by default (combined view). | Decided |
| KAL-04 | Coaches and admins can show all teams («Alle Teams», `?alle=ja`) or any single team. A coach starts with the teams she coaches herself; an admin without a team starts with all teams. Players can choose only their own teams; other teams and «all» answer 403. | Decided (asked for), Assumption (admin without a team) |
| KAL-05 | The own absences ([ABS](18-absences.md)) are marked in the grid (hatched days, part of the accessible name «…, abwesend») and named above the events of the chosen day («Du bist abwesend: Ferien · Skiferien»). | Assumption |

## API

`GET /api/events/calendar?from=&to=&team=…&all=` delivers the teams to choose from, the default teams, the teams shown, the events (`EventSummary`, as in the list) and the own absences of the period. A period has at most 42 days (six weeks of a month grid).
