# Testplan Nachbesserung #40 · Spiele-Liste in F-05-Tests wartet auf geladene Spiele

Issue #40 · PR #41 · Datei `e2e/tests/f05-spieltage.spec.ts` · Anforderungen AUF-10, AUF-12, AUF-14, AUF-16, AUF-24 (nur Tests)

Allgemein:

- Die Kriterien betreffen die Testdatei selbst, keine Oberfläche und keine API.
- Ausgangslage für alle Browser-Fälle: Datenbank läuft (`scripts/db-start.sh`), Backend Profil `test`, Frontend auf Port 4200,
  `POST /api/test/reset`, Standard-«Jetzt» Mo 16.11.2026 06:00.

## K1 · Alle Prüfungen der Spiel-Liste warten automatisch (Kriterium 1)

| Fall | Ausgangslage · Schritte | Erwartung |
|---|---|---|
| K1.1 Statisch | `grep -n "spiele(page)" e2e/tests/f05-spieltage.spec.ts` | Jeder Aufruf steht in `await expect.poll(() => spiele(page))`, kein `expect(await spiele(page))` mehr |
| K1.2 Kein Warten mit fester Zeit | `grep -n waitForTimeout e2e/tests/f05-spieltage.spec.ts` | Kein Treffer |
| K1.3 Gegenprobe verzögerte Antwort | Lokal (nicht committen) im Test «F-05 K2» vor dem Speichern `page.route('**/api/termine/*', …)` mit 2 s Verzögerung für GET einfügen, Test ausführen | Test grün in `mobil` und `desktop` (die Prüfung wartet, bis beide Spiele da sind) |
| K1.4 Gegenprobe alter Stand | Wie K1.3, aber Zeile 109 zurück auf `expect(await spiele(page)).toEqual(…)` | Test rot mit `Received []` – beweist, dass K1.3 den ursprünglichen Fehler auslöst |
| K1.5 Aufräumen | Änderungen aus K1.3/K1.4 verwerfen | `git status` sauber |

## K2 · Wiederholungslauf besteht (Kriterium 2)

| Fall | Ausgangslage · Schritte | Erwartung |
|---|---|---|
| K2.1 Wiederholung | `cd e2e && npx playwright test tests/f05-spieltage.spec.ts --repeat-each 20 --trace off` | 560 bestanden (14 Tests × 20 × 2 Projekte), 0 fehlgeschlagen |

## Regression

| Fall | Ausgangslage · Schritte | Erwartung |
|---|---|---|
| R1 Backend | `cd backend && mvn test` | Alle grün |
| R2 E2E gesamt | `cd e2e && npx playwright test --trace off` (beide Projekte, inkl. axe) | Alle grün, nur die bekannten Projekt-Skips |
| R3 Spieltag im Browser | Reset → als `petra` über `/dev-login` → `/termine/40000000-0000-4000-8000-000000000004`, mobil 375×812 und Desktop 1280×800 | Liste «Spiele» zeigt «09:30 Team 1 – UHC Musterdorf» vor «11:45 Unihockey Beispielstadt – Team 1»; keine Konsolenfehler |
