# 01 · People, teams, team roles

Status legend: see [README](../../README.md#requirement-status).

## Requirements

| ID | Requirement | Status |
|---|---|---|
| PTF-01 | Each person exists exactly once, no matter how many teams they are in. | Accepted |
| PTF-02 | There are several teams. | Decided |
| PTF-03 | A person can belong to several teams. | Decided |
| PTF-04 | A person has exactly one team role per team. Across teams the team roles can be combined freely, e.g. coach in Team 1 and player in Team 2. | Decided |
| PTF-05 | There are at least the team roles «Spielerin» (player) and «Coach». | Decided |
| PTF-06 | Further team roles (e.g. assistant coach, team manager) should be addable later without rebuilding the model. Which ones are needed is not defined. | Open |
| PTF-07 | Coaches are never shown as players and never count towards the player total. | Decided |
| PTF-08 | For now the app is meant for adults. Each person responds for themselves. | Decided |
| PTF-09 | Account (login) and person are modelled separately, so that later one account can respond for several people (parents for children). Today an account belongs to exactly one person. | Accepted |
| PTF-10 | Team memberships apply per season (see [09 · Seasons](09-seasons.md)). | Accepted |
| PTF-11 | Admins and coaches (RR-03) manage people, teams and team memberships. Removing a person from a team is for admins only (RR-08). | Assumption |
| PTF-12 | A person sees all teams they belong to and their team role in each. | Assumption |

## Example

| Person | Team 1 | Team 2 |
|---|---|---|
| Anna | Player | – |
| Petra | Coach | Player |
| Lea | Player | Player |

In Team 1 Petra appears in the coach block, in Team 2 among the players. Lea appears among the players in both teams and can have a different line in each team.

## Not part of the core

- Parent roles: see [Later features](../later-features.md).
- Guests and trial practices: see [Later features](../later-features.md).
