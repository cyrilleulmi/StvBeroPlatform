# Grunddesign «Salbei frisch»

Entschieden am 15.11.2026 (Variante 1b aus der Stil-Werkstatt). Verbindlich für alle Kacheln.

Canvas mit allen Entwürfen: https://claude.ai/artifact/VXurH5jdUpNqMRcPCYxP1f (Seite «Stil-Werkstatt», Artboard «1b · Salbei frisch»). Der Link ist privat und nur für Cyrille zugänglich.

## Charakter

- Freundlich, ein paar Farben, nicht aufdringlich.
- Lightmode: leicht grün getönter Hintergrund, weisse Karten – nicht steril.
- Darkmode: dunkles Tannengrün statt Schwarz – ruhig, nicht «hackermässig».
- Kein Vereinsbezug in den Farben (generisches Design).
- Flächen bleiben ruhig. Kräftige Farbe nur dort, wo etwas zu tun ist (Hauptaktion, offene Helfer-Schichten).

## Farben (Design-Tokens)

| Token | Light | Dark | Verwendung |
|---|---|---|---|
| `bg` | `#F0F5F2` | `#18201D` | Seitenhintergrund |
| `surface` | `#FFFFFF` | `#212B27` | Karten, Kacheln, Navigation |
| `text` | `#1F2A26` | `#E7EEEA` | Haupttext |
| `muted` | `#56645E` | `#A2B0AA` | Nebentext, inaktive Navigation |
| `border` | `#DCE6E0` | `#31403A` | Rahmen, Trennlinien, Fortschritts-Hintergrund |
| `primary` | `#2C7F69` | `#6FD0B4` | Hauptaktion, aktive Navigation, Fortschritt |
| `on-primary` | `#FFFFFF` | `#0E2620` | Text auf `primary` |
| `primary-soft` | `#D7EEE6` | `#224238` | Datumsfeld, Icon-Hintergrund, aktiver Nav-Eintrag |
| `accent-coral` | `#B8503A` | `#F3A493` | Hinweise «offen», Absage, Helfer |
| `accent-coral-soft` | `#FCE4DD` | `#48302C` | Helfer-Kachel, Absage-Button aktiv |
| `accent-mustard` | `#6E6400` | `#E3D97A` | Chips («in 2 Tagen»), Übungen |
| `accent-mustard-soft` | `#F1EFC4` | `#3E3B22` | Chip-/Icon-Hintergrund |
| `accent-blue` | `#2F6C9A` | `#8EC3EC` | Coaches |
| `accent-blue-soft` | `#DCEBF6` | `#233848` | Icon-Hintergrund Coaches |

Zuordnung Kacheln → Farbe:

- Anwesenheit Spielerinnen → `primary`
- Anwesenheit Coaches → `accent-blue`
- Übungssammlung → `accent-mustard`
- Helfereinsatzplanung → `accent-coral` (Kachel mit `accent-coral-soft` hinterlegt)
- Ressourcen → `primary`

## Form («weich»)

- Karten: Radius 22px, 1px Rahmen in `border`, kein Schlagschatten
- Kacheln: Radius 18px, Hintergrund `surface` mit 1px Rahmen (Kacheln «neutral», nicht getönt)
- Icon-Felder / Datumsbox: Radius 12px, 38×38 bzw. 56×58, Hintergrund `*-soft`, Icon in Vollfarbe
- Buttons: Pille (Radius 999px), Höhe 52px, Schrift 16px/700
  - Primär: `primary` / `on-primary`
  - Sekundär: transparent, 1.5px Rahmen `border`, Text `text`
- Chips: Pille, 12px/700, `*-soft` Hintergrund mit Vollfarbe-Text
- Bottom-Navigation: `surface` mit oberem Rahmen. Aktiver Eintrag als Fläche `primary-soft` (Radius 16px) mit Text/Icon `primary`
- Fortschrittsbalken: 8px hoch, Radius 4px, Spur `border`, Füllung `primary`

## Typografie

- Schrift: **Plus Jakarta Sans** (Google Fonts), Gewichte 400/500/600/700/800
- Begrüssung/Seitentitel: 26px/800, letter-spacing -0.02em
- Kartentitel: 18px/700 · Abschnittstitel: 16px/700 · Kacheltitel: 15px/700
- Fliesstext: 14px · Nebentext: 12–13px in `muted`
- Eyebrow («Nächster Termin»): 12px/700, Grossbuchstaben, letter-spacing 0.06em, Farbe `primary`

## Abstände und Layout

- Seitenrand mobil 20px. Abstand zwischen Abschnitten 18px. Kachel-Raster 2 Spalten, Gap 12px
- Karten-Innenabstand 18px, Kachel-Innenabstand 14px
- Touch-Ziele mindestens 44px
- Icons: Lucide-Stil (Stroke 2px, runde Enden)

## Umsetzung (Angular)

Tokens als CSS Custom Properties. Darkmode über `prefers-color-scheme` plus manuellen Umschalter (`data-theme`):

```css
:root, [data-theme="light"] {
  --bg: #F0F5F2; --surface: #FFFFFF; --text: #1F2A26; --muted: #56645E; --border: #DCE6E0;
  --primary: #2C7F69; --on-primary: #FFFFFF; --primary-soft: #D7EEE6;
  --coral: #B8503A; --coral-soft: #FCE4DD;
  --mustard: #6E6400; --mustard-soft: #F1EFC4;
  --blue: #2F6C9A; --blue-soft: #DCEBF6;
  --radius-card: 22px; --radius-tile: 18px; --radius-icon: 12px; --radius-pill: 999px;
  --font: 'Plus Jakarta Sans', system-ui, sans-serif;
}
[data-theme="dark"] {
  --bg: #18201D; --surface: #212B27; --text: #E7EEEA; --muted: #A2B0AA; --border: #31403A;
  --primary: #6FD0B4; --on-primary: #0E2620; --primary-soft: #224238;
  --coral: #F3A493; --coral-soft: #48302C;
  --mustard: #E3D97A; --mustard-soft: #3E3B22;
  --blue: #8EC3EC; --blue-soft: #233848;
}
@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) { /* gleiche Werte wie [data-theme="dark"] */ }
}
```

## Noch nicht festgelegt

Für die Antworten der An- und Abmeldung braucht es eine Farb- und Symbolzuordnung. Naheliegend aus den bestehenden Tokens:

| Antwort | Vorschlag |
|---|---|
| dabei | `primary` |
| nicht dabei | `accent-coral` (ist bereits für «Absage» vorgesehen) |
| vielleicht | `accent-mustard` |
| offen | `muted` |

Der Status darf nicht nur über die Farbe erkennbar sein. Jede Antwort bekommt zusätzlich ein eigenes Symbol.
