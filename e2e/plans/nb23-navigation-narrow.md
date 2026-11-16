# Testplan Nachbesserung #23 · «Verwaltung» in der mobilen Navigation vollständig

Issue #23 · PR #34 · Hauptnavigation (`data-testid="hauptnavigation"`, `docs/ui/navigation.md`)

Allgemein für jeden Fall:

- Vorbereitung: `POST /api/test/reset`, dann `POST /api/test/anmelden` mit `{ "schluessel": "<person>" }`
  (oder `/dev-login`). Uhr: Standard, spielt keine Rolle.
- Gemessen wird pro Link der Hauptnavigation: Beschriftung (`a span`) mit `scrollWidth <= clientWidth`,
  Begrenzungsrechteck der Beschriftung innerhalb der Inhaltsfläche des Links (Link minus Innenabstand),
  Link innerhalb des Bildschirms, Link mindestens 44×44 px. Seite und Navigation ohne waagrechtes Scrollen
  (`document.documentElement.scrollWidth <= innerWidth`).
- Nach jedem Fall: keine Konsolenfehler, keine unerwarteten 4xx/5xx.

## K1 · 375×812 als `alex`, hell und dunkel (Kriterium 1)

| Fall | Ausgangslage · Schritte | Erwartung |
|---|---|---|
| K1.1 Hell | `alex`, 375×812, `/verwaltung` | 5 Links «Start», «Termine», «Teams», «Profil», «Verwaltung» vollständig, kein «…», je ≥ 44×44 px (erwartet 72×56) |
| K1.2 Dunkel | wie K1.1, `prefers-color-scheme: dark`, Bildschirmfoto | wie K1.1; Beschriftungen gut lesbar, aktiver Eintrag hervorgehoben, nichts Weisses auf Weiss |
| K1.3 Accessibility-Tree | `alex`, 375×812, Schnappschuss `navigation "Hauptnavigation"` | 5 Links mit vollständigem Namen |

## K2 · 320 px ohne waagrechtes Scrollen (Kriterium 2)

| Fall | Ausgangslage · Schritte | Erwartung |
|---|---|---|
| K2.1 Admin hell | `alex`, 320×640, `/verwaltung` | Alle fünf Beschriftungen vollständig in der Inhaltsfläche, Links ≥ 44×44 px (erwartet 63×56), `scrollWidth` = 320 |
| K2.2 Admin dunkel | wie K2.1, dunkel, Bildschirmfoto | wie K2.1, lesbar |
| K2.3 Alle Routen | `alex`, 320×640, über die Navigation nacheinander «Start», «Termine», «Teams», «Profil», «Verwaltung» | In jeder Route (aktiver Eintrag wechselt) alle Beschriftungen vollständig, kein waagrechtes Scrollen |
| K2.4 Vier Einträge | `anna`, 320×640 | 4 Links vollständig (erwartet 79×56) |
| K2.5 Umschaltpunkt | `alex`, 359 px und 360 px Breite | Beide Breiten: alle Beschriftungen vollständig (ab 360 px gilt wieder die normale Darstellung, Link 68.8 px) |
| K2.6 Desktop unverändert | `alex`, 1280×800 | Seitennavigation 240 px breit, Schrift 15 px, kein `letter-spacing`, Abstand 2 px |

## K3 · Automatischer Test (Kriterium 3)

| Fall | Schritte | Erwartung |
|---|---|---|
| K3.1 | `e2e/tests/nb23-navigation-schmal.spec.ts` im Projekt `mobil` ausführen | 4 Tests grün (375/320, hell/dunkel); im Projekt `desktop` übersprungen |

## Robustheit und Rollen

| Fall | Schritte | Erwartung |
|---|---|---|
| R1 Zurück · Neuladen | `alex` 320, durch alle Routen klicken → Zurück → `/verwaltung` neu laden | Richtige Route, Beschriftungen weiterhin vollständig |
| R2 Rollen | `anna` öffnet `/verwaltung` per Deep Link | «Keine Berechtigung», kein Eintrag «Verwaltung» in der Navigation (unverändert) |
