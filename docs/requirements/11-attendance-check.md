# 11 · Attendance check

After an event the coach ticks off who was really there (decision E-70). The check is optional and **off by default**: it is switched on per event or per series.

Status legend: see [README](../../README.md#requirement-status).

## Background

The app knows **responses** (intention) and, where recorded, **attendance** (fact). Both stay separate: a response is never turned into attendance, and attendance never changes a response.

## Switch

| ID | Requirement | Status |
|---|---|---|
| PRS-01 | Creating or editing an event offers the switch «Präsenz erfassen». It is off by default. A series has the same switch; it applies to all events the series creates or changes, and a single event can deviate (it then counts as individually changed, TER-06). | Decided |
| PRS-07 | For an event with the switch off, no attendance can be recorded: the UI does not offer it, the API answers 409 (rule `PRS-07`). | Decided |
| PRS-08 | Switching the check off on an event that already has recorded attendance asks for confirmation. The recorded attendance is kept and shows again when the check is switched back on. | Decided |

## Recording

| ID | Requirement | Status |
|---|---|---|
| PRS-02 | For an event with the switch on, coaches and admins see «Präsenz erfassen» on the event from its start time on. Before the start it is not offered (API 409, rule `PRS-02`). There is no end: attendance can be recorded and corrected any time after the start. | Decided (start), Assumption (no end) |
| PRS-03 | The screen lists the players of the involved teams grouped by line (lines, goalies, «Ohne Linie», as in the overview) with their response next to the name. Each player is marked «anwesend», «abwesend» or «entschuldigt». Players who responded «dabei» are preselected as «anwesend», all others as «abwesend». Nothing is saved until the coach saves. | Decided |
| PRS-04 | Saving stores who recorded the attendance and when. It can be corrected later; the last value counts. Players that are not in the saved list keep their earlier value. | Decided (correction), Assumption (partial list) |
| PRS-05 | Coaches are not part of the attendance list (same rule as the call-up, AUF-28). Recording attendance for a coach or a person outside the teams is refused (422, rule `PRS-05`). | Decided |
| PRS-09 | A cancelled event takes no attendance (409, rule `PRS-09`). | Decided |
| PRS-10 | Recording is for coaches (of any team, E-69) and admins; everyone else gets 403. In an archived season attendance is read-only (SAI-02). | Accepted |

## Visibility

| ID | Requirement | Status |
|---|---|---|
| PRS-06 | Players see their own recorded attendance on the event, not that of others. Nothing is shown as long as nothing is recorded for them, or while the check is switched off. | Decided (own only), Assumption (hidden while off) |

## Not part of this feature

| ID | Requirement | Status |
|---|---|---|
| PRS-11 | Statistics per player and season (F-15) and an export, e.g. for J+S. | Later |
