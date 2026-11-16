# Test plan F-06 · Notifications

Issue #7 · Requirements BEN-01, BEN-02, BEN-03, BEN-04, BEN-05, BEN-07, BEN-11, BEN-12, BEN-13, RR-07 ·
Tests: `../tests/f06-benachrichtigungen.spec.ts`, backend `backend/src/test/java/ch/stvbero/platform/kern/benachrichtigung/BenachrichtigungenTest.java`, `…/kern/termin/BenachrichtigungsRegelnTest.java`

General, for every case:

- Setup: `POST /api/test/reset` (now = Mon 16.11.2026 06:00), then `POST /api/test/anmelden` with `{ "schluessel": "<person>" }` or `/dev-login`.
- Check notifications via `GET /api/test/benachrichtigungen`. Newly created ones have IDs that do not start with `90000000` (seed 01–18).
- Views: mobile 375×812, key cases also desktop 1280×800; `/benachrichtigungen` and the bell once in dark mode.
- After each case: no console errors, no unexpected 4xx/5xx.

IDs: team 1 `10000000-0000-4000-8000-000000000001`, team 2 `…002`. Events T01 practice Tue 17.11. 18:30 `40000000-0000-4000-8000-000000000001`,
T02 practice Thu 19.11. `…002`, T03 cancelled `…003`, T04 match day team 1 `…004` (call-up draft), T05 match day team 2 `…005` (call-up published).
People `20000000-0000-4000-8000-0000000000NN` (Petra 02, Marc 03, Anna 05, Lea 06, Mia 07, Nina 09, Kim 11, Eva 12, Ella 15).
Notifications `90000000-0000-4000-8000-0000000000NN` (Anna cancellation T03: 02, Lea cancellation T03: 03, Petra call-up T05: 13, Lea call-up T05: 14).

## Acceptance criteria

| No. | Case | Starting point | Steps | Expected |
|---|---|---|---|---|
| K1 | List and counter | Reset, `lea` | Open `/`; tap the bell | Bell «Benachrichtigungen 2 ungelesen», `benachrichtigungen-zaehler` = 2. `/benachrichtigungen`, title «Benachrichtigungen · STV Bero», list «Benachrichtigungen»: first call-up T05 (15.11. 21:00), then cancellation T03 (14.11. 10:00); both «Neu», `data-gelesen="false"` |
| K1b | No unread | Reset, `petra`, `mia`; `alex` | `/` and `/benachrichtigungen` | Petra/Mia: no counter, entries without «Neu», no button «Alle als gelesen markieren». Alex: «Keine Benachrichtigungen.» |
| K1c | Mark all as read | Reset, `lea` | `/benachrichtigungen` → «Alle als gelesen markieren»; reload | «Alle sind als gelesen markiert.», counter gone, stays after reload |
| K2 | S32 click opens event (BEN-12) | Reset, `anna` | `/benachrichtigungen` → tap the entry for T03; back | `/termine/<T03>` (cancelled with «Halle besetzt»), counter gone; back in the list the entry is read, `data-gelesen="true"`, stays after reload |
| K2b | Other people's (RR-07) | Reset, `anna` | API `POST /api/benachrichtigungen/<Lea 03>/gelesen`; own one twice | 403, and 204, 204. Without login 401. `GET /api/benachrichtigungen` returns only own ones |
| K3 | S5 late cancellation (BEN-02) | Reset, clock 16.11.2026 19:00, `anna` | `/termine/<T01>` → «nicht dabei»; then «vielleicht» with comment | Exactly one new `KURZFRISTIGE_ABSAGE` to Petra «Anna Muster hat sich kurzfristig für Training Di 17.11. abgemeldet.», none to Marc or Anna. Further change: no second one. Petra sees it in `/benachrichtigungen`, counter 1 |
| K4 | S6 reminder (BEN-01, BEN-13) | Reset | Clock 16.11.2026 06:31; then 07:00 | Exactly two `ERINNERUNG` for T01 to Nina and Ella; none more after 07:00. Nina sees counter 1 and the reminder. None before 06:30 (clock 06:29) |
| K5 | S9 publish (BEN-03) | Reset, `petra` | `/termine/<T04>/aufgebot` → «Veröffentlichen» → confirm | 7 new `AUFGEBOT_VEROEFFENTLICHT` to all players of team 1 (Anna, Lea, Mia, Sara, Nina, Jana, Kim); Kim and Nina «nicht aufgeboten», others «aufgeboten». Not Petra |
| K6 | S10 called-up player declines (BEN-04) | Reset, `eva` | `/termine/<T05>` → «nicht dabei» | Marc receives «Eva Steiner ist für Spieltag Sa 28.11. aufgeboten und hat sich abgemeldet.», Eva does not; Marc sees it with counter |
| K7 | Call-up change (BEN-05) | Reset, `marc` | `/termine/<T05>/aufgebot`: tick Lea, untick Ella | Exactly Lea («neu aufgeboten») and Ella («nicht mehr aufgeboten») receive a notification, nobody else. A change in draft T04 (Petra) notifies nobody |
| K8a | Cancellation (BEN-07) | Reset, `petra` | `/termine/<T01>` → cancel with reason | All 12 members of team 1 + 2 except Petra receive `TERMIN_ABGESAGT` with reason |
| K8b | Rescheduling (BEN-07) | Reset, `petra` | `/termine/<T01>/bearbeiten` → start 19:00 | All 12 except Petra receive `TERMIN_VERSCHOBEN`. Changing only the location: none |
| K9 | Interface (BEN-11) | Code | `Kanal`, `AppKanal`, `Benachrichtigungen.senden`, test `ben11…` | Sending goes through the interface, backend test with `TestKanal` green |

## Edge cases

| No. | Case | Steps | Expected |
|---|---|---|---|
| G1 | Roles | `marc` cancels T01 (coach of team 2 only) | Not offered or 403, no notification |
| G2 | Trigger | Petra responds on behalf of Anna at short notice | Petra receives none (O-50) |
| G3 | Double click | «Alle als gelesen markieren» twice, tap an entry twice | No errors, counter correct |
| G4 | Reload | Reload `/benachrichtigungen`, deep link without login | Redirect to `/dev-login?ziel=%2Fbenachrichtigungen`, then back |
| G5 | Theme | Bell on mobile top right and on desktop, light and dark | Readable, covers nothing, sufficient contrast |
| G6 | Bell covers nothing | Mobile `petra` → `/termine/<T01>`, scroll so that «Antwort für Mia Frei eintragen» is at the top right | The bell does not sit over controls of the content; a tap on the right edge of the pencil button hits the button |
| G7 | Focus under header (O-51) | Mobile `petra` → `/termine/<T01>`, scroll to the bottom, Shift+Tab backwards through all controls of `main` | No focused element lies under the header (top 68px) or the bottom navigation (`scroll-padding`) |
| G8 | Header dark mode | Mobile dark, `lea`, `/termine/<T01>` and `/benachrichtigungen` | Header with dark background and divider, bell with counter readable; no header from 1024px |
