# Test plan follow-up fix #44 · Delete matches no longer delivered by swiss unihockey

Issue #44 · File `e2e/tests/nb44-delete-match.spec.ts` · Requirement SUH-06, docs/states.md "Match from swiss unihockey"

Starting point: database running (`scripts/db-start.sh`), backend profile `test`/`dev`, frontend on port 4200, `POST /api/test/reset`.

## K1 · Remove a match that is no longer delivered

| Case | Steps | Expected |
|---|---|---|
| K1.1 Notice and button | `PUT /api/test/swiss-unihockey/matches` with `{"suTeamId":900001,"matches":[match 01]}` → as `alex` `POST /api/teams/10000000-0000-4000-8000-000000000001/swiss-unihockey/sync` → as `petra` `/termine/40000000-0000-4000-8000-000000000004/bearbeiten` | Group «Spiel 2»: «swiss unihockey liefert dieses Spiel nicht mehr.» and button «Spiel 2 entfernen». «Spiel 1»: «Von swiss unihockey, nicht änderbar.», no button |
| K1.2 Remove and save | «Spiel 2 entfernen» → «Änderungen speichern» | On to `/termine/…04`, message «Änderungen gespeichert.», list «Spiele» only «09:30 Team 1 – UHC Musterdorf» |
| K1.3 Reload | Reload the page, `GET /api/events/…04` | Only match 01 (`suMatchId` 5000001) |
| K1.4 Cancel | As K1.1, «Spiel 2 entfernen», then «Abbrechen» or navigate away | Match 2 stays |

## K2 · Matches still delivered stay protected

| Case | Steps | Expected |
|---|---|---|
| K2.1 Without sync | Reset → as `petra` `/termine/…04/bearbeiten` | Both matches «Von swiss unihockey, nicht änderbar.», no button «Spiel 1/2 entfernen» |

## Regression

| Case | Steps | Expected |
|---|---|---|
| R1 | `cd e2e && npx playwright test --trace off` | All green (both projects) |
| R2 | F-05 «Marc ändert die Spiele von Termin 05» | Manual matches still removable |

## Additions by the test agent

Starting point each time: reset, simulator `900001` only match 01, sync as `alex` (as K1.1), unless stated otherwise.

### Roles

| Case | Person | Steps | Expected |
|---|---|---|---|
| R-1 Admin | `alex` | `/termine/…04/bearbeiten` → «Spiel 2 entfernen» → «Änderungen speichern» | As K1.2, match 2 gone |
| R-2 Player | `anna` | `/termine/…04/bearbeiten` | «Keine Berechtigung», no form. API `DELETE /api/events/…04/matches/50000000-0000-4000-8000-000000000002` → 403 |
| R-3 Coach of another team | `marc` | as R-2 | «Keine Berechtigung», API 403 |
| R-4 Still delivered, API | `petra` | without sync `DELETE …/matches/…001` | 409 rule SUH-06, match stays |
| R-5 Player sees notice | `anna` | `/termine/…04` | Match 2 with «swiss unihockey liefert dieses Spiel nicht mehr.», no edit link |

### Robustness

| Case | Steps | Expected |
|---|---|---|
| X-1 Double click | `petra`, «Spiel 2 entfernen», double click «Änderungen speichern» | One save, no error message, no 4xx/5xx, match 2 gone |
| X-2 Reload after removing | «Spiel 2 entfernen», reload the page | Match 2 back (nothing saved) |
| X-3 Back button | «Spiel 2 entfernen», back | Match 2 stays on `/termine/…04` |
| X-4 Combined | «Spiel 2 entfernen», also change notes, save | Both saved |
| X-5 Delivered again after deleting | after K1.2 set the simulator back to match 01 and 02, sync | Match 02 appears again (newly imported), without notice «nicht mehr» |

### Views

- K1.1/K1.2 mobile 375×812 and desktop 1280×800, form once in dark mode (notice and button readable).
- After each case: no console errors, no unexpected 4xx/5xx.
