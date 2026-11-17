import { request as playwrightRequest } from '@playwright/test';
import type { Page } from '@playwright/test';
import { checkAccessibility, expect, login, reset, test } from './helpers';

/**
 * F-18 · Calendar subscription (CAL-01 to CAL-09). Acceptance criteria from issue #57 and docs/pipeline/backlog.md.
 * Anna has a fixed calendar address in the seed (docs/test-data.md); all others get one on the first visit.
 */

const ANNA_TOKEN = 'seed-anna-calendar-000000000000000000000005';
const T03 = '40000000-0000-4000-8000-000000000003';
const T04 = '40000000-0000-4000-8000-000000000004';

test.beforeEach(async ({ request }) => {
  await reset(request);
});

async function calendarAddress(page: Page): Promise<string> {
  const field = page.getByRole('textbox', { name: 'Deine Kalenderadresse' });
  await expect(field).toHaveValue(/\/api\/calendar\/[A-Za-z0-9_-]{43}\.ics$/);
  return field.inputValue();
}

/** Fetches the feed like a calendar app: without cookies. */
async function fetchFeed(address: string) {
  const context = await playwrightRequest.newContext();
  const response = await context.get(address);
  const result = { status: response.status(), type: response.headers()['content-type'], body: await response.text() };
  await context.dispose();
  return result;
}

/** One VEVENT of the feed, unfolded: property (with parameters) → value. */
function entry(feed: string, eventId: string): Map<string, string> {
  const lines = feed.replace(/\r\n /g, '').split('\r\n');
  const start = lines.indexOf(`UID:${eventId}@stvbero`);
  expect(start, `event ${eventId} in the feed`).toBeGreaterThan(0);
  const result = new Map<string, string>();
  for (let i = start; lines[i] !== 'END:VEVENT'; i++) {
    const colon = lines[i].indexOf(':');
    result.set(lines[i].substring(0, colon), lines[i].substring(colon + 1));
  }
  return result;
}

test('F-18 criterion 1 (CAL-01): «Kalender abonnieren» with address, copy button and instructions', async ({
  page,
  context,
}) => {
  await context.grantPermissions(['clipboard-read', 'clipboard-write']);
  await login(page, 'anna');
  await page.goto('/profil');
  const section = page.getByRole('region', { name: 'Kalender abonnieren' });
  await expect(section).toBeVisible();
  const address = await calendarAddress(page);
  expect(address).toBe(`${new URL(page.url()).origin}/api/calendar/${ANNA_TOKEN}.ics`);

  await section.getByRole('button', { name: 'Adresse kopieren' }).click();
  await expect(section.getByRole('status')).toHaveText('Die Adresse ist kopiert.');
  expect(await page.evaluate(() => navigator.clipboard.readText())).toBe(address);

  await expect(section.getByRole('link', { name: 'Im Kalender öffnen' })).toHaveAttribute(
    'href',
    address.replace(/^https?:/, 'webcal:'),
  );

  // Short instructions for iPhone, Android and Outlook
  for (const [name, text] of [
    ['iPhone', 'Kalenderabo hinzufügen'],
    ['Android (Google Kalender)', 'Per URL'],
    ['Outlook', 'Aus dem Internet abonnieren'],
  ]) {
    await section.getByText(name, { exact: true }).click();
    await expect(section.getByText(text)).toBeVisible();
  }
  await checkAccessibility(page);
});

test('F-18 criterion 1 (CAL-01): a person without an address gets one on the first visit', async ({ page }) => {
  await login(page, 'lea');
  await page.goto('/profil');
  const address = await calendarAddress(page);
  expect(address).not.toContain(ANNA_TOKEN);
  await page.reload();
  expect(await calendarAddress(page)).toBe(address);
  const feed = await fetchFeed(address);
  expect(feed.status).toBe(200);
  // Lea plays in Team 1 and Team 2: match day 05 (Team 2) is in her calendar
  entry(feed.body, '40000000-0000-4000-8000-000000000005');
});

test('F-18 criteria 2, 3, 5, 8 (CAL-02, CAL-03, CAL-05, CAL-08): feed without login, match day field by field', async ({
  page,
}) => {
  await login(page, 'anna');
  await page.goto('/profil');
  const feed = await fetchFeed(await calendarAddress(page));
  expect(feed.status).toBe(200);
  expect(feed.type).toMatch(/^text\/calendar/);
  expect(feed.body.startsWith('BEGIN:VCALENDAR\r\nVERSION:2.0\r\n')).toBe(true);
  expect(feed.body).toContain('BEGIN:VTIMEZONE\r\nTZID:Europe/Zurich');

  const matchDay = entry(feed.body, T04);
  expect(matchDay.get('SUMMARY')).toBe('Spieltag · Team 1');
  expect(matchDay.get('DTSTART;TZID=Europe/Zurich')).toBe('20261122T093000');
  expect(matchDay.get('DTEND;TZID=Europe/Zurich')).toBe('20261122T130000');
  expect(matchDay.get('LOCATION')).toBe('Sporthalle Seefeld\\, Musterdorf');
  const description = matchDay.get('DESCRIPTION') ?? '';
  expect(description).toContain('Treffpunkt: 08:00\\, Parkplatz Sporthalle Nord');
  expect(description).toContain('09:30 Team 1 – UHC Musterdorf\\n11:45 Unihockey Beispielstadt – Team 1');
  expect(description).toContain('Notizen: Weisse Trikots mitnehmen');
  expect(description).toContain('Deine Antwort: dabei');
  expect(description).toContain(`/termine/${T04}`);

  // Responses and comments of others never appear (Mia: «Muss evtl. arbeiten» for event 04)
  expect(feed.body).not.toContain('Muss evtl. arbeiten');
  expect(feed.body).not.toContain('Mia');

  // The link in the description opens the event in the app
  await page.goto(matchDay.get('URL') ?? '');
  await expect(page).toHaveURL(`/termine/${T04}`);
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Spieltag');
});

test('F-18 criterion 4 (CAL-04): cancelled events are delivered as cancelled', async ({ page }) => {
  await login(page, 'anna');
  await page.goto('/profil');
  const feed = await fetchFeed(await calendarAddress(page));
  const cancelled = entry(feed.body, T03);
  expect(cancelled.get('STATUS')).toBe('CANCELLED');
  expect(cancelled.get('SUMMARY')).toBe('Abgesagt: Training · Team 1 + Team 2');
});

test('F-18 criteria 6, 7 (CAL-06, CAL-07): «Adresse erneuern» replaces the address at once', async ({ page }) => {
  await login(page, 'anna');
  await page.goto('/profil');
  const old = await calendarAddress(page);
  const section = page.getByRole('region', { name: 'Kalender abonnieren' });

  // Cancel changes nothing
  await section.getByRole('button', { name: 'Adresse erneuern' }).click();
  const dialog = page.getByRole('dialog', { name: 'Adresse erneuern?' });
  await expect(dialog).toBeVisible();
  await checkAccessibility(page);
  await dialog.getByRole('button', { name: 'Abbrechen' }).click();
  await expect(dialog).toBeHidden();
  expect(await calendarAddress(page)).toBe(old);

  await section.getByRole('button', { name: 'Adresse erneuern' }).click();
  await dialog.getByRole('button', { name: 'Erneuern' }).click();
  await expect(section.getByRole('status')).toHaveText(
    'Neue Adresse erstellt. Die bisherige funktioniert nicht mehr.',
  );
  const renewed = await calendarAddress(page);
  expect(renewed).not.toBe(old);
  expect((await fetchFeed(old)).status).toBe(404);
  expect((await fetchFeed(renewed)).status).toBe(200);
});
