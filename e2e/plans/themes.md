# Test plan · Themes (NF-08, E-90)

Profile, switcher «Darstellung» (`data-testid="theme-switch"`, `docs/design-principles.md#themes`).
Tests: `../tests/themes.spec.ts`. «Hell» and «System»: `../tests/f00-foundation.spec.ts` (K6).

General for every case: `POST /api/test/reset`, sign in as `anna`. Mobile (375×812) and desktop (1280×800).

| Case | Steps | Expected |
|---|---|---|
| D1 Choice | Open `/profil` | Exactly three entries: «Hell», «Dunkel», «System» |
| D2 Dunkel | Set the system to light (`prefers-color-scheme`), choose «Dunkel», `/`, event 01, back to `/profil` | `data-theme` is `dark`, background `#14181D`, stays after the page change, entry stays selected, axe without findings on profile, start page and event |
| D3 Old value | `localStorage` `stv-theme` = `dunkel` (no longer exists), reload | «System» is selected, no `data-theme` |
| D4 Old value (NF-40, E-68, E-90) | `localStorage` `stv-theme` = `hell`, `duene` (values before F-11), then `dune`, `github-dark` (removed by E-90), reload, open `/` | «System» is selected, no `data-theme`, no console errors |

Check by hand (not automated): with «Dunkel», the color of the browser or status bar follows their background (`meta[name="theme-color"]`).
