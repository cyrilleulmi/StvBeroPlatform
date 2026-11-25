import { checkAccessibility, expect, login, reset, test } from './helpers';

/**
 * Follow-up #13 · Without a current season GET /api/me answers with 409 (SAI-03).
 * The UI then shows an understandable notice instead of a blank screen.
 */

test.describe('without current season', () => {
  // The browser reports the expected 409 response of /api/me in the console.
  test.use({ expectedConsoleErrors: [/status of 409/] });

  test.beforeEach(async ({ request }) => {
    await reset(request);
    const response = await request.delete('/api/test/current-season');
    expect(response.status()).toBe(204);
  });

  test('SAI-03: without a current season the app shows a notice instead of a blank screen', { tag: '@desktop' }, async ({ page }) => {
    await login(page, 'anna');

    await page.goto('/teams');

    await expect(page).toHaveURL('/keine-saison?ziel=%2Fteams');
    await expect(page).toHaveTitle('Keine aktuelle Saison · STV Bero');
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Noch keine aktuelle Saison');
    await expect(page.getByTestId('no-season-notice')).toContainText('keine Saison als aktuelle Saison festgelegt');
    await expect(page.getByRole('button', { name: 'Nochmals versuchen' })).toBeVisible();
    await checkAccessibility(page);
  });

  test('SAI-03: after setting a season «Nochmals versuchen» leads back to the target', async ({ page, request }) => {
    await login(page, 'anna');
    await page.goto('/');
    await expect(page).toHaveURL('/keine-saison');

    await reset(request);
    await login(page, 'anna');
    await page.getByRole('button', { name: 'Nochmals versuchen' }).click();

    await expect(page).toHaveURL('/');
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Hallo Anna');
  });

  test('SAI-03: sign-in in test mode without a current season leads to the notice', async ({ page }) => {
    await page.goto('/dev-login?ziel=%2Fprofil');
    await page.getByRole('button', { name: /Petra Keller/ }).click();

    await expect(page).toHaveURL('/keine-saison?ziel=%2Fprofil');
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Noch keine aktuelle Saison');
  });

  test('SAI-03: signing out from the notice leads to sign-in', async ({ page }) => {
    await login(page, 'alex');
    await page.goto('/');
    await expect(page).toHaveURL('/keine-saison');

    await page.getByRole('button', { name: 'Abmelden' }).click();

    await expect(page).toHaveURL('/dev-login');
  });
});

test.describe('with current season', () => {
  test.beforeEach(async ({ request }) => {
    await reset(request);
  });

  test('SAI-03: /keine-saison without sign-in leads to sign-in', async ({ page }) => {
    await page.goto('/keine-saison');

    await expect(page).toHaveURL('/dev-login');
    await expect(page.getByTestId('no-season-notice')).toHaveCount(0);
  });

  test('SAI-03: /keine-saison without sign-in keeps the target', async ({ page }) => {
    await page.goto('/keine-saison?ziel=%2Fteams');

    await expect(page).toHaveURL('/dev-login?ziel=%2Fteams');
  });

  test('SAI-03: /keine-saison with a current season leads directly to the target', async ({ page }) => {
    await login(page, 'anna');

    await page.goto('/keine-saison?ziel=%2Fteams');

    await expect(page).toHaveURL('/teams');
    await expect(page.getByTestId('no-season-notice')).toHaveCount(0);
  });

  test('SAI-03: /keine-saison with a current season and a foreign target leads to the start page', async ({ page }) => {
    await login(page, 'anna');

    await page.goto('/keine-saison?ziel=https%3A%2F%2Fexample.com');

    await expect(page).toHaveURL('/');
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Hallo Anna');
  });
});
