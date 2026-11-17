# Test plan follow-up fix #23 · «Verwaltung» complete in the mobile navigation

Issue #23 · PR #34 · Main navigation (`data-testid="hauptnavigation"`, `docs/ui/navigation.md`)

General, for every case:

- Setup: `POST /api/test/reset`, then `POST /api/test/login` with `{ "loginKey": "<person>" }`
  (or `/dev-login`). Clock: default, does not matter.
- Measured per link of the main navigation: label (`a span`) with `scrollWidth <= clientWidth`,
  bounding box of the label inside the content area of the link (link minus padding),
  link inside the screen, link at least 44×44 px. Page and navigation without horizontal scrolling
  (`document.documentElement.scrollWidth <= innerWidth`).
- After each case: no console errors, no unexpected 4xx/5xx.

## K1 · 375×812 as `alex`, light and dark (criterion 1)

| Case | Starting point · steps | Expected |
|---|---|---|
| K1.1 Light | `alex`, 375×812, `/verwaltung` | 5 links «Start», «Termine», «Teams», «Profil», «Verwaltung» complete, no «…», each ≥ 44×44 px (expected 72×56) |
| K1.2 Dark | as K1.1, `prefers-color-scheme: dark`, screenshot | as K1.1; labels easy to read, active entry highlighted, nothing white on white |
| K1.3 Accessibility tree | `alex`, 375×812, snapshot `navigation "Hauptnavigation"` | 5 links with complete name |

## K2 · 320 px without horizontal scrolling (criterion 2)

| Case | Starting point · steps | Expected |
|---|---|---|
| K2.1 Admin light | `alex`, 320×640, `/verwaltung` | All five labels complete inside the content area, links ≥ 44×44 px (expected 63×56), `scrollWidth` = 320 |
| K2.2 Admin dark | as K2.1, dark, screenshot | as K2.1, readable |
| K2.3 All routes | `alex`, 320×640, via the navigation one after the other «Start», «Termine», «Teams», «Profil», «Verwaltung» | On every route (active entry changes) all labels complete, no horizontal scrolling |
| K2.4 Four entries | `anna`, 320×640 | 4 links complete (expected 79×56) |
| K2.5 Breakpoint | `alex`, 359 px and 360 px width | Both widths: all labels complete (from 360 px the normal layout applies again, link 68.8 px) |
| K2.6 Desktop unchanged | `alex`, 1280×800 | Side navigation 240 px wide, font 15 px, no `letter-spacing`, gap 2 px |

## K3 · Automated test (criterion 3)

| Case | Steps | Expected |
|---|---|---|
| K3.1 | Run `e2e/tests/nb23-navigation-schmal.spec.ts` in project `mobil` | 4 tests green (375/320, light/dark); skipped in project `desktop` |

## Robustness and roles

| Case | Steps | Expected |
|---|---|---|
| R1 Back · reload | `alex` 320, click through all routes → back → reload `/verwaltung` | Correct route, labels still complete |
| R2 Roles | `anna` opens `/verwaltung` via deep link | «Keine Berechtigung», no entry «Verwaltung» in the navigation (unchanged) |
