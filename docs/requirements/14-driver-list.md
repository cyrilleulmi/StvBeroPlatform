# 14 · Driver list

For a match day the team plans who drives and who rides along. The distance from the meeting point to the venue is calculated automatically and credited to the drivers (F-20, issue #59, decision E-82). Out of scope: reimbursement in francs, routes with pick-up stops, parents as drivers (needs the parent functions).

Status legend: see [README](../../README.md#requirement-status).

## List

| ID | Requirement | Status |
|---|---|---|
| DRV-01 | Every match day has the section «Fahrerliste» in `/termine/:id`. It can be used as soon as meeting point and venue are set; until then it says what is missing («Es fehlt: Treffpunkt und Spielort.»). Other events have no driver list (API 409). | Decided |
| DRV-02 | Every member of a team of the match day (any team role) enters herself as driver with the number of free seats (1 to 8) and removes herself again. A person drives at most one car per match day. Coaches (of any team, E-69) and admins enter, change and remove drivers on behalf of others. After the end of the match day only coaches and admins still change the list. | Decided (criteria), Assumption (1 to 8 seats; every member may drive, also people who are not called up; after the end only coaches and admins) |
| DRV-03 | Passengers: members add themselves to a car with a free seat and leave it again. Coaches and admins assign and move passengers. A person sits in at most one car (whoever joins another car moves; whoever becomes driver leaves her seat). A car never has more passengers than seats; seats cannot be reduced below the passengers. Concurrent requests for the last seat: one gets it, the other 409. | Decided |
| DRV-04 | The list shows who has no seat yet: with a published call-up the called-up players, otherwise the players who responded «dabei»; without those who drive or ride along. | Decided |

## Route and credits

| ID | Requirement | Status |
|---|---|---|
| DRV-05 | The route from the meeting point to the venue is calculated once per match day: distance in kilometres (one decimal) and driving time in minutes. Each driver is credited with the return trip (twice the distance). | Decided (criteria), Assumption (calculated on the first view of the list) |
| DRV-06 | The calculation sits behind an interface (`Routing`). Profiles `dev` and `test` use a simulator with fixed values per pair of places, controllable through `PUT /api/test/routing`. Production uses OpenRouteService (directions `driving-car`, geocoding in Switzerland), tested with recorded answers; its key comes from the environment variable `STV_ORS_API_KEY`. | Decided (criteria), Assumption (OpenRouteService, see O-63) |
| DRV-07 | Routing unavailable or no result: the list works without distance and shows a hint. Coaches and admins enter the kilometres by hand («Kilometer erfassen»); a manual value is marked «von Hand erfasst». «Strecke neu berechnen» asks the service again and replaces a manual value. An unavailable service is asked again on the next view after 10 minutes; «no result» stays until the places change or someone recalculates. | Decided (criteria), Assumption (retry after 10 minutes, manual value replaced by recalculating) |
| DRV-08 | Changing meeting point or venue recalculates the route and therefore the credits (on the next view of the list or the summary). A manual value applies only to the places it was entered for. | Decided |
| DRV-09 | The page «Fahrten» (`/fahrten`) shows per season the number of trips and the credited kilometres per person. Coaches and admins see everyone who drove, everyone else only themselves. Upcoming match days count as soon as a car is entered; trips without known distance count as trips without kilometres. | Decided (criteria), Assumption (upcoming match days count; only drivers, not passengers) |
| DRV-10 | A cancelled match day keeps its list, but credits no kilometres and counts no trips. Its list can no longer be changed. | Decided |

## Notifications and places

| ID | Requirement | Status |
|---|---|---|
| DRV-11 | Changes to a car notify the people affected (type `DRIVER_LIST_CHANGED`, in the app and by the channels of F-19), never the person who made the change: the driver when someone joins or leaves her car; the passenger when a coach assigns, moves or removes her; the passengers when their car is removed; the driver when a coach enters, changes or removes her car. | Decided (criteria), Assumption (texts) |
| DRV-12 | Coordinates of the places: venue from its stored coordinates, otherwise from the Google Maps link (`@lat,lon`, `!3d…!4d…`, or `query`/`q`/`ll`/`destination`/`daddr` with «lat,lon»), otherwise the geocoder searches the search text of the link or «name, address». The meeting point is free text and is searched as it is. If a place is not found, there is no route (DRV-07). Short links (`maps.app.goo.gl`) are not followed. | Decided (criteria), Assumption (order, short links) |
| DRV-13 | Seed: match day 04 with two cars, Petra's car full (Anna, Lea, Mia) and Sara's car with three free seats; Jana responded «dabei» and has no seat ([test-data.md](../test-data.md#event-04-match-day-sun-22112026-team-1)). | Decided |

## Season plan

| ID | Requirement | Status |
|---|---|---|
| DRV-14 | The page «Fahrerliste der Saison» (`/teams/:id/fahrerliste`) shows every match day of a team in the season with its cars. Often one person plans the whole season: a form enters one driver for several match days at once («Fahrten einplanen»). Members enter themselves, coaches and admins also others; per match day the rules of DRV-01 and DRV-02 apply. Match days on which the person already drives stay as they are; if one match day refuses, nothing is entered. Drivers can be taken off a match day right on the page. | Decided |
| DRV-15 | The real routing service is the first one that is configured: Google Maps (Routes API) with `STV_GOOGLE_MAPS_API_KEY`, OpenRouteService with `STV_ORS_API_KEY`, otherwise OpenStreetMap without a key (Nominatim finds places, OSRM calculates the route). So a route is calculated without any configuration. (Replaces the second half of DRV-06: production no longer depends on an OpenRouteService key.) | Decided |
| DRV-16 | If the meeting point names a venue the app already knows with coordinates (e.g. the home hall delivered by swiss unihockey), those coordinates are used. Otherwise the keyless geocoder searches the text and, if it finds nothing, the text without its first words (down to the town). | Assumption |
| DRV-17 | The season plan shows how the trips are shared: per member of the team the trips on match days that are over, the planned trips on upcoming ones and the kilometres, so that driving can be spread fairly. | Decided |

## Notes

- Only kilometres are counted, no money (assumption to confirm from the issue).
- Configuration: none is required. `STV_GOOGLE_MAPS_API_KEY` switches to Google Maps (needs a Google Cloud project with a billing account; the Routes API has a free monthly quota), `STV_ORS_API_KEY` to OpenRouteService. The OpenStreetMap services (`STV_NOMINATIM_URL`, `STV_OSRM_URL`) are free and ask for moderate use and an identifying user agent (`STV_ROUTING_USER_AGENT`); the app asks once per match day (O-64).
- The routing service is called outside of the database transaction; the result is stored per match day (table `route`).
