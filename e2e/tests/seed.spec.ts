import { anmelden, expect, reset, test } from './helpers';

/** Einstiegstest und Vorlage: Reset, Anmeldung, Dashboard sichtbar. */
test('F-00: Seed, Anmeldung und Start', async ({ page, request }) => {
  await reset(request);
  await anmelden(page, 'petra');

  await page.goto('/');

  await expect(page.getByRole('heading', { level: 1, name: 'Hallo Petra' })).toBeVisible();
  await expect(page.getByTestId('kachel-anwesenheit')).toBeVisible();
  await expect(page.getByRole('navigation', { name: 'Hauptnavigation' })).toBeVisible();
});
