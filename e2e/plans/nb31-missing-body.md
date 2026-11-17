# Test plan follow-up fix #31 · Missing body returns a German message

Issue #31 · PR #61 · Error convention in `docs/api/README.md` (RFC 9457)

General, for every case:

- Setup: `POST /api/test/reset`. Where needed, log in with `POST /api/test/login`
  and `{ "loginKey": "<person>" }`. Clock: default, does not matter.
- "Without body" means: `Content-Type: application/json`, no content (length 0).
- "Problem without body" means: status 400, `Content-Type: application/problem+json`,
  `title` «Ungültige Anfrage», `status` 400,
  `detail` «Die Anfrage enthält keine Daten. Bitte die Eingaben prüfen.», no `fields`
  (or empty), and the response contains neither «must not be null» nor «eingabe».
- No UI affected. One case also runs in the browser (mobile 375×812) via `fetch`
  from a logged-in page; then check the console (only the expected 400).

## K1 · Request without body to an endpoint with a required body (criterion 1)

| Case | Starting point · steps | Expected |
|---|---|---|
| K1.1 Season | As `alex`: `POST /api/seasons` without body | Problem without body |
| K1.2 Test login | Without session: `POST /api/test/login` without body | Problem without body |
| K1.3 JSON `null` | As `alex`: `POST /api/seasons` with body `null` | Problem without body |
| K1.4 Path parameter and body | As `alex`: `PUT /api/teams/10000000-0000-4000-8000-000000000001` without body | Problem without body |
| K1.5 Query parameter and body | As `alex`: `POST /api/teams?season=60000000-0000-4000-8000-000000000001` without body | Problem without body |
| K1.6 Two path parameters | As `alex`: `PUT /api/teams/10000000-0000-4000-8000-000000000001/members/20000000-0000-4000-8000-000000000001` without body | Problem without body |
| K1.7 Further endpoints | As `alex`: `POST /api/persons`, `POST /api/events`, `POST /api/series`, `PUT /api/test/clock`, each without body | Problem without body each |
| K1.8 Player | As `nina`: `PUT /api/events/40000000-0000-4000-8000-000000000001/responses/me` without body | Problem without body |
| K1.9 Browser | Mobile, `/dev-login`, «Alex Admin», then `fetch('/api/seasons', {method:'POST', headers:{'Content-Type':'application/json'}})` | Problem without body; only the expected 400 in the console |

## Regression: field messages stay

| Case | Steps | Expected |
|---|---|---|
| R1 Missing field | As `alex`: `POST /api/seasons` with `{"startDate":"2027-07-01","endDate":"2028-06-30"}` | 400, `detail` «Die Anfrage ist unvollständig oder ungültig.», `fields` contains `name` with a German message |
| R2 Empty object | As `alex`: `POST /api/seasons` with `{}` | 400, German messages for `name`, `startDate`, `endDate` |
| R3 Nested | As `alex`: `PUT /api/test/swiss-unihockey/matches` with `{"suTeamId":1,"matches":[null]}` | 400, field message (not «Die Anfrage enthält keine Daten») |
| R4 Invalid JSON | As `alex`: `POST /api/seasons` with `{"name":` | 400, `detail` «Die Anfrage konnte nicht gelesen werden. Bitte die Eingaben prüfen.» (#16 unchanged) |
| R5 Valid | As `alex`: `POST /api/seasons` with `{"name":"2027/28","startDate":"2027-08-01","endDate":"2028-07-31"}` | 201 |

## Roles

| Case | Steps | Expected |
|---|---|---|
| Ro1 Without session | `POST /api/seasons` without body | 401 (login before reading) |
| Ro2 Not permitted | As `anna`: `POST /api/seasons` without body | 400 problem without body or 403; reveals nothing |

## Edge cases

| Case | Steps | Expected |
|---|---|---|
| G1 415 | As `alex`: `POST /api/seasons` with `Content-Type: text/plain` without body | 415 as before |
| G2 Body at an endpoint without body | As `alex`: `POST /api/test/reset` without body | 204 as before |

## Result round 0

All cases passed. R3 passed (field message instead of «keine Daten»); noticed along the way: `fields[0].field` is
`<list element>` (internal node name). This is unchanged on `main` and also affects
`PUT /api/events/{id}/call-up` with `{"personIds":[null]}` → separate follow-up fix issue.
