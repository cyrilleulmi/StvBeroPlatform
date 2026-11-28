# Test plan · Pre-training («Vortraining»)

Requirements TER-29 to TER-32, decision E-117 · Tests: `../tests/pre-training.spec.ts`

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
