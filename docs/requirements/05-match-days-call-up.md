# 05 · Match days and call-up

The call-up for matches is part of the core.

Status legend: see [README](../../README.md#requirement-status).

## Tournament format

The teams play in tournament format: on a match day several matches take place in the same hall. That is why the unit of the app is the **match day**, not the single match.

| ID | Requirement | Status |
|---|---|---|
| AUF-01 | A match day is an event that includes several matches in the same hall. | Decided |
| AUF-02 | There is one response and one call-up per match day, not per match. | Decided |
| AUF-03 | A match day belongs to exactly one team. | Assumption |
| AUF-04 | A single match outside the tournament format (e.g. friendly, cup) is a match day with one match. | Assumption |

## Match day details

| ID | Requirement | Status |
|---|---|---|
| AUF-10 | The venue can be given with a Google Maps link. | Decided |
| AUF-11 | The Google Maps link can be pasted by hand. For imported match days it is set automatically (see [06](06-swiss-unihockey.md)). | Accepted |
| AUF-12 | A match day has a meeting point with time and place. | Accepted |
| AUF-13 | A match day has a free text field for notes, e.g. the jersey colour. | Accepted |
| AUF-14 | Each match of the match day has an opponent, a start time and home or away. | Accepted |
| AUF-15 | A result can be recorded for each match. | Assumption |
| AUF-16 | Match days can be entered by hand, independently of the swiss unihockey connection. | Accepted |

## Flow

1. The players respond to the match day, exactly as for a practice ([04](04-responses.md)).
2. The coach chooses the call-up.
3. The coach publishes the call-up. Everyone is notified.

| ID | Requirement | Status |
|---|---|---|
| AUF-20 | Responding to a match day works the same as for a practice. | Accepted |
| AUF-21 | The coach selects the players for the call-up. The selection list is grouped by the standard lines (lines, goalies, no line). Gaps caused by cancellations are visible immediately. | Accepted |
| AUF-22 | The coach may call up any player of the team, regardless of their response. The response status is shown next to them. This way she can also select someone with «vielleicht» or without a response. | Decided |
| AUF-23 | The call-up is published explicitly. Before that, only the coaches see it. | Assumption |
| AUF-24 | On publishing, all players of the team are notified. Each sees «aufgeboten» (called up) or «nicht aufgeboten» (not called up) for herself. | Accepted |
| AUF-25 | Called-up players do not have to confirm. Their response already confirms it. | Decided |
| AUF-26 | Anyone who is called up but cannot come after all cancels their response. The coach is notified. | Decided |
| AUF-27 | If the coach changes a published call-up, the affected players are notified. | Assumption |
| AUF-28 | Coaches respond to the match day, are listed in the coach block and are not part of the call-up. | Accepted |

## Warnings in the call-up (F-16)

| ID | Requirement | Status |
|---|---|---|
| AUF-30 | The call-up screen shows the same warnings as the overview ([ANM-40](04-responses.md#warnings-f-16)), based on the selected players instead of the responses, and updates them while selecting. The texts say «aufgeboten» instead of «angemeldet» («Kein Goalie aufgeboten»). | Accepted, Assumption (wording) |
| AUF-31 | Publishing a call-up with warnings is allowed. The confirmation dialog repeats the warnings. | Accepted |

## Not part of the core

- Different lines for a single match day: dropped (see [LIN-08](02-lines.md)).
- Driver list with automatic travel time: built with F-20, see [14 · Driver list](14-driver-list.md). It builds on meeting point and venue.
- Calling up guest players from other teams: see [Later features](../later-features.md).
