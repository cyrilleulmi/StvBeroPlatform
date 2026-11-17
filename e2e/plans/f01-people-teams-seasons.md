# Test plan F-01 · People, teams, seasons

Issue #2 · PR #20 · Requirements PTF, SAI, RR-04, RR-07

General for every case:

- Preparation: `POST /api/test/reset`, then `POST /api/test/login` with `{ "loginKey": "<person>" }`.
- Clock: default ("now" = Mon 16.11.2026 06:00), unless stated otherwise.
- Views: mobile 375×812 (all cases), desktop 1280×800 (core cases K2–K6).
- After each case: no console errors, no unexpected 4xx/5xx.

IDs: Team 1 `10000000-0000-4000-8000-000000000001`, Team 2 `…002`, Team 3 `…003`.
Season 2025/26 `60000000-0000-4000-8000-000000000001` (archive), 2026/27 `…002` (current).
People `20000000-0000-4000-8000-0000000000NN` according to test-data.md.

## K1 · Seed

| Step | Expected |
|---|---|
| As `alex` open `/verwaltung/personen` | 17 people, sorted by last name, Alex with the marker «Admin» |
| `/verwaltung/teams` | Season «2026/27 (aktuell)» selected, Team 1, Team 2, Team 3 |
| `/verwaltung/teams/<Team 1>` | Coaches: Petra Keller. Players (7): Kim Baumann, Sara Brun, Mia Frei, Lea Graf, Anna Muster, Nina Roth, Jana Wyss |
| `/verwaltung/teams?saison=<2025/26>` | Notice `archive-notice`, only Team 1 |

## K2 · Create and edit a person (PTF-11)

| Case | Steps | Expected |
|---|---|---|
| K2.1 Create | `alex` → `/verwaltung/personen` → «Person erfassen» → first name «Flurina», last name «Testerin», e-mail «flurina@example.ch» → «Speichern» | On to `/verwaltung/personen/:id`, status «Flurina Testerin ist erfasst.», exactly once in the list |
| K2.2 Required fields | Save the empty form | «Bitte einen Vornamen eingeben.», «Bitte einen Nachnamen eingeben.» at the field, fields `invalid`, no POST |
| K2.3 Invalid e-mail | Save e-mail «kein-mail» for Kim | «Bitte eine gültige E-Mail-Adresse eingeben.», nothing saved |
| K2.4 Edit | Kim last name «Baumann-Huber» → save → reload | «Änderungen gespeichert.», «Baumann-Huber» after reload |
| K2.5 Long input | Enter a first name with 500 characters | Field takes at most 60 characters. API with 61 characters: 400 «Höchstens 60 Zeichen.». List `/verwaltung/personen` without horizontal scrolling at 375px |
| K2.6 Double click | K2.1 with a double click on «Speichern» | Person created only **once** (exactly one `POST /api/persons`) |
| K2.7 Last admin | Untick «Admin» for Alex → save | Message «Es braucht mindestens eine Person mit Admin-Rechten.» (409, RR-04), checkbox stays ticked after reload |

## K3 · Add a person with a team role to the team (PTF-04)

| Case | Steps | Expected |
|---|---|---|
| K3.1 Add | `alex` → `/verwaltung/teams/<Team 1>` → «Person» = Ruth Meier, «Funktion» = Coach → «Aufnehmen» | Status «Ruth Meier ist jetzt Coach in Team 1.», Ruth under coaches, still there after reload |
| K3.2 Already in the team (UI) | Open the «Person» select | Members with «(schon im Team)» and not selectable |
| K3.3 Already in the team (API) | `POST /api/teams/<Team 1>/members` with Petra, `PLAYER` | 409, `rule: PTF-04` |
| K3.4 Double click | K3.1 with a double click on «Aufnehmen» | Exactly one addition, no error message, no 500 |
| K3.5 Change team role | «Funktion von Anna Muster» = Coach | Anna moves to the coaches, counters coaches 2 / players 6 |
| K3.6 Remove | «Mia Frei entfernen» → reload | Mia no longer in the team, person still exists under `/verwaltung/personen` |

## K4 · Dual role (PTF-12, S20)

| Steps | Expected |
|---|---|
| `petra` → `/teams` | «Team 1 Coach», «Team 2 Spielerin» (`team-card-<id>`) |
| `petra` → `/teams/<Team 2>` | Petra under `team-players` |
| `petra` → `/teams/<Team 3>` | «Keine Berechtigung», API 403 |

## K5 · Coaches and players separate (PTF-07)

| Steps | Expected |
|---|---|
| `petra` → `/teams/<Team 1>` | Coaches 1: Petra Keller. Players 7 without Petra |
| `petra` → `/teams/<Team 2>` | Coaches 1: Marc Huber. Players 6 including Petra, without Marc |
| `marc` → `/teams/<Team 1>` | «Keine Berechtigung», API 403 |

## K6 · New season with carry-over (SAI-04, SAI-06, S21)

| Case | Steps | Expected |
|---|---|---|
| K6.1 Create | `alex` → `/verwaltung/saisons` → suggestion «2027/28», 01.07.2027–30.06.2028, carry-over from 2026/27 → «Saison anlegen» | Status «Saison 2027/28 ist angelegt …», entry «geplant» |
| K6.2 Activate | For 2027/28 «Zur aktuellen Saison machen» → reload | 2027/28 «aktuell», 2026/27 and 2025/26 «Archiv» |
| K6.3 Carry-over | `/verwaltung/teams` → Team 1 | Season 2027/28, same coaches and players as 2026/27 |
| K6.4 Double click | K6.1 with a double click on «Saison anlegen» | Season created once, no 500, no error message |
| K6.5 Duplicate name (API) | `POST /api/seasons` with name «2026/27» | 409 |

## K7 · Archive read-only (S16, SAI-02)

| Steps | Expected |
|---|---|
| `alex` → `/verwaltung/saisons` → for 2025/26 «Teams ansehen» → Team 1 | «Saison 2025/26 im Archiv: nur lesend.», Vera Studer as player, no controls for changes |
| API: add member, remove member, create team with `?season=<2025/26>` | 409 each, `rule: SAI-02` |
| `alex` → `/teams/<Team 1>?saison=<2025/26>` | Archive notice, Vera visible |

## K8 · Administration locked for non-admins (RR-04, RR-07)

| Steps | Expected |
|---|---|
| As `anna` and `petra` every route: `/verwaltung`, `/verwaltung/personen`, `/verwaltung/personen/neu`, `/verwaltung/personen/<id>`, `/verwaltung/teams`, `/verwaltung/teams/<id>`, `/verwaltung/saisons` | `role="alert"` «Keine Berechtigung», no forms |
| As `anna` and `petra`: `GET/POST /api/persons`, `PUT /api/persons/<id>`, `POST /api/teams`, `PUT /api/teams/<id>`, `POST/PUT/DELETE …/members`, `POST /api/seasons` (valid content), `POST /api/seasons/<id>/activate` | 403 |
| As `marc` navigation | no entry «Verwaltung» |

## Theme

- Every new screen (`/teams`, `/teams/:id`, `/verwaltung`, people, person, manage teams, manage team, seasons) once in dark mode at 375px: readable, no white on white, no horizontal scrolling.
- Desktop 1280px: the same screens without overflow.

## Round 1 · Rework (double click, concurrent requests, observations)

| Case | Steps | Expected |
|---|---|---|
| R1.1 Double click activate | `alex` → `/verwaltung/saisons` (2027/28 created) → double click «Zur aktuellen Saison machen» | Exactly one `POST …/activate` 204, status «2027/28 ist jetzt die aktuelle Saison.», no alert |
| R1.2 Double click remove | `alex` → `/verwaltung/teams/<Team 1>` → double click «Mia Frei entfernen» | Mia removed, no alert, no console errors |
| R1.3 Concurrent addition (API) | 3 × concurrently `POST /api/teams/<Team 1>/members` with Ruth, `COACH` | Exactly one 201, otherwise 409 `rule: PTF-04`, never 500 |
| R1.4 Concurrent season (API) | 3 × concurrently `POST /api/seasons` with name «2028/29», `startDate` 2028-07-01, `endDate` 2029-06-30 | Exactly one 201, otherwise 409 `rule: SAI-01`, season exists once |
| R1.5 «noch keinen Coach» | `alex` → `/teams/<Team 1>?saison=<2025/26>` and `/verwaltung/teams/<Team 1>?saison=<2025/26>` | «Dieses Team hat noch keinen Coach.» |
| R1.6 Long name | Enter a first name of 80 × «X» for Kim → save → `/verwaltung/personen` at 375px | Field takes 60 characters, `scrollWidth` = 375 |
