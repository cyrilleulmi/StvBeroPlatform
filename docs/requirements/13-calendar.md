# 13 · Calendar subscription

Members subscribe to their events in the calendar of their phone (F-18, decision E-80). The app delivers a personal iCalendar feed; the calendar app fetches it by itself. Each person chooses which of her events the feed delivers (E-94), so that the subscription works like the calendar sync of Spond: only what she takes part in. Out of scope: writing back from the calendar, one feed per team, several addresses per person.

Status legend: see [README](../../README.md#requirement-status).

## Address

| ID | Requirement | Status |
|---|---|---|
| CAL-01 | The profile has the page «Kalender abonnieren» (`/profil/kalender`, a row in `/profil`, E-109) with the personal address, a button «Adresse kopieren», a link «Im Kalender öffnen» (`webcal:`) and short instructions for iPhone, Android (Google Kalender) and Outlook. Every person has at most one address; it is created on the first visit. | Decided (criteria), Assumption (created on the first visit, `webcal:` link) |
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

## Filter (E-94)

| ID | Requirement | Status |
|---|---|---|
| CAL-10 | The section «Kalender abonnieren» has a part «Was im Kalender erscheint». Event types: «Trainings», «Spieltage», «Anlässe»; at least one stays chosen («Wähle mindestens eine Terminart.», 422 with rule CAL-10). Every change is saved at once; the address stays the same, so nothing has to be subscribed again. | Decided (criteria), Assumption (saved at once) |
| CAL-11 | «Meine Antwort»: events with «dabei» are always in the feed. Each of these can be switched on or off: «Noch nicht beantwortet», «Freiwillig, noch nicht beantwortet» (invited without obligation, TER-15), «vielleicht», «nicht dabei und nicht aufgeboten». The wish of the Spond users («angemeldet oder eingeladen, noch nicht beantwortet») is «dabei» plus «Noch nicht beantwortet». | Decided |
| CAL-12 | A published call-up decides for the players of a match day: called up counts as «dabei» unless she responded «nicht dabei»; not called up counts like «nicht dabei», also with the response «dabei». Coaches are never in a call-up (AUF-28); their response decides. A call-up in draft does not count. | Assumption |
| CAL-13 | A person in several teams can leave out the events of a team («Teams», shown from two teams on). An event stays in the feed as long as one of her teams in it is not left out. | Assumption |
| CAL-14 | A new address delivers everything, as before the filter (F-18). Renewing the address (CAL-07) keeps the filter. | Decided |
| CAL-15 | Cancelled events follow the same filter, so that an entry the calendar already shows is struck through or removed (CAL-04) instead of staying. An event that no longer passes the filter (e.g. after «nicht dabei») is left out of the feed; calendar apps then remove it at their next fetch. | Assumption |

## Notes

- The feed has no `METHOD`: a subscribed feed is not an iTIP message.
- How often a calendar app fetches again is up to the app (Apple: minutes to hours, Google: up to a day). The feed suggests one hour (`REFRESH-INTERVAL`, `X-PUBLISHED-TTL`). A real two-way sync like Spond's would need a calendar API per provider; out of scope (O-68).
- The links in the feed point to the address of the app (`stv.app-url`, environment variable `STV_APP_URL`, default `http://localhost:4200`). The address shown in the profile is the address of the app plus the path from the API, so app and API must share the origin (in development through the proxy).
