# Later features

These features are deliberately postponed. They are not implemented now. But the model should be built so that they can be added later without rebuilding it.

## Postponed

| Feature | Description | What the model already takes into account | Status |
|---|---|---|---|
| **Driver list with automatic travel time** | For match days: who drives, who rides along. The travel time to the venue is calculated automatically. | Match days have a meeting point and a venue with a Google Maps link or coordinates. | Later (decided) |
| **Parent features** | One login responds for several children. | Account and person are separate ([PTF-09](requirements/01-people-teams-roles.md)). | Later (decided) |
| **Guests and trial practices** | A player from another team practises or plays along once. Or someone comes for a trial practice. | – | Later (accepted) |
| **Calendar subscription** | Events via iCal in one's own phone calendar. | – | Later (accepted) |
| **Several lines per player** | A player belongs to several lines in the same team. | The limit of one line is a rule, not a structure ([LIN-04](requirements/02-lines.md)). | Later (decided: «for now only one line») |
| **More team roles** | Assistant coach, team manager and similar. | Team roles are extensible ([PTF-06](requirements/01-people-teams-roles.md)). | Open |

## Done

| Feature | Where |
|---|---|
| **Attendance check** | Built with F-14 (E-70, E-77): switch «Präsenz erfassen» per event or series, off by default; the coach records «anwesend», «abwesend», «entschuldigt» from the start on ([PRS](requirements/11-attendance-check.md)). Response (intention) and attendance (fact) stay separate. The statistics per player were built with F-15 ([STA](requirements/12-statistics.md), E-78). |
| **Warnings** | Built with F-16 (E-79): «Kein Goalie angemeldet», «Nur <n> Feldspielerinnen angemeldet», «<Linie> nur zu <n>» in the overview, the call-up and the event list for coaches and admins, minimums per team on the team page ([ANM-40 to ANM-46](requirements/04-responses.md#warnings-f-16), [AUF-30, AUF-31](requirements/05-match-days-call-up.md#warnings-in-the-call-up-f-16)). |

## Ideas without a decision

| Idea | Description |
|---|---|
| Export of attendance | Once the attendance check exists, a list could be exported from it, e.g. for J+S. |

## Dropped

| Feature | Decision |
|---|---|
| **Lines per event** | Different lines for a single practice or match day are not needed. There are only standard lines ([LIN-08](requirements/02-lines.md)). |
