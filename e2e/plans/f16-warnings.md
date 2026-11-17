# Test plan F-16 · Warnings for events and call-ups

Issue #55, PR #70. Requirements ANM-40 to ANM-46, AUF-30, AUF-31 (E-79, O-58). Seed and scenarios S48–S51 from [test-data.md](../../docs/test-data.md). Default clock: Mon 16.11.2026 06:00.

Before every case: `POST /api/test/reset`, log in via `POST /api/test/login` or `/dev-login`. Viewports: mobile 375×812 (all cases), desktop 1280×800 (cases 1, 2, 3, 4, 8). After every case: no console errors, no unexpected 4xx/5xx.

IDs: Team 1 `10000000-0000-4000-8000-000000000001`, events 01–06 `40000000-0000-4000-8000-00000000000<n>`.

## 1. Overview shows warnings above the responses (criteria 1, 7, 9; S48, S49)

1. As `petra` open `/termine/<event 02>`.
   - Expect: region «Warnungen» inside `event-warnings` above «Team 1», items «Kein Goalie angemeldet», «Nur 1 Feldspielerin angemeldet», «Linie 1 nur zu 1». Each `warning` has an icon and text, `data-warning` = `GOALIES`, `FIELD_PLAYERS`, `LINE`. `event-warnings` has `aria-live="polite"`.
2. As `alex` (admin) open `/termine/<event 01>`.
   - Expect: the 7 warnings of S49, each prefixed with «Team 1: » or «Team 2: ».
3. As `petra` on event 02 enter «dabei» for Jana via «Antwort für Jana Wyss eintragen».
   - Expect: «Kein Goalie angemeldet» disappears without reload; after reload still gone.

## 2. Call-up warns from the selection, live (criterion 2; S50)

1. As `petra` open `/termine/<event 04>/aufgebot`.
   - Expect: `call-up-warnings` «Nur 4 Feldspielerinnen aufgeboten», «Linie 2 nur zu 1».
2. Untick Jana.
   - Expect: «Kein Goalie aufgeboten» added without reload; reload keeps the selection and the warning.
3. Tick Jana again → goalie warning gone.

## 3. Publishing with warnings (criterion 3; S50)

1. From case 2.2 click «Veröffentlichen».
   - Expect: dialog «Aufgebot veröffentlichen?» with section «Achtung:» (`call-up-dialog-warnings`) listing the same warnings. «Abbrechen» closes without publishing.
2. Click «Veröffentlichen» again and confirm.
   - Expect: the call-up is published (status visible in `/termine/<event 04>`).
3. Robustness: double click on confirm publishes once, no 5xx.

## 4. Minimums per team (criterion 4; S51)

1. As `petra` open `/teams/<Team 1>`, section «Warnungen» (`team-warning-minimums`).
   - Expect: defaults 1 · 10 · 6 · 3.
2. Set «Goalies mindestens» 0, «Feldspielerinnen im Training mindestens» 1, «Pro Linie mindestens» 2, «Mindestzahlen speichern».
   - Expect: «Die Mindestzahlen sind gespeichert.»; after reload the values stay; event 02 shows only «Linie 1 nur zu 1».
3. Edge cases: empty field, 100, −1 → no save, understandable message; API `PUT` with 100 → 400.
4. As `alex` (admin) changing works too.
5. As `anna` no section «Warnungen»; `PUT /api/teams/<Team 1>/warning-minimums` → 403. Unknown team → 404.

## 5. Only upcoming, not cancelled events (criterion 5)

1. As `petra` `/termine`: cancelled event 03 and «Anlass» event 06 have no chip; `/termine/<event 03>` no section «Warnungen».
2. Clock after the end of event 02 (`PUT /api/test/clock`, e.g. 20.11.2026 08:00): event 02 shows no warnings in the overview and no chip.

## 6. Players see no warnings (criterion 6)

1. As `anna`: `/termine`, `/termine/<event 02>`, `/teams/<Team 1>` show no warnings.
2. API as `anna`: `GET /api/events`, `GET /api/events/<event 02>/overview` (or the equivalent overview route), `GET /api/teams/<Team 1>` contain no `warnings`, `warningCount` or `warningMinimums`.

## 7. Accessibility and dark mode (criterion 7)

1. Every screen with warnings (overview, call-up, dialog, team page, list) once in dark mode: screenshot readable, mustard accent visible, nothing light on light.
2. Each warning is an `li` with text in the accessibility tree; the icon is decorative.

## 8. Event list marks events with warnings (criterion 8; S48)

1. As `petra` `/termine`: event 02 chip «3 Warnungen», event 01 «7 Warnungen» (`data-warning-count`).
2. As `anna`: no chips.

## 9. Seed (criterion 9)

Covered by case 1.1: event 02 is the upcoming practice of Team 1 without a goalie «dabei».

## 10. Later functions (criterion 10)

`docs/later-features.md`: «Warnings» is listed as done.
