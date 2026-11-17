# Base design «Salbei frisch»

Decided on 15.11.2026 (variant 1b from the style workshop). Binding for all tiles. Since 16.11.2026 there are three [themes](#themes): the colors of «Salbei frisch» (fresh sage) are the theme «Hell», plus «Düne» and «GitHub Dark» (E-67). Shape, typography and spacing apply to all three.

Canvas with all drafts: https://claude.ai/artifact/VXurH5jdUpNqMRcPCYxP1f (page «Stil-Werkstatt», artboard «1b · Salbei frisch»). The link is private and only accessible to Cyrille.

## Character

- Friendly, a few colors, not intrusive.
- «Hell»: slightly green-tinted background, white cards – not sterile.
- «Düne»: warm sand tone with petrol as the main color – light, without green.
- «GitHub Dark»: almost black background with blue as the main color. It is the dark mode and replaces the dark fir green (E-67).
- No club reference in the colors (generic design).
- Surfaces stay calm. Strong color only where there is something to do (main action, open helper shifts).

## Colors (design tokens)

| Token | Hell | Düne | GitHub Dark | Use |
|---|---|---|---|---|
| `bg` | `#F0F5F2` | `#F3ECE1` | `#0D1117` | Page background |
| `surface` | `#FFFFFF` | `#FFFAF3` | `#161B22` | Cards, tiles, navigation |
| `text` | `#1F2A26` | `#33291F` | `#E6EDF3` | Main text |
| `muted` | `#56645E` | `#72634F` | `#9198A1` | Secondary text, inactive navigation |
| `border` | `#DCE6E0` | `#E4D8C6` | `#30363D` | Borders, dividers, progress background |
| `primary` | `#2C7F69` | `#2B5F75` | `#58A6FF` | Main action, active navigation, progress |
| `on-primary` | `#FFFFFF` | `#FFFFFF` | `#0C1724` | Text on `primary` |
| `primary-soft` | `#D7EEE6` | `#E1E4E1` | `#1B2C41` | Date box, icon background, active nav item |
| `accent-coral` | `#B8503A` | `#AD442D` | `#FF7B72` | Notices «offen», cancellation, helpers |
| `accent-coral-soft` | `#FCE4DD` | `#F4E1D7` | `#392427` | Helper tile, cancel button active |
| `accent-mustard` | `#6E6400` | `#7C5E00` | `#E3B341` | Chips («in 2 Tagen»), drills |
| `accent-mustard-soft` | `#F1EFC4` | `#EDE4D1` | `#342E1F` | Chip/icon background |
| `accent-blue` | `#2F6C9A` | `#5B4FA8` | `#BC8CFF` | Coaches |
| `accent-blue-soft` | `#DCEBF6` | `#E8E2E9` | `#2D2741` | Icon background for coaches |

In «Düne» and «GitHub Dark», `primary` itself is bluish. `accent-blue` is violet there, so coaches stay distinguishable.

Mapping tiles → color:

- Player attendance → `primary`
- Coach attendance → `accent-blue`
- Drill collection → `accent-mustard`
- Helper scheduling → `accent-coral` (tile with `accent-coral-soft` background)
- Resources → `primary`

## Shape («weich», soft)

- Cards: radius 22px, 1px border in `border`, no drop shadow
- Tiles: radius 18px, background `surface` with 1px border (tiles «neutral», not tinted)
- Icon boxes / date box: radius 12px, 38×38 or 56×58, background `*-soft`, icon in full color
- Buttons: pill (radius 999px), height 52px, font 16px/700
  - Primary: `primary` / `on-primary`
  - Secondary: transparent, 1.5px border `border`, text `text`
- Chips: pill, 12px/700, `*-soft` background with full-color text
- Bottom navigation: `surface` with top border. Active item as an area in `primary-soft` (radius 16px) with text/icon in `primary`
- Progress bar: 8px high, radius 4px, track `border`, fill `primary`

## Typography

- Font: **Plus Jakarta Sans** (Google Fonts), weights 400/500/600/700/800
- Greeting/page title: 26px/800, letter-spacing -0.02em
- Card title: 18px/700 · section title: 16px/700 · tile title: 15px/700
- Body text: 14px · secondary text: 12–13px in `muted`
- Eyebrow («Nächster Termin»): 12px/700, uppercase, letter-spacing 0.06em, color `primary`

## Spacing and layout

- Page margin on mobile 20px. Spacing between sections 18px. Tile grid 2 columns, gap 12px
- Card padding 18px, tile padding 14px
- Touch targets at least 44px
- Icons: Lucide style (stroke 2px, round caps)

## Implementation (Angular)

Tokens as CSS custom properties in `frontend/src/styles.css`. The theme is set as `data-theme` on the document. «System» sets no `data-theme` and follows `prefers-color-scheme`:

```css
:root, [data-theme="light"] {       /* Hell */
  --bg: #F0F5F2; --surface: #FFFFFF; --text: #1F2A26; --muted: #56645E; --border: #DCE6E0;
  --primary: #2C7F69; --on-primary: #FFFFFF; --primary-soft: #D7EEE6;
  --coral: #B8503A; --coral-soft: #FCE4DD;
  --mustard: #6E6400; --mustard-soft: #F1EFC4;
  --blue: #2F6C9A; --blue-soft: #DCEBF6;
  --radius-card: 22px; --radius-tile: 18px; --radius-icon: 12px; --radius-pill: 999px;
  --font: 'Plus Jakarta Sans', system-ui, sans-serif;
}
[data-theme="dune"] { /* values of the column «Düne» */ }
[data-theme="github-dark"] { /* values of the column «GitHub Dark» */ }
@media (prefers-color-scheme: dark) {
  :root:not([data-theme]) { /* same values as [data-theme="github-dark"] */ }
}
```

## Themes

Decided on 16.11.2026 (E-65 to E-67, NF-08). Reason: the base design was far too green for Cyrille. After trying out 21 themes, three remain: «Hell», «Düne» and «GitHub Dark».

| Entry in the profile | `data-theme` | Stored value | Shows |
|---|---|---|---|
| «Hell» | `light` | `light` | Colors of «Salbei frisch» |
| «Düne» | `dune` | `dune` | Warm sand with petrol |
| «GitHub Dark» | `github-dark` | `github-dark` | Almost black with blue |
| «System» (default) | none | `system` | «Hell» in light mode, «GitHub Dark» in dark mode |

Rules:

- A theme changes **only the color tokens**. Shape, typography, spacing and layout are the same everywhere.
- Every theme sets all 14 color tokens. Components use only the tokens, never fixed colors.
- The meaning of the tokens stays: `primary` main action and «dabei», `accent-coral` cancellation and «nicht dabei» (reddish), `accent-mustard` «vielleicht» (yellowish), `accent-blue` coaches. `primary` is never red, orange or yellow.
- Contrast at least 4.5:1 (WCAG AA) for text and accent colors on `bg`, `surface` and the `*-soft` areas, and for `on-primary` on `primary`. The colors of «GitHub Dark» are based on the theme of the same name.
- «Düne» and «GitHub Dark» apply regardless of the system setting. The choice is stored in the browser (`localStorage`, key `stv-theme`), not in the account. A stored value that no longer exists falls back to «System», without an error. This also applies to the German values from before F-11 (`hell`, `duene`); they are not migrated.
- In the switcher, each choice is shown in its own colors (background, font and the four accent colors as dots).
- Another theme needs: a block `[data-theme='<id>']` in `styles.css`, an entry in `theme.service.ts`, in the inline script of `index.html` and in the switcher (`pages/profile/profile.ts`), and a test in `e2e/tests/themes.spec.ts`.

It is open what «System» should show in light mode ([O-53](open-points.md)).

## Not yet defined

The responses for signing up and out need a color and symbol mapping. The obvious choice from the existing tokens:

| Response | Proposal |
|---|---|
| «dabei» | `primary` |
| «nicht dabei» | `accent-coral` (already intended for «Absage») |
| «vielleicht» | `accent-mustard` |
| «offen» | `muted` |

The status must not be recognizable by color alone. Each response also gets its own symbol.
