# Base design «Sporthalle»

Decided on 22.11.2026 (E-90), replacing «Salbei frisch» (E-03, E-65 to E-67). Binding for all tiles. There are two [themes](#themes): «Hell» and «Dunkel». Shape, typography, spacing and motion apply to both.

How it came about: on 21./22.11.2026 several design skills were tried out on proof-of-concept branches (`poc/ux-*`). Cyrille chose the identity of the skill `frontend-design` (Anthropic), had the scoreboard softened and the dark theme made calmer, and had it refined with the skills impeccable, better-interface, Vercel Web Interface Guidelines and emil-design-eng (branch `poc/ux-sporthalle`).

## Character

- The language of a sports hall: hall-floor grey, white boards, ink, court-line paint, the condensed DIN of hall signage.
- The response counts are a **scoreboard** («Anzeigetafel»): big digits above their word, one column per response.
- Court lines as accents: month headings with a line, the active navigation item, the top edge of the scoreboard, the colour line under the date box of an event.
- «Hell»: grey-blue hall floor, white cards, ultramarine as the main color.
- «Dunkel»: cool slate, the night version of «Hell». Muted colors, no neon.
- No club reference in the colors (generic design).
- What a player decides on leads: when, where, «bin ich dabei?». Everything else stays quiet (muted text, frameless icon buttons).

## Colors (design tokens)

| Token | Hell | Dunkel | Use |
|---|---|---|---|
| `bg` | `#E9EDF0` | `#14181D` | Page background |
| `surface` | `#FFFFFF` | `#1C2128` | Cards, tiles, navigation |
| `text` | `#15202B` | `#E3E7EC` | Main text, time and place of an event |
| `muted` | `#4E5A66` | `#9BA4AE` | Secondary text, inactive navigation |
| `border` | `#C9D1D9` | `#353D47` | Card borders, dividers |
| `control-border` | `#75808B` | `#737C86` | Boundary of inputs, selects and outlined buttons (at least 3:1, WCAG 1.4.11) |
| `primary` | `#2343B0` | `#93A7DE` | Main action, «dabei», active navigation, court lines |
| `on-primary` | `#FFFFFF` | `#111A33` | Text on `primary` |
| `primary-soft` | `#DDE4F7` | `#252E47` | Icon background, soft areas |
| `coral` | `#B02A22` | `#DC8F86` | «nicht dabei», cancellation, destructive buttons |
| `on-coral` | `#FFFFFF` | `#2B1210` | Text on `coral` |
| `coral-soft` | `#F9DEDA` | `#3A2729` | Background for `coral` |
| `mustard` | `#765A00` | `#D0B26A` | «vielleicht», warnings |
| `mustard-soft` | `#F3E7B8` | `#332D1F` | Warning box, chip background |
| `blue` | `#6A3DA8` | `#B6A3D9` | Coaches (violet, so that it stays distinct from `primary`) |
| `blue-soft` | `#EAE2F6` | `#2C2839` | Icon background for coaches |
| `board` | `#DFE5F6` | `#232A35` | Scoreboard and start times of matches |
| `board-border` | `#B9C5EA` | `#3B4658` | Border of the scoreboard |

### Colours of the event variants (VAR-04)

The event list tells the kinds of events apart by colour (TER-14). There is a palette of nine colours, each a pair of tokens per theme: `--tag-<name>` (text and date) and `--tag-<name>-soft` (background). A variant stores the name of the colour, never a value, so that every choice works in both themes.

| Name (UI) | Token | Hell | Dunkel |
|---|---|---|---|
| «Grün» | `tag-green` | `#1B7045` / `#DCF1E4` | `#86BF96` / `#1C2A22` |
| «Türkis» | `tag-teal` | `#0C6A6E` / `#D6EEF0` | `#79B9BA` / `#1A2A2C` |
| «Blau» | `tag-blue` | `#245C9E` / `#DCE8F8` | `#92B2DB` / `#1C2737` |
| «Violett» | `tag-violet` | `#6541AD` / `#E8E0F7` | `#B3A0DA` / `#272338` |
| «Pink» | `tag-pink` | `#9F2E73` / `#F8DCEC` | `#D596B9` / `#33222C` |
| «Rot» | `tag-red` | `#AA3434` / `#FADEDD` | `#DC8F88` / `#352324` |
| «Orange» | `tag-orange` | `#96480A` / `#FBE5CF` | `#D6A272` / `#33281D` |
| «Gelb» | `tag-yellow` | `#6F5900` / `#F5ECC0` | `#CDB46A` / `#302B1B` |
| «Grau» | `tag-grey` | `#4F5963` / `#E6EAEE` | `#A9B1BA` / `#262B32` |

Each pair reaches at least 4.5:1 for the full colour on its soft background, on `surface` and on `bg`. The colour is never the only sign: the name of the variant is always next to it. Elements pick their pair with `data-tag="<NAME>"`. «Rot» and «Gelb» of the palette are for kinds of events only; cancellations stay `coral`, «vielleicht» and warnings `mustard`.

## The scoreboard

- Component `app-scoreboard` (`core/scoreboard.ts`) inside an element with the class `scoreboard`; the panel around it has the class `board`.
- Panel: background `board`, border `board-border`, a 4px court line in `primary` along the top. It stands out from the white cards but stays in the theme (no near-black board in «Hell»).
- Digits in the condensed display font, tabular figures, coloured by response: «dabei» `primary`, «nicht dabei» `coral`, «vielleicht» `mustard`, «offen» `text`. Words below in `muted`.
- The text content stays «8 dabei · 3 nicht dabei · 1 vielleicht · 2 offen» for screen readers and tests; the separators are only hidden visually.

## Shape

- Radius hierarchy: cards and tiles 14px, controls 10px, icon boxes 8px, chips 6px, navigation items 10px, pills 999px.
- Cards: 1.5px border in `border`, no drop shadow.
- Buttons: primary filled `primary` / `on-primary`; secondary transparent with a 1.5px border in `control-border`; destructive with border and text in `coral`.
- Response buttons: the chosen one filled, the others outlined. Labels stay on one line («Nicht dabei» too, at 390px).
- Icon buttons (pencil «für jemanden antworten»): no frame at rest, muted icon; frame, tint and `primary` on hover. Target still 44px.
- Filter chips (teams and «Freiwillige» in `/termine`, E-111): a small pill (14px/600, 1.5px `control-border`), a real checkbox underneath; on: `primary` border, `primary-soft` and a tick, so the state is not only a colour. Target 44px high. Several chips stay in one row that scrolls sideways on a phone.
- Page actions as icons (title row of `/termine`, E-111): 44px, 1.5px `control-border`, the main one filled `primary`; the name in `aria-label` and `title`.
- Date box of an event: `tag-*-soft` background, date in the full colour, a court line in the variant colour underneath.

## Typography

- One family in two widths, local fonts only (no download):
  - Text: `Bahnschrift`, `Segoe UI`, system-ui, `-apple-system`, `Roboto`, sans-serif.
  - Display (headings, digits, time and place, button labels): `Bahnschrift`, `DIN Condensed`, `Roboto Condensed`, `sans-serif-condensed`, `Arial Narrow`, condensed to 80%.
- Devices without these fonts show the system fallback; the layout must hold with it.
- Page title 34px/700, card title 21px/700, body 15px, secondary text 13–14px in `muted`.
- Mixed case, no uppercase eyebrows. Tabular figures for times, dates and counts.
- Headings use `text-wrap: balance`.

## Spacing and layout

- Page margin on mobile 20px. Spacing between sections 18px.
- Card padding 18px.
- Touch targets at least 44px.
- Icons: Lucide style (stroke 2px, round caps).
- Event list entry: title, then «Zeit · Ort» in ink, then the teams muted, one row of chips (variant, «freiwillig», warnings), the counts on one line, then the response buttons.

## Motion

- Tokens: `--ease-out: cubic-bezier(0.23, 1, 0.32, 1)`, `--duration-press` 160ms, `--duration-color` 150ms, `--duration-enter` 200ms, `--duration-exit` 150ms.
- Buttons and tiles give under a press (scale 0.97, tiles 0.98). Colours change in 150ms.
- Dialogs: 200ms in from scale 0.96, 150ms out; the backdrop fades.
- Status messages slide in 4px. «Lädt …» shows only after 300ms. Disabled dimming and the busy spinner start after 200ms, so fast saves do not flicker.
- A changed scoreboard digit drops in (6px, 200ms); never on the first render.
- Never animated: navigation, route changes, the focus ring, list entrances.
- With `prefers-reduced-motion: reduce` nothing moves.
- Hover styles only for `@media (hover: hover)`.

## Implementation (Angular)

Tokens as CSS custom properties in `frontend/src/styles.css`. The theme is set as `data-theme` on the document. «System» sets no `data-theme` and follows `prefers-color-scheme`:

```css
:root, [data-theme='light'] { /* Hell */ }
[data-theme='dark'] { /* Dunkel */ }
@media (prefers-color-scheme: dark) {
  :root:not([data-theme]) { /* same values as [data-theme='dark'] */ }
}
```

## Themes

Decided on 22.11.2026 (E-90, NF-08). Before: «Hell», «Düne» and «GitHub Dark» (E-67).

| Entry in the profile | `data-theme` | Stored value | Shows |
|---|---|---|---|
| «Hell» (default, E-100) | `light` | `light` | Hall floor with ultramarine |
| «Dunkel» | `dark` | `dark` | Cool slate, muted colours |
| «System» | none | `system` | «Hell» in light mode, «Dunkel» in dark mode |

Without a stored choice the app is «Hell», also on a device in dark mode; «Dunkel» and «System» are chosen in the profile (E-100).

Rules:

- A theme changes **only the color tokens**. Shape, typography, spacing, motion and layout are the same everywhere.
- Every theme sets all color tokens of the table above and the 18 tokens of the event variants (`--tag-*`). Components use only the tokens, never fixed colors.
- The meaning of the tokens stays: `primary` main action and «dabei», `coral` cancellation and «nicht dabei» (reddish), `mustard` «vielleicht» (yellowish), `blue` coaches. `primary` is never red, orange or yellow.
- Contrast at least 4.5:1 (WCAG AA) for text and accent colors on `bg`, `surface`, `board` and the `*-soft` areas, and for `on-primary` on `primary`. Control boundaries at least 3:1.
- «Dunkel» applies regardless of the system setting. The choice is stored in the browser (`localStorage`, key `stv-theme`), not in the account. A stored value that no longer exists falls back to «System», without an error: the German values from before F-11 (`hell`, `duene`) and the themes removed by E-90 (`dune`, `github-dark`). They are not migrated.
- The color of the browser or status bar follows the theme (`meta[name="theme-color"]`, `manifest.webmanifest`).
- In the switcher, each choice is shown in its own colors (background, font and the four accent colors as dots).
- Another theme needs: a block `[data-theme='<id>']` in `styles.css`, an entry in `theme.service.ts`, in the inline script of `index.html` and in the switcher (`pages/profile/profile.ts`), and a test in `e2e/tests/themes.spec.ts`.

## Responses

| Response | Color | Symbol |
|---|---|---|
| «dabei» | `primary` | check |
| «nicht dabei» | `coral` | cross |
| «vielleicht» | `mustard` | question mark |
| «offen» | `muted` | dashed circle |

The status is never recognizable by color alone: each response has its own symbol and word.
