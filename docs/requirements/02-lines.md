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

## Special line-ups

Besides the standard lines a team has line-ups for special situations, above all power play («Überzahl») and penalty kill («Unterzahl»). They do not replace the lines and are not used for grouping the responses (batch «event variants», decision E-88).

| ID | Requirement | Status |
|---|---|---|
| LIN-13 | A team has any number of special line-ups («Spezialaufstellungen», at most 12) per season. Each has a free name («Überzahl 1», «Unterzahl», «Box», …) and 1 to 10 places. The form offers «Überzahl» and «Unterzahl» as names to start from. | Decided |
| LIN-14 | Each place has an optional label («Position», e.g. «Center», «Verteidigung links») and at most one player. A place can stay empty. A player appears at most once per line-up, but can be in several line-ups and in a standard line at the same time (LIN-02 applies to the standard lines only). Only players of the team can be set (LIN-10), goalies included. | Decided (flexible), Assumption (labels, limits) |
| LIN-15 | Coaches (of any team, RR-03) and admins create, change and delete special line-ups on the page «Linien» of the team (`/teams/:id/linien`, section «Spezialaufstellungen»). Name, number of places, labels and players are all changed in one dialog. They are shown in the order they were created. Members of the team see the line-ups, but cannot change them. | Decided |
| LIN-16 | A match day shows the special line-ups of its team in the section «Aufstellungen» ([VAR-07](16-event-variants.md)): every place with its player and her response, with a published call-up also whether she is called up. A place whose player is not coming (declined, or not called up) is marked, so that the coach sees the gap. Without special line-ups the section is not shown. | Decided (shown on the match day), Assumption (gaps) |
| LIN-17 | Special line-ups apply per season like the lines (LIN-11) and are copied into a new season together with them; a place whose player is no longer a player of the team is emptied. | Assumption |

## Display order within a team

1. Lines in their order
2. Goalies
3. No line
