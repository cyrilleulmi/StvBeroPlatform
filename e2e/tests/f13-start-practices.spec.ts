import type { Locator, Page } from '@playwright/test';
import { checkAccessibility, expect, login, reset, test } from './helpers';

/**
 * Start page (F-13, changed by E-113 · TER-25, AUF-35, TER-26): one list sorted by date with the practices of the week
 * of the next practice, the next match day and pinned events; response buttons and counts on every card. Seed from
 * docs/test-data.md: event 06 «Teamessen» (Fri 11.12., Teams 1 + 2 + 3) is pinned. Default "now": Mon 16.11.2026
 * 06:00.
 */

const T01 = '40000000-0000-4000-8000-000000000001';
const T02 = '40000000-0000-4000-8000-000000000002';
const T04 = '40000000-0000-4000-8000-000000000004';
const T05 = '40000000-0000-4000-8000-000000000005';
const T06 = '40000000-0000-4000-8000-000000000006';

/** The cards of the start page in display order. */
function cards(page: Page): Locator {
  return page.getByTestId('start-events').getByTestId(/^(start-practice-|start-pinned-|next-match-day$)/);
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

test('TER-25 AUF-35 TER-26: this week\'s practices, the next match day and the pinned event in date order', { tag: '@desktop' }, async ({ page }) => {
  await login(page, 'anna');
  await page.goto('/');
  await expect(cards(page)).toHaveCount(4);
  await expect(cards(page).nth(0)).toHaveAccessibleName('Training Di 17.11. 18:30');
  await expect(cards(page).nth(1)).toHaveAccessibleName('Training Do 19.11. 19:00');
  await expect(cards(page).nth(2)).toHaveAccessibleName('Spieltag So 22.11. 09:30');
  await expect(cards(page).nth(3)).toHaveAccessibleName('Teamessen Fr 11.12. 19:00');
  await expect(page.getByTestId(`start-practice-${T01}`).getByRole('link')).toHaveAttribute('href', `/termine/${T01}`);
  await expect(page.getByTestId(`start-practice-${T02}`)).toContainText('19:00–20:30');

  // The week above the first practice; the next practice and the next match day are filled (E-105)
  await expect(page.getByTestId('start-week')).toHaveText('Diese Woche');
  await expect(page.getByTestId('start-week')).toHaveAttribute('data-week', 'current');
  await expect(page.getByTestId(`start-practice-${T01}`)).toHaveAttribute('data-filled', 'true');
  await expect(page.getByTestId('next-match-day')).toHaveAttribute('data-filled', 'true');
  await expect(page.getByTestId('next-match-day')).toContainText('Nächster Spieltag');
  await expect(page.getByTestId(`start-practice-${T02}`)).not.toHaveAttribute('data-filled', 'true');

  // The pinned event carries its marker; the others do not
  const dinner = page.getByTestId(`start-pinned-${T06}`);
  await expect(dinner.getByTestId('event-pinned')).toHaveText('Angeheftet');
  await expect(page.getByTestId('start-events').getByTestId('event-pinned')).toHaveCount(1);
  // A player edits nothing
  await expect(page.getByTestId(/^event-edit-/)).toHaveCount(0);
  await checkAccessibility(page);
});

test('TER-25: later in the week only the practices that are not over yet', async ({ page }) => {
  // Tue 17.11. 20:30: the Tuesday practice is over
  await setClock(page, '2026-11-17T20:30:00+01:00');
  await login(page, 'anna');
  await page.goto('/');
  await expect(cards(page)).toHaveCount(3);
  await expect(cards(page).nth(0)).toHaveAccessibleName('Training Do 19.11. 19:00');
  await expect(page.getByTestId(`start-practice-${T01}`)).toHaveCount(0);
  await expect(page.getByTestId('start-week')).toHaveText('Diese Woche');
});

test('TER-25: after the last practice of the week the practices of next week', async ({ page }) => {
  await setClock(page, '2026-11-20T08:00:00+01:00');
  await login(page, 'anna');
  await page.goto('/');
  // Tue 24.11. is cancelled: only Thu 26.11. is left next week; the match day of this Sunday comes first
  await expect(cards(page)).toHaveCount(3);
  await expect(cards(page).nth(0)).toHaveAccessibleName('Spieltag So 22.11. 09:30');
  await expect(cards(page).nth(1)).toHaveAccessibleName('Training Do 26.11. 19:00');
  await expect(cards(page).nth(2)).toHaveAccessibleName('Teamessen Fr 11.12. 19:00');
  await expect(page.getByTestId('start-week')).toHaveText('Nächste Woche');
  await expect(page.getByTestId('start-week')).toHaveAttribute('data-week', 'next');
  await expect(page.getByText('Di 24.11.')).toHaveCount(0);
});

test('TER-25: a week without practices is skipped, the cancelled practice does not count', async ({ page }) => {
  // Sat 21.11.: Eva's (Team 2) next Tuesday practice is cancelled, so the week of 30.11.; her match day comes first
  await setClock(page, '2026-11-21T08:00:00+01:00');
  await login(page, 'eva');
  await page.goto('/');
  await expect(cards(page)).toHaveCount(3);
  await expect(cards(page).nth(0)).toHaveAccessibleName('Spieltag Sa 28.11. 14:00');
  await expect(page.getByTestId('next-match-day').getByRole('link')).toHaveAttribute('href', `/termine/${T05}`);
  await expect(cards(page).nth(1)).toHaveAccessibleName('Training Di 01.12. 18:30');
  await expect(cards(page).nth(1)).toContainText('Team 1 + Team 2');
  await expect(page.getByTestId('start-week')).toHaveText('Woche vom 30.11.');
  await expect(page.getByTestId('start-week')).toHaveAttribute('data-week', 'later');

  // Christmas holidays: after Tue 15.12. the next practice of Team 2 is in January
  await setClock(page, '2026-12-15T21:00:00+01:00');
  await page.reload();
  await expect(cards(page)).toHaveCount(1);
  await expect(cards(page).nth(0)).toHaveAccessibleName('Training Di 05.01.2027 18:30');
  await expect(page.getByTestId('start-week')).toHaveText('Woche vom 04.01.2027');
});

test('F-13 K3: Response on a card updates its counter without reloading', async ({ page }) => {
  await login(page, 'nina');
  await page.goto('/');
  const practice = page.getByTestId(`start-practice-${T02}`);
  const counter = practice.getByTestId('event-counter');
  await expect(counter).toHaveText('1 dabei · 0 nicht dabei · 0 vielleicht · 6 offen');
  await responseButton(practice, 'attending').click();
  await expect(practice.getByRole('status')).toHaveText('Gespeichert: dabei.');
  await expect(counter).toHaveText('2 dabei · 0 nicht dabei · 0 vielleicht · 5 offen');
  await expect(responseButton(practice, 'attending')).toHaveAttribute('aria-pressed', 'true');
  // Only the touched card changes
  await expect(page.getByTestId(`start-practice-${T01}`).getByTestId('event-counter')).toHaveText(
    '7 dabei · 2 nicht dabei · 1 vielleicht · 2 offen',
  );

  // The pinned event as well
  const dinner = page.getByTestId(`start-pinned-${T06}`);
  await responseButton(dinner, 'attending').click();
  await expect(dinner.getByRole('status')).toHaveText('Gespeichert: dabei.');
  await expect(dinner.getByTestId('event-counter')).toContainText('1 dabei');
});

test('F-13 K5: Person in two teams sees the practices of both teams with the team name', async ({ page }) => {
  await login(page, 'lea');
  await page.goto('/');
  await expect(page.getByTestId(`start-practice-${T01}`)).toContainText('Team 1 + Team 2');
  await expect(page.getByTestId(`start-practice-${T02}`)).toContainText('Team 1');
  await expect(page.getByTestId(`start-practice-${T02}`)).not.toContainText('Team 2');
});

test('F-13 K5: Only events of the own teams count', async ({ page }) => {
  // Team 3 has a practice on Wed 18.11., but Anna is only in Team 1.
  await login(page, 'anna');
  await page.goto('/');
  await expect(page.getByText('Mi 18.11.')).toHaveCount(0);
});

test('F-13 K6: With only one practice left there is no placeholder', async ({ page }) => {
  // After Tue 27.04.2027 only Thu 29.04.2027 (series 02) is left for Team 1; the Teamessen is over
  await setClock(page, '2027-04-28T06:00:00+02:00');
  await login(page, 'anna');
  await page.goto('/');
  await expect(cards(page)).toHaveCount(1);
  await expect(cards(page).nth(0)).toContainText('Do 29.04.');
  await expect(page.getByTestId('my-response')).toHaveCount(1);
});

test('F-13 K6: Without events the empty state stays', async ({ page }) => {
  await login(page, 'alex');
  await page.goto('/');
  await expect(page.getByTestId('start-empty')).toHaveText('Keine kommenden Termine');
  await expect(page.getByTestId('start-events')).toHaveCount(0);
  await expect(page.getByTestId('my-response')).toHaveCount(0);
  await expect(page.getByTestId('all-events')).toBeVisible();
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

test('F-13 K9 E-111: a coach who also plays sees the events of both teams, with the pencil', { tag: '@desktop' }, async ({ page }) => {
  await login(page, 'petra');
  await page.goto('/');
  // Coach of Team 1, player in Team 2: the next match day is the one of Team 1 (22.11.), not Team 2's (28.11.)
  await expect(cards(page)).toHaveCount(4);
  await expect(page.getByTestId('next-match-day').getByRole('link', { name: 'Spieltag So 22.11. · 09:30–13:00' })).toHaveAttribute('href', `/termine/${T04}`);
  await expect(page.getByTestId(`start-practice-${T01}`).getByTestId('my-response')).toBeVisible();
  const pencil = page.getByTestId(`event-edit-${T02}`);
  await expect(pencil).toHaveAccessibleName('Training Do 19.11. 19:00 bearbeiten');
  await pencil.click();
  await expect(page).toHaveURL(`/termine/${T02}/bearbeiten`);
  await page.goBack();
  await expect(page.getByTestId(`event-edit-${T06}`)).toBeVisible();
  await checkAccessibility(page);
});

test('F-13 K9: a coach of Team 3 sees its practice of the week and the pinned Teamessen', async ({ page }) => {
  await login(page, 'ruth');
  await page.goto('/');
  await expect(cards(page)).toHaveCount(2);
  await expect(cards(page).nth(0)).toContainText('Mi 18.11.');
  await expect(cards(page).nth(0)).toContainText('Team 3');
  await expect(cards(page).nth(1).getByTestId('event-pinned')).toBeVisible();
});
