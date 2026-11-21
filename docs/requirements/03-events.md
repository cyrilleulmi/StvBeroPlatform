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
| TER-11 | Coaches (of any team, RR-03) can create, edit and cancel events. | Accepted |
| TER-12 | If an event is cancelled or moved, everyone affected is notified. | Assumption |
| TER-13 | Each person sees the upcoming events of all their teams in one list, with their own response. | Assumption |
| TER-14 | The event list tells the kinds of events apart at a glance: every entry shows the colour and the name of its event variant ([VAR-05](16-event-variants.md)). Colours and names are settings of the team with defaults. | Decided |
| TER-15 | **Expected or invited.** In an event of several teams each team is either expected («erwartet», the default) or invited without obligation («freiwillig»): a joint practice where the first team must respond and the second one is welcome to join. At least one team is expected. Coaches set it per team in the form of an event or a series; events of a series take it over. A person is «freiwillig» in an event if all her teams there are invited; someone who is also in an expected team is expected. | Decided |
| TER-16 | People who are «freiwillig» respond if they like, exactly like everyone else, and appear in the overview with their team. They get no reminder before the response deadline ([BEN-01](08-notifications.md)). Without a response they are not counted as «offen»: the counts of the event and of an invited team hold only those who responded ([ANM-23](04-responses.md)). An invited team has no warnings (ANM-40), and the event does not count in the statistics of that team (STA). Cancellations and reschedulings still go to everyone. | Decided (no reminder), Assumption (counts, warnings, statistics, cancellations) |
| TER-17 | The event list marks clearly what is voluntary for the person: a chip «Freiwillig» on the entry, and the teams line names the invited teams («Damen 1 + Damen 2 (freiwillig)»). A switch «Freiwillige Termine» shows or hides these entries; the choice is in the address (`?freiwillig=aus`). The default shows them. The event page says «Für dich freiwillig» above the own response, and the overview marks an invited team «freiwillig». | Decided (mark, filter), Assumption (default shown, wording) |

## Event types

| Type | Teams | Specifics |
|---|---|---|
| Practice | one or more | Usually a series. |
| Match day | one (assumption) | Includes several matches (tournament format), has a meeting point, a venue with a Google Maps link and a call-up. Details: [05 · Match days and call-up](05-match-days-call-up.md). |
| Other event | one or more | Anything else, e.g. a team event. Responses only. |

How an event of a type looks and behaves in a team (name, colour, call-up, sections) is set by its [event variant](16-event-variants.md).

## Not part of the core

- Calendar subscription (iCal): built with F-18, see [13 · Calendar subscription](13-calendar.md).
