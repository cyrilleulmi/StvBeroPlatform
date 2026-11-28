import type { Page } from '@playwright/test';
import { expect, login, reset, test } from './helpers';

/**
 * Pre-training («Vortraining», TER-29 to TER-32): the fields in the event form and in the series form,
 * the check before saving, removing it again. Plan: ../plans/pre-training.md. IDs from docs/test-data.md.
 * Default "now" after reset: Mon 16.11.2026, 06:00.
 */

const TEAM_1 = '10000000-0000-4000-8000-000000000001';

test.beforeEach(async ({ request }) => {
  await reset(request);
});

/** Fills the new-event form of a practice for Team 1 (the only team of Petra) up to the pre-training. */
async function fillPractice(page: Page): Promise<void> {
  await page.goto('/termine/neu');
  await page.getByLabel('Titel', { exact: true }).fill('Training mit Vortraining');
  await page.getByLabel('Datum', { exact: true }).fill('2026-11-20');
  await page.getByLabel('Beginn', { exact: true }).fill('18:00');
  await page.getByLabel('Ende', { exact: true }).fill('19:30');
}

test('TER-29 TER-31 TER-32: A coach adds a pre-training to a practice, the event page shows it', async ({ page }) => {
  await login(page, 'petra');
  await fillPractice(page);
  await expect(page.getByTestId('pre-training-time')).toHaveCount(0);
  await page.getByRole('checkbox', { name: 'Mit Vortraining' }).check();
  // The start is suggested 15 minutes before the practice
  await expect(page.getByTestId('pre-training-time')).toHaveValue('17:45');
  await page.getByTestId('pre-training-title').fill('Joggen');
  await page.getByTestId('pre-training-time').fill('17:30');
  await page.getByRole('button', { name: 'Termin speichern' }).click();

  await expect(page).toHaveURL(/\/termine\/[0-9a-f-]{36}$/);
  await expect(page.getByRole('status')).toHaveText('Der Termin ist erfasst.');
  const preTraining = page.getByTestId('pre-training');
  await expect(preTraining).toContainText('17:30');
  await expect(preTraining).toContainText('Joggen');
});

test('TER-32: The form refuses a pre-training that does not start before the practice', async ({ page }) => {
  await login(page, 'petra');
  await fillPractice(page);
  await page.getByRole('checkbox', { name: 'Mit Vortraining' }).check();
  await page.getByTestId('pre-training-title').fill('Joggen');
  await page.getByTestId('pre-training-time').fill('18:30');
  await page.getByRole('button', { name: 'Termin speichern' }).click();

  await expect(page.getByText('Das Vortraining muss vor dem Training beginnen.')).toBeVisible();
  await expect(page.getByTestId('pre-training-time')).toHaveAttribute('aria-invalid', 'true');
  await expect(page).toHaveURL(/\/termine\/neu$/);

  // Without a title the form asks for it
  await page.getByTestId('pre-training-time').fill('17:30');
  await page.getByTestId('pre-training-title').fill('');
  await page.getByRole('button', { name: 'Termin speichern' }).click();
  await expect(page.getByText('Bitte angeben, was im Vortraining gemacht wird.')).toBeVisible();
  await expect(page.getByTestId('pre-training-title')).toHaveAttribute('aria-invalid', 'true');
  await expect(page).toHaveURL(/\/termine\/neu$/);
});

test('TER-32: Editing the event removes the pre-training', async ({ page }) => {
  await login(page, 'petra');
  const response = await page.request.post('/api/events', {
    data: {
      type: 'PRACTICE',
      title: 'Training mit Vortraining',
      start: '2026-11-20T18:00:00+01:00',
      end: '2026-11-20T19:30:00+01:00',
      teamIds: [TEAM_1],
      preTraining: { title: 'Joggen', start: '2026-11-20T17:30:00+01:00' },
    },
  });
  expect(response.status()).toBe(201);
  const id = (await response.json()).id as string;

  await page.goto(`/termine/${id}`);
  await expect(page.getByTestId('pre-training')).toContainText('Joggen');

  await page.goto(`/termine/${id}/bearbeiten`);
  const toggle = page.getByRole('checkbox', { name: 'Mit Vortraining' });
  await expect(toggle).toBeChecked();
  await expect(page.getByTestId('pre-training-title')).toHaveValue('Joggen');
  await expect(page.getByTestId('pre-training-time')).toHaveValue('17:30');
  await toggle.uncheck();
  await expect(page.getByTestId('pre-training-title')).toHaveCount(0);
  await page.getByRole('button', { name: 'Änderungen speichern' }).click();

  await expect(page).toHaveURL(`/termine/${id}`);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Training mit Vortraining');
  await expect(page.getByTestId('pre-training')).toHaveCount(0);
});

test('TER-30 TER-32: A series of practices with a pre-training gives it to its events', async ({ page }) => {
  await login(page, 'petra');
  await page.goto('/termine/neu');
  await page.getByRole('radio', { name: 'Serie (jede Woche)' }).check();
  await expect(page).toHaveURL(/art=serie/);
  await page.getByLabel('Titel', { exact: true }).fill('Konditionstraining');
  await page.getByLabel('Wochentag').selectOption({ label: 'Montag' });
  await page.getByLabel('Beginn', { exact: true }).fill('19:30');
  await page.getByLabel('Ende', { exact: true }).fill('21:00');
  await page.getByLabel('Erster Tag der Serie').fill('2026-11-16');
  await page.getByLabel('Letzter Tag der Serie').fill('2026-12-14');
  await page.getByRole('checkbox', { name: 'Mit Vortraining' }).check();
  await expect(page.getByTestId('pre-training-time')).toHaveValue('19:15');
  await page.getByTestId('pre-training-title').fill('Joggen');
  await page.getByTestId('pre-training-time').fill('19:00');
  await page.getByRole('button', { name: 'Serie anlegen' }).click();

  await expect(page).toHaveURL(/\/serien\/[0-9a-f-]{36}$/);
  await expect(page.getByRole('status')).toContainText('Die Serie ist angelegt.');
  await expect(page.getByTestId('series-pre-training')).toContainText('19:00');
  await expect(page.getByTestId('series-pre-training')).toContainText('Joggen');

  // One of its events shows it
  const response = await page.request.get('/api/events?from=2026-11-23&to=2026-11-23');
  const events = (await response.json()) as { id: string; title: string }[];
  const event = events.find((e) => e.title === 'Konditionstraining');
  expect(event).toBeDefined();
  await page.goto(`/termine/${event!.id}`);
  const preTraining = page.getByTestId('pre-training');
  await expect(preTraining).toContainText('19:00');
  await expect(preTraining).toContainText('Joggen');
});
