import type { Page } from '@playwright/test';
import { checkAccessibility, expect, login, reset, test } from './helpers';

/**
 * Simple driver list, proposal A «Fahrplan» (DRV-29 to DRV-31, docs/proposals/driver-list.md). Seed from
 * docs/test-data.md: match day 04 (Sun 22.11.) of Team 1 with Petra Keller's full car and Sara Brun's empty car;
 * Jana responded «dabei» and has no seat. Every test adds a match day on Sun 06.12. without cars. Default "now":
 * Mon 16.11.2026 06:00.
 */

const TEAM_1 = '10000000-0000-4000-8000-000000000001';
const T04 = '40000000-0000-4000-8000-000000000004';
const SARA = '20000000-0000-4000-8000-000000000008';

test.beforeEach(async ({ request }) => {
  await reset(request);
});

/** Signs in, adds the match day in December as the coach and opens the driver list. Returns the new match day. */
async function openPlan(page: Page, key: string): Promise<string> {
  await login(page, 'petra');
  const response = await page.request.post('/api/events', {
    data: {
      type: 'MATCH_DAY',
      title: 'Spieltag',
      start: '2026-12-06T10:00:00+01:00',
      end: '2026-12-06T14:00:00+01:00',
      teamIds: [TEAM_1],
      location: { name: 'Sporthalle Seefeld' },
      meetingPoint: { time: null, location: 'Parkplatz Sporthalle Nord' },
    },
  });
  expect(response.status()).toBe(201);
  const december = (await response.json()).id as string;
  if (key !== 'petra') await login(page, key);
  await page.goto(`/teams/${TEAM_1}/fahrerliste`);
  await expect(page.getByRole('heading', { level: 1, name: 'Fahrerliste' })).toBeVisible();
  return december;
}

/** The cars of match day 04 with the people in them. */
function board(page: Page) {
  return page.getByTestId(`seat-board-${T04}`);
}

test('DRV-03 DRV-19: right before the match the coach seats everyone with one tap, or person by person', async ({ page }) => {
  await openPlan(page, 'petra');
  await expect(board(page).getByTestId('seat-waiting')).toContainText('Jana');

  // Person by person: tap Jana, then Sara's car
  await board(page).getByTestId('seat-waiting').getByRole('button', { name: /^Jana/ }).click();
  await board(page).getByTestId(`seat-enter-${SARA}`).click();
  await expect(board(page).getByTestId(`seat-car-${SARA}`)).toContainText('Jana');
  await expect(board(page).getByTestId('seat-waiting')).toHaveCount(0);
  await expect(board(page).getByTestId('seat-status')).toContainText('Alle haben Platz');

  // Out again, and then everyone at once
  await board(page).getByTestId(`seat-car-${SARA}`).getByRole('button', { name: 'Jana Wyss' }).click();
  await board(page).getByRole('button', { name: 'Jana aussteigen' }).click();
  await expect(board(page).getByTestId('seat-waiting')).toContainText('Jana');
  await board(page).getByTestId('seat-fill').click();
  await expect(page.getByRole('status')).toContainText('Alle haben einen Platz');
  await expect(board(page).getByTestId('seat-waiting')).toHaveCount(0);
  await checkAccessibility(page);
});

test('DRV-29 DRV-30: the coach sets the number of people and plans the season with one button', async ({ page }) => {
  const december = await openPlan(page, 'petra');
  await expect(page.getByTestId(`plan-match-day-${december}`)).toHaveAttribute('data-state', 'short');

  // One person less is expected
  const before = Number(await page.getByTestId('expected').innerText());
  await page.getByRole('button', { name: 'Personen pro Spieltag: eine weniger' }).click();
  await expect(page.getByTestId('expected')).toHaveText(String(before - 1));

  await page.getByTestId('distribute').click();
  await expect(page.getByRole('status')).toContainText('Die Fahrten sind verteilt');
  await expect(page.getByTestId(`plan-match-day-${december}`)).toHaveAttribute('data-state', 'enough');
  await expect(page.getByTestId(`plan-match-day-${T04}`)).toHaveAttribute('data-state', 'enough');
  await expect(page.getByTestId('distribute')).toBeDisabled();
  await checkAccessibility(page);
});

test('DRV-25 DRV-31: a tap on a driver replaces her; a free slot adds one', async ({ page }) => {
  await openPlan(page, 'petra');
  const day = page.getByTestId(`plan-match-day-${T04}`);
  const drivers = day.getByLabel('Fahrerinnen und Fahrer').getByTestId(/^plan-car-/);
  await expect(drivers).toHaveCount(2);

  // Sara is replaced by the person who is next in line
  await day.getByTestId(`plan-car-${SARA}`).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toContainText('Sara Brun · So 22.11.');
  await expect(dialog.getByRole('list', { name: 'Wer fahren kann' }).getByRole('listitem').first()).toContainText('ist dran');
  await dialog.getByRole('list', { name: 'Wer fahren kann' }).getByRole('button').first().click();
  await expect(page.getByRole('status')).toContainText('statt Sara Brun');
  await expect(day.getByTestId(`plan-car-${SARA}`)).toHaveCount(0);
  await expect(drivers).toHaveCount(2);

  // One more driver
  await day.getByTestId(`plan-add-${T04}`).click();
  await dialog.getByTestId(`picker-${SARA}`).click();
  await expect(drivers).toHaveCount(3);

  // And off again
  await day.getByTestId(`plan-car-${SARA}`).click();
  await dialog.getByTestId('picker-remove').click();
  await expect(drivers).toHaveCount(2);
});

test('DRV-31: a player sees her trips, passes one on and enters herself', async ({ page }) => {
  const december = await openPlan(page, 'sara');
  await expect(page.getByTestId('my-trips')).toContainText('Du fährst 1-mal: So 22.11.');
  // Players do not plan the season
  await expect(page.getByTestId('planning')).toHaveCount(0);

  await page.getByTestId(`plan-car-${SARA}`).click();
  await page.getByTestId('picker-pass').click();
  await expect(page.getByTestId('my-trips')).toContainText('an keinem Spieltag');

  await page.getByTestId(`plan-join-${december}`).click();
  await expect(page.getByTestId('my-trips')).toContainText('Du fährst 1-mal: So 06.12.');
});
