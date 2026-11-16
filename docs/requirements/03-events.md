# 03 · Events

That a practice can belong to several teams at once is one of the two main reasons for building our own app.

Status legend: see [README](../../README.md#requirement-status).

## Requirements

| ID | Requirement | Status |
|---|---|---|
| TER-01 | A practice can belong to several teams at once. | Decided |
| TER-02 | From the start there are the event types practice, match day and other event. Responding works the same for all of them. | Accepted |
| TER-03 | Each event belongs to at least one team. | Accepted |
| TER-04 | An event has a date, start, end, location and a free text field for notes. | Assumption |
| TER-05 | Series: a recurring event is entered once, e.g. «jeden Dienstag 18:30, Halle X, Team 1 + Team 2» (every Tuesday 18:30, hall X, Team 1 + Team 2). | Accepted |
| TER-06 | Single dates of a series can be cancelled or moved without changing the series. | Accepted |
| TER-07 | Holidays and other periods can be excluded from a series. | Accepted |
| TER-08 | Changes to a series apply to future events. Responses already given are kept. | Assumption |
| TER-09 | Events have a response deadline: X hours before the start. It controls reminders and what counts as late (see [04](04-responses.md) and [08](08-notifications.md)). | Accepted |
| TER-10 | The response deadline can be set per event or series, with a sensible default. | Assumption |
| TER-11 | The coach of a participating team can create, edit and cancel events. | Accepted |
| TER-12 | If an event is cancelled or moved, everyone affected is notified. | Assumption |
| TER-13 | Each person sees the upcoming events of all their teams in one list, with their own response. | Assumption |

## Event types

| Type | Teams | Specifics |
|---|---|---|
| Practice | one or more | Usually a series. |
| Match day | one (assumption) | Includes several matches (tournament format), has a meeting point, a venue with a Google Maps link and a call-up. Details: [05 · Match days and call-up](05-match-days-call-up.md). |
| Other event | one or more | Anything else, e.g. a team event. Responses only. |

## Not part of the core

- Calendar subscription (iCal): see [Later features](../later-features.md).
