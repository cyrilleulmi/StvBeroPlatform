# 08 · Notifications

Status legend: see [README](../../README.md#requirement-status).

## Requirements

| ID | Trigger | Who is notified | Status |
|---|---|---|---|
| BEN-01 | The response deadline of an event is approaching and someone has not responded yet. People whose teams are all invited without obligation («freiwillig», [TER-16](03-events.md)) are not reminded. | The person without a response | Accepted |
| BEN-02 | Late cancellation: someone changes to «nicht dabei» or «vielleicht» after the response deadline. | Coaches of the participating teams | Accepted |
| BEN-03 | A call-up is published. | All players of the team | Accepted |
| BEN-04 | A called-up player cancels. | Coaches of the team | Decided |
| BEN-05 | A published call-up is changed. | The affected players | Assumption |
| BEN-06 | An imported match is moved or changed. | All members of the team | Accepted |
| BEN-07 | An event is cancelled or moved. | All members of the participating teams | Assumption |
| BEN-08 | Someone gets a special permission for an event or a series (type `EVENT_GRANTED`, subject «Sonderberechtigung», [RR-14](07-roles-permissions.md)). | The person, not the one who gave it | Assumption |
| DRV-11 | A car of the driver list changes (F-20, type `DRIVER_LIST_CHANGED`, subject «Fahrerliste geändert»). | The people affected, never the one who made the change ([14 · Driver list](14-driver-list.md)) | Decided |

## Further requirements

| ID | Requirement | Status |
|---|---|---|
| BEN-10 | Notifications go out by e-mail and by push via the PWA (both), in addition to the list in the app (E-71, F-19). | Decided |
| BEN-11 | Notifications are built so that the channel can be swapped and further channels can be added. | Decided |
| BEN-12 | A notification leads directly to the affected event. | Assumption |
| BEN-22 | Late cancellations (BEN-02) and a cancelling called-up player (BEN-04) also go to the people with a special permission for the event (RR-09), and the text ends with the reason or comment, if there is one: «… Grund: «Krank»» (ANM-07: only these recipients may see it). | Assumption |
| BEN-13 | There is at most one reminder per trigger. Nobody is reminded of the same event more than once. | Assumption |

## Channels: e-mail and push (F-19)

| ID | Requirement | Status |
|---|---|---|
| BEN-14 | Every notification is in the list in the app. E-mail and push come on top, through the same `Channel` interface as the app. | Decided |
| BEN-15 | The profile has a section «Benachrichtigungen» with one switch per channel. E-mail is on by default if the person has an e-mail address; without an address it is off and cannot be switched on. Push is off until the person allows it on this device. | Decided |
| BEN-16 | Switching on push asks for the browser permission and registers the device. A person can have several devices; the other devices are listed and can be removed. A denied permission is explained with a hint on how to allow it again. On an iPhone the hint says to add the app to the home screen first. | Decided (criteria), Assumption (list of devices) |
| BEN-17 | Push uses the service worker of the PWA and Web Push (VAPID, RFC 8291/8292). A tap on the push opens the event in the app (BEN-12). | Decided |
| BEN-18 | E-mails are German, plain and readable on a phone: subject with the essence («Abgesagt: Training Di 24.11.»), greeting, the sentence of the notification, a button to the event and a line on how to switch e-mails off. | Decided (criteria), Assumption (wording) |
| BEN-19 | A failed delivery does not block the change that caused it and is retried after 1, 5, 30 and 120 minutes; after the fifth attempt it is given up. A push subscription the push service reports as gone (404, 410) is removed. | Decided (criteria), Assumption (retry times) |
| BEN-20 | One notification leads to at most one e-mail and one push per device. No digest: every notification is sent on its own. | Decided (criteria), Assumption (no digest) |
| BEN-21 | In the profiles `dev` and `test` nothing is sent to the outside. E-mails and pushes are recorded and can be read with their channel through `GET /api/test/notifications`. | Decided |

### Configuration for production

Without configuration a channel is off and the app runs normally (only the list in the app). The provider for e-mails is open (O-02, hosting).

| Variable | Meaning |
|---|---|
| `STV_MAIL_FROM` | Sender of the e-mails, e.g. `STV Bero <noreply@stv-bero.ch>`. Required for e-mail |
| `STV_SMTP_HOST` | Mail server. Required for e-mail |
| `STV_SMTP_PORT` | Port, default `587` |
| `STV_SMTP_USER`, `STV_SMTP_PASSWORD` | Login at the mail server, if it needs one |
| `STV_SMTP_STARTTLS` | `REQUIRED` (default), `OPTIONAL` or `DISABLED` |
| `STV_SMTP_TLS` | `true` for implicit TLS (port 465, then `STV_SMTP_STARTTLS=DISABLED`), default `false` |
| `STV_VAPID_PUBLIC_KEY`, `STV_VAPID_PRIVATE_KEY` | VAPID key pair for push, base64url, e.g. from `npx web-push generate-vapid-keys`. Required for push. Changing the keys makes the existing devices invalid; people switch push on again |
| `STV_VAPID_SUBJECT` | Contact for the push services, `mailto:…` or `https://…`. Required for push |
| `STV_APP_URL` | Address of the app, for the button in the e-mail (also used by the calendar feed) |

In `dev` and `test` the simulator replaces the mail server and the push services; it creates a VAPID key in memory at every start, so the browser can register a device.
