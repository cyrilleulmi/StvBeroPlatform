# Testplan F-00 · Grundgerüst

Issue #1, PR #11. Anforderungen: NF-01–NF-24, RR-05.

**Allgemeine Ausgangslage:** `POST /api/test/reset` (Seed F-00: Saisons, Personen 01–17 mit Konto), Uhr Standard (Mo 16.11.2026 06:00). App: Frontend `http://localhost:4200` (Development-Build), Backend `http://localhost:8080`.
Viewports: mobil 375×812, Desktop 1280×800. Nach jedem Fall: Konsole ohne Fehler, keine unerwarteten 4xx/5xx.

## K1 · `scripts/start.sh`

1. Aus dem Repo-Stand `scripts/start.sh` ausführen (DB, Backend Profil `dev`, Frontend).
2. Erwartung: Meldung «Bereit.», `http://localhost:4200` antwortet 200, `GET http://localhost:4200/api/sitzung` (Proxy) antwortet 200 mit `angemeldet: false`.

## K2 · `scripts/test.sh`

1. `scripts/test.sh` ausführen.
2. Erwartung: API-Lint ohne Befund, Backend-Tests grün, Frontend-Lint und -Build grün, E2E mobil + desktop (mit axe) grün, Exit-Code 0.

## K3 · `/dev-login`

Ausgangslage: Reset, nicht angemeldet.

1. `/dev-login` öffnen.
2. Erwartung: Alle 17 Seed-Personen sind sichtbar (Test-ID `dev-login-person-<schluessel>`, z.B. `dev-login-person-petra`), mit Name. Banner `testmodus-hinweis` sichtbar.
3. Auf «Anna Muster» klicken.
4. Erwartung: URL `/`, Begrüssung mit «Anna». Nach Neuladen weiterhin angemeldet.
5. Grenzfall: `POST /api/test/anmelden {schluessel: "gibtsnicht"}` → 400 bzw. 404 (Problem Details), keine Sitzung. Leerer Body → 400.

## K4 · Start und Kacheln

Ausgangslage: Reset, als `anna` angemeldet.

1. `/` öffnen.
2. Erwartung: Überschrift mit «Anna». Kacheln entsprechen `GET /api/kacheln`; Kachel `kachel-anwesenheit` sichtbar.
3. Kachel «Anwesenheit» antippen.
4. Erwartung: URL `/termine`, leerer Zustand mit Text (kein leerer Bildschirm).
5. Rolle: als `alex` (Admin) `GET /api/kacheln` und Start vergleichen.

## K5 · Navigation (RR-05)

1. Mobil als `anna`: `nav` «Hauptnavigation» unten, Einträge Start, Termine, Teams, Profil (keine «Verwaltung»). Aktiver Eintrag `aria-current="page"`, wechselt beim Navigieren.
2. Desktop als `anna`: Navigation links.
3. Als `alex`: zusätzlich «Verwaltung», `/verwaltung` zeigt leeren Zustand.
4. Als `anna`: `/verwaltung` per Deep Link → «Keine Berechtigung» (`role="alert"`).
5. Grenzfall 1023px vs. 1024px: Navigation wechselt von unten nach links.

## K6 · Design-Tokens und Darstellung

1. Als `anna` `/profil` öffnen, Umschalter `theme-umschalter` mit Hell/Düne/GitHub Dark/System (seit E-67, vorher Hell/Dunkel/System).
2. «GitHub Dark» wählen → `<html data-theme="github-dark">`, Bildschirm dunkel; neu laden → bleibt dunkel.
3. «Hell» → hell, bleibt nach Neuladen. «System» mit `prefers-color-scheme: dark` → dunkel (GitHub Dark).
4. Jeder Screen (`/dev-login`, `/`, `/termine`, `/teams`, `/profil`, `/verwaltung`, Nicht gefunden) im Darkmode: lesbar, nichts Weisses auf Weiss.
5. CSS Custom Properties aus design-grundlagen.md auf `:root` vorhanden (z.B. `--primary`).

## K7 · PWA

1. `/manifest.webmanifest` liefert gültiges Manifest (name, icons 192/512, start_url, display standalone).
2. Service Worker `sw.js` wird registriert (`navigator.serviceWorker.getRegistration()`).

## K8 · Test-Endpunkte

1. Profil dev: `POST /api/test/reset` 204, `PUT /api/test/uhr` 204, `POST /api/test/anmelden` 204 mit Cookie.
2. Profil prod: automatischer Test `ProdOhneTesthilfenTest` (404, nicht in OpenAPI).

## K9 · OpenAPI-Vergleich

`OpenApiVertragTest` läuft grün in den Backend-Tests.

## K10 · Uhr

1. `PUT /api/test/uhr` auf 2027-01-05T10:00 → `GET /api/ich` liefert weiterhin konsistente Daten; Backend-Test `uhrSetzenVeraendertJetztImGanzenBackend` grün.
2. Ungültige Uhr (`{}` oder Text) → 400.

## K11 · CI

Checks auf dem PR: Workflow `ci.yml` grün.

## K12 · Rückleitung

Ausgangslage: Reset, nicht angemeldet.

1. `/profil` öffnen → Weiterleitung auf `/dev-login?ziel=%2Fprofil`.
2. Als `petra` anmelden → URL `/profil`.
3. Gleiches für `/termine`, `/teams`, `/verwaltung` (als `alex`), unbekannte Route.
4. Grenzfall offener Redirect: `/dev-login?ziel=https://example.com` bzw. `?ziel=//example.com` → nach Anmeldung auf `/`.
5. Abmelden im Profil → `/anmelden` bzw. `/dev-login`; Zurück-Taste zeigt keine geschützten Inhalte.

## Robustheit

- Doppelklick auf eine Person in `/dev-login`: genau eine Anmeldung, keine Fehler.
- Neuladen auf `/profil` nach Theme-Wechsel.
- Zurück-Taste nach Anmeldung.
- Unbekannte Route angemeldet: «Diese Seite gibt es nicht.» mit Link «Zur Startseite».
