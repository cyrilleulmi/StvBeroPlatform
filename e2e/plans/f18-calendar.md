# Test plan F-18 · Calendar subscription (iCal)

Issue #57, PR #72. Requirements CAL-01 to CAL-09 ([13-calendar.md](../../docs/requirements/13-calendar.md)), decision E-80, assumptions O-61. Seed: Anna's fixed token `seed-anna-calendar-000000000000000000000005`, scenarios S56, S57 in [test-data.md](../../docs/test-data.md); rules «Calendar subscription» in [states.md](../../docs/states.md). Default clock: Mon 16.11.2026 06:00.

Before every case: `POST /api/test/reset`, log in via `POST /api/test/login` or `/dev-login`. Viewports: mobile 375×812 (all cases), desktop 1280×800 (cases 1, 4, 7). Feeds are fetched like a calendar app: in a fresh request context without cookies. After every case: no console errors, no unexpected 4xx/5xx.

IDs: event 03 `40000000-0000-4000-8000-000000000003` (cancelled), event 04 `…0004` (match day Team 1).

## 1. Section «Kalender abonnieren» (criterion 1, CAL-01)

1. As `anna` open `/profil`.
   - Expect: region `calendar-subscription` «Kalender abonnieren»; read-only field «Deine Kalenderadresse» = `http://localhost:4200/api/calendar/seed-anna-calendar-000000000000000000000005.ics`; button «Adresse kopieren»; link «Im Kalender öffnen» with `webcal://localhost:4200/api/calendar/….ics`; heading «So gehts» with the disclosures «iPhone», «Android (Google Kalender)», «Outlook» (`calendar-help-*`), each with steps; button «Adresse erneuern».
2. Open all three disclosures. Expect: no horizontal page scroll at 375px.
3. With clipboard permission tap «Adresse kopieren». Expect: clipboard holds the address, message «Die Adresse ist kopiert.».
4. As `marc` (no address yet) open `/profil`. Expect: a new address with a 43-character token; reload → same address.
5. As `alex` (no team) open `/profil`. Expect: address shown; the feed is a valid empty calendar (no `VEVENT`).

## 2. Feed content and window (criteria 2, 3; S56)

1. Fetch Anna's seed feed without login.
   - Expect: 200, `text/calendar; charset=utf-8`, `Cache-Control: private, no-cache`; events 01, 02, 03, 04, 06, 07, 09, 10 and the series events of Team 1 from Tue 20.10.2026 on; not Thu 15.10., not 05 (Team 2 only), not 08 (archive).
2. Event 04: `SUMMARY:Spieltag · Team 1`, `DTSTART;TZID=Europe/Zurich:20261122T093000`, `DTEND …T130000`, `LOCATION:Sporthalle Seefeld\, Musterdorf`, description with «Treffpunkt: 08:00, Parkplatz Sporthalle Nord», both matches, «Notizen: Weisse Trikots mitnehmen», Maps link, «Deine Antwort: dabei», link and `URL` to `/termine/<04>`.
3. Set clock to Sun 20.12.2026 12:00. Expect: first entry is 22.11. (17.11. and 19.11. ended more than 30 days ago).
4. Roles: feeds of `marc` (Team 2: 01, 03, 05, 06, 07), `petra` (Team 1 and 2), `alex` (empty).

## 3. Cancelled and moved events (criterion 4, CAL-04)

1. Event 03 in Anna's feed: `STATUS:CANCELLED`, `SUMMARY:Abgesagt: Training · Team 1 + Team 2`, description starts with «Abgesagt: Halle besetzt».
2. As `petra` edit event 04: end 14:00, notes «Weisse Trikots; Schoner, Stöcke» plus a second line. Save.
   - Expect: Anna's feed has exactly one `UID:<04>@stvbero` with `DTEND …T140000`; `;` `,` and the line break are escaped (`\;`, `\,`, `\n`).
3. Edge (existing rule): moving match day 04 to another date is refused with the hint in the form; no change in the feed.

## 4. Daylight saving (criterion 5, CAL-05)

1. Anna's feed contains a `VTIMEZONE` for Europe/Zurich (DAYLIGHT last Sunday in March, STANDARD last Sunday in October).
2. Series practices before and after 25.10.2026 and 28.03.2027 (e.g. 20.10., 27.10., 23.03., 30.03.) all have local start 18:30.

## 5. Token and access (criteria 6, 7; S57; CAL-06, CAL-07)

1. Unknown, too short, malformed (`../x`) and too long tokens → 404. `POST` on a feed → 405.
2. Without login: `GET /api/me/calendar` → 401, `POST /api/me/calendar/renew` → 401.
3. A feed token sent as cookie `stv_session` → 401 (token is no session).
4. As `anna` «Adresse erneuern» → dialog «Adresse erneuern?» → «Abbrechen»: address unchanged.
5. «Adresse erneuern» → double click «Erneuern»: exactly one `POST /api/me/calendar/renew`, message «Neue Adresse erstellt. Die bisherige funktioniert nicht mehr.», new address shown; old address 404 at once, new one 200; reload shows the new address.

## 6. Privacy (criterion 8, CAL-08)

1. Anna's feed contains only her own responses (e.g. «nicht dabei («Krank»)» for her own response), never «Muss evtl. arbeiten» (Mia).
2. Mia's feed contains «vielleicht («Muss evtl. arbeiten»)».
3. Marc's and Petra's feeds contain no comments of others.

## 7. Dark mode and layout

1. As `marc` in dark mode, mobile and desktop: section, disclosures, buttons and the dialog are readable, no white on white; the long address stays inside the field.

## 8. Automated (criteria 9, 10)

1. `CalendarTest.cal09FeedIsValidICalendar` and `cal09MatchDayFieldByField` green; `scripts/check-links.py` green; `docs/later-features.md` lists the calendar as done.
