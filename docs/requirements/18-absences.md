# 18 · Absences

Players (and every other member) enter longer absences such as holidays or an injury once, instead of declining every practice by hand (E-93).

Status legend: see [README](../../README.md#requirement-status).

| ID | Requirement | Status |
|---|---|---|
| ABS-01 | `/profil/abwesenheiten` («Abwesenheiten», link from `/profil` and from the calendar): enter an absence with first day, last day (inclusive), reason «Ferien», «Verletzung» or «Anderes» and an optional note (120 characters). Change and delete (dialog) own absences. Every person manages only her own; someone else's absence answers 404. | Decided (asked for), Assumption (reasons, only own) |
| ABS-02 | The last day is not before the first; at most one year; not completely in the past; «Anderes» needs a note (422); two absences of the same person do not share a day (409). | Assumption |
| ABS-03 | Every upcoming practice (type practice, not cancelled, not started) of one of the person's teams that touches a day of the absence (Europe/Zurich) is answered «nicht dabei» with the comment «Abwesend: Ferien · <Bemerkung>» («Abwesend: Verletzung …», «Abwesend: <Bemerkung>» for «Anderes»). An own «nicht dabei» keeps its comment; «dabei» and «vielleicht» are replaced when the absence is entered. Match days and other events are not changed. The coaches see the reason like any comment. No notification, no late cancellation. | Decided (practices, with reason), Assumption (rest) |
| ABS-04 | A response set by an absence remembers it. Practices created, moved or given more teams later, and teams a person joins later, follow the absence. A response the person gives by hand afterwards wins and belongs to her again. When a series is changed, a practice with only absence responses counts as without responses. | Assumption |
| ABS-05 | Changing an absence updates its responses: practices no longer covered are «offen» again, newly covered ones are declined. Deleting it makes its upcoming practices «offen» again; past ones keep «nicht dabei». | Assumption |
| ABS-06 | The list shows absences that are not over yet, by first day, each with the number of upcoming practices it declined. | Assumption |
| ABS-07 | Absences are private: only the person herself sees them (list, calendar). Others see only the declined response with its comment, with the usual comment rights (ANM-07). | Assumption |
