import type { Page } from '@playwright/test';
import { expect, login, reset, test } from './helpers';

/**
 * Follow-up fix #26 · Signing out is idempotent. Two (also simultaneous) `POST /api/logout`
 * with the same session cookie both answer with 204 and delete the cookie, never with 500.
 * `helpers.ts` checks the browser console after every test.
 */

/** Collects all responses of the page with status 5xx. */
function collectServerErrors(page: Page): string[] {
  const errors: string[] = [];
  page.on('response', (r) => {
    if (r.status() >= 500) errors.push(`${r.status()} ${r.request().method()} ${new URL(r.url()).pathname}`);
  });
  return errors;
}

/** Console errors of another tab (`helpers.ts` checks only `page`). */
function collectConsoleErrors(tab: Page, allowed: RegExp[] = []): string[] {
  const errors: string[] = [];
  tab.on('console', (m) => {
    if (m.type() === 'error' && !allowed.some((e) => e.test(m.text()))) errors.push(m.text());
  });
  tab.on('pageerror', (e) => errors.push(e.message));
  return errors;
}

test.beforeEach(async ({ request }) => {
  await reset(request);
});

test('Follow-up fix #26 K1: two simultaneous sign-outs with the same cookie both answer with 204', async ({ page }) => {
  await login(page, 'anna');

  const responses = await Promise.all(
    Array.from({ length: 4 }, () => page.request.post('/api/logout')),
  );

  for (const response of responses) {
    expect(response.status()).toBe(204);
    expect(response.headers()['set-cookie']).toContain('stv_session=;');
  }
  expect((await (await page.request.get('/api/session')).json()).signedIn).toBe(false);
});

test('Follow-up fix #26 K2: double click on Abmelden in the profile leads to sign-in, without 5xx', async ({ page }) => {
  const errors = collectServerErrors(page);
  await login(page, 'anna');
  await page.goto('/profil');

  await page.getByRole('button', { name: 'Abmelden' }).dblclick();

  await expect(page).toHaveURL('/dev-login');
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  expect(errors).toEqual([]);
});

test('Follow-up fix #26 K2: signing out in two tabs at the same time leads both to sign-in, without 5xx', async ({ page, context }) => {
  await login(page, 'anna');
  const second = await context.newPage();
  const errors = [page, second].map(collectServerErrors);
  const secondConsole = collectConsoleErrors(second);
  await page.goto('/profil');
  await second.goto('/profil');

  await Promise.all([
    page.getByRole('button', { name: 'Abmelden' }).click(),
    second.getByRole('button', { name: 'Abmelden' }).click(),
  ]);

  await expect(page).toHaveURL('/dev-login');
  await expect(second).toHaveURL('/dev-login');
  expect(errors.flat()).toEqual([]);
  expect(secondConsole, 'Errors in the console of the second tab').toEqual([]);
  await second.close();
});

test.describe('without current season', () => {
  test.use({ expectedConsoleErrors: [/status of 409/] });

  test('Follow-up fix #26 K2: double click on Abmelden in the «keine Saison» notice leads to sign-in, without 5xx', async ({ page, request }) => {
    expect((await request.delete('/api/test/current-season')).status()).toBe(204);
    const errors = collectServerErrors(page);
    await login(page, 'anna');
    await page.goto('/');
    await expect(page).toHaveURL('/keine-saison');

    await page.getByRole('button', { name: 'Abmelden' }).dblclick();

    await expect(page).toHaveURL('/dev-login');
    expect(errors).toEqual([]);
  });

  test('Follow-up fix #26 K2: signing out from the «keine Saison» notice in two tabs at the same time, without 5xx', async ({ page, context, request }) => {
    expect((await request.delete('/api/test/current-season')).status()).toBe(204);
    await login(page, 'anna');
    const second = await context.newPage();
    const errors = [page, second].map(collectServerErrors);
    const secondConsole = collectConsoleErrors(second, [/status of 409/]);
    await page.goto('/');
    await second.goto('/');
    await expect(page).toHaveURL('/keine-saison');
    await expect(second).toHaveURL('/keine-saison');

    await Promise.all([
      page.getByRole('button', { name: 'Abmelden' }).click(),
      second.getByRole('button', { name: 'Abmelden' }).click(),
    ]);

    await expect(page).toHaveURL('/dev-login');
    await expect(second).toHaveURL('/dev-login');
    expect(errors.flat()).toEqual([]);
    expect(secondConsole, 'Errors in the console of the second tab').toEqual([]);
    await second.close();
  });
});
