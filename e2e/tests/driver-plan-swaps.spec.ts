import type { Page } from '@playwright/test';
import { checkAccessibility, expect, login, reset, test } from './helpers';

/**
 * E-92 · Start page and driver plan (AUF-35, DRV-21 to DRV-24; the swiss unihockey page is tested in su-overview.spec.ts). Swaps, other
 * trips and the history (DRV-25 to DRV-28) have no page since E-98; their API is tested in the backend. Seed from
 * docs/test-data.md: match day 04 (Sun 22.11.) of Team 1 with Petra Keller's full car and Sara Brun's empty car;
 * Team 1 is linked with swiss unihockey (900001), Team 2 is not. Default "now": Mon 16.11.2026 06:00.
 */

const TEAM_1 = '10000000-0000-4000-8000-000000000001';
const T04 = '40000000-0000-4000-8000-000000000004';
const KIM = '20000000-0000-4000-8000-000000000011';
const SARA = '20000000-0000-4000-8000-000000000008';

test.beforeEach(async ({ request }) => {
  await reset(request);
});

async function openPlan(page: Page): Promise<void> {
  await page.goto(`/teams/${TEAM_1}/fahrerliste`);
  await expect(page.getByRole('heading', { level: 1, name: 'Fahrerliste' })).toBeVisible();
}

test('AUF-35: the start page always shows the next match day', async ({ page }) => {
  await login(page, 'anna');
  await page.goto('/');
  // The practices of the week come first; the match day of Sun 22.11. shows on its own card (TER-25)
  await expect(page.getByTestId('start-events').getByTestId(/^start-practice-/).first()).toContainText('Training');
  const card = page.getByTestId('next-match-day');
  await expect(card).toContainText('Nächster Spieltag');
  await expect(card.getByRole('link', { name: /Spieltag/ })).toHaveAttribute('href', `/termine/${T04}`);
  await expect(card.getByTestId('event-counter')).toBeVisible();
  await expect(page.getByTestId('tile-fahrerliste')).toBeVisible();
  await expect(page.getByTestId('tile-swissunihockey')).toBeVisible();
  await checkAccessibility(page);
});

test('DRV-23 DRV-32: the match day shows the places; travelling on one own stays; link to the plan', async ({ page }) => {
  await login(page, 'jana');
  await page.goto(`/termine/${T04}`);
  const list = page.getByTestId('driver-list');
  await expect(list.getByTestId('driver-seat-summary')).toContainText('Plätze für');
  // The cars show only behind «Plätze zuweisen»
  await expect(list.getByRole('listitem', { name: 'Auto von Sara Brun', exact: true })).toHaveCount(0);
  await list.getByTestId('individual-self').click();
  await expect(list.getByTestId('individual').getByRole('listitem', { name: 'Jana Wyss' })).toBeVisible();
  await list.getByTestId('driver-assign').click();
  await expect(list.getByRole('listitem', { name: 'Auto von Sara Brun', exact: true })).toBeVisible();
  await list.getByTestId('driver-plan-link').click();
  await expect(page).toHaveURL(`/teams/${TEAM_1}/fahrerliste`);
});

test('DRV-24: on the driver list a match day opens its cars for seating people', async ({ page }) => {
  await login(page, 'kim');
  await openPlan(page);
  await page.getByTestId(`plan-day-${T04}`).click();
  const editor = page.getByTestId(`plan-editor-${T04}`);
  await editor.getByRole('button', { name: 'Jana Wyss' }).click();
  await editor.getByTestId(`seat-enter-${SARA}`).click();
  await expect(editor.getByTestId(`seat-car-${SARA}`)).toContainText('Jana Wyss');
  await checkAccessibility(page);
});

test('DRV-21 DRV-22: coaches overwrite the driving time; the info tells how it was calculated', async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/termine/${T04}`);
  const list = page.getByTestId('driver-list');
  await list.getByText('So wird die Fahrzeit berechnet').click();
  await expect(list.getByTestId('driver-route-info')).toContainText('Berechnet mit dem Simulator des Testmodus');
  await expect(list.getByTestId('driver-route-info')).toContainText('ohne Verkehr');
  await list.getByTestId('driver-route-duration').click();
  const dialog = page.getByRole('dialog', { name: 'Fahrzeit anpassen' });
  await dialog.getByLabel('Fahrzeit in Minuten').fill('0');
  await dialog.getByRole('button', { name: 'Fahrzeit speichern' }).click();
  await expect(dialog.getByLabel('Fahrzeit in Minuten')).toHaveAttribute('aria-invalid', 'true');
  await dialog.getByLabel('Fahrzeit in Minuten').fill('40');
  await dialog.getByRole('button', { name: 'Fahrzeit speichern' }).click();
  await expect(list.getByTestId('driver-route-distance')).toContainText('23.4 km · etwa 40 Min. Fahrzeit');
  await expect(list.getByTestId('driver-route-duration-manual')).toHaveText('Fahrzeit angepasst');
  await expect(page.getByTestId('timeline-items')).toContainText('40 Min. Fahrt');
  await list.getByTestId('driver-route-duration-reset').click();
  await expect(list.getByTestId('driver-route-distance')).toContainText('etwa 26 Min. Fahrzeit');

  // Players see the info but cannot change the time
  await login(page, 'anna');
  await page.goto(`/termine/${T04}`);
  await expect(page.getByTestId('driver-route-info')).toBeVisible();
  await expect(page.getByTestId('driver-route-duration')).toHaveCount(0);
});

test('E-92: coaches find «<Team> verwalten» in the desktop navigation', { tag: '@desktop' }, async ({ page, isMobile }) => {
  await login(page, 'petra');
  await page.goto('/');
  const link = page.getByTestId(`nav-manage-team-${TEAM_1}`);
  if (isMobile) {
    await expect(link).toBeHidden();
    return;
  }
  await expect(link).toHaveText('Team 1 verwalten');
  await link.click();
  await expect(page).toHaveURL(`/verwaltung/teams/${TEAM_1}`);
  await login(page, 'anna');
  await page.goto('/');
  await expect(page.getByTestId('nav-manage-teams')).toHaveCount(0);
});
