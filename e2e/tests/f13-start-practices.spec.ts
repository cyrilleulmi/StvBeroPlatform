import type { Locator, Page } from '@playwright/test';
import { checkAccessibility, expect, login, reset, test } from './helpers';

/**
 * F-13 · Start shows the next two practices (ANM extended). Acceptance criteria K1–K9 from issue #52
 * and docs/pipeline/backlog.md, scenarios S39 and S40 from docs/test-data.md. Default "now": Mon 16.11.2026 06:00.
 */

const T01 = '40000000-0000-4000-8000-000000000001';
const T02 = '40000000-0000-4000-8000-000000000002';
const T04 = '40000000-0000-4000-8000-000000000004';

function nextEvent(page: Page): Locator {
  return page.getByTestId('next-event');
}

/** The further practices below the next event, in display order. */
function practices(page: Page): Locator {
  return page.getByTestId('next-practices').getByTestId(/^start-practice-/);
}

function responseButton(area: Locator, choice: 'attending' | 'declined' | 'maybe'): Locator {
  return area.getByTestId('my-response').getByTestId(`response-${choice}`);
}

async function setClock(page: Page, now: string): Promise<void> {
  const response = await page.request.put('/api/test/clock', { data: { now } });
  expect(response.status()).toBe(204);
}

test.beforeEach(async ({ request }) => {
  await reset(request);
});

test('F-13 K1 K2: Next event is a practice, one more practice follows', async ({ page }) => {
  await login(page, 'anna');
  await page.goto('/');
  await expect(nextEvent(page).getByRole('link')).toHaveAttribute('href', `/termine/${T01}`);
  await expect(nextEvent(page)).toContainText('Di 17.11.');
  await expect(practices(page)).toHaveCount(1);
  await expect(page.getByTestId(`start-practice-${T02}`)).toContainText('Do 19.11.');
  await expect(page.getByTestId(`start-practice-${T02}`)).toContainText('19:00–20:30');
  await expect(
    page.getByRole('heading', { name: 'Weitere Trainings' }),
  ).toBeVisible();
  await checkAccessibility(page);
});

test('F-13 K1 K2 K4: Next event is a match day, two practices follow, cancelled one skipped', async ({ page }) => {
  await setClock(page, '2026-11-22T06:00:00+01:00');
  await login(page, 'anna');
  await page.goto('/');
  await expect(nextEvent(page).getByRole('link')).toHaveAttribute('href', `/termine/${T04}`);
  await expect(nextEvent(page)).toContainText('So 22.11.');
  // Tue 24.11. (event 03) is cancelled: neither shown nor counted.
  await expect(practices(page)).toHaveCount(2);
  await expect(practices(page).nth(0)).toContainText('Do 26.11.');
  await expect(practices(page).nth(1)).toContainText('Di 01.12.');
  await expect(page.getByText('Di 24.11.')).toHaveCount(0);
  await checkAccessibility(page);
});

test('F-13 K4: Cancelled practice is skipped for Team 2', async ({ page }) => {
  await login(page, 'eva');
  await page.goto('/');
  await expect(nextEvent(page).getByRole('link')).toHaveAttribute('href', `/termine/${T01}`);
  // Next practice of Team 2 after Tue 17.11.: Tue 24.11. is cancelled, so Tue 01.12. (match day 28.11. is no practice).
  await expect(practices(page)).toHaveCount(1);
  await expect(practices(page).nth(0)).toContainText('Di 01.12.');
  await expect(practices(page).nth(0)).toContainText('Team 1 + Team 2');
});

test('F-13 K3: Response on a practice updates the counter without reloading', async ({ page }) => {
  await login(page, 'nina');
  await page.goto('/');
  const practice = page.getByTestId(`start-practice-${T02}`);
  const counter = practice.getByTestId('event-counter');
  await expect(counter).toHaveText('1 dabei · 0 nicht dabei · 0 vielleicht · 6 offen');
  await expect(nextEvent(page).getByTestId('event-counter')).toBeVisible();
  for (const choice of ['attending', 'declined', 'maybe'] as const) {
    await expect(responseButton(practice, choice)).toBeVisible();
  }
  await responseButton(practice, 'attending').click();
  await expect(practice.getByRole('status')).toHaveText('Gespeichert: dabei.');
  await expect(counter).toHaveText('2 dabei · 0 nicht dabei · 0 vielleicht · 5 offen');
  await expect(responseButton(practice, 'attending')).toHaveAttribute('aria-pressed', 'true');

  // Next event: same behaviour.
  const next = nextEvent(page);
  const nextCounter = next.getByTestId('event-counter');
  const before = (await nextCounter.textContent())?.trim() ?? '';
  await responseButton(next, 'attending').click();
  await expect(next.getByRole('status')).toHaveText('Gespeichert: dabei.');
  await expect(nextCounter).not.toHaveText(before);
});

test('F-13 K5: Person in two teams sees the practices of both teams with the team name', async ({ page }) => {
  await login(page, 'lea');
  await page.goto('/');
  await expect(nextEvent(page)).toContainText('Team 1 + Team 2');
  await expect(practices(page)).toHaveCount(1);
  await expect(page.getByTestId(`start-practice-${T02}`)).toContainText('Team 1');
  await expect(page.getByTestId(`start-practice-${T02}`)).not.toContainText('Team 2');
});

test('F-13 K5: Only practices of the own teams count', async ({ page }) => {
  // Team 3 has a practice on Wed 18.11., but Anna is only in Team 1.
  await login(page, 'anna');
  await page.goto('/');
  await expect(page.getByText('Mi 18.11.')).toHaveCount(0);
  await expect(page.getByText('Team 3')).toHaveCount(0);
});

test('F-13 K6: With only one upcoming practice there is no placeholder', async ({ page }) => {
  // After Tue 27.04.2027 only Thu 29.04.2027 (series 02) is left for Team 1.
  await setClock(page, '2027-04-28T06:00:00+02:00');
  await login(page, 'anna');
  await page.goto('/');
  await expect(nextEvent(page)).toContainText('Do 29.04.');
  await expect(page.getByTestId('next-practices')).toHaveCount(0);
  await expect(page.getByTestId('my-response')).toHaveCount(1);
});

test('F-13 K6: Without events the empty state stays', async ({ page }) => {
  await login(page, 'alex');
  await page.goto('/');
  await expect(nextEvent(page)).toContainText('Keine kommenden Termine');
  await expect(page.getByTestId('next-practices')).toHaveCount(0);
  await expect(page.getByTestId('my-response')).toHaveCount(0);
});

test('F-13 K7 ANM-04: «Vielleicht» on the start screen asks for a comment', async ({ page }) => {
  await login(page, 'nina');
  await page.goto('/');
  const practice = page.getByTestId(`start-practice-${T02}`);
  await responseButton(practice, 'maybe').click();
  const dialog = page.getByRole('dialog', { name: 'Vielleicht – bitte kurz begründen' });
  await expect(dialog).toBeVisible();
  await dialog.getByRole('button', { name: 'Speichern' }).click();
  await expect(dialog.getByLabel('Kommentar')).toHaveAttribute('aria-invalid', 'true');
  await dialog.getByLabel('Kommentar').fill('Komme später');
  await dialog.getByRole('button', { name: 'Speichern' }).click();
  await expect(dialog).toBeHidden();
  await expect(responseButton(practice, 'maybe')).toHaveAttribute('aria-pressed', 'true');
  await expect(practice.getByTestId('event-counter')).toContainText('1 vielleicht');
});

test('F-13 K8: «Alle Termine» leads to the event list', async ({ page }) => {
  await login(page, 'anna');
  await page.goto('/');
  await page.getByRole('link', { name: 'Alle Termine' }).click();
  await expect(page).toHaveURL(/\/termine$/);
  await expect(page.getByRole('heading', { level: 1, name: 'Termine' })).toBeVisible();
});

test('F-13 K9: Coach sees the next two practices of the own team', async ({ page }) => {
  await login(page, 'ruth');
  await page.goto('/');
  await expect(nextEvent(page)).toContainText('Mi 18.11.');
  await expect(nextEvent(page)).toContainText('Team 3');
  await expect(practices(page)).toHaveCount(1);
  await expect(practices(page).nth(0)).toContainText('Mi 25.11.');
  await expect(practices(page).nth(0).getByTestId('my-response')).toBeVisible();
  await checkAccessibility(page);
});
