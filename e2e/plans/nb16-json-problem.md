# Test plan follow-up fix #16 · Invalid JSON returns problem details

Issue #16 · PR #30 · Error convention in `docs/api/README.md` (RFC 9457)

General, for every case:

- Setup: `POST /api/test/reset`. Where needed, log in with `POST /api/test/login`
  and `{ "loginKey": "<person>" }`. Clock: default, does not matter.
- Requests with `Content-Type: application/json` and the given body (raw, not escaped).
- "Problem details" means: status 400, `Content-Type: application/problem+json`,
  body with `title` «Ungültige Anfrage», `status` 400 and
  `detail` «Die Anfrage konnte nicht gelesen werden. Bitte die Eingaben prüfen.»
- No UI affected. One case also runs in the browser (mobile 375×812) via `fetch`
  from a logged-in page; then check the console (only the expected 400).

## K1 · Invalid JSON at any endpoint (criterion 1)

| Case | Starting point · steps | Expected |
|---|---|---|
| K1.1 Test endpoint, truncated | Without session `POST /api/test/login` with `{"loginKey": ` | Problem details, without `fields` |
| K1.2 Not JSON | As K1.1 with `nicht json` | Problem details |
| K1.3 Wrong type | As K1.1 with `[1,2]` | Problem details |
| K1.4 Wrong date, test endpoint | `PUT /api/test/clock` with `{"now":"gestern"}` | Problem details with `fields[0]` = `{field: "now", message: "Ungültiger Wert."}` |
| K1.5 Domain endpoint | As `alex`: `POST /api/seasons` with `{"name":"2027/28","startDate":"2027-08-01","endDate":` | Problem details |
| K1.6 Wrong date format | As `alex`: `POST /api/seasons` with `{"name":"2027/28","startDate":"irgendwann","endDate":"2028-07-31"}` | Problem details, `fields[0].field` = `startDate` |
| K1.7 Wrong field type | As `alex`: `POST /api/seasons` with `{"name":["x"],"startDate":"2027-08-01","endDate":"2028-07-31"}` | Problem details, `fields[0].field` = `name` |
| K1.8 PUT | As `alex`: `PUT /api/teams/10000000-0000-4000-8000-000000000001` with `{"name":` | Problem details |
| K1.9 Valid request | As `alex`: `POST /api/seasons` with `{"name":"2027/28","startDate":"2027-08-01","endDate":"2028-07-31"}` | 201, unchanged |
| K1.10 Browser | Mobile, `/dev-login?ziel=%2Fverwaltung%2Fsaisons`, «Alex Admin», then `fetch('/api/seasons', POST, '{"name":"2027/28","startDate":"x"')` | Problem details; the page stays on `/verwaltung/saisons`, no further console errors |

## Roles

| Case | Starting point · steps | Expected |
|---|---|---|
| R1 Without session | `POST /api/seasons` with `{"name":` | 401 (login is checked before reading) |
| R2 Not permitted | As `anna`: `POST /api/seasons` with `{"name":` | 400 problem details (body is read before the role check, reveals nothing) |

## Edge cases: framework responses stay unchanged

| Case | Steps | Expected |
|---|---|---|
| G1 415 | As `alex`: `POST /api/seasons` with `Content-Type: text/plain` | 415 as before |
| G2 404 | `GET /api/gibtsnicht` | 404 as before |
| G3 405 | `DELETE /api/seasons` | 405 as before |
| G4 Empty body | As `alex`: `POST /api/seasons` without body | 400 `application/problem+json`, `detail` «Die Anfrage ist unvollständig oder ungültig.» (existing validation mapper) |
| G5 Empty object | As `alex`: `POST /api/seasons` with `{}` | 400 with German field messages for `name`, `startDate`, `endDate` |
