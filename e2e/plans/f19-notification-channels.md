# Test plan F-19 · Notifications by e-mail and push

Issue #58, PR #73. Requirements BEN-10, BEN-11, BEN-14 to BEN-21, BEN-01 to BEN-07, RR-07 ([08-notifications.md](../../docs/requirements/08-notifications.md)), decision E-81, open point O-62. Seed: Kim has e-mail off, Lea has one push device (`a0000000-0000-4000-8000-000000000001`, «Chrome auf Android», endpoint `https://push.example.test/seed-lea-phone`), scenarios S57–S59 in [test-data.md](../../docs/test-data.md); state diagram «Delivery» in [states.md](../../docs/states.md). Default clock: Mon 16.11.2026 06:00.

Before every case: `POST /api/test/reset` (also resets `PUT /api/test/delivery`), log in via `POST /api/test/login` or `/dev-login`. Viewports: mobile 375×812 (all cases), desktop 1280×800 (cases 2, 3, 5). Dark mode once for the section «Benachrichtigungen». After every case: no console errors, no unexpected 4xx/5xx.

Event 01: `40000000-0000-4000-8000-000000000001` («Training Di 17.11.», Team 1 + Team 2).

The cloud browser has no reachable push service. Where a real subscription is needed, stub `Notification.permission`/`requestPermission` and `PushManager.subscribe` in the page (like `fakePush` in `f19-notification-channels.spec.ts`); the service worker is the real one.

## 1. Channels behind the interface; app keeps every notification (criterion 1, BEN-11; S57)

1. As `petra` cancel event 01 with reason «Halle gesperrt» (`/termine/<01>/bearbeiten` → «Termin absagen»).
2. `GET /api/test/notifications`.
   - Expect: 12 new notifications in the app (Kim included); 11 deliveries `EMAIL` (not Kim) to `<loginKey>@example.ch`, subject «Abgesagt: Training Di 17.11.», text «Training Di 17.11. ist abgesagt: Halle gesperrt», path `/termine/<01>`, status `SENT`, 1 attempt; exactly one `PUSH` (Lea's phone).
3. As `kim` open `/benachrichtigungen`. Expect: the cancellation is listed.

## 2. Section «Benachrichtigungen» (criterion 2, BEN-15)

1. As `anna` open `/profil`. Expect: region `notification-settings` «Benachrichtigungen»; switch «E-Mail» on, hint «An anna@example.ch»; switch «Push auf diesem Gerät» off.
2. Switch «E-Mail» off → message «E-Mails sind ausgeschaltet.»; reload → still off. Switch on again → «E-Mails sind eingeschaltet.».
3. As `kim` open `/profil`. Expect: «E-Mail» off.
4. A person without e-mail address (set by the administration): switch «E-Mail» disabled, hint «Für E-Mails braucht es eine E-Mail-Adresse. …».
5. Roles/API: without login `GET /api/me/notification-settings` → 401; `PUT` with invalid body → 400.
6. Robustness: double click on the e-mail switch → state consistent with the server after reload.
7. Concurrency (round 1): as `mia` (no stored choice) send `PUT /api/me/notification-settings` 6 times in parallel with alternating values → all 200.

## 3. Switching on push (criterion 3, BEN-16, RR-07)

1. As `anna`, permission denied (headless default): tap «Push auf diesem Gerät». Expect: switch stays off; alert `push-denied-hint` «Mitteilungen sind für diese App blockiert» with the desktop/Android text and «Lade die Seite danach neu …».
2. As `anna`, permission granted and `PushManager.subscribe` stubbed: tap the switch. Expect: `POST /api/me/push-subscriptions` 201, message «Push ist auf diesem Gerät eingeschaltet.», switch on; reload → on. Switch off → `DELETE`, «Push ist auf diesem Gerät ausgeschaltet.».
3. As `lea`: list `push-devices` «Weitere Geräte mit Push» shows «Chrome auf Android · seit 15.11.2026»; «Chrome auf Android entfernen» → removed, message «Push auf «Chrome auf Android» ist ausgeschaltet.».
4. Several devices: register a second endpoint for `lea` via API → both listed.
5. iPhone user agent (Safari, not standalone): `push-iphone-hint` with the three steps; push switch disabled.
6. Roles: as `anna` `DELETE /api/me/push-subscriptions/<Lea's device>` → 403, also as admin `petra`; without login 401. `POST` with non-https endpoint → 400.
7. Concurrency (round 1, states.md «never 500»): as `anna` send the same new `POST /api/me/push-subscriptions` body 8 times in parallel → all 201, one device. As `anna` and `lea` register the same endpoint in parallel → all 201, the device belongs to exactly one person.
8. Concurrent removal: as `lea` send `DELETE /api/me/push-subscriptions/a0000000-0000-4000-8000-000000000001` twice in parallel (two tabs or two devices) → 204 and 204 or 404, never 500. A sequential second `DELETE` → 404 «Dieses Gerät gibt es nicht.».
9. Removal while the device moves (round 2): as `lea` send `DELETE /api/me/push-subscriptions/a0000000-0000-4000-8000-000000000001` three times while `anna` posts the same endpoint (`https://push.example.test/seed-lea-phone`) three times, all in parallel → only 204/404 and 201, never 500; afterwards the endpoint belongs to at most one person and `GET /api/me/notification-settings` is consistent for both.
10. UI: as `lea` double tap «Chrome auf Android entfernen» → exactly one `DELETE` (204), message «Push auf «Chrome auf Android» ist ausgeschaltet.».

## 4. Service worker and tap (criterion 4, BEN-17)

1. `/sw.js` is served and registered on `/profil`.
2. Dispatch a `push` event with payload `{title, body, path:"/termine/<01>"}` in the service worker → a notification is shown; `notificationclick` → the app opens `/termine/<01>`.
3. `GET /api/me/notification-settings` gives a `pushPublicKey` (VAPID, base64url, 65 bytes).

## 5. E-mail content (criterion 5, BEN-18)

1. From case 1: subject has the essence, text one sentence, HTML has a button «Termin ansehen» to `…/termine/<01>`, and a line on how to switch e-mails off (profile).
2. Rescheduling: as `petra` move event 01 to another time → subject «Verschoben: Training», text names old and new date.
3. A reason with `<script>` is escaped in the HTML.

## 6. All types use the channels (criterion 6, BEN-01 to BEN-07)

1. Reminder to respond (BEN-01): set the clock to the reminder time of an event with open responses → reminder notifications have `EMAIL` deliveries (not Kim) and a `PUSH` for Lea if she is addressed.
2. Another type, e.g. a new event or a change by the coach → deliveries with the same rules.

## 7. Failure and retry (criterion 7, BEN-19; S58, S59)

1. `PUT /api/test/delivery {"failing":["EMAIL"]}`, as `petra` cancel event 01. Expect: 200, event cancelled; e-mails `PENDING`, 1 attempt, next attempt 06:01. Then `PUT /api/test/delivery {}`, clock 06:01, wait for the scheduler → `SENT`, 2 attempts.
2. `PUT /api/test/delivery {"expiredEndpoints":["https://push.example.test/seed-lea-phone"]}`, cancel event 01 → push `DISCARDED`; as `lea` `/profil` shows no other device; her e-mail `SENT`.

## 8. Nothing goes out in dev/test (criterion 8, BEN-21)

1. Backend log has no SMTP connection; deliveries are only recorded by the simulator and readable with `channel` in `GET /api/test/notifications`.

## 9. Production configuration (criterion 9)

1. `docs/requirements/08-notifications.md` documents `STV_MAIL_FROM`, `STV_SMTP_*`, `STV_VAPID_*`; covered by `ProdWithoutTestSupportTest` (channel off without configuration).

## 10. At most once (criterion 10, BEN-20)

1. From case 1: per notification at most one `EMAIL` and one `PUSH` per device (check duplicates of `notificationId, channel, address`).
2. Double click on «Termin absagen» → only one cancellation, no duplicate deliveries.

## 11. Documents (criterion 11)

1. BEN-10 says «decided: e-mail and push»; E-81 in decisions.md; O-05 closed.

## Dark mode

`/profil` as `lea` in dark mode, mobile: section, switches, hints, device list readable.
