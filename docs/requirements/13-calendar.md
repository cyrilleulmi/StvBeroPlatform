# 13 · Calendar subscription

Members subscribe to their events in the calendar of their phone (F-18, decision E-80). The app delivers a personal iCalendar feed; the calendar app fetches it by itself. Out of scope: writing back from the calendar, one feed per team.

Status legend: see [README](../../README.md#requirement-status).

## Address

| ID | Requirement | Status |
|---|---|---|
| CAL-01 | The profile (`/profil`) has the section «Kalender abonnieren» with the personal address, a button «Adresse kopieren», a link «Im Kalender öffnen» (`webcal:`) and short instructions for iPhone, Android (Google Kalender) and Outlook. Every person has at most one address; it is created on the first visit. | Decided (criteria), Assumption (created on the first visit, `webcal:` link) |
| CAL-06 | The address contains a long random token (256 bits, 43 characters) and works without login, because calendar apps cannot log in. The token grants read access to the feed only; it is not a session. | Decided |
| CAL-07 | «Adresse erneuern» (after a confirmation) creates a new token. The old address answers 404 at once. | Decided |

## Feed

| ID | Requirement | Status |
|---|---|---|
| CAL-02 | The address delivers an iCalendar feed (`text/calendar`, RFC 5545) with all upcoming events of the person's teams plus the past 30 days. «The person's teams» means, as in the event list (TER-13): member (any team role) of a participating team in the season of the event. «Past 30 days» means: the event ended after «now» minus 30 days. | Decided (criteria), Assumption (end as the limit) |
| CAL-03 | Each entry has title (type and teams, e.g. «Training · Team 1 + Team 2»), start, end, venue (`LOCATION`, with `GEO` if coordinates are known) and in the description: meeting point, the matches of a match day with time and pairing, notes, the Google Maps link, the person's own response (with her own comment) and a link to the event in the app (also as `URL`). | Decided (criteria), Assumption (wording) |
| CAL-04 | Cancelled events are delivered with `STATUS:CANCELLED` and the title «Abgesagt: …», the reason first in the description. Moved and changed events keep their identity (`UID` = event ID plus `@stvbero`), so calendars update the entry instead of adding one. | Decided (criteria), Assumption (title prefix) |
| CAL-05 | Times are local times in Europe/Zurich with a `VTIMEZONE` (daylight saving time from the last Sunday in March to the last Sunday in October), so they are correct on both sides of a change. | Decided |
| CAL-08 | The feed contains no comments of other people and no responses of other people. | Decided |
| CAL-09 | A feed test validates the output against the iCalendar format (ical4j) and checks one seed event (match day 04 for Anna) field by field. | Decided |

## Notes

- The feed has no `METHOD`: a subscribed feed is not an iTIP message.
- How often a calendar app fetches again is up to the app (Apple: minutes to hours, Google: up to a day). The feed suggests one hour (`REFRESH-INTERVAL`, `X-PUBLISHED-TTL`).
- The links in the feed point to the address of the app (`stv.app-url`, environment variable `STV_APP_URL`, default `http://localhost:4200`). The address shown in the profile is the address of the app plus the path from the API, so app and API must share the origin (in development through the proxy).
