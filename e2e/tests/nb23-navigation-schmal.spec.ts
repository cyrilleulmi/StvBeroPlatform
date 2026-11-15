import { anmelden, expect, reset, test } from './hilfen';

/**
 * Nachbesserung #23 · Die Beschriftungen der mobilen Hauptnavigation sind für Admins (fünf Einträge)
 * bei 375px und auch bei 320px vollständig lesbar, in hell und dunkel, ohne horizontales Scrollen
 * (docs/design-grundlagen.md: mobil zuerst, lesbare Beschriftungen).
 */

const ADMIN_EINTRAEGE = ['Start', 'Termine', 'Teams', 'Profil', 'Verwaltung'];

test.beforeEach(async ({ request }) => {
  await reset(request);
});

for (const breite of [375, 320]) {
  for (const schema of ['light', 'dark'] as const) {
    test(`Nachbesserung #23 K1/K2/K3: Navigation für alex bei ${breite}px (${schema}) vollständig lesbar`, async ({ page }, testInfo) => {
      test.skip(testInfo.project.name !== 'mobil', 'Bottom-Navigation gibt es nur mobil');
      await page.setViewportSize({ width: breite, height: 812 });
      await page.emulateMedia({ colorScheme: schema });
      await anmelden(page, 'alex');
      await page.goto('/verwaltung');
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('Verwaltung');

      const nav = page.getByRole('navigation', { name: 'Hauptnavigation' });
      await expect(nav.getByRole('link')).toHaveText(ADMIN_EINTRAEGE);

      for (const text of ADMIN_EINTRAEGE) {
        const link = nav.getByRole('link', { name: text });
        const beschriftung = link.getByText(text, { exact: true });
        await expect(beschriftung).toBeVisible();
        const masse = await beschriftung.evaluate((el) => {
          const s = el.getBoundingClientRect();
          const a = el.closest('a')!;
          const l = a.getBoundingClientRect();
          const stil = getComputedStyle(a);
          // Der Text muss in der Inhaltsfläche des Eintrags liegen, nicht im Innenabstand bis an den Rand der Markierung.
          return {
            ueberlauf: el.scrollWidth - el.clientWidth,
            links: s.left - (l.left + parseFloat(stil.paddingLeft)),
            rechts: l.right - parseFloat(stil.paddingRight) - s.right,
            linksImFenster: s.left,
            rechtsImFenster: window.innerWidth - s.right,
          };
        });
        expect(masse.ueberlauf, `${text}: Text läuft über`).toBeLessThanOrEqual(0);
        expect(masse.links, `${text}: ragt links aus dem Eintrag`).toBeGreaterThanOrEqual(0);
        expect(masse.rechts, `${text}: ragt rechts aus dem Eintrag`).toBeGreaterThanOrEqual(0);
        expect(masse.linksImFenster, `${text}: links ausserhalb des Bildschirms`).toBeGreaterThanOrEqual(0);
        expect(masse.rechtsImFenster, `${text}: rechts ausserhalb des Bildschirms`).toBeGreaterThanOrEqual(0);

        const box = (await link.boundingBox())!;
        expect(box.width, `${text}: Breite Touch-Ziel`).toBeGreaterThanOrEqual(44);
        expect(box.height, `${text}: Höhe Touch-Ziel`).toBeGreaterThanOrEqual(44);
      }

      // K2: kein horizontales Scrollen, weder auf der Seite noch in der Navigation.
      const scroll = await nav.evaluate((el) => ({
        seite: document.documentElement.scrollWidth - document.documentElement.clientWidth,
        navigation: el.scrollWidth - el.clientWidth,
      }));
      expect(scroll.seite, 'Seite scrollt horizontal').toBeLessThanOrEqual(0);
      expect(scroll.navigation, 'Navigation scrollt horizontal').toBeLessThanOrEqual(0);
    });
  }
}
