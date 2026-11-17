import { expect, login, reset, test } from './helpers';

/** Entry test and template: reset, sign in, dashboard visible. */
test('F-00: Seed, sign in and start', async ({ page, request }) => {
  await reset(request);
  await login(page, 'petra');

  await page.goto('/');

  await expect(page.getByRole('heading', { level: 1, name: 'Hallo Petra' })).toBeVisible();
  await expect(page.getByTestId('tile-anwesenheit')).toBeVisible();
  await expect(page.getByRole('navigation', { name: 'Hauptnavigation' })).toBeVisible();
});
