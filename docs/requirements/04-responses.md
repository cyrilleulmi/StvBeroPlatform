# 04 · Responses

Status legend: see [README](../../README.md#requirement-status).

## Responses

| ID | Requirement | Status |
|---|---|---|
| ANM-01 | Players and coaches respond to events. Coaches do it the same way as players. | Decided |
| ANM-02 | There is exactly one response per person and event, not one per team. | Accepted |
| ANM-03 | Possible responses: «dabei» (attending), «nicht dabei» (declined), «vielleicht» (maybe). | Decided |
| ANM-04 | With «vielleicht» a comment is required. | Decided |
| ANM-05 | With «nicht dabei» a reason is optional. | Accepted |
| ANM-06 | As long as someone has not responded, the status is «offen» (pending). Nobody is attending automatically. | Accepted |
| ANM-07 | Comments and reasons are visible only to the coaches of the participating teams and to the person themselves. | Accepted |
| ANM-08 | The most common action («dabei» / «nicht dabei») takes one tap, directly from the event list. | Decided |
| ANM-09 | A response can be changed at any time, also after the response deadline. | Assumption |
| ANM-10 | If someone changes from «dabei» to «nicht dabei» or «vielleicht» after the response deadline, it counts as a late cancellation. The coaches are notified. | Accepted |
| ANM-11 | A coach can respond on behalf of players of her team, e.g. when someone cancelled in person. | Accepted |
| ANM-12 | For responses on behalf, it is visible who entered them. | Assumption |

## Event view

| ID | Requirement | Status |
|---|---|---|
| ANM-20 | The view shows the members grouped by team. | Decided |
| ANM-21 | Within a team, players are grouped by line: lines in their order, then goalies, then «Ohne Linie» (no line). | Decided (grouping), Accepted (order) |
| ANM-22 | Coaches are in a block of their own, with their team shown. They do not appear among the players. | Decided |
| ANM-23 | Per team and for the whole event the counts are: attending, declined, maybe, pending. Only players are counted. | Accepted |
| ANM-24 | Each person's status can be seen at a glance. | Accepted |
| ANM-25 | All members of the participating teams see the status of the others, but not their comments (see ANM-07). | Assumption |

## Dual roles in the same event

If an event belongs to several teams, the same person can be in more than one of them.

| ID | Requirement | Status |
|---|---|---|
| ANM-30 | The person gives a single response. It applies to the whole event. | Accepted |
| ANM-31 | The person appears in each participating team in their team role there: as a player in her line, as a coach in the coach block. | Accepted |
| ANM-32 | In the team total she counts in every team where she is a player. In the overall total of the event she counts only once. | Accepted |

## Example: joint practice of two teams

```
Di 18:30 · Team 1 + Team 2          19 dabei · 4 ab · 5 offen

Team 1                              10 dabei · 2 ab · 3 offen
  Linie 1     Anna ✓  Lea ✓  Mia ✗  Sara ✓  Nina ?
  Linie 2     ...
  Goalies     Jana ✓
  Ohne Linie  Kim ✓
Team 2                              9 dabei · 2 ab · 2 offen
  Linie 1     ...

Coaches       Petra ✓ (Team 1)  Marc ✗ (Team 2)
```

The sketch shows the structure, not the design. For the look, [design-principles.md](../design-principles.md) applies. «Vielleicht» is missing from the sketch and is added as its own counter and its own symbol.

## Not part of the core

- Attendance check (who was really there): see [Later features](../later-features.md).
- Warnings such as «kein Goalie angemeldet» (no goalie attending): see [Later features](../later-features.md).
