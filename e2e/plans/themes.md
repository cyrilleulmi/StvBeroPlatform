# Test plan · Themes (NF-08, E-67)

Profile, switcher «Darstellung» (`data-testid="theme-umschalter"`, `docs/design-principles.md#themes`).
Tests: `../tests/darstellungen.spec.ts`. «Hell» and «System»: `../tests/f00-grundgeruest.spec.ts` (K6).

General for every case: `POST /api/test/reset`, sign in as `anna`. Mobile (375×812) and desktop (1280×800).

| Case | Steps | Expected |
|---|---|---|
| D1 Choice | Open `/profil` | Exactly four entries: «Hell», «Düne», «GitHub Dark», «System» |
| D2 Düne, GitHub Dark | Set the system to the opposite (`prefers-color-scheme`), choose the theme, `/`, event 01, back to `/profil` | `data-theme` is `duene` or `github-dark`, background `#F3ECE1` or `#0D1117`, stays after the page change, entry stays selected, axe without findings on profile, start page and event |
| D3 Old value | `localStorage` `stv-theme` = `dunkel` (no longer exists), reload | «System» is selected, no `data-theme` |

Check by hand (not automated): with «Düne» and «GitHub Dark», the color of the browser or status bar follows their background (`meta[name="theme-color"]`).
