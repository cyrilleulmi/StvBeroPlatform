import type { Page } from '@playwright/test';
import { checkAccessibility, expect, login, reset, test } from './helpers';

/**
 * The event list takes less room (E-111): actions as icons in the title row, team chips instead of a select, the
 * statistics only from «Verwaltung», a pencil on every event the person may edit. Seed from docs/test-data.md:
 * petra coach Team 1 + player Team 2, anna player Team 1, alex admin without a team, kim player Team 1.
 */

const TEAM_1 = '10000000-0000-4000-8000-000000000001';
const TEAM_2 = '10000000-0000-4000-8000-000000000002';
const TEAM_3 = '10000000-0000-4000-8000-000000000003';
const T01 = '40000000-0000-4000-8000-000000000001';
const T02 = '40000000-0000-4000-8000-000000000002';
const T03 = '40000000-0000-4000-8000-000000000003';
const T05 = '40000000-0000-4000-8000-000000000005';
const KIM = '20000000-0000-4000-8000-000000000011';

function card(page: Page, id: string) {
  return page.getByTestId(`event-card-${id}`);
}

test.beforeEach(async ({ request }) => {
  await reset(request);
});

test('E-111: title and the actions as icons in one row, chips in one row, no statistics', { tag: '@desktop' }, async ({ page }) => {
  await login(page, 'petra');
  await page.goto('/termine');
  const title = page.getByRole('heading', { level: 1, name: 'Termine' });
  const calendar = page.getByRole('link', { name: 'Kalender' });
  const rework = page.getByRole('link', { name: 'Überarbeiten' });
  const create = page.getByRole('link', { name: 'Termin erfassen' });
  await expect(calendar).toHaveAttribute('href', '/termine/kalender');
  await expect(rework).toHaveAttribute('href', '/termine/ueberarbeiten');
  await expect(create).toHaveAttribute('href', '/termine/neu');
  await expect(create).toHaveAttribute('title', 'Termin erfassen');
  await expect(page.getByRole('link', { name: 'Statistik' })).toHaveCount(0);

  // Same row: the icons sit at the height of the title, each a 44px target
  const titleBox = (await title.boundingBox())!;
  for (const link of [calendar, rework, create]) {
    const box = (await link.boundingBox())!;
    expect(box.width).toBeGreaterThanOrEqual(44);
    expect(box.height).toBeGreaterThanOrEqual(44);
    expect(Math.abs(box.y + box.height / 2 - (titleBox.y + titleBox.height / 2))).toBeLessThan(12);
  }
  await expect(page.getByRole('group', { name: 'Teams', exact: true })).toBeVisible();
  await checkAccessibility(page);
});

test('E-111: an admin with ten teams gets one row of chips that scrolls sideways', async ({ page }) => {
  await login(page, 'alex');
  // Seven more teams, so that the chips no longer fit next to each other
  for (let i = 1; i <= 7; i++) {
    const response = await page.request.post('/api/teams', { data: { name: `Junioren ${i}` } });
    expect(response.status()).toBe(201);
  }
  await page.goto('/termine');
  const chips = page.getByRole('group', { name: 'Teams', exact: true }).getByRole('checkbox');
  await expect(chips).toHaveCount(10);
  // Alex is in no team: nothing is on, the list is empty as before
  await expect(page.getByText('Keine kommenden Termine')).toBeVisible();
  const first = (await page.getByTestId(`team-chip-${TEAM_1}`).boundingBox())!;
  const last = (await chips.last().boundingBox())!;
  expect(last.y).toBe(first.y);

  // A tap switches a team on, another one switches it off again
  await page.getByTestId(`team-chip-${TEAM_3}`).check();
  await expect(page).toHaveURL(`/termine?teams=${TEAM_3}`);
  await expect(page.getByTestId(`team-chip-${TEAM_3}`)).toBeEnabled();
  await page.getByTestId(`team-chip-${TEAM_2}`).check();
  await expect(page).toHaveURL(`/termine?teams=${TEAM_2},${TEAM_3}`);
  await expect(card(page, T05)).toBeVisible();
  await page.getByTestId(`team-chip-${TEAM_2}`).uncheck();
  await page.getByTestId(`team-chip-${TEAM_3}`).uncheck();
  await expect(page).toHaveURL('/termine');
});

test('E-111 TER-21: «Überarbeiten» hands over the team when exactly one is on', async ({ page }) => {
  await login(page, 'petra');
  await page.goto('/termine');
  await expect(page.getByRole('link', { name: 'Überarbeiten' })).toHaveAttribute('href', '/termine/ueberarbeiten');
  await page.getByTestId(`team-chip-${TEAM_2}`).uncheck();
  await expect(page).toHaveURL(`/termine?teams=${TEAM_1}`);
  // The last team stays on
  await expect(page.getByTestId(`team-chip-${TEAM_1}`)).toBeDisabled();
  await expect(page.getByRole('link', { name: 'Überarbeiten' })).toHaveAttribute(
    'href',
    `/termine/ueberarbeiten?team=${TEAM_1}`,
  );
});

test('E-111 RR-09: the pencil on an event shows for coaches and special permissions, not for players', async ({ page }) => {
  // Coach: on every planned event, not on the cancelled one
  await login(page, 'petra');
  await page.goto('/termine');
  const edit = page.getByTestId(`event-edit-${T01}`);
  await expect(edit).toHaveAccessibleName('Training Di 17.11. 18:30 bearbeiten');
  await expect(page.getByTestId(`event-edit-${T02}`)).toBeVisible();
  await expect(page.getByTestId(`event-edit-${T03}`)).toHaveCount(0);
  // Not inside the link of the card: the card still opens the event
  await expect(card(page, T01).getByRole('link')).toHaveCount(2);
  await edit.click();
  await expect(page).toHaveURL(`/termine/${T01}/bearbeiten`);

  // Petra gives Kim a special permission for event 01
  const granted = await page.request.put(`/api/events/${T01}/grants`, { data: { personIds: [KIM] } });
  expect(granted.status()).toBe(200);

  // A player without rights: no pencil
  await login(page, 'anna');
  await page.goto('/termine');
  await expect(card(page, T01)).toBeVisible();
  await expect(page.getByRole('link', { name: /bearbeiten$/ })).toHaveCount(0);

  // Kim: only on her event
  await login(page, 'kim');
  await page.goto('/termine');
  await expect(page.getByTestId(`event-edit-${T01}`)).toBeVisible();
  await expect(page.getByTestId(`event-edit-${T02}`)).toHaveCount(0);
  await page.getByTestId(`event-edit-${T01}`).click();
  await expect(page).toHaveURL(`/termine/${T01}/bearbeiten`);
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
});
