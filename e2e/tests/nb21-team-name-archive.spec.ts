import { expect, login, reset, test } from './helpers';

/**
 * Follow-up fix #21 · The team name applies per season (SAI-02).
 * Renaming in the current season leaves the name in the archive unchanged.
 */

const TEAM_1 = '10000000-0000-4000-8000-000000000001';
const SEASON_2025 = '60000000-0000-4000-8000-000000000001';

test.beforeEach(async ({ request }) => {
  await reset(request);
});

test('Follow-up fix #21 AK1 (SAI-02): Renaming in the current season does not change the name in the archive', async ({ page }) => {
  await login(page, 'alex');
  await page.goto(`/verwaltung/teams/${TEAM_1}`);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Team 1');
  await page.getByLabel('Teamname').fill('Damen 1');
  await page.getByRole('button', { name: 'Umbenennen' }).click();
  await expect(page.getByRole('status')).toHaveText('Name gespeichert.');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Damen 1');

  await page.goto(`/teams/${TEAM_1}?saison=${SEASON_2025}`);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Team 1');
  await expect(page.getByTestId('archive-notice')).toBeVisible();

  await page.goto(`/teams/${TEAM_1}`);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Damen 1');
});
