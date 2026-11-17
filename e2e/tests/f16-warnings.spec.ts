import type { APIRequestContext, Locator, Page } from '@playwright/test';
import { checkAccessibility, expect, login, reset, test } from './helpers';

/**
 * F-16 · Warnings for events and call-ups (ANM, AUF extended). Acceptance criteria from issue #55, seed from
 * docs/test-data.md (scenarios S48 to S51). Default "now": Mon 16.11.2026 06:00.
 */

const TEAM_1 = '10000000-0000-4000-8000-000000000001';
const JANA = '20000000-0000-4000-8000-000000000010';
const NINA = '20000000-0000-4000-8000-000000000009';
const T01 = '40000000-0000-4000-8000-000000000001';
const T02 = '40000000-0000-4000-8000-000000000002';
const T03 = '40000000-0000-4000-8000-000000000003';
const T04 = '40000000-0000-4000-8000-000000000004';
const T06 = '40000000-0000-4000-8000-000000000006';

function warnings(page: Page, testId: string): Locator {
  return page.getByTestId(testId).getByTestId('warning');
}

async function setClock(request: APIRequestContext, now: string): Promise<void> {
  const response = await request.put('/api/test/clock', { data: { now } });
  expect(response.status()).toBe(204);
}

test.beforeEach(async ({ request }) => {
  await reset(request);
});

test('F-16 criteria 1, 7, 9: event 02 warns that no goalie, too few field players and Linie 1 too small', async ({
  page,
}) => {
  await login(page, 'petra');
  await page.goto(`/termine/${T02}`);
  const overview = page.getByTestId('overview');
  const section = overview.getByRole('region', { name: 'Warnungen' });
  await expect(section).toBeVisible();
  await expect(section.getByRole('listitem')).toHaveText([
    'Kein Goalie angemeldet',
    'Nur 1 Feldspielerin angemeldet',
    'Linie 1 nur zu 1',
  ]);
  // Criterion 7: not only a colour; type as attribute, and announced as a live region
  await expect(warnings(page, 'event-warnings')).toHaveCount(3);
  await expect(warnings(page, 'event-warnings').first()).toHaveAttribute('data-warning', 'GOALIES');
  await expect(page.getByTestId('event-warnings')).toHaveAttribute('aria-live', 'polite');
  // Above the list of responses
  const box = await section.boundingBox();
  const team = await page.getByTestId(`overview-team-${TEAM_1}`).boundingBox();
  expect(box!.y).toBeLessThan(team!.y);
  await checkAccessibility(page);
});

test('F-16 criterion 1: an event of two teams warns per team with the team name', async ({ page }) => {
  await login(page, 'alex');
  await page.goto(`/termine/${T01}`);
  await expect(warnings(page, 'event-warnings')).toHaveText([
    'Team 1: Nur 4 Feldspielerinnen angemeldet',
    'Team 1: Linie 1 nur zu 2',
    'Team 1: Linie 2 nur zu 1',
    'Team 2: Kein Goalie angemeldet',
    'Team 2: Nur 3 Feldspielerinnen angemeldet',
    'Team 2: Linie 1 nur zu 2',
    'Team 2: Linie 2 nur zu 1',
  ]);
});

test('F-16 criterion 1: the goalie warning disappears once the goalie responds «dabei»', async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/termine/${T02}`);
  await expect(warnings(page, 'event-warnings').first()).toHaveText('Kein Goalie angemeldet');
  await page.getByTestId(`overview-person-${JANA}`).getByRole('button', { name: 'Antwort für Jana Wyss eintragen' }).click();
  const dialog = page.getByRole('dialog', { name: 'Antwort für Jana Wyss eintragen' });
  await dialog.getByRole('radio', { name: 'dabei', exact: true }).check();
  await dialog.getByRole('button', { name: 'Speichern' }).click();
  await expect(warnings(page, 'event-warnings')).toHaveText(['Nur 1 Feldspielerin angemeldet', 'Linie 1 nur zu 1']);
});

test('F-16 criterion 2: the call-up warns from the selection and updates while selecting', async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/termine/${T04}/aufgebot`);
  const list = warnings(page, 'call-up-warnings');
  // Draft: Anna, Lea, Mia, Sara, Jana
  await expect(list).toHaveText(['Nur 4 Feldspielerinnen aufgeboten', 'Linie 2 nur zu 1']);
  await expect(page.getByTestId('call-up-warnings')).toHaveAttribute('aria-live', 'polite');

  await page.getByTestId(`call-up-person-${JANA}`).getByRole('checkbox', { name: 'Jana Wyss' }).uncheck();
  await expect(page.getByRole('status')).toHaveText('Jana Wyss ist nicht mehr aufgeboten.');
  await expect(list).toHaveText(['Kein Goalie aufgeboten', 'Nur 4 Feldspielerinnen aufgeboten', 'Linie 2 nur zu 1']);

  // Nina has not responded: selected anyway, Linie 2 now has two
  await page.getByTestId(`call-up-person-${NINA}`).getByRole('checkbox', { name: 'Nina Roth' }).check();
  await expect(page.getByRole('status')).toHaveText('Nina Roth ist aufgeboten.');
  await expect(list).toHaveText(['Kein Goalie aufgeboten', 'Nur 5 Feldspielerinnen aufgeboten', 'Linie 2 nur zu 2']);
  await checkAccessibility(page);
});

test('F-16 criterion 3: publishing with warnings is allowed and the dialog repeats them', async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/termine/${T04}/aufgebot`);
  await page.getByTestId('call-up-publish').click();
  const dialog = page.getByRole('dialog', { name: 'Aufgebot veröffentlichen?' });
  await expect(dialog.getByRole('region', { name: 'Achtung:' }).getByRole('listitem')).toHaveText([
    'Nur 4 Feldspielerinnen aufgeboten',
    'Linie 2 nur zu 1',
  ]);
  await checkAccessibility(page);
  await dialog.getByRole('button', { name: 'Veröffentlichen' }).click();
  await expect(page.getByRole('status')).toHaveText('Das Aufgebot ist veröffentlicht.');
  await expect(page.getByTestId('call-up-status')).toContainText('veröffentlicht');
});

test('F-16 criterion 4: coaches change the minimums on the team page', async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/teams/${TEAM_1}`);
  const section = page.getByTestId('team-warning-minimums');
  await expect(section.getByRole('heading', { name: 'Warnungen' })).toBeVisible();
  await expect(section.getByLabel('Goalies mindestens')).toHaveValue('1');
  await expect(section.getByLabel('Feldspielerinnen am Spieltag mindestens')).toHaveValue('10');
  await expect(section.getByLabel('Feldspielerinnen im Training mindestens')).toHaveValue('6');
  await expect(section.getByLabel('Pro Linie mindestens')).toHaveValue('3');
  await checkAccessibility(page);

  // Invalid value: error at the field, nothing saved
  await section.getByLabel('Pro Linie mindestens').fill('-1');
  await section.getByRole('button', { name: 'Mindestzahlen speichern' }).click();
  await expect(section.getByLabel('Pro Linie mindestens')).toHaveAttribute('aria-invalid', 'true');
  await expect(section.getByText('Bitte eine Zahl von 0 bis 99 eingeben.')).toBeVisible();
  await expect(section.getByLabel('Pro Linie mindestens')).toBeFocused();

  await section.getByLabel('Goalies mindestens').fill('0');
  await section.getByLabel('Feldspielerinnen im Training mindestens').fill('1');
  await section.getByLabel('Pro Linie mindestens').fill('2');
  await section.getByRole('button', { name: 'Mindestzahlen speichern' }).click();
  await expect(section.getByRole('status')).toHaveText('Die Mindestzahlen sind gespeichert.');
  await page.reload();
  await expect(section.getByLabel('Pro Linie mindestens')).toHaveValue('2');

  await page.goto(`/termine/${T02}`);
  await expect(warnings(page, 'event-warnings')).toHaveText(['Linie 1 nur zu 1']);
});

test('F-16 criteria 4, 6: players see neither warnings nor the minimums', async ({ page }) => {
  await login(page, 'anna');
  await page.goto(`/termine/${T02}`);
  await expect(page.getByTestId('overview')).toBeVisible();
  await expect(page.getByRole('region', { name: 'Warnungen' })).toHaveCount(0);
  await expect(page.getByTestId('event-warnings')).toHaveCount(0);

  await page.goto('/termine');
  await expect(page.getByTestId(`event-card-${T02}`)).toBeVisible();
  await expect(page.getByTestId('event-warnings')).toHaveCount(0);

  await page.goto(`/teams/${TEAM_1}`);
  await expect(page.getByTestId('team-players')).toBeVisible();
  await expect(page.getByTestId('team-warning-minimums')).toHaveCount(0);
});

test('F-16 criterion 5: no warnings for cancelled, other and past events', async ({ page, request }) => {
  await login(page, 'petra');
  await page.goto(`/termine/${T03}`);
  await expect(page.getByTestId('overview')).toBeVisible();
  await expect(warnings(page, 'event-warnings')).toHaveCount(0);
  await page.goto(`/termine/${T06}`);
  await expect(page.getByTestId('overview')).toBeVisible();
  await expect(warnings(page, 'event-warnings')).toHaveCount(0);

  // After event 02 (Thu 19.11.2026 19:00–20:30)
  await setClock(request, '2026-11-19T21:00:00+01:00');
  await page.goto(`/termine/${T02}`);
  await expect(page.getByTestId('overview')).toBeVisible();
  await expect(warnings(page, 'event-warnings')).toHaveCount(0);
  await page.goto('/termine?ansicht=vergangen');
  await expect(page.getByTestId(`event-card-${T02}`)).toBeVisible();
  await expect(page.getByTestId(`event-card-${T02}`).getByTestId('event-warnings')).toHaveCount(0);
});

test('F-16 criterion 8: the event list marks events with warnings for coaches', async ({ page }) => {
  await login(page, 'petra');
  await page.goto('/termine');
  const marker = page.getByTestId(`event-card-${T02}`).getByTestId('event-warnings');
  await expect(marker).toHaveText('3 Warnungen');
  await expect(marker).toHaveAttribute('data-warning-count', '3');
  await expect(page.getByTestId(`event-card-${T01}`).getByTestId('event-warnings')).toHaveText('7 Warnungen');
  // Cancelled event and «Teamessen» have none
  await expect(page.getByTestId(`event-card-${T03}`)).toBeVisible();
  await expect(page.getByTestId(`event-card-${T03}`).getByTestId('event-warnings')).toHaveCount(0);
  await expect(page.getByTestId(`event-card-${T06}`).getByTestId('event-warnings')).toHaveCount(0);
  await checkAccessibility(page);
});
