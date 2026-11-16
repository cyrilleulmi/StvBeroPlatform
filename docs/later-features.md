# Later features

These features are deliberately postponed. They are not implemented now. But the model should be built so that they can be added later without rebuilding it.

## Postponed

| Feature | Description | What the model already takes into account | Status |
|---|---|---|---|
| **Attendance check** | After practice the coach ticks off who was really there. This produces a season statistic per player. | Response (intention) and attendance (fact) are separate things. The response is not reinterpreted as attendance. | Later (decided) |
| **Driver list with automatic travel time** | For match days: who drives, who rides along. The travel time to the venue is calculated automatically. | Match days have a meeting point and a venue with a Google Maps link or coordinates. | Later (decided) |
| **Parent features** | One login responds for several children. | Account and person are separate ([PTF-09](requirements/01-people-teams-roles.md)). | Later (decided) |
| **Guests and trial practices** | A player from another team practises or plays along once. Or someone comes for a trial practice. | – | Later (accepted) |
| **Warnings** | Notes such as «kein Goalie angemeldet» (no goalie attending) or «Linie 2 nur zu dritt» (line 2 only three players). | Lines and goalies are groups of their own, the counts per group can be derived. | Later (accepted) |
| **Calendar subscription** | Events via iCal in one's own phone calendar. | – | Later (accepted) |
| **Several lines per player** | A player belongs to several lines in the same team. | The limit of one line is a rule, not a structure ([LIN-04](requirements/02-lines.md)). | Later (decided: «for now only one line») |
| **More team roles** | Assistant coach, team manager and similar. | Team roles are extensible ([PTF-06](requirements/01-people-teams-roles.md)). | Open |

## Ideas without a decision

| Idea | Description |
|---|---|
| Export of attendance | Once the attendance check exists, a list could be exported from it, e.g. for J+S. |

## Dropped

| Feature | Decision |
|---|---|
| **Lines per event** | Different lines for a single practice or match day are not needed. There are only standard lines ([LIN-08](requirements/02-lines.md)). |
