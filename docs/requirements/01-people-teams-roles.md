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
| PTF-13 | **New team assistant («Neues Team»).** Coaches and admins create a team with a guided dialog (`/verwaltung/teams/neu`) that leads step by step through what a team needs: (1) «Name» (name and season; creates the team), (2) «Personen» (coaches and players with their team role, PTF-03; picked from the table of PTF-15), (3) «swiss unihockey» (link the team, SUH-02, so matches and table come by themselves), (4) «Trainings» (one or several practice series, TER-05), (5) «Spieltage» (match duration, arrival and usual meeting point, TML-02, AUF-32; the event variants for those who want more, VAR), (6) «Linien» (training line-up, LIN; opens the line page of the team) and (7) «Fertig» (what is set up and what is still open, with links). Each step explains in one or two sentences what it is for. Every step after the first can be skipped; the progress («Schritt 2 von 7») and the steps show where one is, and every step has its address (`?team=<id>&schritt=<key>`), so one can leave and come back. Everything can be changed later on the team page in «Verwaltung». | Decided (E-99), Assumption (steps and order) |
| PTF-14 | «Team erstellen» on `/verwaltung/teams` opens the assistant (PTF-13) instead of the short form. | Decided (E-99) |
| PTF-15 | **Picking people.** A person can have a gender («weiblich», «männlich») and a year of birth («Jahrgang»); both are optional and entered with the person. Where people are put into a team, everyone of the club shows as a table, sorted by gender first (women, men, without details) and then by year of birth; filters narrow it down (gender, year of birth from and to, name, team of this season or of the season before, «ohne Team») and the order can be changed (year first, name, team of the season before). One tap on the field of a person puts her into the team, with the team role chosen above the table («Spielerin oder Spieler» by default, or «Coach»); a tap with the other team role changes it. The tap shows at once and is saved in the background, so one goes on tapping right away: nothing is locked, no row moves or disappears and the page keeps its height (only filters, the order and the actions for all shown people change the list). Admins take someone out again with another tap (RR-08); for coaches the field stays. | Decided (E-100), Assumption (which filters, women first) |
| PTF-16 | **Team memberships of the club («Teamzuteilung»).** Coaches and admins see everyone of the club and every team of a season in one table (`/verwaltung/zuteilung`, `?saison=<id>`): a row per person with gender, year of birth and her teams of the season before, a column per team with the number of players and coaches, and in each field whether the person is a player or a coach of the team. It works like PTF-15, for every team at once. Teams of the season before that are not part of the season yet show as columns too and join the season with their first member, so a new season can be filled without creating every team again. Archived seasons show read-only (SAI-02). | Decided (E-100) |
| PTF-17 | **Many at once.** For the people who are shown (after filtering) there are actions for all of them: put them into a team with the chosen team role, and, for admins, remove them from a team (RR-08). The app asks first and says how many people it concerns; whoever is already in the team stays as she is. This is how a year of juniors moves on at the end of a season: filter by the team of the season before, put everyone into the new team, and take them out of the old one if it was carried over (SAI-04). | Decided (E-100), Assumption (the actions) |

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
