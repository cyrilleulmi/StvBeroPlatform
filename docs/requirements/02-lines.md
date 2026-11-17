# 02 · Lines

Lines are one of the two main reasons for building our own app: players should appear grouped by line right away when they respond.

Status legend: see [README](../../README.md#requirement-status).

## Requirements

| ID | Requirement | Status |
|---|---|---|
| LIN-01 | Players can be assigned to lines. | Decided |
| LIN-02 | For now a player belongs to at most one line. | Decided |
| LIN-03 | Lines belong to a team. The rule from LIN-02 applies per team: a player in two teams can have her own line in each team. | Accepted |
| LIN-04 | The limit of one line is implemented as a rule, not as a fixed structure. «Several lines per player» should be possible later without rebuilding the model. | Accepted |
| LIN-05 | Per team, goalies form their own group next to the lines. A player is either in a line or with the goalies. | Accepted |
| LIN-06 | Players without an assignment appear under «Ohne Linie» (no line). | Accepted |
| LIN-07 | Coaches (of any team, RR-03) manage the lines: create, rename and reorder lines and assign players by drag and drop. Deleting a line is for admins only (RR-08). On the phone this must also work without drag and drop (touch). | Accepted |
| LIN-08 | There are only standard lines. Different lines for a single event are not needed. | Decided |
| LIN-09 | Lines have a name and an order (Linie 1, Linie 2, …). The order determines the display. | Assumption |
| LIN-10 | Only people with the team role «Spielerin» (player) in the respective team can be assigned to a line or to the goalies. | Assumption |
| LIN-11 | Lines apply per season (see [09 · Seasons](09-seasons.md)). | Accepted |
| LIN-12 | In responses and in the call-up, players are shown grouped by line (see [04](04-responses.md) and [05](05-match-days-call-up.md)). | Decided |

## Display order within a team

1. Lines in their order
2. Goalies
3. No line
