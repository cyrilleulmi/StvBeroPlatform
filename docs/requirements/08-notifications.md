# 08 · Notifications

Status legend: see [README](../../README.md#requirement-status).

## Requirements

| ID | Trigger | Who is notified | Status |
|---|---|---|---|
| BEN-01 | The response deadline of an event is approaching and someone has not responded yet. | The person without a response | Accepted |
| BEN-02 | Late cancellation: someone changes to «nicht dabei» or «vielleicht» after the response deadline. | Coaches of the participating teams | Accepted |
| BEN-03 | A call-up is published. | All players of the team | Accepted |
| BEN-04 | A called-up player cancels. | Coaches of the team | Decided |
| BEN-05 | A published call-up is changed. | The affected players | Assumption |
| BEN-06 | An imported match is moved or changed. | All members of the team | Accepted |
| BEN-07 | An event is cancelled or moved. | All members of the participating teams | Assumption |

## Further requirements

| ID | Requirement | Status |
|---|---|---|
| BEN-10 | Which channel is used for notifications (e-mail, push via the PWA or both) is not defined. | Open |
| BEN-11 | Notifications are built so that the channel can be swapped and further channels can be added. | Assumption |
| BEN-12 | A notification leads directly to the affected event. | Assumption |
| BEN-13 | There is at most one reminder per trigger. Nobody is reminded of the same event more than once. | Assumption |
