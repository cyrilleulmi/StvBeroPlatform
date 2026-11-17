# Test plan follow-up fix #75 · Accessible name of «Resultat erfassen»

Issue #75 · PR #76 · File `e2e/tests/nb75-result-label.spec.ts` · Requirement AUF-15, NF accessibility (WCAG 2.5.3 «Label in Name»)

Starting point: database running (`scripts/db-start.sh`), backend profile `test`/`dev`, frontend on port 4200, `POST /api/test/reset`, seed clock. T04 = `40000000-0000-4000-8000-000000000004` (match day Sun 22.11.2026, match 01 09:30 Team 1 – UHC Musterdorf, match 02 11:45 Unihockey Beispielstadt – Team 1). T05 = `…000000000005` (match day Team 2).

## K1 · Name contains the visible text, unique per match

| Case | Steps | Expected |
|---|---|---|
| K1.1 Names | Reset → login `petra` → `/termine/<T04>` → accessibility snapshot of `matches` | Two buttons, visible text «Resultat erfassen», names «Resultat erfassen: Team 1 – UHC Musterdorf» and «Resultat erfassen: Unihockey Beispielstadt – Team 1» |
| K1.2 Find by visible text | `getByRole('button', { name: 'Resultat erfassen' })` within `matches` | Exactly 2 matches; exact name selects one button each |
| K1.3 Function | «Resultat erfassen: Team 1 – UHC Musterdorf» → «5-3» → save, then «5:3» → save; reload | Message at the field for «5-3»; afterwards «Resultat 5:3» in match 01, stays after reload; status «Resultat für Team 1 – UHC Musterdorf gespeichert.» (unchanged) |
| K1.4 Admin | Reset → `alex` → `/termine/<T04>` | Same names as K1.1 |
| K1.5 Other team | Reset → `marc` → `/termine/<T05>` | Button name «Resultat erfassen: <Heim> – <Gast>» of match 03 |

## K2 · axe without `label-content-name-mismatch`

| Case | Steps | Expected |
|---|---|---|
| K2.1 Mobile | 375×812, `petra` → `/termine/<T04>`, axe with rule `label-content-name-mismatch` enabled | No violation |
| K2.2 Desktop | 1280×800, same | No violation |
| K2.3 Dark | Same in dark mode, screenshot | Buttons readable, no visual change against main |

## K3 · Existing tests adapted

| Case | Steps | Expected |
|---|---|---|
| K3.1 | `cd e2e && npx playwright test --trace off` (both projects) | All green, including `f05-match-days.spec.ts` (K8, SUH-05) |

## Roles

| Case | Steps | Expected |
|---|---|---|
| R1 | `anna` (player) → `/termine/<T04>` | No button «Resultat erfassen» |
| R2 | `anna` → API `PUT /api/events/<T04>/matches/<match 01>` with result (full `MatchInput`) | 403; `marc` (coach Team 2) 200 according to E-69 |

## Robustness

| Case | Steps | Expected |
|---|---|---|
| G1 | Double click on «Resultat speichern» | Exactly one `PUT …/matches/<match 01>`, no console error |
