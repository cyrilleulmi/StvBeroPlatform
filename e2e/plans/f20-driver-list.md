# Test plan F-20 · Driver list for match days

Issue #59 · PR #74 · File `e2e/tests/f20-driver-list.spec.ts` · Requirements DRV-01 to DRV-13 (`docs/requirements/14-driver-list.md`), states in `docs/states.md` «Driver list of a match day», scenarios S60–S63 in `docs/test-data.md`.

Starting point for every case: database running (`scripts/db-start.sh`), backend profile `test`, frontend on port 4200, `POST /api/test/reset` (clock Mon 16.11.2026 06:00, routing simulator default pairs), login via `POST /api/test/login` or `/dev-login`. Viewport mobile 375×812 unless stated otherwise.

IDs: event 04 `40000000-0000-4000-8000-000000000004` (match day Team 1), event 05 `…005` (match day Team 2), event 01 `…001` (practice), car 01 `b0000000-0000-4000-8000-000000000001` (Petra), car 02 `…002` (Sara). People `20000000-0000-4000-8000-0000000000NN` (02 petra, 03 marc, 05 anna, 06 lea, 07 mia, 08 sara, 09 nina, 10 jana, 11 kim).

## K1 · Section and readiness (DRV-01, DRV-13)

| Case | Person | Steps | Expected |
|---|---|---|---|
| K1.1 Seed | `jana` | `/termine/…004` | Section «Fahrerliste» (`driver-list`): route «Parkplatz Sporthalle Nord → Sporthalle Seefeld», «23.4 km · etwa 26 Min. Fahrzeit», «Gutschrift pro Auto: 46.8 km (hin und zurück)»; car of Petra «voll» with Anna, Lea, Mia; car of Sara «3 von 3 Plätzen frei»; «Ohne Platz»: Jana |
| K1.2 Missing places | `petra` | `/termine/…004/bearbeiten` → clear meeting point → save → `/termine/…004` | «Es fehlt: Treffpunkt» (and coach hint «Ergänze die Angaben unter «Termin bearbeiten».»); `POST …/driver-list/cars` → 409 |
| K1.3 Not a match day | `petra` | `/termine/…001`; `GET /api/events/…001/driver-list` | No section; API 409 |

## K2 · Drivers (DRV-02)

| Case | Person | Steps | Expected |
|---|---|---|---|
| K2.1 Enter herself | `jana` | event 04 → «Freie Plätze in deinem Auto» 2 → «Ich fahre» | Status «Du fährst mit 2 Plätzen.», new car «Auto von Jana Wyss», Jana no longer «Ohne Platz»; persists after reload |
| K2.2 Remove herself | `jana` | after K2.1 «Ich fahre nicht mehr» → confirm | Car gone, Jana back in «Ohne Platz» |
| K2.3 Not called-up member may drive | `kim` | event 04 → «Ich fahre» | Allowed (assumption) |
| K2.4 Coach on behalf | `petra` | «Fahrerin oder Fahrer eintragen» → Person Nina, 4 seats → «Eintragen»; change seats of Sara's car | Car of Nina; Nina notified (`GET /api/test/notifications`) |
| K2.5 Unauthorized | `anna` | API `POST …/cars` with `personId` Nina; `PUT …/cars/…002` seats 5; `DELETE …/cars/…002` | 403 each, nothing changed |
| K2.6 Other team member | `eva` (Team 2) | `GET /api/events/…004/driver-list` | 403 (not a member) |
| K2.7 Coach of another team | `marc` | event 04, enter a driver | Allowed (E-69) |
| K2.8 Seats out of range | `jana` | `POST …/cars` seats 0 and 9 | 400/422 |
| K2.9 Second car | `petra` | `POST …/cars` for herself again | 409/422, still one car |
| K2.10 After the end | `jana` / `petra` | `PUT /api/test/clock` 22.11.2026 14:00; jana tries «Ich fahre», petra changes seats | jana: no controls, API 409; petra: allowed |

## K3 · Passengers (DRV-03)

| Case | Person | Steps | Expected |
|---|---|---|---|
| K3.1 Ride along (S60) | `jana` | «Bei Sara Brun mitfahren» | «Du fährst bei Sara Brun mit.», «2 von 3 Plätzen frei», «Alle haben einen Platz.»; Sara gets «Jana Wyss fährt am Spieltag So 22.11. bei dir mit.» |
| K3.2 Leave | `jana` | after K3.1 «Aussteigen» | Back in «Ohne Platz», Sara notified |
| K3.3 Full car (S61) | `jana` | Petra's car shows «voll», no «mitfahren»; API `PUT …/cars/…001/passengers/<jana>` | 409 |
| K3.4 Move | `petra` | «Mitfahrende für Sara Brun wählen» → Anna «(bei Petra Keller)» → «Zuteilen» | Anna in Sara's car only, Petra's car «1 von 3 Plätzen frei»; Anna notified |
| K3.5 Seats below passengers | `petra` | Petra's car seats 3 → 2 | Rejected (422/409), message |
| K3.6 Driver rides along | `sara` | API `PUT …/cars/…001/passengers/<sara>` | 422 |
| K3.7 Passenger becomes driver | `anna` | «Ich fahre» | Anna leaves Petra's seat, own car |
| K3.8 Unauthorized assign | `anna` | API `PUT …/cars/…002/passengers/<nina>` | 403 |
| K3.9 Double click | `jana` | double click «Bei Sara Brun mitfahren» | One seat taken, no error |

## K4 · Without a seat (DRV-04)

| Case | Person | Steps | Expected |
|---|---|---|---|
| K4.1 Draft call-up | `petra` | event 04 | «Ohne Platz»: Jana (players «dabei»; Petra drives) |
| K4.2 Published call-up | `marc` | event 05 | The called-up players of Team 2 without a seat |

## K5 · Route and credits (DRV-05 to DRV-08)

| Case | Person | Steps | Expected |
|---|---|---|---|
| K5.1 Calculated once | `petra` | open event 04 twice | Same route; `data-route-status` `CALCULATED` |
| K5.2 Outage (S62) | `petra` | `PUT /api/test/routing {"unavailable":true}`, change meeting point, open | Hint «Die Strecke lässt sich gerade nicht berechnen …», no credit; «Kilometer erfassen» 21.5 → «von Hand erfasst», credit 43.0 km |
| K5.3 No route | `petra` | meeting point «Irgendwo» | «Für Treffpunkt und Spielort wurde keine Strecke gefunden.», `NOT_FOUND` |
| K5.4 Recalculate | `petra` | after K5.2 restore routing, «Strecke neu berechnen» | Calculated value replaces manual |
| K5.5 Manual km by player | `anna` | no «Kilometer erfassen»; API `PUT …/route` | 403 |
| K5.6 Recalc on change (DRV-08) | `petra` | `PUT /api/test/routing` adds «Bahnhof Musterdorf» → «Sporthalle Seefeld» 30 km; change meeting point | Route 30.0 km, credit 60.0 km |
| K5.7 Invalid km | `petra` | dialog with empty, 0, negative, letters | Validation message, nothing saved |

## K6 · Season summary (DRV-09)

| Case | Person | Steps | Expected |
|---|---|---|---|
| K6.1 Coach | `petra` | `/fahrten` | Table «Fahrten pro Person»: Petra Keller 1 · 46.8, Sara Brun 1 · 46.8 |
| K6.2 Member | `anna` / `sara` | `/fahrten` | Only own: anna 0, sara 1 / 46.8; no table |
| K6.3 Profile link | `anna` | `/profil` → «Fahrten ansehen» | `/fahrten` |
| K6.4 Archived season | `petra` | `/fahrten?saison=<2025/26>` | Empty «In dieser Saison ist noch niemand gefahren.» |

## K7 · Cancelled (DRV-10, S63)

| Case | Person | Steps | Expected |
|---|---|---|---|
| K7.1 | `petra` | cancel event 04, open | Notice (`driver-list-cancelled`), cars kept, no credit, no controls; `/fahrten` empty; API changes 409 |

## K8 · Notifications (DRV-11)

| Case | Person | Steps | Expected |
|---|---|---|---|
| K8.1 | `petra` removes Sara's car after Jana joined | | Jana notified; Petra (actor) not notified |
| K8.2 | `jana` joins Sara's car | | Only Sara notified, not Jana |

## K9 · Views and robustness

- Every case of K1–K3 on mobile; K1.1, K2.1, K3.1, K6.1 on desktop 1280×800.
- Dark mode: event 04 driver list and `/fahrten` (screenshots: contrast, no white on white).
- Reload in the middle (after «Ich fahre»), back button from `/fahrten`.
- After each case: no console errors, no unexpected 4xx/5xx.

## Regression

| Case | Steps | Expected |
|---|---|---|
| R1 | `scripts/test.sh` | All green (backend, E2E both projects, axe) |

## Additions by the test agent (round 1)

| Case | Person | Steps | Expected |
|---|---|---|---|
| A1 Rejected seat change resets the select | `petra` | event 04 → «Plätze im Auto von Petra Keller» 3 → 2 (car has 3 passengers) | Alert «Im Auto sitzen schon 3 Personen. Weniger Plätze gehen nicht.» and the select shows 3 again (the saved value), also after another change in the list. Values below the number of passengers are not offered, or are rejected without leaving the select on them |
| A2 Concurrent last seat | `jana`, `kim` | `PUT …/cars/…002 {"seats":1}` as petra, then both `PUT …/cars/…002/passengers/<self>` at the same time | One 200, one 409 «Auto voll», car has exactly 1 passenger |
| A3 Non-member as passenger | `marc` | `PUT …/cars/…002/passengers/<eva>` (Team 2 only) | 422 rule DRV-02 |
| A4 After the end, member | `jana` | clock 22.11.2026 14:00, event 04 | No controls (no «mitfahren», «Ich fahre»); API 409 «Spieltag vorbei» |

## Additions by the test agent (round 2)

| Case | Person | Steps | Expected |
|---|---|---|---|
| A5 Seat options follow the passengers | `petra` | event 04 → «Plätze im Auto von Petra Keller» | Offers 3–8 (3 passengers); after 3 → 5 and «Anna Muster aussteigen lassen» it offers 2–8 and stays on 5 |
| A6 Stale page, rejected seat change | `petra`, `marc` | Petra's car on 5 seats with 2 passengers; `marc` puts Jana, Kim, Nina into it via API; on Petra's stale page select 3 | Alert «Im Auto sitzen schon 5 Personen. Weniger Plätze gehen nicht.» (expected 422), select back on 5, options 5–8; after another change in the list it still shows the saved value |
| A7 Own car without passengers | `sara` | `PUT …/cars/…002 {"seats":1}` | 200; the select offers 1–8 |
