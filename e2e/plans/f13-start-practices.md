# Test plan F-13 · Start shows the next two practices

Issue #52 · PR #67 · File `e2e/tests/f13-start-practices.spec.ts` · Requirements ANM (extended), ANM-04, ANM-08 · Scenarios S39, S40 (docs/test-data.md)

Starting point for every case: database running (`scripts/db-start.sh`), backend profile `dev`/`test`, frontend on port 4200, `POST /api/test/reset`, clock at default (Mon 16.11.2026 06:00) unless stated, login via `/dev-login` (`dev-login-person-<key>`) or `POST /api/test/login`. Mobile 375×812, central cases also desktop 1280×800. Screen: `/`.

IDs: Event 01 `40000000-0000-4000-8000-000000000001`, 02 `…002`, 03 `…003` (cancelled), 04 `…004` (match day Team 1), 05 `…005` (match day Team 2).

Test IDs: `next-event`, `next-practices`, `start-practice-<id>`, `event-counter` (`data-counter="attending|declined|maybe|pending"`), `my-response` (`data-response`), `all-events`.

## K1 · Next own event of any type first

| Case | Steps | Expected |
|---|---|---|
| K1.1 Practice first | As `anna` open `/` | `next-event` shows «Training», «Di 17.11.», «Team 1, Team 2», link to `/termine/…001` |
| K1.2 Match day first (S40) | Clock `PUT /api/test/clock` 2026-11-22T06:00 Europe/Zurich. As `anna` open `/` | `next-event` shows match day 04 «So 22.11.» |

## K2 · Practices until two are visible

| Case | Steps | Expected |
|---|---|---|
| K2.1 After a practice: one more (S39) | As `anna` | `next-practices` heading «Weitere Trainings», exactly one entry `start-practice-…002` «Training Do 19.11. 19:00» |
| K2.2 After a match day: two more (S40) | Clock 22.11.2026 06:00, as `anna` | Two entries: Thu 26.11. 19:00, Tue 01.12. 18:30 (Tue 24.11. cancelled skipped) |
| K2.3 Not counted: match day / other event | As `eva` (Team 2) | Next event 01, then exactly one practice Tue 01.12.; match day 05 (Sat 28.11.) not in the list |

## K3 · Response buttons and counters without reload

| Case | Steps | Expected |
|---|---|---|
| K3.1 Practice entry | As `anna` → in `start-practice-…002` tap «Nicht dabei» | Feedback «Gespeichert: …», `data-response="DECLINED"`, counter of that entry updates (declined +1, attending −1), no page reload; still after reload |
| K3.2 Next event | As `nina` (pending on 01) → «Dabei» in `next-event` | Counter attending +1, pending −1 without reload |
| K3.3 Only the touched entry changes | After K3.1 | `next-event` counter unchanged |

## K4 · Cancelled practices neither counted nor shown

| Case | Steps | Expected |
|---|---|---|
| K4.1 | As `eva` | Event 03 (Tue 24.11., cancelled) appears nowhere on `/` |
| K4.2 | Clock 22.11., as `anna` | 24.11. skipped, two practices still shown |

## K5 · Only own teams, across teams with team name

| Case | Steps | Expected |
|---|---|---|
| K5.1 Two teams | As `lea` | Next event 01 «Team 1, Team 2», practice 02 «Team 1» |
| K5.2 Other teams not counted | As `ruth` (Team 3) | Wed 18.11. then Wed 25.11.; no Team 1/2 practice |
| K5.3 Petra dual role | As `petra` | Practices of Team 1 and Team 2 only |

## K6 · Fewer than two / none

| Case | Steps | Expected |
|---|---|---|
| K6.1 Only one left | Clock 28.04.2027 06:00, as `anna` | Only Thu 29.04.2027 in `next-event`, no `next-practices`, no placeholder |
| K6.2 None | As `alex` (no team) | `next-event` «Keine kommenden Termine», no `next-practices`, «Alle Termine» present |

## K7 · «Vielleicht» asks for a comment (ANM-04)

| Case | Steps | Expected |
|---|---|---|
| K7.1 | As `anna` → «Vielleicht» in `start-practice-…002`, leave comment empty, save | Not saved, error at the comment field; `data-response` unchanged |
| K7.2 | Enter «Später», save | `data-response="MAYBE"`, counter maybe +1 |

## K8 · Link «Alle Termine»

| Case | Steps | Expected |
|---|---|---|
| K8.1 | Tap `all-events` | URL `/termine`, event list visible; back button returns to `/` |

## K9 · Coaches

| Case | Steps | Expected |
|---|---|---|
| K9.1 | As `ruth` | Wed 18.11. in `next-event`, Wed 25.11. in `next-practices`, both with response buttons |
| K9.2 | As `marc` | Next event 01, practice Tue 01.12. (Team 2) |

## Robustness and display

- Double tap «Dabei» on a practice entry: one saved response, counter consistent.
- Reload after responding: state kept.
- Dark mode `/` on mobile: cards readable, no white on white.
- Console: no errors; network: no unexpected 4xx/5xx.
