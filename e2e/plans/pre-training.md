# Test plan · Pre-training («Vortraining»)

Requirements TER-29 to TER-32 and ANM-14 to ANM-17, decisions E-117 and E-119 · Tests: `../tests/pre-training.spec.ts` (V1–V4), `../tests/pre-training-responses.spec.ts` (V5–V7)

General for every case:

- Preparation: `POST /api/test/reset` (now = Mon 16.11.2026 06:00), then `POST /api/test/login` with `{ "loginKey": "petra" }` (coach of Team 1).
- Views: mobile 375×812 and desktop 1280×800, light and dark.
- Elements: switch «Mit Vortraining» (`pre-training-toggle`), fields «Was» (`pre-training-title`) and «Beginn» (`pre-training-time`); every place that shows a pre-training renders `pre-training` with the title and the start as «HH:mm»; the series page shows `series-pre-training`.

| No. | Case | Steps | Expected |
|---|---|---|---|
| V1 | Practice with pre-training | `/termine/neu`, practice Fri 20.11. 18:00–19:30; switch on; «Was» «Joggen»; «Beginn» 17:30 → «Termin speichern» | The start is suggested as 17:45 when the switch goes on; the event page shows «17:30» and «Joggen» |
| V2 | Start not before the practice | Same, «Beginn» 18:30; then 17:30 with an empty «Was» | «Das Vortraining muss vor dem Training beginnen.», then «Bitte angeben, was im Vortraining gemacht wird.»; both fields `aria-invalid`; nothing is saved |
| V3 | Remove | Event with pre-training → «Termin bearbeiten»; switch is on and the fields filled; switch off → «Änderungen speichern» | The event page shows no pre-training |
| V4 | Series | `/termine/neu?art=serie`, Monday 19:30–21:00, 16.11. to 14.12.; switch on; suggestion 19:15; «Joggen» 19:00 → «Serie anlegen» | The series page shows «19:00 Joggen»; the event of Mon 23.11. shows «19:00» and «Joggen» |

## Responses to the pre-training (ANM-14 to ANM-17)

Preparation for V5 to V7: after the reset Petra creates through the API a practice of Team 1 on Fri 20.11. 18:00–19:30 with the pre-training «Joggen» at 17:30. Anna and Lea play in Team 1, Petra coaches it.

Elements: the block `pre-training-response` below the response buttons with `pre-training-question` and the buttons `pre-training-response-attending` and `pre-training-response-declined`; in the overview the chip `overview-pre-training` per person and the count `overview-pre-training-total`; in the dialog «Antwort für … eintragen» the group «Vortraining».

| No. | Case | Steps | Expected |
|---|---|---|---|
| V5 | Second question on the event page | As Anna open the practice; «Dabei»; in the block «Dabei», then «Nicht dabei»; reload; «Nicht dabei» at the practice (skip the reason); «Dabei» again | No block before «Dabei». Then «Auch beim Vortraining?» with «17:30 · Joggen», no button pressed. After the answer the block reads «Vortraining» and says «Gespeichert: beim Vortraining dabei.», then «Gespeichert: ohne Vortraining.»; the practice stays «dabei»; the overview marks Anna «ohne Vortraining»; the answer survives the reload. With «nicht dabei» at the practice the block and the mark are gone; with the next «Dabei» the question is open again |
| V6 | Second question on the card | As Anna `/termine`; on the card of the practice «Dabei», then «Dabei» in the block; reload | The block appears in the card right below the buttons and keeps the answer; no other card has a block |
| V7 | Coach, on behalf, counts | As Petra open the practice; «Dabei» at both; «Antwort für Anna Muster eintragen»: «nicht dabei», then «dabei» and «Vortraining» «dabei» → «Speichern»; «Antwort für Lea Graf eintragen»: «dabei» → «Speichern» | Petra shows «mit Vortraining» in the coach block and is not counted. In the dialog the group «Vortraining» is disabled with «nicht dabei» («Nur mit «dabei» beim Training.») and reads «Noch offen» until chosen. Anna shows «mit Vortraining», Lea «Vortraining offen»; the count «Vortraining» goes from «0 dabei» to «1 dabei»; the dialog of Anna opens with «dabei» chosen for the pre-training |
