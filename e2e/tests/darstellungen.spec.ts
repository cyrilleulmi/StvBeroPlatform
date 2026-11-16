import { anmelden, barrierefrei, dokumentWurzel, expect, reset, test } from './hilfen';

/**
 * Darstellungen (NF-08, E-67): Im Profil stehen Hell, Düne, GitHub Dark und System zur Wahl.
 * Düne und GitHub Dark gelten unabhängig von der Systemeinstellung, bleiben nach dem Neuladen
 * erhalten und bestehen den Barrierefreiheits-Check (Kontrast). Hell und System prüft
 * f00-grundgeruest.spec.ts. Plan: e2e/plaene/darstellungen.md
 */

const DARSTELLUNGEN = [
  { name: 'Düne', id: 'duene', farbe: 'rgb(243, 236, 225)', system: 'dark' },
  { name: 'GitHub Dark', id: 'github-dark', farbe: 'rgb(13, 17, 23)', system: 'light' },
] as const;
const TERMIN_01 = '/termine/40000000-0000-4000-8000-000000000001';

const hintergrund = (page: import('@playwright/test').Page) =>
  page.evaluate(() => getComputedStyle(document.body).backgroundColor);

test.beforeEach(async ({ request }) => {
  await reset(request);
});

test('NF-08: Der Umschalter bietet genau Hell, Düne, GitHub Dark und System', async ({ page }) => {
  await anmelden(page, 'anna');
  await page.goto('/profil');

  const umschalter = page.getByTestId('theme-umschalter');
  await expect(umschalter.getByRole('radio')).toHaveCount(4);
  for (const name of ['Hell', 'Düne', 'GitHub Dark', 'System']) {
    await expect(umschalter.getByRole('radio', { name, exact: true })).toBeVisible();
  }
});

for (const d of DARSTELLUNGEN) {
  test(`NF-08: Darstellung «${d.name}» wählen, bleibt erhalten, ohne Barrierefreiheits-Befunde`, async ({ page }) => {
    // Das System steht auf dem Gegenteil: Die gewählte Darstellung muss trotzdem gelten.
    await page.emulateMedia({ colorScheme: d.system });
    await anmelden(page, 'anna');
    await page.goto('/profil');

    await page.getByTestId('theme-umschalter').getByRole('radio', { name: d.name, exact: true }).check();
    await expect(dokumentWurzel(page)).toHaveAttribute('data-theme', d.id);
    expect(await hintergrund(page)).toBe(d.farbe);
    await barrierefrei(page);

    await page.goto('/');
    await expect(page.getByTestId('kachel-anwesenheit')).toBeVisible();
    await expect(dokumentWurzel(page)).toHaveAttribute('data-theme', d.id);
    expect(await hintergrund(page)).toBe(d.farbe);
    await barrierefrei(page);

    await page.goto(TERMIN_01);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await barrierefrei(page);

    await page.goto('/profil');
    await expect(
      page.getByTestId('theme-umschalter').getByRole('radio', { name: d.name, exact: true }),
    ).toBeChecked();
  });
}

test('NF-08: Eine gespeicherte Darstellung, die es nicht mehr gibt, fällt auf «System» zurück', async ({ page }) => {
  await page.emulateMedia({ colorScheme: 'light' });
  await anmelden(page, 'anna');
  await page.goto('/profil');
  await page.evaluate(() => localStorage.setItem('stv-theme', 'dunkel'));
  await page.reload();

  await expect(page.getByTestId('theme-umschalter').getByRole('radio', { name: 'System', exact: true })).toBeChecked();
  await expect(dokumentWurzel(page)).not.toHaveAttribute('data-theme', /.*/);
});
