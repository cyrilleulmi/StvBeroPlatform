# Grunddesign «Salbei frisch»

Entschieden am 15.11.2026 (Variante 1b aus der Stil-Werkstatt). Verbindlich für alle Kacheln. Seit dem 16.11.2026 gibt es drei [Darstellungen](#darstellungen): Die Farben von «Salbei frisch» sind die Darstellung «Hell», dazu kommen «Düne» und «GitHub Dark» (E-67). Form, Typografie und Abstände gelten für alle drei.

Canvas mit allen Entwürfen: https://claude.ai/artifact/VXurH5jdUpNqMRcPCYxP1f (Seite «Stil-Werkstatt», Artboard «1b · Salbei frisch»). Der Link ist privat und nur für Cyrille zugänglich.

## Charakter

- Freundlich, ein paar Farben, nicht aufdringlich.
- «Hell»: leicht grün getönter Hintergrund, weisse Karten – nicht steril.
- «Düne»: warmer Sandton mit Petrol als Hauptfarbe – hell, ohne Grün.
- «GitHub Dark»: fast schwarzer Hintergrund mit Blau als Hauptfarbe. Es ist der Darkmode und ersetzt das dunkle Tannengrün (E-67).
- Kein Vereinsbezug in den Farben (generisches Design).
- Flächen bleiben ruhig. Kräftige Farbe nur dort, wo etwas zu tun ist (Hauptaktion, offene Helfer-Schichten).

## Farben (Design-Tokens)

| Token | Hell | Düne | GitHub Dark | Verwendung |
|---|---|---|---|---|
| `bg` | `#F0F5F2` | `#F3ECE1` | `#0D1117` | Seitenhintergrund |
| `surface` | `#FFFFFF` | `#FFFAF3` | `#161B22` | Karten, Kacheln, Navigation |
| `text` | `#1F2A26` | `#33291F` | `#E6EDF3` | Haupttext |
| `muted` | `#56645E` | `#72634F` | `#9198A1` | Nebentext, inaktive Navigation |
| `border` | `#DCE6E0` | `#E4D8C6` | `#30363D` | Rahmen, Trennlinien, Fortschritts-Hintergrund |
| `primary` | `#2C7F69` | `#2B5F75` | `#58A6FF` | Hauptaktion, aktive Navigation, Fortschritt |
| `on-primary` | `#FFFFFF` | `#FFFFFF` | `#0C1724` | Text auf `primary` |
| `primary-soft` | `#D7EEE6` | `#E1E4E1` | `#1B2C41` | Datumsfeld, Icon-Hintergrund, aktiver Nav-Eintrag |
| `accent-coral` | `#B8503A` | `#AD442D` | `#FF7B72` | Hinweise «offen», Absage, Helfer |
| `accent-coral-soft` | `#FCE4DD` | `#F4E1D7` | `#392427` | Helfer-Kachel, Absage-Button aktiv |
| `accent-mustard` | `#6E6400` | `#7C5E00` | `#E3B341` | Chips («in 2 Tagen»), Übungen |
| `accent-mustard-soft` | `#F1EFC4` | `#EDE4D1` | `#342E1F` | Chip-/Icon-Hintergrund |
| `accent-blue` | `#2F6C9A` | `#5B4FA8` | `#BC8CFF` | Coaches |
| `accent-blue-soft` | `#DCEBF6` | `#E8E2E9` | `#2D2741` | Icon-Hintergrund Coaches |

In «Düne» und «GitHub Dark» ist `primary` selbst bläulich. `accent-blue` ist dort violett, damit Coaches unterscheidbar bleiben.

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

Tokens als CSS Custom Properties in `frontend/src/styles.css`. Die Darstellung steht als `data-theme` am Dokument. «System» setzt kein `data-theme` und folgt `prefers-color-scheme`:

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
[data-theme="duene"] { /* Werte der Spalte «Düne» */ }
[data-theme="github-dark"] { /* Werte der Spalte «GitHub Dark» */ }
@media (prefers-color-scheme: dark) {
  :root:not([data-theme]) { /* gleiche Werte wie [data-theme="github-dark"] */ }
}
```

## Darstellungen

Entschieden am 16.11.2026 (E-65 bis E-67, NF-08). Anlass: Das Grunddesign war Cyrille viel zu grün. Nach dem Ausprobieren von 21 Darstellungen bleiben drei: «Hell», «Düne» und «GitHub Dark».

| Eintrag im Profil | `data-theme` | Gespeicherter Wert | Zeigt |
|---|---|---|---|
| «Hell» | `light` | `hell` | Farben von «Salbei frisch» |
| «Düne» | `duene` | `duene` | Warmer Sand mit Petrol |
| «GitHub Dark» | `github-dark` | `github-dark` | Fast Schwarz mit Blau |
| «System» (Standard) | keines | `system` | Im Hellen «Hell», im Dunkeln «GitHub Dark» |

Regeln:

- Eine Darstellung ändert **nur die Farb-Tokens**. Form, Typografie, Abstände und Layout sind überall gleich.
- Jede Darstellung belegt alle 14 Farb-Tokens. Komponenten verwenden nur die Tokens, nie feste Farben.
- Die Bedeutung der Tokens bleibt: `primary` Hauptaktion und «dabei», `accent-coral` Absage und «nicht dabei» (rötlich), `accent-mustard` «vielleicht» (gelblich), `accent-blue` Coaches. `primary` ist nie rot, orange oder gelb.
- Kontrast mindestens 4.5:1 (WCAG AA) für Text und Akzentfarben auf `bg`, `surface` und den `*-soft`-Flächen sowie für `on-primary` auf `primary`. Die Farben von «GitHub Dark» sind an das gleichnamige Theme angelehnt.
- «Düne» und «GitHub Dark» gelten unabhängig von der Systemeinstellung. Die Wahl ist im Browser gespeichert (`localStorage`, Schlüssel `stv-theme`), nicht im Konto. Ein gespeicherter Wert, den es nicht mehr gibt, fällt auf «System» zurück.
- Im Umschalter zeigt sich jede Wahl in ihren eigenen Farben (Hintergrund, Schrift und die vier Akzentfarben als Punkte).
- Eine weitere Darstellung braucht: einen Block `[data-theme='<id>']` in `styles.css`, einen Eintrag in `theme.service.ts`, im Inline-Skript von `index.html` und im Umschalter (`profil.ts`), und einen Test in `e2e/tests/darstellungen.spec.ts`.

Offen ist, was «System» im Hellen zeigen soll ([O-53](offene-punkte.md)).

## Noch nicht festgelegt

Für die Antworten der An- und Abmeldung braucht es eine Farb- und Symbolzuordnung. Naheliegend aus den bestehenden Tokens:

| Antwort | Vorschlag |
|---|---|
| dabei | `primary` |
| nicht dabei | `accent-coral` (ist bereits für «Absage» vorgesehen) |
| vielleicht | `accent-mustard` |
| offen | `muted` |

Der Status darf nicht nur über die Farbe erkennbar sein. Jede Antwort bekommt zusätzlich ein eigenes Symbol.
