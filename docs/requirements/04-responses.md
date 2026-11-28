# 04 · Responses

Status legend: see [README](../../README.md#requirement-status).

## Responses

| ID | Requirement | Status |
|---|---|---|
| ANM-01 | Players and coaches respond to events. Coaches do it the same way as players. | Decided |
| ANM-02 | There is exactly one response per person and event, not one per team. | Accepted |
| ANM-03 | Possible responses: «dabei» (attending), «nicht dabei» (declined), «vielleicht» (maybe). | Decided |
| ANM-04 | With «vielleicht» a comment is required. | Decided |
| ANM-05 | With «nicht dabei» a reason is optional, unless the event variant requires one (VAR-12): then «nicht dabei» is only saved with a reason («Bitte gib einen Grund an.», 422 with rule ANM-05), also on behalf of someone. The app asks for the reason before saving. | Accepted (optional), Decided (configurable, E-95) |
| ANM-06 | As long as someone has not responded, the status is «offen» (pending). Nobody is attending automatically. | Accepted |
| ANM-07 | Comments and reasons are visible only to coaches (of any team, RR-03), admins, people with a special permission for the event (RR-09) and the person themselves. | Accepted |
| ANM-08 | The most common action («dabei» / «nicht dabei») takes one tap, directly from the event list. | Decided |
| ANM-09 | A response can be changed at any time, also after the response deadline. | Assumption |
| ANM-10 | If someone changes from «dabei» to «nicht dabei» or «vielleicht» after the response deadline, it counts as a late cancellation. The coaches are notified. | Accepted |
| ANM-11 | A coach (of any team, RR-03) can respond on behalf of anyone in the event, e.g. when someone cancelled in person. | Accepted |
| ANM-12 | For responses on behalf, it is visible who entered them. | Assumption |
| ANM-13 | On a match day, someone who responds «dabei» can add «Ich reise individuell an» (travels on her own). She then needs no seat in the driver list ([DRV-20](14-driver-list.md)). The mark is visible to everyone who sees the responses, and it goes away when the response is no longer «dabei». Coaches set it on behalf together with the response. | Decided |
| ANM-14 | **Response to the pre-training (E-119).** A practice with a pre-training ([TER-29](03-events.md)) asks two things: the response to the practice (ANM-03) and, for a person who is «dabei», whether she is at the pre-training too: «dabei» or «nicht dabei», one tap each, right below the response buttons («Auch beim Vortraining?»). Until she says so the pre-training is «offen» for her; nobody is at the pre-training automatically (ANM-06). There is no «vielleicht» and no comment for the pre-training. | Decided (Cyrille, 28.11.2026), Assumption (two answers only, open until answered) |
| ANM-15 | Only the pre-training without the practice is not possible: «dabei» at the pre-training needs «dabei» at the practice (422 with rule ANM-15 otherwise). Without «dabei» at the practice the pre-training follows the practice: «nicht dabei», «vielleicht» or «offen». A change away from «dabei» at the practice removes the response to the pre-training; it is asked again with the next «dabei». | Decided (Cyrille, 28.11.2026), Assumption (follows the practice, asked again) |
| ANM-16 | Coaches respond to the pre-training the same way as players (ANM-01). Whoever may respond on behalf of someone (ANM-11) enters the pre-training with it, in the same dialog. | Decided (Cyrille, 28.11.2026), Assumption (on behalf) |
| ANM-17 | The overview of the event (ANM-20) shows who is at the pre-training: a count «Vortraining» for the whole event and per team (players only, like ANM-23), and for every person who is «dabei» a mark «mit Vortraining», «ohne Vortraining» or «Vortraining offen». The deadline, the late cancellation (ANM-10), reminders, the attendance check and the statistics refer to the practice alone; a changed response to the pre-training notifies nobody. | Assumption |
| ANM-18 | How many are at the pre-training is visible at once, without opening the overview (E-120): the cards of the event list and of the start page and the event page show the number of players who are «dabei» at the pre-training right at it («6 dabei»), next to the counts of the practice (ANM-23). The same players are counted as in ANM-17. The number follows a response without reloading. A cancelled event shows none. | Decided (Cyrille, 28.11.2026) |

## Event view

| ID | Requirement | Status |
|---|---|---|
| ANM-20 | The view shows the members grouped by team. | Decided |
| ANM-21 | Within a team, players are grouped by line: lines in their order, then goalies, then «Ohne Linie» (no line). | Decided (grouping), Accepted (order) |
| ANM-22 | Coaches are in a block of their own, with their team shown. They do not appear among the players. | Decided |
| ANM-23 | Per team and for the whole event the counts are: attending, declined, maybe, pending. Only players are counted. Players of a team that is invited without obligation count once they respond, never as pending ([TER-16](03-events.md)). | Accepted |
| ANM-24 | Each person's status can be seen at a glance. | Accepted |
| ANM-25 | All members of the participating teams see the status of the others, but not their comments (see ANM-07). | Assumption |

## Dual roles in the same event

If an event belongs to several teams, the same person can be in more than one of them.

| ID | Requirement | Status |
|---|---|---|
| ANM-30 | The person gives a single response. It applies to the whole event. | Accepted |
| ANM-31 | The person appears in each participating team in their team role there: as a player in her line, as a coach in the coach block. | Accepted |
| ANM-32 | In the team total she counts in every team where she is a player. In the overall total of the event she counts only once. | Accepted |

## Example: joint practice of two teams

```
Di 18:30 · Team 1 + Team 2          19 dabei · 4 ab · 5 offen

Team 1                              10 dabei · 2 ab · 3 offen
  Linie 1     Anna ✓  Lea ✓  Mia ✗  Sara ✓  Nina ?
  Linie 2     ...
  Goalies     Jana ✓
  Ohne Linie  Kim ✓
Team 2                              9 dabei · 2 ab · 2 offen
  Linie 1     ...

Coaches       Petra ✓ (Team 1)  Marc ✗ (Team 2)
```

The sketch shows the structure, not the design. For the look, [design-principles.md](../design-principles.md) applies. «Vielleicht» is missing from the sketch and is added as its own counter and its own symbol.

## Warnings (F-16)

Coaches see at a glance when an event has a gap, for example no goalie (E-79). The call-up has the same warnings, see [AUF-30](05-match-days-call-up.md#warnings-in-the-call-up-f-16).

| ID | Requirement | Status |
|---|---|---|
| ANM-40 | The overview of an event shows coaches and admins warnings above the list of responses: «Kein Goalie angemeldet» when fewer goalies than the minimum responded «dabei», «Nur <n> Feldspielerinnen angemeldet» when fewer field players than the minimum responded «dabei», «<Linie> nur zu <n>» when a line has fewer players «dabei» than the line minimum. | Accepted |
| ANM-41 | The minimums are settings per team with defaults: 1 goalie, 10 field players for match days, 6 for practices, 3 per line. Coaches and admins change them on the team page. 0 switches a warning off. | Accepted (defaults, page), Assumption (0 switches off, applies to the team in every season) |
| ANM-42 | Warnings are shown for upcoming events only, not for cancelled events. Other events (e.g. «Teamessen») have no warnings. | Accepted, Assumption (other events) |
| ANM-43 | Players do not see warnings, neither in the UI nor in the API. | Accepted |
| ANM-44 | Warnings are not only a colour: each has an icon and a text (`accent-mustard`) and is announced to screen readers. | Accepted |
| ANM-45 | The event list for coaches and admins marks events with at least one warning («3 Warnungen»). | Accepted |
| ANM-46 | Warnings apply per team, with the minimums of that team. In an event of several teams each warning names its team («Team 2: Kein Goalie angemeldet»). Field players are all players outside the goalies, including «Ohne Linie». A line nobody of comes gives no line warning (the field player warning covers it). | Assumption |

## Not part of the core

- Attendance check (who was really there): built with F-14, see [11-attendance-check.md](11-attendance-check.md).
- Notifications about warnings: not planned (out of scope of F-16).
