import AxeBuilder from '@axe-core/playwright';
import { APIRequestContext, Locator, Page, expect, test as basis } from '@playwright/test';

/**
 * Gemeinsame Hilfen für alle E2E-Tests.
 * - `test` prüft nach jedem Test, dass die Browser-Konsole ohne Fehler blieb.
 * - `erwarteteKonsolenfehler`: Muster für Meldungen, die ein Test bewusst auslöst (z.B. der
 *   Browser-Eintrag zu einer erwarteten 409-Antwort). Standard: keine.
 */
export const test = basis.extend<{ konsole: string[]; erwarteteKonsolenfehler: RegExp[] }>({
  erwarteteKonsolenfehler: [[], { option: true }],
  konsole: [
    async ({ page, erwarteteKonsolenfehler }, use) => {
      const fehler: string[] = [];
      page.on('console', (m) => {
        if (m.type() === 'error') fehler.push(m.text());
      });
      page.on('pageerror', (e) => fehler.push(e.message));
      await use(fehler);
      const unerwartet = fehler.filter((f) => !erwarteteKonsolenfehler.some((m) => m.test(f)));
      expect(unerwartet, 'Fehler in der Browser-Konsole').toEqual([]);
    },
    { auto: true },
  ],
});

export { expect };

/** Datenbank auf den Seed zurücksetzen (docs/testdaten.md). */
export async function reset(request: APIRequestContext): Promise<void> {
  const antwort = await request.post('/api/test/reset');
  expect(antwort.status()).toBe(204);
}

/** Als Seed-Person anmelden (Schlüssel aus docs/testdaten.md). Das Cookie gilt für die Seite. */
export async function anmelden(page: Page, schluessel: string): Promise<void> {
  const antwort = await page.request.post('/api/test/anmelden', { data: { schluessel } });
  expect(antwort.status()).toBe(204);
}

/** Barrierefreiheits-Check (axe) ohne kritische oder schwere Befunde. */
export async function barrierefrei(page: Page): Promise<void> {
  const ergebnis = await new AxeBuilder({ page }).analyze();
  const befunde = ergebnis.violations
    .filter((v) => v.impact === 'critical' || v.impact === 'serious')
    .map((v) => `${v.id}: ${v.help} (${v.nodes.map((n) => n.target.join(' ')).join(', ')})`);
  expect(befunde, 'axe-Befunde').toEqual([]);
}

/**
 * Dokument-Elemente ohne Rolle (docs/testbarkeit.md, Ausnahme zu «keine CSS-Selektoren»).
 * `html` und `link` erscheinen nicht im Accessibility-Tree, deshalb gibt es für sie keinen
 * Locator über Rolle, Label, Text oder Test-ID. Nur hier sind CSS-Selektoren erlaubt;
 * nb17-keine-css-selektoren.spec.ts prüft, dass die übrigen Tests keine verwenden.
 */
export function dokumentWurzel(page: Page): Locator {
  return page.locator('html');
}

/** Der `<link rel="…">`-Eintrag im Dokumentkopf, z.B. `manifest`. */
export function kopfLink(page: Page, rel: string): Locator {
  return page.locator(`head link[rel="${rel}"]`);
}
