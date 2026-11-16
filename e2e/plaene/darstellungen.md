# Testplan · Darstellungen (NF-08, E-67)

Profil, Umschalter «Darstellung» (`data-testid="theme-umschalter"`, `docs/design-grundlagen.md#darstellungen`).
Tests: `../tests/darstellungen.spec.ts`. Hell und System: `../tests/f00-grundgeruest.spec.ts` (K6).

Allgemein für jeden Fall: `POST /api/test/reset`, anmelden als `anna`. Mobil (375×812) und Desktop (1280×800).

| Fall | Schritte | Erwartung |
|---|---|---|
| D1 Auswahl | `/profil` öffnen | Genau vier Einträge: «Hell», «Düne», «GitHub Dark», «System» |
| D2 Düne, GitHub Dark | System auf das Gegenteil stellen (`prefers-color-scheme`), Darstellung wählen, `/`, Termin 01, zurück zu `/profil` | `data-theme` ist `duene` bzw. `github-dark`, Hintergrund `#F3ECE1` bzw. `#0D1117`, bleibt nach dem Seitenwechsel, Eintrag bleibt gewählt, axe ohne Befunde auf Profil, Start und Termin |
| D3 Alter Wert | `localStorage` `stv-theme` = `dunkel` (gibt es nicht mehr), neu laden | «System» ist gewählt, kein `data-theme` |

Von Hand zu prüfen (nicht automatisiert): Die Farbe der Browser- bzw. Statusleiste folgt bei Düne und GitHub Dark deren Hintergrund (`meta[name="theme-color"]`).
