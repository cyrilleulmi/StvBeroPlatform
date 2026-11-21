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
| AUF-12 | A match day has a meeting point with time and place. The time can stay open: the timeline then suggests a departure ([TML-04](15-timeline.md)). | Accepted |
| AUF-13 | A match day has a free text field for notes, e.g. the jersey colour. | Accepted |
| AUF-14 | Each match of the match day has an opponent, a start time and home or away. | Accepted |
| AUF-15 | A result can be recorded for each match. | Assumption |
| AUF-16 | Match days can be entered by hand, independently of the swiss unihockey connection. | Accepted |

## Flow

A team decides per [event variant](16-event-variants.md) whether its match days have a call-up at all ([VAR-06](16-event-variants.md), AUF-33). With a call-up:

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
| AUF-33 | A call-up is only made where the event variant of the match day says so ([VAR-06](16-event-variants.md)). Default: no call-up, the responses are enough («nur An- und Abmeldungen»). Without a call-up AUF-21 to AUF-31 do not apply to the match day. | Decided |
| AUF-34 | The match day shows the current ranks of the league table next to its matches: the rank of the own team with the number of teams and the league («3. Rang von 10 · Herren KF 3. Liga Gruppe 2», linked to the league page), and per match the rank of the opponent («Gegner: 5. Rang»). The call-up screen shows the same lines above the selection. The ranks come from the league data last loaded (F-17), for teams linked with swiss unihockey; an opponent that is not in the table has no rank. | Decided (ranks at the call-up), Assumption (also without a call-up, wording) |

## Warnings in the call-up (F-16)

| ID | Requirement | Status |
|---|---|---|
| AUF-30 | The call-up screen shows the same warnings as the overview ([ANM-40](04-responses.md#warnings-f-16)), based on the selected players instead of the responses, and updates them while selecting. The texts say «aufgeboten» instead of «angemeldet» («Kein Goalie aufgeboten»). | Accepted, Assumption (wording) |
| AUF-31 | Publishing a call-up with warnings is allowed. The confirmation dialog repeats the warnings. | Accepted |

## Usual meeting point

| ID | Requirement | Status |
|---|---|---|
| AUF-32 | A team can have a usual meeting point («Üblicher Treffpunkt», e.g. the home hall). Match days created by the sync start with it, without a meeting time; coaches change it per match day as before. | Decided |

## Not part of the core

- Different lines for a single match day: dropped (see [LIN-08](02-lines.md)).
- Driver list with automatic travel time: built with F-20, see [14 · Driver list](14-driver-list.md). It builds on meeting point and venue.
- Timeline of the match day: see [15 · Timeline](15-timeline.md).
- Calling up guest players from other teams: see [Later features](../later-features.md).
