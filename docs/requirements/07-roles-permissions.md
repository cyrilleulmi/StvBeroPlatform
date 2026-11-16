# 07 · Roles and permissions

Status legend: see [README](../../README.md#requirement-status).

## Requirements

| ID | Requirement | Status |
|---|---|---|
| RR-01 | A person's permissions follow from their team role in the respective team. Someone who is coach in Team 1 and player in Team 2 has coach permissions in Team 1 and player permissions in Team 2. | Accepted |
| RR-02 | A player sees her teams and their events and responds for herself. | Accepted |
| RR-03 | A coach edits events and lines of her team, responds on behalf, sees comments and creates the call-up. | Accepted |
| RR-04 | Admins may do everything, across all teams. Admin is not a team role but a property of the person. | Accepted |
| RR-05 | Tiles and actions are only visible to the roles allowed to use them. | Decided |
| RR-06 | For an event with several teams, the coach of a participating team may edit the event. She may respond on behalf and see comments only for her own team. | Assumption |
| RR-07 | Permissions are enforced in the backend, not just hidden in the user interface. | Assumption |

## Overview

| Action | Player | Coach (own team) | Admin |
|---|---|---|---|
| See own events | ✓ | ✓ | ✓ |
| Respond for oneself | ✓ | ✓ | ✓ |
| See the status of others in the event | ✓ | ✓ | ✓ |
| See comments and reasons of others | – | ✓ | ✓ |
| Respond on behalf | – | ✓ | ✓ |
| Create, edit, cancel events and series | – | ✓ | ✓ |
| Manage lines | – | ✓ | ✓ |
| Create and publish the call-up | – | ✓ | ✓ |
| Manage people, teams, team memberships | – | – | ✓ |
| Link a team to swiss unihockey | – | – | ✓ |
| Manage seasons | – | – | ✓ |

## Note on the roles of the platform

For the whole platform at least the roles player, coach, helper/parent and admin are planned. Helpers and parents play no part in the core. They become relevant with helper scheduling and the parent features.
