import type { Locator, Page } from '@playwright/test';
import { expect, login, reset, test } from './helpers';

/**
 * Follow-up fix #14 · Writing actions are disabled while their request is running.
 * A double click triggers exactly one request (docs/states.md, section "Forms").
 *
 * So that the second click surely falls into the running request, `throttle` holds the response
 * back briefly. During that time the button must be `disabled` and `aria-busy`.
 */

const TEAM_1 = '10000000-0000-4000-8000-000000000001';
const ANNA = '20000000-0000-4000-8000-000000000005';

interface Throttle {
  /** Number of requests with method and path since `throttle` was called. */
  count: number;
  /** Releases the held-back responses. */
  release: () => void;
}

/** Counts requests (method + path) and holds them back until `release` is called. */
async function throttle(page: Page, method: string, path: RegExp): Promise<Throttle> {
  let release!: () => void;
  const released = new Promise<void>((r) => (release = r));
  const throttled: Throttle = { count: 0, release: () => release() };
  page.on('request', (r) => {
    if (r.method() === method && path.test(new URL(r.url()).pathname)) throttled.count++;
  });
  await page.route(
    (url) => path.test(url.pathname),
    async (route) => {
      if (route.request().method() === method) await released;
      await route.fallback();
    },
  );
  return throttled;
}

/**
 * Double click, then check that the button is disabled during the request and marked as
 * running. `others` buttons next to it are disabled, but without `aria-busy`
 * (docs/testability.md "action running").
 */
async function doubleClickDuringRequest(
  button: Locator,
  throttled: Throttle,
  others: Locator[] = [],
): Promise<void> {
  await button.dblclick();
  await expect(button).toBeDisabled();
  await expect(button).toHaveAttribute('aria-busy', 'true');
  for (const a of others) {
    await expect(a).toBeDisabled();
    await expect(a).not.toHaveAttribute('aria-busy');
  }
  throttled.release();
}

test.beforeEach(async ({ request }) => {
  await reset(request);
});

test('Follow-up fix #14 K1: Double click on /dev-login signs in exactly once', async ({ page }) => {
  await page.goto('/dev-login');
  const throttled = await throttle(page, 'POST', /^\/api\/test\/login$/);
  const anna = page.getByRole('button', { name: /Anna Muster/ });
  // Petra appears in both of her teams (E-85): take her first entry
  const petra = page.getByTestId('dev-login-person-petra').getByRole('button');

  await anna.dblclick();
  await expect(anna).toBeDisabled();
  await expect(anna).toHaveAttribute('aria-busy', 'true');
  // The other people are disabled too, but not marked as running.
  await expect(petra).toBeDisabled();
  await expect(petra).not.toHaveAttribute('aria-busy');
  throttled.release();

  await expect(page).toHaveURL('/');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Hallo Anna');
  expect(throttled.count).toBe(1);
});

test.describe('failed sign-in', () => {
  // The browser reports the deliberately triggered 503 response in the console.
  test.use({ expectedConsoleErrors: [/status of 503/] });

  test('Follow-up fix #14 K1: After an error, sign-in on /dev-login is possible again', async ({ page }) => {
    await page.goto('/dev-login');
    let first = true;
    await page.route('**/api/test/login', async (route) => {
      if (first) {
        first = false;
        await route.fulfill({ status: 503, body: '' });
        return;
      }
      await route.fallback();
    });
    const anna = page.getByRole('button', { name: /Anna Muster/ });

    await anna.click();
    await expect(page.getByRole('alert')).toBeVisible();
    await expect(anna).toBeEnabled();
    await expect(anna).not.toHaveAttribute('aria-busy');

    await anna.click();
    await expect(page).toHaveURL('/');
  });
});

test('Follow-up fix #14 K2: Double click on Abmelden in the profile signs out once', async ({ page }) => {
  await login(page, 'anna');
  await page.goto('/profil');
  const throttled = await throttle(page, 'POST', /^\/api\/logout$/);

  await doubleClickDuringRequest(page.getByRole('button', { name: 'Abmelden' }), throttled);

  await expect(page).toHaveURL('/dev-login');
  expect(throttled.count).toBe(1);
});

test.describe('without current season', () => {
  test.use({ expectedConsoleErrors: [/status of 409/] });

  test('Follow-up fix #14 K2: Double click on Abmelden in the «keine Saison» notice signs out once', async ({ page, request }) => {
    expect((await request.delete('/api/test/current-season')).status()).toBe(204);
    await login(page, 'anna');
    await page.goto('/');
    await expect(page).toHaveURL('/keine-saison');
    const throttled = await throttle(page, 'POST', /^\/api\/logout$/);

    await doubleClickDuringRequest(page.getByRole('button', { name: 'Abmelden' }), throttled);

    await expect(page).toHaveURL('/dev-login');
    expect(throttled.count).toBe(1);
  });
});

test('Follow-up fix #14 K2: Double click on Umbenennen saves the name once', async ({ page }) => {
  await login(page, 'alex');
  await page.goto(`/verwaltung/teams/${TEAM_1}`);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Team 1');
  const throttled = await throttle(page, 'PUT', new RegExp(`^/api/teams/${TEAM_1}$`));
  await page.getByLabel('Teamname').fill('Damen 1');

  await doubleClickDuringRequest(page.getByRole('button', { name: 'Umbenennen' }), throttled, [
    page.getByRole('button', { name: 'Aufnehmen' }),
    page.getByRole('button', { name: 'Anna Muster entfernen' }),
  ]);

  await expect(page.getByRole('status')).toHaveText('Name gespeichert.');
  expect(throttled.count).toBe(1);
});

test('Follow-up fix #14 K2: Double click on Entfernen removes the person once, without errors', async ({ page }) => {
  await login(page, 'alex');
  await page.goto(`/verwaltung/teams/${TEAM_1}`);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Team 1');
  const throttled = await throttle(page, 'DELETE', new RegExp(`/members/${ANNA}$`));

  await doubleClickDuringRequest(
    page.getByRole('button', { name: 'Anna Muster entfernen' }),
    throttled,
    [
      page.getByRole('button', { name: 'Umbenennen' }),
      page.getByRole('button', { name: 'Aufnehmen' }),
      page.getByRole('button', { name: /entfernen$/ }).filter({ hasNotText: 'Anna Muster' }).first(),
    ],
  );

  await expect(page.getByRole('status')).toHaveText('Anna Muster ist nicht mehr im Team.');
  await expect(page.getByRole('alert')).toHaveCount(0);
  expect(throttled.count).toBe(1);
});

test('Follow-up fix #14 K2: Double click on Speichern of a person saves once', async ({ page }) => {
  await login(page, 'alex');
  await page.goto(`/verwaltung/personen/${ANNA}`);
  await expect(page.getByLabel('Vorname')).toHaveValue('Anna');
  const throttled = await throttle(page, 'PUT', new RegExp(`^/api/persons/${ANNA}$`));
  await page.getByLabel('Vorname').fill('Annina');

  await doubleClickDuringRequest(page.getByRole('button', { name: 'Speichern' }), throttled);

  await expect(page.getByRole('status')).toHaveText('Änderungen gespeichert.');
  expect(throttled.count).toBe(1);
});

test('Follow-up fix #14 K2: Double click on «Zur aktuellen Saison machen» switches once', async ({ page }) => {
  await login(page, 'alex');
  await page.goto('/verwaltung/saisons');
  await expect(page.getByLabel('Name')).toHaveValue('2027/28');
  await page.getByRole('button', { name: 'Saison anlegen' }).click();
  await expect(page.getByRole('status')).toContainText('2027/28 ist angelegt');
  const throttled = await throttle(page, 'POST', /^\/api\/seasons\/[^/]+\/activate$/);

  const button = page
    .getByRole('listitem', { name: '2027/28' })
    .getByRole('button', { name: 'Zur aktuellen Saison machen' });
  await doubleClickDuringRequest(button, throttled, [
    page.getByRole('button', { name: 'Saison anlegen' }),
  ]);

  await expect(page.getByRole('status')).toHaveText('2027/28 ist jetzt die aktuelle Saison.');
  await expect(page.getByRole('alert')).toHaveCount(0);
  expect(throttled.count).toBe(1);
});
