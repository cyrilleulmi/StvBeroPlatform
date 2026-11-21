# 07 · Roles and permissions

Status legend: see [README](../../README.md#requirement-status).

## Requirements

| ID | Requirement | Status |
|---|---|---|
| RR-01 | A person's permissions follow from her team role in the respective team. Someone who is coach in Team 1 and player in Team 2 has player permissions in Team 2 for herself (she responds for herself there). Coach rights apply in every team as soon as she is coach in one team (RR-03). | Accepted |
| RR-02 | A player sees her teams and their events and responds for herself. A person who is only a player in every team has exactly these rights, also after F-12. | Accepted |
| RR-03 | A person who is coach of at least one team in the current season («coach») may, for **every** team: see teams, players and team memberships; create and edit people; add people to teams and change their team role; create, rename and reorder lines and assign players; create, edit, move and cancel events and series and add series exceptions; respond on behalf of anyone; see comments and reasons; create and publish the call-up; enter results. Not: deleting (RR-08), seasons, swiss unihockey and the admin flag (RR-04). | Decided (E-69) |
| RR-04 | Admins may do everything, across all teams. Admin is not a team role but a property of the person. Only admins manage seasons (create, make current), reload the league by hand, and grant or remove the admin flag. Linking a team to swiss unihockey and triggering the sync is open to coaches too (E-84). | Decided (E-69) |
| RR-05 | Tiles and actions are only visible to the roles allowed to use them. | Decided |
| RR-06 | For an event with several teams, every coach (RR-03) may edit the event, respond on behalf of every person in it and see all comments. Notifications that go to «the coaches of the team» (late cancellation, withdrawal of a called-up player) still go only to the coaches of that team, not to all coaches. | Decided (E-69) |
| RR-07 | Permissions are enforced in the backend, not just hidden in the user interface. | Assumption |
| RR-08 | Deleting is for admins. Deleting means: delete a person, a team, a line, an event, a series or a series exception, and remove a person from a team. These actions are hidden for coaches and answer 403 in the API. Cancelling an event is not deleting. Removing a match from a match day counts as editing the match day, not as deleting (assumption, O-55). | Decided (E-69), Accepted (scope) |

## Overview

| Action | Player | Coach (any team) | Admin |
|---|---|---|---|
| See own events | ✓ | ✓ | ✓ |
| Respond for oneself | ✓ | ✓ | ✓ |
| See the status of others in the event | ✓ | ✓ | ✓ |
| See events of other teams (team filter, detail) | – | ✓ | ✓ |
| See comments and reasons of others | – | ✓ | ✓ |
| Respond on behalf | – | ✓ | ✓ |
| Create, edit, move, cancel events and series, add series exceptions | – | ✓ | ✓ |
| Manage lines (create, rename, reorder, assign) | – | ✓ | ✓ |
| Manage special line-ups: create, change, delete (LIN-15) | – | ✓ | ✓ |
| Manage event variants of a team: add, change, delete the team's own (VAR-10) | – | ✓ | ✓ |
| Propose and apply a driver list, mark others as travelling on their own (DRV-19, DRV-20) | – | ✓ | ✓ |
| Change whether one can drive and the places of the car: oneself (DRV-18) | ✓ | ✓ | ✓ |
| Change the driving settings of others (DRV-18) | – | ✓ | ✓ |
| Create and publish the call-up, enter results, add and remove matches | – | ✓ | ✓ |
| See teams, players and team memberships of all teams | – | ✓ | ✓ |
| Create and edit people (without the admin flag) | – | ✓ | ✓ |
| Create and rename teams, add people to teams, change their team role | – | ✓ | ✓ |
| Delete a line, remove a person from a team (RR-08) | – | – | ✓ |
| Delete a person, team, event, series or series exception (RR-08; no such function exists yet) | – | – | ✓ |
| Grant or remove the admin flag | – | – | ✓ |
| Link a team to swiss unihockey, trigger the sync, confirm a change by swiss unihockey | – | ✓ | ✓ |
| Change the match day settings of a team (match duration, arrival lead, usual meeting point) | – | ✓ | ✓ |
| Manage seasons | – | – | ✓ |

«Coach» means coach of at least one team in the current season. In the API the flag `coach` of `GET /api/me` shows it.

## Note on the roles of the platform

For the whole platform at least the roles player, coach, helper/parent and admin are planned. Helpers and parents play no part in the core. They become relevant with helper scheduling and the parent features.
