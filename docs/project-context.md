# Project context

## Goal

One central web platform for the unihockey section of STV Beromünster. It brings together the organisational tools that are scattered today (lists, chats, spreadsheets) in one place – simple enough that players, coaches and helpers can use it without instructions.

For now the app is meant for adults. Features for parents (one login responds for several children) are a possible later extension and must not be ruled out by the design.

## Principles

1. **Extensible:** Each feature is a self-contained module («Kachel», tile). New tiles are added without changing existing ones.
2. **User-friendly:** Few clicks, clear language (German), no technical terms. The most common action («Ich bin dabei / nicht dabei», I'm attending / not attending) takes one tap.
3. **Mobile first, desktop fully featured:** Players use the app on their phone in the hall, coaches and organisers also on the desktop. Responsive layout, large touch targets, installable as a PWA.
4. **Results, not interim states:** Features are implemented completely and made directly accessible. Follow-up decisions are taken based on the results.

## Tiles (modules)

| Tile | Purpose | Main users | State |
|---|---|---|---|
| Player attendance | Responses for practices and match days, overview per event, call-up | Players, coaches | Requirements worked out, see [requirements/](requirements/) |
| Coach attendance | Assignment and attendance of coaches per practice and match day, substitutes | Coaches, organisers | Responding worked out in the core, rest open |
| Drill library | Practice drills with description, sketch/video, tags, practice planning | Coaches | Not worked out yet |
| Helper scheduling | Shifts for tournaments and events, signing up and reminders | Members, organisers | Not worked out yet |
| Resources | Help videos, rules, technique tips, documents | Players | Not worked out yet |

Details on the tiles not yet worked out: [more-tiles.md](more-tiles.md).

More tiles can be added later (e.g. calendar, news, material/equipment).

## The core

The core is what gets built first and why the app exists at all:

- People, teams and team roles
- Lines
- Events (practices, match days, other events), also for several teams at once
- Responses with a view by team and line
- Match days with call-up, where possible with match data from swiss unihockey

## Way of working

- Plans are only worked out together when this is explicitly requested.
- Otherwise: make sensible assumptions and implement completely to the best of one's knowledge, without questions or intermediate stops.
- Assumptions made are listed briefly with the result, so that decisions can be based on them.
- Every result runs and is directly accessible (URL or start command), checked on desktop and mobile, with seed data to try it out.
- Tests for backend logic.
