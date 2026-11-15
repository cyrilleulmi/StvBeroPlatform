import { anmelden, expect, reset, test } from './hilfen';

/**
 * Nachbesserung #21 · Der Teamname gilt pro Saison (SAI-02).
 * Umbenennen in der aktuellen Saison lässt den Namen im Archiv unverändert.
 */

const TEAM_1 = '10000000-0000-4000-8000-000000000001';
const SAISON_2025 = '60000000-0000-4000-8000-000000000001';

test.beforeEach(async ({ request }) => {
  await reset(request);
});

test('Nachbesserung #21 AK1 (SAI-02): Umbenennen in der aktuellen Saison ändert den Namen im Archiv nicht', async ({ page }) => {
  await anmelden(page, 'alex');
  await page.goto(`/verwaltung/teams/${TEAM_1}`);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Team 1');
  await page.getByLabel('Teamname').fill('Damen 1');
  await page.getByRole('button', { name: 'Umbenennen' }).click();
  await expect(page.getByRole('status')).toHaveText('Name gespeichert.');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Damen 1');

  await page.goto(`/teams/${TEAM_1}?saison=${SAISON_2025}`);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Team 1');
  await expect(page.getByTestId('archiv-hinweis')).toBeVisible();

  await page.goto(`/teams/${TEAM_1}`);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Damen 1');
});
