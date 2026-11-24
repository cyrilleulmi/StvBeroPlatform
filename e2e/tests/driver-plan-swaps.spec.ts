import type { Page } from '@playwright/test';
import { checkAccessibility, expect, login, reset, test } from './helpers';

/**
 * E-92 · Start page, swiss unihockey page and driver plan (AUF-35, SUH-16, DRV-21 to DRV-28). Seed from
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
  // The next event is the practice of Tue 17.11.; the match day of Sun 22.11. shows on its own card
  await expect(page.getByTestId('next-event')).toContainText('Training');
  const card = page.getByTestId('next-match-day');
  await expect(card.getByRole('heading', { name: 'Nächster Spieltag' })).toBeVisible();
  await expect(card.getByRole('link', { name: /Spieltag/ })).toHaveAttribute('href', `/termine/${T04}`);
  await expect(card.getByTestId('event-counter')).toBeVisible();
  await expect(page.getByTestId('tile-fahrerliste')).toBeVisible();
  await expect(page.getByTestId('tile-swissunihockey')).toBeVisible();
  await checkAccessibility(page);
});

test('SUH-16: the swiss unihockey page links to the team page, players without a link ask the coach', async ({ page }) => {
  await login(page, 'anna');
  await page.goto('/swissunihockey');
  await expect(page.getByRole('heading', { level: 1, name: 'swiss unihockey' })).toBeVisible();
  const team = page.getByTestId(`su-team-${TEAM_1}`);
  await expect(team.getByRole('link', { name: /auf swissunihockey\.ch ansehen/ })).toHaveAttribute(
    'href',
    'https://www.swissunihockey.ch/de/team-detail/?team_id=900001',
  );
  await expect(team.getByTestId('su-league-link')).toHaveAttribute('href', `/teams/${TEAM_1}/liga`);
  await expect(team.getByRole('button', { name: 'Team verknüpfen' })).toHaveCount(0);
  await checkAccessibility(page);

  // Eva plays in Team 2, which is not linked
  await login(page, 'eva');
  await page.goto('/swissunihockey');
  await expect(page.getByTestId('su-not-linked')).toHaveText('Nicht verbunden. Frag den Coach zum Einrichten.');
});

test('DRV-23: on the match day the driver list only shows; travelling on one own stays; link to the plan', async ({ page }) => {
  await login(page, 'jana');
  await page.goto(`/termine/${T04}`);
  const list = page.getByTestId('driver-list');
  await expect(list.getByRole('listitem', { name: 'Auto von Sara Brun', exact: true })).toBeVisible();
  await expect(list.getByRole('button', { name: /mitfahren/ })).toHaveCount(0);
  await expect(list.getByRole('button', { name: 'Ich fahre' })).toHaveCount(0);
  await expect(list.getByTestId('driver-list-plan-hint')).toBeVisible();
  await list.getByTestId('individual-self').click();
  await expect(list.getByTestId('individual').getByRole('listitem', { name: 'Jana Wyss' })).toBeVisible();
  await list.getByTestId('driver-plan-link').click();
  await expect(page).toHaveURL(`/teams/${TEAM_1}/fahrerliste`);
});

test('DRV-24: on the driver plan a match day opens for entering passengers and drivers', async ({ page }) => {
  await login(page, 'kim');
  await openPlan(page);
  await page.getByTestId(`plan-edit-${T04}`).click();
  const editor = page.getByTestId(`plan-editor-${T04}`);
  await editor.getByRole('button', { name: 'Bei Sara Brun mitfahren' }).click();
  await expect(
    editor.getByRole('list', { name: 'Mitfahrende bei Sara Brun' }).getByRole('listitem', { name: 'Kim Baumann' }),
  ).toBeVisible();
  await expect(page.getByTestId(`plan-match-day-${T04}`).getByTestId(`plan-car-${SARA}`)).toContainText('Kim Baumann');
  await checkAccessibility(page);
});

test('DRV-25 DRV-26: swap directly or ask; the history tells who did what', async ({ page }) => {
  await login(page, 'sara');
  await openPlan(page);
  const form = page.getByTestId('plan-swap-form');
  await form.getByLabel('Fahrt am').selectOption({ index: 1 });
  await form.getByLabel('An wen').selectOption({ label: 'Kim Baumann' });
  await form.getByTestId('plan-swap-ask').click();
  await expect(page.getByRole('status')).toContainText('Kim Baumann ist gefragt');
  await expect(page.getByTestId('plan-swaps-open')).toContainText('Sara Brun fragt Kim Baumann');

  // Kim accepts: she drives now
  await login(page, 'kim');
  await openPlan(page);
  await page.getByTestId('plan-swaps-open').getByRole('button', { name: 'Annehmen' }).click();
  await expect(page.getByRole('status')).toContainText('Angenommen');
  await expect(page.getByTestId(`plan-match-day-${T04}`).getByTestId(`plan-car-${KIM}`)).toBeVisible();
  const history = page.getByTestId('driver-plan-history');
  await expect(history.getByRole('listitem').first()).toContainText(
    'Übernommen: Kim Baumann fährt am Spieltag So 22.11. statt Sara Brun.',
  );
  await expect(history).toContainText('Eingetragen: Petra Keller fährt am Spieltag So 22.11.');

  // Kim gives it back directly
  const form2 = page.getByTestId('plan-swap-form');
  await form2.getByLabel('Fahrt am').selectOption({ index: 1 });
  await form2.getByLabel('An wen').selectOption({ label: 'Sara Brun' });
  await form2.getByTestId('plan-swap-direct').click();
  await expect(page.getByRole('status')).toContainText('Getauscht');
  await expect(page.getByTestId(`plan-match-day-${T04}`).getByTestId(`plan-car-${SARA}`)).toBeVisible();
  await checkAccessibility(page);
});

test('DRV-27 DRV-28: other trips count and the distribution ends with a total', async ({ page }) => {
  await login(page, 'kim');
  await openPlan(page);
  const trips = page.getByTestId('plan-trips');
  await trips.getByLabel('Datum').fill('2026-11-01');
  await trips.getByLabel('Kilometer (einfacher Weg)').fill('100');
  await trips.getByLabel('Wofür').fill('Turnier Tenero');
  await trips.getByRole('button', { name: 'Fahrt hinzufügen' }).click();
  await expect(trips.getByRole('status')).toHaveText('Die Fahrt ist hinzugefügt.');
  await expect(trips.getByRole('listitem', { name: 'Turnier Tenero' })).toContainText('01.11.2026 · Turnier Tenero · Kim Baumann');
  await expect(page.getByTestId(`plan-share-${KIM}`).getByRole('cell').nth(0)).toHaveText('1');
  const total = page.getByTestId('plan-share-total');
  await expect(total.getByRole('cell').nth(0)).toHaveText('1');
  await expect(total.getByRole('cell').nth(1)).toHaveText('2');
  await expect(total.getByRole('cell').nth(2)).toHaveText('3');
  await trips.getByRole('button', { name: 'Fahrt «Turnier Tenero» entfernen' }).click();
  await expect(trips.getByRole('status')).toHaveText('Die Fahrt ist entfernt.');
  await expect(total.getByRole('cell').nth(0)).toHaveText('0');
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

test('E-92: coaches find «<Team> verwalten» in the desktop navigation', async ({ page, isMobile }) => {
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
