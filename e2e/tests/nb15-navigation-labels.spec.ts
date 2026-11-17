import { expect, login, reset, test } from './helpers';

/**
 * Nachbesserung #15 · Mobil sind alle Beschriftungen der Hauptnavigation vollständig lesbar,
 * auch mit fünf Einträgen für Admins. Touch-Ziele bleiben mindestens 44px gross
 * (docs/testbarkeit.md, docs/design-grundlagen.md).
 */

const ADMIN_EINTRAEGE = ['Start', 'Termine', 'Teams', 'Profil', 'Verwaltung'];

test.beforeEach(async ({ request }) => {
  await reset(request);
});

for (const [schluessel, eintraege] of [
  ['alex', ADMIN_EINTRAEGE],
  ['anna', ADMIN_EINTRAEGE.slice(0, 4)],
] as const) {
  test(`Nachbesserung #15 K1/K2: Navigation für ${schluessel} vollständig beschriftet, Touch-Ziele ab 44px`, async ({ page }) => {
    await login(page, schluessel);
    await page.goto('/');
    const nav = page.getByRole('navigation', { name: 'Hauptnavigation' });
    const links = nav.getByRole('list').getByRole('link');
    await expect(links).toHaveText([...eintraege]);

    for (const text of eintraege) {
      const link = nav.getByRole('link', { name: text });
      const beschriftung = link.getByText(text, { exact: true });
      await expect(beschriftung).toBeVisible();

      // K1: Text nicht abgeschnitten (kein Überlauf, keine Auslassungspunkte) und ganz innerhalb des Eintrags.
      const masse = await beschriftung.evaluate((el) => {
        const s = el.getBoundingClientRect();
        const l = el.closest('a')!.getBoundingClientRect();
        return {
          ueberlauf: el.scrollWidth - el.clientWidth,
          links: s.left - l.left,
          rechts: l.right - s.right,
          rechtsImFenster: window.innerWidth - s.right,
        };
      });
      expect(masse.ueberlauf, `${text}: Text läuft über`).toBeLessThanOrEqual(0);
      expect(masse.links, `${text}: ragt links hinaus`).toBeGreaterThanOrEqual(0);
      expect(masse.rechts, `${text}: ragt rechts hinaus`).toBeGreaterThanOrEqual(0);
      expect(masse.rechtsImFenster, `${text}: ausserhalb des Bildschirms`).toBeGreaterThanOrEqual(0);

      // K2: Touch-Ziel mindestens 44 × 44px.
      const box = (await link.boundingBox())!;
      expect(box.width, `${text}: Breite`).toBeGreaterThanOrEqual(44);
      expect(box.height, `${text}: Höhe`).toBeGreaterThanOrEqual(44);
    }
  });
}
