# Proposals for a simpler driver list

Status: **proposal, not decided.** Cyrille was not happy with the page «Fahrerliste» of E-91: it grew into many forms (plan one driver for several match days, swaps on request, other trips, history, distribution table, who can drive). The driver list should be simple and serve two cases:

1. **Before the season:** assign all trips at once, as fairly as possible, with an assumption of how many people travel (a number set by hand, by default all players and coaches).
2. **Right before a match day:** add or remove a driver, and put the people into the cars.

Both must be easy to change afterwards. Three proposals show different ways to get there. Each one is a branch that runs; they share the same backend.

## Shared basis (branch `claude/driver-list-simple-base`)

| What | Where |
|---|---|
| Expected people per match day: default all players and coaches, set per team and per match day (DRV-29) | `PUT /api/teams/{teamId}/driver-plan/expected`, `PUT …/match-days/{eventId}/expected`, fields `expected`, `places`, `attending` in the plan |
| «Fair verteilen»: every upcoming match day gets drivers until there is a place for everyone expected; fewest trips first, then fewer kilometres; nobody who responded «nicht dabei»; home match days need nobody; one notification per driver instead of one per match day (DRV-30) | `POST …/driver-plan/distribute`, rules in `DriverDistributionRules` |
| The next in line takes a trip: one more car, or instead of a driver who cannot (DRV-31) | `POST …/driver-plan/match-days/{eventId}/next` |
| Everyone who still needs a seat, coaches included | field `needSeat` of the driver list |
| Frontend parts all proposals use: plan store, the cars of a match day with tap-to-seat and «Alle verteilen», the list «Wer fährt wie oft» with who can drive | `frontend/src/app/drivers/simple/` |

Requirements: DRV-29 to DRV-31 in [14-driver-list.md](../requirements/14-driver-list.md), status «Assumption». The API contract is updated. Nothing of the old backend was removed: swaps, other trips and the history still work through the API and the page `/fahrten`.

## The proposals

| | A «Fahrplan» | B «Raster» | C «Reihum» |
|---|---|---|---|
| Branch | `claude/driver-list-a-fahrplan` | `claude/driver-list-b-raster` | `claude/driver-list-c-reihum` |
| Picture | The sheet a coach hands out: one row per match day with its drivers | A grid: people down, match days across | The team drives in turns; the app says who is next |
| Planning the season | Card «Saison planen»: number of people, «Fair verteilen», «Neu verteilen» | The same controls above the grid; the grid fills | One call «An n Spieltagen fehlen Autos» with «Saison verteilen»; it disappears when nothing is missing |
| Changing a driver | Tap the driver: a list of who can take over, fewest trips first; «Austragen» | Tap a cell: drives or does not drive | «Kann nicht»: the next in line takes over, nobody chooses; «Ein Auto mehr», «Ein Auto weniger» |
| Before the match day | The next match day is open: cars with people, tap a person then a car, «Alle verteilen» | Tap a match day: its cars open below the grid | «Autos anpassen»: everyone gets a seat, cars that are not needed go, the match day then counts with the people who really come |
| For players | «Du fährst 2-mal: …», «+ Ich fahre», own trip: «Ich kann nicht fahren» | Own row can be tapped | Card «Deine nächste Fahrt» with «Ich kann nicht fahren», or the place in the row |
| Fairness is visible | List «Wer fährt wie oft» | The number in every row, plus the list | The row «Wer ist dran», the next first |
| Strong | Familiar, reads top to bottom on a phone | The whole season at one glance, best on a desktop | Fewest decisions and taps |
| Weak | Fairness is only visible further down | On a phone only about three match days fit side by side | Less direct control over who drives when |

In all three the page no longer shows: the form «Fahrten einplanen», swaps on request, other trips, the history, and the distribution table with totals. Travelling on one's own, the route and the driving time stay on the match day (`/termine/:id`).

## Assumptions

- The number of expected people is a number per team and season, with an override per match day. It includes the drivers.
- A car counts with its seats plus the driver. «Fair verteilen» gives each new car the usual seats of its driver (DRV-18).
- Home match days are recognised by the meeting point naming the venue («Halle», «Halle Ort», «Halle, Ort»).
- Fairness counts trips first and kilometres second, over the whole season (trips that are over and planned ones).
- «Neu verteilen» keeps cars that already have passengers.
- A player may pass her own trip to the next in line without asking; the new driver is notified.

## Open

- The E2E tests of the old page (`driver-plan-swaps.spec.ts` DRV-24 to DRV-28, and the tests in `f20-driver-list.spec.ts`, `event-variants.spec.ts` and `club-data.spec.ts` that open `/teams/:id/fahrerliste`) are not adapted in the proposal branches; they fail there. Each proposal has its own spec `e2e/tests/driver-list-simple.spec.ts`. Adapting the rest belongs to the proposal that is chosen.
- `docs/ui/navigation.md` and the status of DRV-14, DRV-17 and DRV-24 to DRV-28 are updated once a proposal is chosen, with an entry in `docs/decisions.md`.
- Whether swaps on request, other trips and the history come back somewhere (e.g. on `/fahrten`) or are dropped.

## Trying it out

```bash
git switch claude/driver-list-a-fahrplan   # or -b-raster, -c-reihum
scripts/db-start.sh reset                  # once: the proposals add migration V19
scripts/start.sh
```

Then http://localhost:4200/dev-login. Good to look at: team «Herren KF 4. Liga II» (eight match days entered by hand) as coach `reto` and as player `silvan`, page «Fahrerliste».
