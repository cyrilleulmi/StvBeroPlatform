import { request as playwrightRequest } from '@playwright/test';
import { checkAccessibility, expect, login, reset, test } from './helpers';

/**
 * Calendar subscription with filters (CAL-10 to CAL-15, E-94): which events the feed delivers. Anna has a fixed
 * address in the seed (docs/test-data.md): event 01 «dabei», 04 match day «dabei», 06 «Teamessen» without a
 * response, 07 «nicht dabei».
 */

const ANNA_FEED = '/api/calendar/seed-anna-calendar-000000000000000000000005.ics';
const T01 = '40000000-0000-4000-8000-000000000001';
const T04 = '40000000-0000-4000-8000-000000000004';
const T06 = '40000000-0000-4000-8000-000000000006';
const T07 = '40000000-0000-4000-8000-000000000007';

test.beforeEach(async ({ request }) => {
  await reset(request);
});

async function feed(baseURL: string | undefined): Promise<string> {
  const context = await playwrightRequest.newContext({ baseURL });
  const response = await context.get(ANNA_FEED);
  expect(response.status()).toBe(200);
  const body = await response.text();
  await context.dispose();
  return body;
}

test('CAL-10 CAL-11: only match days, and only where I am in or have not answered', async ({ page, baseURL }) => {
  await login(page, 'anna');
  await page.goto('/profil');
  const filter = page.getByTestId('calendar-filter');
  await expect(filter.getByRole('checkbox', { name: 'Trainings' })).toBeChecked();
  // «dabei» is always in the feed
  await expect(filter.getByRole('checkbox', { name: '«dabei» und aufgeboten' })).toBeDisabled();
  await checkAccessibility(page);

  await Promise.all([
    page.waitForResponse((r) => r.url().endsWith('/api/me/calendar/filter') && r.ok()),
    filter.getByRole('checkbox', { name: '«nicht dabei» und nicht aufgeboten' }).uncheck(),
  ]);
  await expect(filter.getByRole('status')).toContainText('Gespeichert.');
  let body = await feed(baseURL);
  expect(body).toContain(`UID:${T01}@stvbero`);
  expect(body).toContain(`UID:${T06}@stvbero`);
  expect(body).not.toContain(`UID:${T07}@stvbero`);

  await Promise.all([
    page.waitForResponse((r) => r.url().endsWith('/api/me/calendar/filter') && r.ok()),
    filter.getByRole('checkbox', { name: 'Trainings' }).uncheck(),
  ]);
  await Promise.all([
    page.waitForResponse((r) => r.url().endsWith('/api/me/calendar/filter') && r.ok()),
    filter.getByRole('checkbox', { name: 'Anlässe' }).uncheck(),
  ]);
  body = await feed(baseURL);
  expect(body).toContain(`UID:${T04}@stvbero`);
  expect(body).not.toContain(`UID:${T01}@stvbero`);
  expect(body).not.toContain(`UID:${T06}@stvbero`);

  // At least one type (CAL-10): the last one stays checked
  await filter.getByRole('checkbox', { name: 'Spieltage' }).click();
  await expect(filter.getByRole('alert')).toHaveText('Wähle mindestens eine Terminart.');
  await expect(filter.getByRole('checkbox', { name: 'Spieltage' })).toBeChecked();

  // The address stays the same, the choice is kept after a reload
  await page.reload();
  await expect(page.getByRole('textbox', { name: 'Deine Kalenderadresse' })).toHaveValue(new RegExp(`${ANNA_FEED}$`));
  await expect(filter.getByRole('checkbox', { name: 'Trainings' })).not.toBeChecked();
  await expect(filter.getByRole('checkbox', { name: 'Spieltage' })).toBeChecked();
});

test('CAL-13: a person in two teams leaves out one team', async ({ page }) => {
  await login(page, 'lea');
  await page.goto('/profil');
  const filter = page.getByTestId('calendar-filter');
  const team2 = filter.getByRole('checkbox', { name: 'Team 2' });
  await expect(team2).toBeChecked();
  await team2.uncheck();
  await expect(filter.getByRole('status')).toContainText('Gespeichert.');
  await page.reload();
  await expect(filter.getByRole('checkbox', { name: 'Team 2' })).not.toBeChecked();
  await expect(filter.getByRole('checkbox', { name: 'Team 1' })).toBeChecked();
});
