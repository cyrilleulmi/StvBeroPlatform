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
| TER-05 | Series: a recurring event is entered once, e.g. «jeden Dienstag 18:30, Halle X, Team 1 + Team 2» (every Tuesday 18:30, hall X, Team 1 + Team 2). How far ahead its events exist: TER-22; its end: TER-23. | Accepted |
| TER-06 | Single dates of a series can be cancelled or moved without changing the series. | Accepted |
| TER-07 | Holidays and other periods can be excluded from a series. | Accepted |
| TER-08 | Changes to a series apply to future events. Responses already given are kept. | Assumption |
| TER-09 | Events have a response deadline: X hours before the start. It controls reminders and what counts as late (see [04](04-responses.md) and [08](08-notifications.md)). | Accepted |
| TER-10 | The response deadline can be set per event or series, with a sensible default. | Assumption |
| TER-11 | Coaches (of any team, RR-03) can create, edit and cancel events. | Accepted |
| TER-12 | If an event is cancelled or moved, everyone affected is notified. | Assumption |
| TER-13 | Each person sees the upcoming events of all their teams in one list, with their own response. | Assumption |
| TER-14 | The event list tells the kinds of events apart at a glance: every entry shows the colour and the name of its event variant ([VAR-05](16-event-variants.md)). Colours and names are settings of the team with defaults. Since E-105 every event of the list is a card like on the start screen, with a court line along the top in the colour of its event variant; the next practice and the next match day are also filled with a light tint of their colour, on the list and on the start screen. | Decided |
| TER-15 | **Expected or invited.** In an event of several teams each team is either expected («erwartet», the default) or invited without obligation («freiwillig»): a joint practice where the first team must respond and the second one is welcome to join. At least one team is expected. Coaches set it per team in the form of an event or a series; events of a series take it over. A person is «freiwillig» in an event if all her teams there are invited; someone who is also in an expected team is expected. | Decided |
| TER-16 | People who are «freiwillig» respond if they like, exactly like everyone else, and appear in the overview with their team. They get no reminder before the response deadline ([BEN-01](08-notifications.md)). Without a response they are not counted as «offen»: the counts of the event and of an invited team hold only those who responded ([ANM-23](04-responses.md)). An invited team has no warnings (ANM-40), and the event does not count in the statistics of that team (STA). Cancellations and reschedulings still go to everyone. | Decided (no reminder), Assumption (counts, warnings, statistics, cancellations) |
| TER-17 | The event list marks clearly what is voluntary for the person: a chip «Freiwillig» on the entry, and the teams line names the invited teams («Damen 1 + Damen 2 (freiwillig)»). A switch «Freiwillige Termine» (since E-111 a chip «Freiwillige» in the row of the team chips, TER-24) shows or hides these entries; the choice is in the address (`?freiwillig=aus`). The default shows them. The event page says «Für dich freiwillig» above the own response, and the overview marks an invited team «freiwillig». | Decided (mark, filter), Assumption (default shown, wording) |
| TER-18 | The series of a team can be listed with their upcoming events (count and the next one); series that still run come first. | Decided (E-101) |
| TER-19 | Before a change of a series is saved, the app says what it does to the upcoming events: how many take over the new details, how many are new, how many no longer fit and are removed, and how many are cancelled because someone has already responded (everyone is notified). Past events and events changed by hand stay as they are, and the app says so. | Decided (E-101) |
| TER-20 | A series can be ended from a day on: its planned upcoming events from that day on are removed, or cancelled («Serie beendet») where someone has already responded; past events stay. The app asks first and says how many events go. | Decided (E-101) |
| TER-21 | **Rework events («Termine überarbeiten»).** Coaches and admins find the button «Überarbeiten» on `/termine` (since E-111 an icon, TER-24). It opens `/termine/ueberarbeiten?team=<id>` (the team is handed over when exactly one team chip is on), where the events of a team are reworked in one place: the series (change with the preview of TER-19, holidays TER-07, end TER-20, new series), the next single events with a link to edit them, the match day settings (TML-02, AUF-32), the link with swiss unihockey (SUH-02) and the event variants (VAR). Clear hints say that changes apply to upcoming events only and warn before something is removed or cancelled. | Decided (E-101), Assumption (content of the page) |
| TER-22 | **One year ahead.** The events of a series exist up to one year from today. As time goes on the app creates the events that are missing, regularly and by itself, so a series never stops without a reason: only its end (TER-23), ending it (TER-20) or a change stops it. A series does not end with its season; new events belong to the season of their day. If that season does not exist yet, or the teams are not part of it yet, they belong to the season of the series and move into their season once it is there with the teams; from then on its team memberships decide who is in the event (SAI-01). Days that are excluded (TER-07) stay excluded for events created later, and an event that was removed does not come back. The series says up to which day its events exist. | Decided (E-102) |
| TER-23 | **End of a series.** The last day of a series is optional. Without it the series runs on (TER-22); with it no events are created after that day. A series may last longer than a year (before: one year at most). The end can be set, changed and removed when the series is changed (TER-19), also in the assistant «Neues Team» (PTF-13), where each series of the team can be changed, get holidays and be ended right in the step «Trainings». | Decided (E-102) |
| TER-24 | **Compact event list (E-111).** On a phone the list starts with little above the first event: the title and the actions in one row, the actions as icons with their name for screen readers and as a tooltip («Kalender»; coaches and admins «Überarbeiten» and «Termin erfassen»); then the switch «Kommend»/«Vergangen» and one row of chips that scrolls sideways: «Freiwillige» (TER-17) and one chip per team, switched on and off by a tap, several at once. The own teams are on by default (TER-13); coaches and admins choose from all teams of the season (E-69). The choice is in the address (`?teams=<id>,<id>`; `?team=<id>` of earlier links still works). Every event the person may edit (coach, admin, a special permission for the event or its series, RR-09, RR-10) has a pencil that opens «Termin bearbeiten» directly. The statistics are no longer linked from here (STA-01). | Decided (icons, chips, pencil, statistics), Assumption (own teams on by default, last team stays on, chip row) |

## Event types

| Type | Teams | Specifics |
|---|---|---|
| Practice | one or more | Usually a series. |
| Match day | one (assumption) | Includes several matches (tournament format), has a meeting point, a venue with a Google Maps link and a call-up. Details: [05 · Match days and call-up](05-match-days-call-up.md). |
| Other event | one or more | Anything else, e.g. a team event. Responses only. |

How an event of a type looks and behaves in a team (name, colour, call-up, sections) is set by its [event variant](16-event-variants.md).

## Not part of the core

- Calendar subscription (iCal): built with F-18, see [13 · Calendar subscription](13-calendar.md).
