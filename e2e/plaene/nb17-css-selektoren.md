# Testplan Nachbesserung #17 · CSS-Selektoren in E2E-Tests

Issue #17 · PR #32 · Konventionen für E2E-Tests in `docs/testbarkeit.md`

Allgemein:

- Die Kriterien betreffen die Testdateien selbst, keine Oberfläche und keine API.
  Die statischen Fälle brauchen weder Datenbank noch Anmeldung.
- Die Browser-Fälle (K3) prüfen, dass die über die Hilfen getesteten Stellen weiter stimmen:
  Vorbereitung `POST /api/test/reset`, Anmeldung als `anna` über `/dev-login`, mobil 375×812.

## K1 · Ausnahme dokumentiert und gekapselt (Kriterium 1)

| Fall | Ausgangslage · Schritte | Erwartung |
|---|---|---|
| K1.1 Doku | `docs/testbarkeit.md`, Abschnitt «Konventionen für E2E-Tests» lesen | Nennt als einzige Ausnahme Dokument-Elemente ohne Rolle (`<html>`, `<link>` im Kopf) und verweist auf `dokumentWurzel(page)` und `kopfLink(page, rel)` in `e2e/tests/hilfen.ts` |
| K1.2 Hilfen vorhanden | `e2e/tests/hilfen.ts` | Exportiert `dokumentWurzel(page)` und `kopfLink(page, rel)`; nur dort stehen die CSS-Selektoren |
| K1.3 Automatisch | `npx playwright test nb17` | «AK1» grün in `mobil` und `desktop` |

## K2 · Keine CSS-Selektoren ausserhalb der Ausnahme (Kriterium 2)

| Fall | Ausgangslage · Schritte | Erwartung |
|---|---|---|
| K2.1 Ist-Zustand | `npx playwright test nb17` | «AK2» grün; ausserhalb von `hilfen.ts` kein Treffer für `locator(`, `$(`, `$$(`, `$eval(`, `waitForSelector(`, `css=`, `xpath=` |
| K2.2 Gegenprobe `locator` | In `seed.spec.ts` die Zeile `page.locator("html")` anhängen, AK2 ausführen | AK2 rot, Meldung nennt `seed.spec.ts:<zeile>: locator() mit CSS-Selektor` |
| K2.3 Gegenprobe verkettet | Wie K2.2 mit `getByRole('x').locator('y')` | AK2 rot, `locator()` |
| K2.4 Gegenprobe `$eval`, `$$` | Wie K2.2 mit `page.$eval('div', e => e)` und `page.$$('a')` | AK2 rot, je ein Fund `$()/$$()/$eval()` |
| K2.5 Gegenprobe `waitForSelector` | Wie K2.2 mit `page.waitForSelector('x')` | AK2 rot, `waitForSelector()` |
| K2.6 Gegenprobe `xpath=` | Wie K2.2 mit `page.getByText('xpath=//div')` | AK2 rot, «CSS- oder XPath-Selektor» |
| K2.7 Aufräumen | Gegenproben-Zeilen entfernen | `git status` sauber, AK2 wieder grün |

## K3 · Umgestellte Tests prüfen weiterhin dasselbe (Regression F-00)

| Fall | Ausgangslage · Schritte | Erwartung |
|---|---|---|
| K3.1 Darkmode | Reset → als `anna` → `/profil` → Radio «Dunkel» | `<html data-theme="dark">`, Hintergrund dunkel; F-00-Test «Design» grün |
| K3.2 Manifest | Als `anna` auf `/` | `<link rel="manifest">` im Kopf mit `href="manifest.webmanifest"`; F-00-Test «PWA» grün |
| K3.3 Konsole | Nach K3.1/K3.2 | Keine Fehler oder Warnungen |
| K3.4 Gesamtlauf | `npx playwright test` (beide Projekte, inkl. axe) | Alle grün |
