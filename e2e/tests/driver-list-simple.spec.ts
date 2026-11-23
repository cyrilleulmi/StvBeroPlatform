import type { Page } from '@playwright/test';
import { checkAccessibility, expect, login, reset, test } from './helpers';

/**
 * Driver list «Raster» (E-96: DRV-29 to DRV-33, docs/proposals/driver-list.md). Seed from
 * docs/test-data.md: match day 04 (Sun 22.11.) of Team 1 with Petra Keller's full car and Sara Brun's empty car;
 * Jana responded «dabei» and has no seat. Every test adds a match day on Sun 06.12. without cars. Default "now":
 * Mon 16.11.2026 06:00.
 */

const TEAM_1 = '10000000-0000-4000-8000-000000000001';
const T04 = '40000000-0000-4000-8000-000000000004';
const SARA = '20000000-0000-4000-8000-000000000008';
const KIM = '20000000-0000-4000-8000-000000000011';

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
  await expect(page.getByTestId(`plan-places-${december}`)).toHaveAttribute('data-state', 'short');

  // One person less is expected
  const before = Number(await page.getByTestId('expected').innerText());
  await page.getByRole('button', { name: 'Personen pro Spieltag: eine weniger' }).click();
  await expect(page.getByTestId('expected')).toHaveText(String(before - 1));

  // «Fair verteilen» first shows a proposal: dashed cars, nothing entered yet
  await page.getByTestId('distribute').click();
  const proposal = page.getByTestId('distribution-proposal');
  await expect(proposal).toContainText('eingetragen ist noch nichts');
  await expect(page.getByRole('img', { name: /^Vorschlag: .* fährt am So 06\.12\.$/ }).first()).toBeVisible();
  await expect(page.getByTestId(`plan-places-${december}`)).toHaveAttribute('data-state', 'enough');
  await checkAccessibility(page);
  // Discarding enters nothing
  await page.getByTestId('distribute-discard').click();
  await expect(proposal).toHaveCount(0);
  await expect(page.getByTestId(`plan-places-${december}`)).toHaveAttribute('data-state', 'short');

  // Accepting enters the trips, and they stay
  await page.getByTestId('distribute').click();
  await page.getByTestId('distribute-accept').click();
  await expect(page.getByRole('status')).toContainText('Die Fahrten sind eingetragen');
  await expect(page.getByTestId(`plan-places-${december}`)).toHaveAttribute('data-state', 'enough');
  await expect(page.getByTestId(`plan-places-${T04}`)).toHaveAttribute('data-state', 'enough');
  await expect(page.getByRole('img', { name: /^Vorschlag: / })).toHaveCount(0);
  await expect(page.getByTestId('distribute')).toBeDisabled();
  // Petra and Sara keep their cars on match day 04
  await expect(page.getByTestId(`cell-${SARA}-${T04}`)).toHaveAttribute('aria-pressed', 'true');
});

test('DRV-33: «Alle entfernen» takes every planned driver off after a question', async ({ page }) => {
  const december = await openPlan(page, 'petra');
  await page.getByTestId('remove-all').click();
  const dialog = page.getByRole('dialog', { name: 'Alle geplanten Fahrten entfernen?' });
  await expect(dialog).toContainText('2 Fahrten an 1 kommenden Spieltag wird entfernt');
  await dialog.getByRole('button', { name: 'Abbrechen' }).click();
  await expect(page.getByTestId(`cell-${SARA}-${T04}`)).toHaveAttribute('aria-pressed', 'true');

  await page.getByTestId('remove-all').click();
  await page.getByTestId('plan-confirm-confirm').click();
  await expect(page.getByRole('status')).toHaveText('Alle geplanten Fahrten sind entfernt.');
  await expect(page.getByTestId(`cell-${SARA}-${T04}`)).toHaveAttribute('aria-pressed', 'false');
  await expect(page.getByTestId(`plan-places-${T04}`)).toHaveAttribute('data-state', 'short');
  await expect(page.getByTestId(`plan-places-${december}`)).toHaveAttribute('data-state', 'short');
  await expect(page.getByTestId('remove-all')).toHaveCount(0);
});

test('E-96: the grid shows the places of each car, the distance of each match day and the totals', async ({ page }) => {
  const december = await openPlan(page, 'petra');
  await expect(page.getByTestId(`plan-row-${SARA}`).getByTestId('plan-car-seats')).toContainText(/\d/);
  await expect(page.getByTestId(`plan-day-km-${T04}`)).toHaveText('23.4 km');
  await expect(page.getByTestId(`plan-day-km-${december}`)).toHaveText('23.4 km');
  // Sara drives once on match day 04: one trip, 46.8 km there and back
  const total = page.getByTestId(`plan-row-${SARA}`).getByTestId('plan-total');
  await expect(total).toHaveAttribute('data-trips', '1');
  await expect(total).toContainText('46.8');
  await expect(page.getByTestId('plan-total-sum')).toContainText('2');
});

test('DRV-02: a tap on a cell says who drives; a car with passengers goes only after a question', async ({ page }) => {
  await openPlan(page, 'petra');
  const kim = page.getByTestId(`cell-${KIM}-${T04}`);
  await expect(kim).toHaveAttribute('aria-pressed', 'false');
  await expect(page.getByTestId(`plan-row-${KIM}`).getByTestId('plan-total')).toHaveAttribute('data-trips', '0');

  await kim.click();
  await expect(kim).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByTestId(`plan-row-${KIM}`).getByTestId('plan-total')).toHaveAttribute('data-trips', '1');

  // Sara's car is empty: one tap takes her off
  await page.getByTestId(`cell-${SARA}-${T04}`).click();
  await expect(page.getByTestId(`cell-${SARA}-${T04}`)).toHaveAttribute('aria-pressed', 'false');

  // Petra's car is full: the app asks first
  const petra = page.getByRole('button', { name: 'Petra Keller fährt am So 22.11.', exact: true });
  await petra.click();
  const dialog = page.getByRole('dialog', { name: 'Petra Keller austragen?' });
  await expect(dialog).toContainText('Im Auto von Petra Keller sitzen 3 Personen');
  await dialog.getByRole('button', { name: 'Abbrechen' }).click();
  await expect(petra).toHaveAttribute('aria-pressed', 'true');
  await petra.click();
  await page.getByTestId('plan-confirm-confirm').click();
  await expect(page.getByRole('button', { name: 'Petra Keller fährt am So 22.11. nicht' })).toHaveAttribute(
    'aria-pressed',
    'false',
  );
  await expect(board(page).getByTestId('seat-waiting')).toContainText('Anna Muster');
});

test('DRV-02: a player changes her own row only; a tap on a match day shows its cars', async ({ page }) => {
  const december = await openPlan(page, 'sara');
  // Players do not plan the season
  await expect(page.getByTestId('planning')).toHaveCount(0);
  await expect(page.getByTestId(`cell-${KIM}-${december}`)).toHaveCount(0);

  await page.getByTestId(`cell-${SARA}-${december}`).click();
  await expect(page.getByTestId(`cell-${SARA}-${december}`)).toHaveAttribute('aria-pressed', 'true');

  await page.getByTestId(`plan-day-${december}`).click();
  await expect(page.getByTestId(`plan-editor-${december}`)).toContainText('Sara Brun');
});
