import { anmelden, barrierefrei, expect, reset, test } from './hilfen';

/**
 * Nachbesserung #13 · Ohne aktuelle Saison antwortet GET /api/ich mit 409 (SAI-03).
 * Die Oberfläche zeigt dann einen verständlichen Hinweis statt eines leeren Bildschirms.
 */

// Der Browser meldet die erwartete 409-Antwort von /api/ich in der Konsole.
test.use({ erwarteteKonsolenfehler: [/status of 409/] });

test.beforeEach(async ({ request }) => {
  await reset(request);
  const antwort = await request.delete('/api/test/aktuelle-saison');
  expect(antwort.status()).toBe(204);
});

test('SAI-03: ohne aktuelle Saison zeigt die App einen Hinweis statt eines leeren Bildschirms', async ({ page }) => {
  await anmelden(page, 'anna');

  await page.goto('/teams');

  await expect(page).toHaveURL('/keine-saison?ziel=%2Fteams');
  await expect(page).toHaveTitle('Keine aktuelle Saison · STV Bero');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Noch keine aktuelle Saison');
  await expect(page.getByTestId('keine-saison-hinweis')).toContainText('keine Saison als aktuelle Saison festgelegt');
  await expect(page.getByRole('button', { name: 'Nochmals versuchen' })).toBeVisible();
  await barrierefrei(page);
});

test('SAI-03: nach dem Festlegen einer Saison führt «Nochmals versuchen» zum Ziel zurück', async ({ page, request }) => {
  await anmelden(page, 'anna');
  await page.goto('/');
  await expect(page).toHaveURL('/keine-saison');

  await reset(request);
  await anmelden(page, 'anna');
  await page.getByRole('button', { name: 'Nochmals versuchen' }).click();

  await expect(page).toHaveURL('/');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Hallo Anna');
});

test('SAI-03: Anmeldung im Testmodus ohne aktuelle Saison führt auf den Hinweis', async ({ page }) => {
  await page.goto('/dev-login?ziel=%2Fprofil');
  await page.getByRole('button', { name: /Petra Keller/ }).click();

  await expect(page).toHaveURL('/keine-saison?ziel=%2Fprofil');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Noch keine aktuelle Saison');
});

test('SAI-03: Abmelden vom Hinweis führt zur Anmeldung', async ({ page }) => {
  await anmelden(page, 'alex');
  await page.goto('/');
  await expect(page).toHaveURL('/keine-saison');

  await page.getByRole('button', { name: 'Abmelden' }).click();

  await expect(page).toHaveURL('/dev-login');
});
