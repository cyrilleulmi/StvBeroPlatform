# Testplan Nachbesserung #15 · Navigationsbeschriftungen mobil vollständig

Issue #15 · PR #29 · Hauptnavigation (`data-testid="hauptnavigation"`, `docs/ui/navigation.md`)

Allgemein für jeden Fall:

- Vorbereitung: `POST /api/test/reset`, dann `POST /api/test/anmelden` mit `{ "schluessel": "<person>" }`
  (oder `/dev-login`). Uhr: Standard, spielt keine Rolle.
- Gemessen wird pro Link der Hauptnavigation: Beschriftung (`a span`) mit `scrollWidth <= clientWidth`,
  Begrenzungsrechteck der Beschriftung innerhalb des Links, Link innerhalb des Bildschirms,
  Link mindestens 44×44 px. Kein waagrechtes Scrollen der Seite.
- Nach jedem Fall: keine Konsolenfehler, keine unerwarteten 4xx/5xx.

## K1 · Alle Beschriftungen vollständig (Kriterium 1)

| Fall | Ausgangslage · Schritte | Erwartung |
|---|---|---|
| K1.1 Admin 375 | `alex`, 375×812, `/verwaltung` | 5 Links «Start», «Termine», «Teams», «Profil», «Verwaltung», Text genau so, nicht abgeschnitten, kein «…» |
| K1.2 Aktiver Eintrag | `alex`, 375×812, nacheinander `/`, `/termine`, `/teams`, `/profil`, `/verwaltung` | In jeder Route (auch «Verwaltung» fett/aktiv) alle Beschriftungen vollständig |
| K1.3 Spielerin | `anna`, 375×812, alle fünf Routen | 4 Links, alle vollständig |
| K1.4 Coach | `petra`, 375×812, alle fünf Routen | 4 Links, alle vollständig |
| K1.5 Schmales Handy | `alex`, `anna`, `petra`, 320×640, alle Routen | Alle Beschriftungen vollständig, kein waagrechtes Scrollen |
| K1.6 Desktop | alle drei Personen, 1280×800 | Seitennavigation unverändert, Schrift 15 px, alle Beschriftungen vollständig |
| K1.7 Darkmode | `alex`, 375×812 und 1280×800, `prefers-color-scheme: dark`, `/verwaltung`, Bildschirmfoto | Beschriftungen gut lesbar, aktiver Eintrag hervorgehoben, nichts Weisses auf Weiss |
| K1.8 Accessibility-Tree | `alex`, 375×812, Schnappschuss `navigation "Hauptnavigation"` | 5 Links mit vollständigem Namen |

## K2 · Touch-Ziele (Kriterium 2)

| Fall | Ausgangslage · Schritte | Erwartung |
|---|---|---|
| K2.1 Admin 375 | wie K1.1 | Jeder Link ≥ 44×44 px (erwartet 72×56) |
| K2.2 Vier Einträge 375 | `anna`, `petra` | Jeder Link ≥ 44×44 px (erwartet 90×56) |
| K2.3 320 px | `alex` 320×640 | Jeder Link ≥ 44×44 px (erwartet 61×56) |

## Robustheit und Rollen

| Fall | Schritte | Erwartung |
|---|---|---|
| R1 Neuladen · Zurück | `alex` 375, `/verwaltung` → Navigation «Teams» → Neuladen → Zurück | Landet auf `/verwaltung`, Beschriftungen weiterhin vollständig |
| R2 Rollen | `anna` öffnet `/verwaltung` per Deep Link | «Keine Berechtigung», kein Eintrag «Verwaltung» in der Navigation (unverändert) |
