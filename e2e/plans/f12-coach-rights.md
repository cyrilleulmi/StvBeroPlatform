# Test plan F-12 · Coaches manage all teams

Issue #51 · PR #66 · File `e2e/tests/f12-coach-rights.spec.ts` · Requirements RR-01, RR-03, RR-04, RR-06, RR-08, E-69, E-76 · Scenarios S37, S38 (docs/test-data.md)

Starting point for every case: database running (`scripts/db-start.sh`), backend profile `dev`/`test`, frontend on port 4200, `POST /api/test/reset`, clock at default (Mon 16.11.2026 06:00), login via `/dev-login` (`dev-login-person-<key>`) or `POST /api/test/login`. Mobile 375×812, central cases also desktop 1280×800.

IDs: Team 1 `10000000-0000-4000-8000-000000000001`, Team 2 `…002`, Team 3 `…003`. Anna `20000000-0000-4000-8000-000000000005`, Nina `…009`, Eva `…012`, Zoe `…014`, Tina `…016`. Linie 2 Team 1 `30000000-0000-4000-8000-000000000002`, Linie 2 Team 2 `…005`. Event 01 `40000000-0000-4000-8000-000000000001`, 02 `…002`, 04 `…004`, 05 `…005`. Team 3 series event Wed 18.11. `b9bcd455-7602-3b71-92f5-beb323cba706` (`termin-3-2026-11-18`).

## K1 · `marc` manages Team 1 (S37)

| Case | Steps | Expected |
|---|---|---|
| K1.1 Team filter | As `marc` `/termine` → select «Team» | Options «Alle Teams», «Team 1», «Team 2», «Team 3». Choosing «Team 1» sets `?team=<Team 1>`, survives reload |
| K1.2 Edit event | `/termine/…002/bearbeiten` → «Titel» «Training extra» → «Änderungen speichern» | On to `/termine/…002`, «Änderungen gespeichert.», title «Training extra». API `PUT /api/events/…002` 200 |
| K1.3 Assign line | `/verwaltung/teams/<Team 1>` → «Team ansehen» → «Linien einteilen» → «Linie von Anna Muster» = «Linie 2» | Status «Anna Muster ist jetzt in Linie 2.», still after reload. No «… löschen». API `PUT /api/teams/<T1>/assignments/<Anna>` 204 |
| K1.4 Respond on behalf | `/termine/…001` → «Antwort für Nina Roth eintragen» → «dabei» → save → reload | Nina `data-response="ATTENDING"`, Team 1 «6 dabei · 1 nicht dabei · 0 vielleicht · 0 offen» |
| K1.5 Comments | `/termine/…001` | Mia «nicht dabei» «Krank» visible. API `GET /api/events/…001/overview` contains «Krank» |
| K1.6 More API | as `marc` for Team 1: create line `POST /api/teams/<T1>/groups` 201, rename 200, call-up event 04 200, series 02 exception 201, cancel event 02 200, `GET /api/teams/<T3>` 200 | as stated |

## K2 · `petra` creates a person and adds her to Team 2

| Case | Steps | Expected |
|---|---|---|
| K2.1 Navigation | As `petra` | `main-navigation` contains «Verwaltung». `/verwaltung` shows «Personen» and «Teams verwalten», no «Saisons» |
| K2.2 Create person | «Personen» → «Person erfassen» → «Olivia», «Neu», e-mail → «Speichern» | On to `/verwaltung/personen/:id`, «Olivia Neu ist erfasst.», no admin checkbox |
| K2.3 Add to Team 2 | `/verwaltung/teams/<T2>` → «Person» «Olivia Neu», «Funktion» «Coach» → «Aufnehmen» → reload | «Olivia Neu» under «Coaches», no «… entfernen» |
| K2.4 API | `POST /api/persons` 201, `POST /api/teams/<T2>/members` 201, `PUT …/members/<id>` `{teamRole: COACH}` 200, `POST /api/teams` 201 | as stated |

## K3 · `petra` does not delete (S38, RR-08)

| Case | Steps | Expected |
|---|---|---|
| K3.1 UI | `/teams/<T1>/linien`, `/teams/<T2>/linien`, `/verwaltung/teams/<T1>`, `/verwaltung/personen/<Anna>` | No «<Linie> löschen», no «<Name> entfernen» |
| K3.2 Delete line | `DELETE /api/teams/<T2>/groups/<Linie 2 T2>` as `petra`; as `marc` on Team 1 | 403, detail «Keine Berechtigung» |
| K3.3 Remove member | `DELETE /api/teams/<T1>/members/<Nina>` as `petra`; `marc` removes himself from Team 2; `ruth` removes Tina from Team 3 | 403 «Keine Berechtigung» |
| K3.4 No endpoint | `DELETE /api/persons/<Anna>`, `DELETE /api/events/…006` | 405 (no delete function for person, team, event, series, series exception yet) |
| K3.5 Remove match (edit, O-55) | `DELETE /api/events/…005/matches/…003` as `petra` | 204 |

## K4 · Admin only (RR-04)

| Case | Steps | Expected |
|---|---|---|
| K4.1 Seasons | As `petra` `/verwaltung/saisons`; `POST /api/seasons`, `POST /api/seasons/<01>/activate` | «Keine Berechtigung»; 403, 403 |
| K4.2 swiss unihockey | `/verwaltung/teams/<T1>`; `PUT /api/teams/<T2>/swiss-unihockey`, `POST /api/teams/<T1>/swiss-unihockey/sync` | No section «swiss unihockey»; 403, 403 |
| K4.3 Admin flag | `/verwaltung/personen/<Anna>`, `/verwaltung/personen/<Alex>`; `PUT /api/persons/<new>` with `admin:true`, `POST /api/persons` with `admin:true`, `PUT /api/persons/<Alex>` with `admin:false` | No checkbox; 403, 403, 403. Without `admin` field: 200, Alex stays admin |

## K5 · `alex` unchanged

| Case | Steps | Expected |
|---|---|---|
| K5.1 | `/verwaltung` with «Saisons»; `/teams/<T2>/linien` → «Linie 2 löschen» → dialog «Linie 2 löschen?» → confirm | Line gone. `/verwaltung/teams/<T3>` has «Tina Bucher entfernen», section «swiss unihockey»; person form with admin checkbox |
| K5.2 API | delete line 204, remove member 204, create season 201, set admin flag 200 | as stated |

## K6 · `anna` unchanged

| Case | Steps | Expected |
|---|---|---|
| K6.1 UI | Navigation without «Verwaltung». `/verwaltung`, `/verwaltung/personen`, `/verwaltung/teams/<T1>`, `/teams/<T2>`, `/termine/…005` | «Keine Berechtigung» |
| K6.2 Lines read-only | `/teams/<T1>/linien` | «Nur Coaches können die Einteilung ändern.», no select fields |
| K6.3 Event 01 | `/termine/…001` | No «Krank», no «Antwort für …» buttons |
| K6.4 API | `GET /api/persons`, `GET /api/teams/<T2>`, `GET /api/events/…005`, call-up 05, `POST /api/persons`, respond for Mia, assign Mia, edit event 02, create team, create line, remove member | all 403. `GET /api/events?team=<T2>` only shared events (Team 1 + Team 2, Team 1 + 2 + 3) |

## K7 · Dual role `petra`

| Case | Steps | Expected |
|---|---|---|
| K7.1 | As `petra` in Team 2 (where she is a player): `/teams/<T2>/linien` editable, respond for Eva on event 01, assign Zoe to Linie 1, call-up event 05, «Resultat für …» on event 05 | All allowed (200/204) |

## K8 · Notifications only to the team's coaches

| Case | Steps | Expected |
|---|---|---|
| K8.1 S5 | Clock 16.11.2026 19:00, `anna` «nicht dabei» on event 01 | `GET /api/test/notifications`: «Anna Muster hat sich kurzfristig für Training Di 17.11. abgemeldet.» only to Petra |
| K8.2 S10 | `eva` «nicht dabei» on event 05 | «Eva Steiner ist für Spieltag Sa 28.11. aufgeboten und hat sich abgemeldet.» only to Marc |

## K9 · Documents

`docs/requirements/07-roles-permissions.md`: RR-03, RR-04, RR-06 rewritten, RR-08 added, matrix «Overview».

## Additions by the test agent

| Case | Person | Steps | Expected |
|---|---|---|---|
| Z1 Own response for other teams | `marc` (also `petra`, `alex`) | `/termine?team=<Team 3>` → event «Training Mi 18.11.» | **No** group «Meine Antwort für …» on events where the person is not a member (the event detail `/termine/b9bcd455-…` already hides it). Round 0: buttons shown, «Dabei» → 403 «Keine Berechtigung» + console error |
| Z2 Double click «Aufnehmen» | `marc` | `/verwaltung/teams/<T3>` → «Kim Baumann» → double click «Aufnehmen» | Kim once in Team 3, no 5xx |
| Z3 Dark mode | `marc` | `/verwaltung` (375), `/verwaltung/teams/<T1>` (375), `/teams/<T1>/linien` (1280), `/verwaltung/personen/<Anna>` (375) | readable, no white on white |
| Z4 Console | all | after every case | no console errors, no unexpected 4xx/5xx |
