# Test plan follow-up fix #15 · Navigation labels complete on mobile

Issue #15 · PR #29 · Main navigation (`data-testid="hauptnavigation"`, `docs/ui/navigation.md`)

General, for every case:

- Setup: `POST /api/test/reset`, then `POST /api/test/anmelden` with `{ "schluessel": "<person>" }`
  (or `/dev-login`). Clock: default, does not matter.
- Measured per link of the main navigation: label (`a span`) with `scrollWidth <= clientWidth`,
  bounding box of the label inside the link, link inside the screen,
  link at least 44×44 px. No horizontal scrolling of the page.
- After each case: no console errors, no unexpected 4xx/5xx.

## K1 · All labels complete (criterion 1)

| Case | Starting point · steps | Expected |
|---|---|---|
| K1.1 Admin 375 | `alex`, 375×812, `/verwaltung` | 5 links «Start», «Termine», «Teams», «Profil», «Verwaltung», text exactly like this, not cut off, no «…» |
| K1.2 Active entry | `alex`, 375×812, one after the other `/`, `/termine`, `/teams`, `/profil`, `/verwaltung` | On every route (also «Verwaltung» bold/active) all labels complete |
| K1.3 Player | `anna`, 375×812, all five routes | 4 links, all complete |
| K1.4 Coach | `petra`, 375×812, all five routes | 4 links, all complete |
| K1.5 Narrow phone | `alex`, `anna`, `petra`, 320×640, all routes | All labels complete, no horizontal scrolling |
| K1.6 Desktop | all three people, 1280×800 | Side navigation unchanged, font 15 px, all labels complete |
| K1.7 Dark mode | `alex`, 375×812 and 1280×800, `prefers-color-scheme: dark`, `/verwaltung`, screenshot | Labels easy to read, active entry highlighted, nothing white on white |
| K1.8 Accessibility tree | `alex`, 375×812, snapshot `navigation "Hauptnavigation"` | 5 links with complete name |

## K2 · Touch targets (criterion 2)

| Case | Starting point · steps | Expected |
|---|---|---|
| K2.1 Admin 375 | as K1.1 | Each link ≥ 44×44 px (expected 72×56) |
| K2.2 Four entries 375 | `anna`, `petra` | Each link ≥ 44×44 px (expected 90×56) |
| K2.3 320 px | `alex` 320×640 | Each link ≥ 44×44 px (expected 61×56) |

## Robustness and roles

| Case | Steps | Expected |
|---|---|---|
| R1 Reload · back | `alex` 375, `/verwaltung` → navigation «Teams» → reload → back | Lands on `/verwaltung`, labels still complete |
| R2 Roles | `anna` opens `/verwaltung` via deep link | «Keine Berechtigung», no entry «Verwaltung» in the navigation (unchanged) |
