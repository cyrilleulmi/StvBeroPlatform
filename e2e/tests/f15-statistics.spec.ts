import type { APIRequestContext, Locator, Page } from '@playwright/test';
import { checkAccessibility, expect, login, reset, test } from './helpers';

/**
 * F-15 · Attendance statistics (STA-01 to STA-11). Acceptance criteria from issue #54, expected values
 * from docs/test-data.md (scenarios S44–S47). Default "now": Mon 16.11.2026 06:00.
 */

const ANNA = '20000000-0000-4000-8000-000000000005';
const LEA = '20000000-0000-4000-8000-000000000006';
const MIA = '20000000-0000-4000-8000-000000000007';
const KIM = '20000000-0000-4000-8000-000000000011';
const TEAM_1 = '10000000-0000-4000-8000-000000000001';
const TEAM_2 = '10000000-0000-4000-8000-000000000002';
const T07 = '40000000-0000-4000-8000-000000000007';
const T09 = '40000000-0000-4000-8000-000000000009';
const T10 = '40000000-0000-4000-8000-000000000010';
const LINE_1 = '30000000-0000-4000-8000-000000000001';

/** Anna in Team 1, whole season 2026/27: 26 events, 2 «dabei», 1 «nicht dabei» (late), 23 «offen». */
const ANNA_ROW = ['26', '2', '1', '0', '23', '8 %', '1', '1', '0', '0', '100 %'];

function row(page: Page, personId: string): Locator {
  return page.getByTestId(`statistics-person-${personId}`);
}

async function setClock(request: APIRequestContext, now: string): Promise<void> {
  const response = await request.put('/api/test/clock', { data: { now } });
  expect(response.status()).toBe(204);
}

test.beforeEach(async ({ request }) => {
  await reset(request);
});

test('STA-01: coaches reach «Statistik» from the events and from a team page', async ({ page }) => {
  await login(page, 'petra');
  await page.goto('/termine');
  await page.getByRole('link', { name: 'Statistik' }).click();
  await expect(page).toHaveURL('/statistik');
  await expect(page.getByRole('heading', { level: 1, name: 'Statistik' })).toBeVisible();
  await expect(page.getByText('Team 1 · Saison 2026/27')).toBeVisible();
  await expect(page.getByTestId('statistics-base')).toHaveText(
    '26 Termine im Zeitraum. Die Präsenzquote beruht auf 1 Termin mit erfasster Präsenz.',
  );
  await checkAccessibility(page);

  // Marc coaches Team 2 and still sees every team (F-12)
  await login(page, 'marc');
  await page.goto(`/teams/${TEAM_1}`);
  await page.getByRole('link', { name: 'Statistik' }).click();
  await expect(page).toHaveURL(`/statistik?team=${TEAM_1}`);
  await expect(page.getByLabel('Team', { exact: true })).toHaveValue(TEAM_1);
  await expect(row(page, ANNA).getByRole('cell')).toHaveText(ANNA_ROW);
});

test('STA-02: team, period and event type are filters in the address', async ({ page }) => {
  await login(page, 'petra');
  await page.goto('/statistik');
  await expect(page.getByLabel('Von')).toHaveValue('2026-07-01');
  await expect(page.getByLabel('Bis')).toHaveValue('2027-06-30');

  await page.getByLabel('Team', { exact: true }).selectOption(TEAM_2);
  await expect(page).toHaveURL(`/statistik?team=${TEAM_2}`);
  await expect(page.getByText('Team 2 · Saison 2026/27')).toBeVisible();
  await expect(row(page, LEA).getByRole('cell').first()).toHaveText('13');

  await page.getByLabel('Team', { exact: true }).selectOption(TEAM_1);
  await page.getByLabel('Von').fill('2026-11-01');
  await expect(page).toHaveURL(/von=2026-11-01/);
  await page.getByLabel('Bis').fill('2026-11-15');
  await expect(page).toHaveURL(/bis=2026-11-15/);
  await expect(page.getByTestId('statistics-base')).toHaveText(
    '4 Termine im Zeitraum. Die Präsenzquote beruht auf 1 Termin mit erfasster Präsenz.',
  );
  await expect(row(page, ANNA).getByRole('cell')).toHaveText([
    '4', '2', '1', '0', '1', '50 %', '1', '1', '0', '0', '100 %',
  ]);

  await page.getByLabel('Art').selectOption({ label: 'Spieltage' });
  await expect(page).toHaveURL(/art=spieltag/);
  await expect(page.getByTestId('statistics-base')).toHaveText(
    '0 Termine im Zeitraum. Präsenz ist für keinen davon erfasst.',
  );
  await expect(row(page, ANNA).getByRole('cell').nth(5)).toHaveText('–');

  // Deep link after reload
  await page.reload();
  await expect(page.getByLabel('Art')).toHaveValue('spieltag');
  await expect(page.getByLabel('Von')).toHaveValue('2026-11-01');

  await page.getByRole('button', { name: 'Ganze Saison' }).click();
  await page.getByLabel('Art').selectOption({ label: 'Trainings' });
  await expect(page).toHaveURL(`/statistik?team=${TEAM_1}&art=training`);
  await expect(row(page, ANNA).getByRole('cell')).toHaveText(ANNA_ROW);
  await checkAccessibility(page);
});

test('STA-03 STA-04 STA-09: the numbers per player match the seed', async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/statistik?team=${TEAM_1}`);
  const header = page.getByRole('table', { name: 'Statistik Linie 1' }).getByRole('columnheader');
  await expect(header).toHaveText([
    'Name', 'Termine', 'dabei', 'nicht dabei', 'vielleicht', 'offen', 'Zusagequote',
    'kurzfristig abgemeldet', 'anwesend', 'abwesend', 'entschuldigt', 'Präsenzquote',
  ]);
  await expect(row(page, ANNA).getByRole('rowheader')).toHaveText('Anna Muster');
  await expect(row(page, ANNA).getByRole('cell')).toHaveText(ANNA_ROW);
  // Mia: excused on event 10
  await expect(row(page, MIA).getByRole('cell')).toHaveText([
    '26', '1', '2', '0', '23', '4 %', '0', '0', '0', '1', '0 %',
  ]);
});

test('STA-05: grouped by line, sortable by rate, with the team average', { tag: '@desktop' }, async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/statistik?team=${TEAM_1}`);
  const groups = page.getByRole('heading', { level: 2 });
  await expect(groups).toContainText(['Linie 1', 'Linie 2', 'Goalies', 'Ohne Linie', 'Teamschnitt']);
  const line1 = page.getByTestId(`statistics-group-${LINE_1}`);
  await expect(line1.getByRole('rowheader')).toHaveText(['Mia Frei', 'Lea Graf', 'Anna Muster']);

  await page.getByLabel('Sortieren nach').selectOption({ label: 'Zusagequote' });
  await expect(page).toHaveURL(`/statistik?team=${TEAM_1}&sortierung=zusage`);
  await expect(line1.getByRole('rowheader')).toHaveText(['Lea Graf', 'Anna Muster', 'Mia Frei']);
  await expect(line1.getByRole('columnheader', { name: 'Zusagequote' })).toHaveAttribute('aria-sort', 'descending');

  await page.getByLabel('Sortieren nach').selectOption({ label: 'Präsenzquote' });
  await expect(line1.getByRole('rowheader')).toHaveText(['Lea Graf', 'Anna Muster', 'Mia Frei']);
  await page.getByLabel('Sortieren nach').selectOption({ label: 'Name' });
  await expect(page).toHaveURL(`/statistik?team=${TEAM_1}`);

  await expect(page.getByTestId('statistics-average-row').getByRole('cell')).toHaveText([
    '24.1', '1.6', '0.4', '0.1', '22.0', '6 %', '0.1', '0.6', '0.3', '0.1', '57 %',
  ]);
  await checkAccessibility(page);
});

test('STA-06: a click on a player opens her events with response and attendance', { tag: '@desktop' }, async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/statistik?team=${TEAM_1}&von=2026-11-01`);
  await row(page, ANNA).getByRole('link', { name: 'Anna Muster' }).click();
  await expect(page).toHaveURL(`/statistik/${ANNA}?team=${TEAM_1}&von=2026-11-01`);
  await expect(page.getByRole('heading', { level: 1, name: 'Anna Muster' })).toBeVisible();
  const events = page.getByRole('list', { name: 'Termine im Zeitraum' }).getByRole('listitem');
  await expect(events).toHaveCount(4);
  await expect(events.first().getByRole('link')).toHaveText('Training Do 12.11. 19:00');
  await expect(page.getByTestId(`statistics-event-${T09}`)).toContainText('Antwort: dabei');
  await expect(page.getByTestId(`statistics-event-${T09}`)).not.toContainText('Präsenz:');
  await expect(page.getByTestId(`statistics-event-${T07}`)).toContainText('Antwort: nicht dabei');
  await expect(page.getByTestId(`statistics-event-${T07}`)).toContainText('kurzfristig abgemeldet');
  await expect(page.getByTestId(`statistics-event-${T10}`)).toContainText('Präsenz: anwesend');
  await expect(page.getByTestId(`statistics-event-${T10}`)).toHaveAttribute('data-attendance', 'PRESENT');
  await checkAccessibility(page);

  await page.getByRole('link', { name: 'Zur Statistik' }).click();
  await expect(page).toHaveURL(`/statistik?team=${TEAM_1}&von=2026-11-01`);
});

test('STA-07: only past events count, cancelled ones not, and a player from the day she joined', async ({
  page,
  request,
}) => {
  await login(page, 'petra');
  await page.goto(`/statistik?team=${TEAM_1}`);
  // Kim joined Team 1 on 01.10.2026
  await expect(row(page, KIM).getByRole('cell')).toHaveText([
    '13', '0', '0', '0', '13', '0 %', '0', '0', '1', '0', '0 %',
  ]);
  await row(page, KIM).getByRole('link').click();
  await expect(page.getByTestId('statistics-base')).toContainText('Im Team seit 01.10.2026.');

  // 25.11.: 17.11., 19.11. and the match day 22.11. are past now, 24.11. is cancelled
  await setClock(request, '2026-11-25T07:00:00+01:00');
  await page.goto(`/statistik?team=${TEAM_1}`);
  await expect(page.getByTestId('statistics-base')).toHaveText(
    '29 Termine im Zeitraum. Die Präsenzquote beruht auf 1 Termin mit erfasster Präsenz.',
  );
});

test('STA-08: players see only their own numbers, under the profile', { tag: '@desktop' }, async ({ page }) => {
  await login(page, 'anna');
  await page.goto('/termine');
  await expect(page.getByRole('heading', { level: 1, name: 'Termine' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Statistik' })).toHaveCount(0);

  await page.goto('/profil');
  await page.getByRole('link', { name: 'Meine Statistik' }).click();
  await expect(page).toHaveURL('/profil/statistik');
  await expect(page.getByRole('heading', { level: 1, name: 'Meine Statistik' })).toBeVisible();
  const numbers = page.getByTestId('statistics-numbers');
  await expect(numbers.getByRole('definition')).toHaveText(ANNA_ROW);
  await expect(page.getByRole('list', { name: 'Termine im Zeitraum' }).getByRole('listitem')).toHaveCount(26);
  await checkAccessibility(page);

  await page.goto('/statistik');
  await expect(page.getByRole('alert')).toHaveText('Keine Berechtigung');
  expect((await page.request.get('/api/statistics')).status()).toBe(403);
  expect((await page.request.get(`/api/statistics/persons/${MIA}`)).status()).toBe(403);
});

test('STA-08: a player on her own /statistik/:personId is led back to the profile', async ({ page }) => {
  await login(page, 'anna');
  await page.goto(`/statistik/${ANNA}`);
  await expect(page.getByRole('heading', { level: 1, name: 'Anna Muster' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Zur Statistik' })).toHaveCount(0);
  await page.getByRole('link', { name: 'Zum Profil' }).click();
  await expect(page).toHaveURL('/profil');
});

test.describe('with the expected 403', () => {
  test.use({ expectedConsoleErrors: [/403/] });

  test('STA-08: another player\'s statistics show «Keine Berechtigung»', async ({ page }) => {
    await login(page, 'anna');
    await page.goto(`/statistik/${MIA}`);
    await expect(page.getByRole('alert')).toHaveText('Keine Berechtigung');
    await expect(page.getByText('nicht dabei')).toHaveCount(0);
  });
});

test('STA-10: readable at 375px without horizontal page scrolling', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await login(page, 'petra');
  await page.goto(`/statistik?team=${TEAM_1}`);
  await expect(row(page, ANNA).getByRole('cell')).toHaveText(ANNA_ROW);
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  expect(overflow).toBeLessThanOrEqual(0);
  // The table scrolls inside its card; the region can be reached by keyboard
  await expect(page.getByRole('region', { name: 'Statistik Linie 1' })).toHaveAttribute('tabindex', '0');
  await page.goto(`/statistik/${ANNA}?team=${TEAM_1}`);
  await expect(page.getByRole('heading', { level: 1, name: 'Anna Muster' })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)).toBeLessThanOrEqual(0);
  // «100 %» stays on one line next to «Präsenzquote» in the numbers card
  const rate = page.getByTestId('statistics-numbers').getByRole('definition').last();
  await expect(rate).toHaveText('100 %');
  const lines = await rate.evaluate((el) => {
    const style = getComputedStyle(el);
    return Math.round(el.getBoundingClientRect().height / parseFloat(style.lineHeight));
  });
  expect(lines).toBe(1);
});
