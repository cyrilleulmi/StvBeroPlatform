import type { Page } from '@playwright/test';
import { checkAccessibility, expect, login, reset, test } from './helpers';

/**
 * E-114 · Pinning an event to the start page (TER-26 to TER-28). Whoever may edit the event pins and unpins it on the
 * event page; everyone sees the marker «Angeheftet» on the event page, the cards of /termine and the start page.
 * Seed from docs/test-data.md: event 06 «Teamessen» (Fri 11.12., Teams 1 + 2 + 3) is pinned. Default "now": Mon
 * 16.11.2026 06:00.
 */

const T06 = '40000000-0000-4000-8000-000000000006';

test.beforeEach(async ({ request }) => {
  await reset(request);
});

/** ID of the practice of Team 1 on Thu 26.11. (series 02), as the list of the coach gives it. */
async function practiceOn26(page: Page): Promise<string> {
  const response = await page.request.get('/api/events?from=2026-11-26&to=2026-11-26');
  expect(response.status()).toBe(200);
  const events = (await response.json()) as { id: string; type: string }[];
  return events.filter((e) => e.type === 'PRACTICE')[0].id;
}

test('TER-26 TER-27: a coach pins a practice, a player sees it on her start page, then the coach unpins it', { tag: '@desktop' }, async ({ browser, page }) => {
  await login(page, 'petra');
  const id = await practiceOn26(page);
  await page.goto(`/termine/${id}`);
  const pin = page.getByTestId('event-pin');
  await expect(pin).toHaveText('An Startseite anheften');
  await expect(page.getByTestId('event-pinned')).toHaveCount(0);
  await checkAccessibility(page);

  await pin.click();
  await expect(page.getByRole('status')).toHaveText('Angeheftet. Der Termin steht jetzt auf der Startseite der Teams.');
  await expect(page.getByTestId('event-pinned')).toHaveText('Angeheftet');
  await expect(pin).toHaveText('Nicht mehr anheften');

  // Anna (player in Team 1) finds it on her start page, in date order and with the marker
  const annaContext = await browser.newContext();
  const anna = await annaContext.newPage();
  await login(anna, 'anna');
  await anna.goto('/');
  const card = anna.getByTestId(`start-practice-${id}`);
  await expect(card.getByTestId('event-pinned')).toBeVisible();
  const names = await anna
    .getByTestId('start-events')
    .getByTestId(/^(start-practice-|start-pinned-|next-match-day$)/)
    .evaluateAll((items) => items.map((item) => item.getAttribute('aria-label')));
  expect(names).toEqual([
    'Training Di 17.11. 18:30',
    'Training Do 19.11. 19:00',
    'Spieltag So 22.11. 09:30',
    'Training Do 26.11. 19:00',
    'Teamessen Fr 11.12. 19:00',
  ]);
  // The list of events marks it too
  await anna.goto('/termine');
  await expect(anna.getByTestId(`event-card-${id}`).getByTestId('event-pinned')).toBeVisible();

  await pin.click();
  await expect(page.getByRole('status')).toHaveText('Nicht mehr angeheftet.');
  await expect(page.getByTestId('event-pinned')).toHaveCount(0);
  await expect(pin).toHaveText('An Startseite anheften');
  await anna.goto('/');
  await expect(anna.getByTestId('start-events')).toBeVisible();
  await expect(anna.getByTestId(`start-practice-${id}`)).toHaveCount(0);
  await annaContext.close();
});

test('TER-27: a player sees that an event is pinned but has no action to pin', async ({ page }) => {
  await login(page, 'anna');
  await page.goto(`/termine/${T06}`);
  await expect(page.getByRole('heading', { level: 1, name: 'Teamessen' })).toBeVisible();
  await expect(page.getByTestId('event-pinned')).toHaveText('Angeheftet');
  await expect(page.getByTestId('event-pin')).toHaveCount(0);
  await checkAccessibility(page);
  // The API refuses as well
  expect((await page.request.post(`/api/events/${T06}/unpin`)).status()).toBe(403);
});

test('TER-26: a pinned event that is cancelled leaves the start page', async ({ page }) => {
  await login(page, 'petra');
  const response = await page.request.post(`/api/events/${T06}/cancel`, { data: { reason: 'Restaurant zu' } });
  expect(response.status()).toBe(200);
  await page.goto('/');
  await expect(page.getByTestId('start-events')).toBeVisible();
  await expect(page.getByTestId(`start-pinned-${T06}`)).toHaveCount(0);
  // The event page keeps the marker but offers no action any more
  await page.goto(`/termine/${T06}`);
  await expect(page.getByTestId('event-pinned')).toBeVisible();
  await expect(page.getByTestId('event-pin')).toHaveCount(0);
});
