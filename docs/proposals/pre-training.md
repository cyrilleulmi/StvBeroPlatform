# Proposals: how the pre-training shows

Status: **to decide (O-77)**. Cyrille asked for three to five designs of how the pre-training of a practice («Vortraining», TER-29) shows on the cards and on the event page (E-118). All five run in the app on the real pages with the real data; one is chosen, the others and the switch bar are removed.

## Try them

1. Start the app with the club data set and sign in as someone of the women's teams, e.g. Petra Keller (`/dev-login`).
2. Open the start page, `/termine` or a practice with a pre-training (Thursday «Joggen», Friday «Kraft & Stabilität»).
3. Switch with the dark bar at the bottom («Design B · Ablauf», arrows or the arrow keys), or with the address: `?vortraining=a` to `?vortraining=e`. The choice stays while moving through the app.

The bar exists in test mode only and only while a pre-training is on the page. Without a choice proposal A shows.

## The question

A pre-training moves the moment people have to be there: «Joggen» at 20:00, the practice at 20:20. The card must say both without burying the time of the practice, on a phone with about 270px of width inside the card. The proposals differ in what leads: the usual time line, the order of the evening, its proportions, or the time to be there.

## The proposals

| | A «Zeile» | B «Ablauf» | C «Zeitbalken» | D «Anzeigetafel» | E «Ticket» |
|---|---|---|---|---|---|
| Picture | One more line under the time | Two stops on a court line, top to bottom | The evening to scale as a bar | The time to be there on a small scoreboard | A coupon attached to the event |
| Card | «Do 03.12. · 20:20–21:50», below it a stopwatch and «Vortraining 20:00 · Joggen» | The date, then «20:00 Joggen Vortraining» and «20:20 Training bis 21:50» with a ring and a dot on one line | The date, then «20:00», «20:20» and «21:50» above a bar: the pre-training hatched, the practice filled; below «Joggen · 20 Min. Vortraining» | The date, then a board with «20:00» and «da sein», next to it «Joggen» and «dann Training 20:20–21:50» | The usual line, below it a coupon with notches: «Vorher 20:00», a tear line, «Joggen», «Vortraining · 20 Min.» |
| Event page | One line: «Vortraining 20:00 · Joggen», «20 Min. vor dem Training» | A card «Ablauf» with three stops: pre-training, practice, end, each with its duration | A card with the bar and a legend for both parts with their durations | Two boards side by side: «20:00 da sein Joggen» › «20:20 Training bis 21:50» | The coupon in large |
| What leads | The time of the practice, as today | The order | The proportions | The time to be there | The time of the practice; the pre-training is an eye-catcher |
| Strong | Quiet, changes least, a card with a pre-training is as high as one without plus a line | Reads like a plan; both times are equal; grows naturally if there is more one day (e.g. a meeting after) | Shows at a glance how long the pre-training is compared with the practice | Nobody misses when to be there; speaks the language of the response counts | Hard to overlook without changing the time line; friendly |
| Weak | Easy to overlook; the eye stays on «20:20» | Two lines more per card; the time of the practice is no longer in the usual place | Needs the most explaining; a short pre-training is a narrow part (it is never drawn narrower than 16 %) | The practice time becomes secondary; a second board on a card that already ends with the counts | The loudest; a lot of colour in a list with many pre-trainings |

Shared by all: the pre-training takes the colour of the kind of event (VAR-04); every design contains the title and the start as text, for screen readers and tests (`data-testid="pre-training"`); the event page lists it under «Details» as well («Vortraining: 20:00 · Joggen»); an event without a pre-training looks exactly as before.

## Where the code is

| What | Where |
|---|---|
| The designs: template and styles | `frontend/src/app/attendance/pre-training.html`, `pre-training.css`, `pre-training.ts` (`<app-pre-training>`) |
| Which design shows, the address parameter | `frontend/src/app/attendance/pre-training-designs.ts` |
| The switch bar | `frontend/src/app/attendance/pre-training-switch.ts` |
| Times, durations, the share of the bar | `frontend/src/app/attendance/pre-training-model.ts` (unit tested) |
| Used by | `attendance/events.ts` (cards of `/termine`), `pages/start/start.html` (start page), `attendance/event.ts` (event page) |

## After the choice

Keep the chosen `@case` of both places in `pre-training.html` and its rules in `pre-training.css`; remove `pre-training-designs.ts`, `pre-training-switch.ts`, the keys `designs`, `switch`, `switchLabel`, `previous`, `next` of `TEXTS.preTraining` and the texts the chosen design does not use; record the decision in [decisions.md](../decisions.md), close O-77 and describe the look in [design-principles.md](../design-principles.md).

## Chosen

Open.
