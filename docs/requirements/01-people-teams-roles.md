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
| PTF-13 | **New team assistant («Neues Team»).** Coaches and admins create a team with a guided dialog (`/verwaltung/teams/neu`) that leads step by step through what a team needs: (1) «Name» (name and season; creates the team), (2) «Personen» (coaches and players with their team role, PTF-03), (3) «swiss unihockey» (link the team, SUH-02, so matches and table come by themselves), (4) «Trainings» (one or several practice series, TER-05), (5) «Spieltage» (match duration, arrival and usual meeting point, TML-02, AUF-32; the event variants for those who want more, VAR), (6) «Linien» (training line-up, LIN; opens the line page of the team) and (7) «Fertig» (what is set up and what is still open, with links). Each step explains in one or two sentences what it is for. Every step after the first can be skipped; the progress («Schritt 2 von 7») and the steps show where one is, and every step has its address (`?team=<id>&schritt=<key>`), so one can leave and come back. Everything can be changed later on the team page in «Verwaltung». | Decided (E-99), Assumption (steps and order) |
| PTF-14 | «Team erstellen» on `/verwaltung/teams` opens the assistant (PTF-13) instead of the short form. | Decided (E-99) |

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
