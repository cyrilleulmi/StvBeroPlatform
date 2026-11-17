# Recorded OpenRouteService answers (F-20, DRV-06)

Answers in the format of the OpenRouteService API v2 as documented at
<https://openrouteservice.org/dev/#/api-docs>, shortened to the fields the client reads. The cloud environment
cannot reach OpenRouteService, so the files follow the documented structure and published examples, not a live
recording (see docs/open-points.md, O-63). `OrsApiTest` serves them from a local HTTP server.

| File | Endpoint | Case |
|---|---|---|
| `geocode-seefeld.json` | `GET /geocode/search` | One hit (GeoJSON, coordinates lon/lat) |
| `geocode-empty.json` | `GET /geocode/search` | No hit |
| `directions.json` | `GET /v2/directions/driving-car` | Route with summary distance (m) and duration (s) |
| `directions-not-found.json` | `GET /v2/directions/driving-car` | HTTP 404, error 2010 «Could not find routable point» |
| `unexpected.json` | any | JSON without the expected structure |
