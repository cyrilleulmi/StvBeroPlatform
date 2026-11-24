import type { APIRequestContext, Page } from '@playwright/test';
import { checkAccessibility, expect, login, reset, test } from './helpers';

/**
 * F-06 · Notifications (BEN). Acceptance criteria from issue #7 and docs/pipeline/backlog.md,
 * scenarios S5, S6, S9 and S10 from docs/test-data.md. Default "now": Mon 16.11.2026 06:00.
 * Seed: cancellation of event 03 to all members of Team 1 and 2 except Petra (Mia read), call-up
 * for event 05 to all players of Team 2 (Petra read).
 */

const PETRA = '20000000-0000-4000-8000-000000000002';
const MARC = '20000000-0000-4000-8000-000000000003';
const ANNA = '20000000-0000-4000-8000-000000000005';
const LEA = '20000000-0000-4000-8000-000000000006';
const MIA = '20000000-0000-4000-8000-000000000007';
const SARA = '20000000-0000-4000-8000-000000000008';
const NINA = '20000000-0000-4000-8000-000000000009';
const JANA = '20000000-0000-4000-8000-000000000010';
const KIM = '20000000-0000-4000-8000-000000000011';
const EVA = '20000000-0000-4000-8000-000000000012';
const LARA = '20000000-0000-4000-8000-000000000013';
const ZOE = '20000000-0000-4000-8000-000000000014';
const ELLA = '20000000-0000-4000-8000-000000000015';
const TEAM_1 = '10000000-0000-4000-8000-000000000001';
const TEAM_2 = '10000000-0000-4000-8000-000000000002';
const T01 = '40000000-0000-4000-8000-000000000001';
const T03 = '40000000-0000-4000-8000-000000000003';
const T04 = '40000000-0000-4000-8000-000000000004';
const T05 = '40000000-0000-4000-8000-000000000005';
const ANNA_CANCELLATION_T03 = '90000000-0000-4000-8000-000000000002';
const LEA_CALL_UP_T05 = '90000000-0000-4000-8000-000000000014';
const LEA_CANCELLATION_T03 = '90000000-0000-4000-8000-000000000003';
const PETRA_CALL_UP_T05 = '90000000-0000-4000-8000-000000000013';
const TEAM_1_AND_2_WITHOUT_PETRA = [ANNA, LEA, MIA, SARA, NINA, JANA, KIM, MARC, EVA, LARA, ZOE, ELLA];

interface ApiNotification {
  id: string;
  type: string;
  recipient: { id: string };
  text: string;
  eventId: string | null;
}

/** Notifications of one type for an event created after the reset (without seed). */
async function newNotifications(request: APIRequestContext, type: string, eventId: string): Promise<ApiNotification[]> {
  const response = await request.get('/api/test/notifications');
  expect(response.status()).toBe(200);
  const all = (await response.json()) as ApiNotification[];
  return all.filter((b) => b.type === type && b.eventId === eventId && !b.id.startsWith('90000000'));
}

const recipients = (list: ApiNotification[]) => list.map((b) => b.recipient.id).sort();

async function setClock(request: APIRequestContext, now: string): Promise<void> {
  const response = await request.put('/api/test/clock', { data: { now } });
  expect(response.status()).toBe(204);
}

function bell(page: Page) {
  return page.getByRole('navigation', { name: 'Hauptnavigation' }).getByRole('link', { name: /^Benachrichtigungen/ });
}

function list(page: Page) {
  return page.getByRole('list', { name: 'Benachrichtigungen' });
}

test.beforeEach(async ({ request }) => {
  await reset(request);
});

test('F-06 K1: List newest first, unread marked, count in the navigation', { tag: '@desktop' }, async ({ page }) => {
  await login(page, 'lea');
  await page.goto('/');
  await expect(page.getByTestId('notifications-counter')).toHaveText('2');
  await expect(bell(page)).toHaveAccessibleName('Benachrichtigungen 2 ungelesen');

  await bell(page).click();
  await expect(page).toHaveURL('/benachrichtigungen');
  await expect(page).toHaveTitle('Benachrichtigungen · STV Bero');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Benachrichtigungen');
  await expect(bell(page)).toHaveAttribute('aria-current', 'page');

  const entries = list(page).getByRole('listitem');
  await expect(entries).toHaveCount(2);
  // Newest first: call-up (15.11.) before cancellation (14.11.)
  await expect(entries.nth(0)).toHaveAttribute('data-testid', `notification-${LEA_CALL_UP_T05}`);
  await expect(entries.nth(1)).toHaveAttribute('data-testid', `notification-${LEA_CANCELLATION_T03}`);
  await expect(entries.nth(0)).toContainText('Das Aufgebot für Spieltag Sa 28.11. ist da: Du bist nicht aufgeboten.');
  await expect(entries.nth(0)).toContainText('So 15.11. 21:00');
  await expect(entries.nth(1)).toContainText('Training Di 24.11. ist abgesagt: Halle besetzt');
  for (const e of await entries.all()) {
    await expect(e).toHaveAttribute('data-read', 'false');
    await expect(e.getByText('Neu', { exact: true })).toBeVisible();
  }
  await checkAccessibility(page);
});

test('F-06 K1: Read ones without «Neu», no counter when everything is read', async ({ page }) => {
  await login(page, 'petra');
  await page.goto('/benachrichtigungen');
  const entry = page.getByTestId(`notification-${PETRA_CALL_UP_T05}`);
  await expect(entry).toHaveAttribute('data-read', 'true');
  await expect(entry.getByText('Neu', { exact: true })).toHaveCount(0);
  await expect(page.getByTestId('notifications-counter')).toHaveCount(0);
  await expect(bell(page)).toHaveAccessibleName('Benachrichtigungen');
  await expect(page.getByRole('button', { name: 'Alle als gelesen markieren' })).toHaveCount(0);
});

test('F-06 K1: a notice without notifications', async ({ page }) => {
  await login(page, 'alex');
  await page.goto('/benachrichtigungen');
  await expect(page.getByText('Keine Benachrichtigungen.')).toBeVisible();
  await expect(page.getByTestId('notifications-counter')).toHaveCount(0);
});

test('F-06 K2 BEN-12: Click leads to the event and marks it as read', async ({ page }) => {
  await login(page, 'anna');
  await page.goto('/benachrichtigungen');
  await expect(page.getByTestId('notifications-counter')).toHaveText('1');

  await page.getByTestId(`notification-${ANNA_CANCELLATION_T03}`).getByRole('link').click();
  await expect(page).toHaveURL(`/termine/${T03}`);
  await expect(page.getByTestId('event-status')).toContainText('Halle besetzt');
  await expect(page.getByTestId('notifications-counter')).toHaveCount(0);

  await bell(page).click();
  await expect(page.getByTestId(`notification-${ANNA_CANCELLATION_T03}`)).toHaveAttribute('data-read', 'true');
  // Stays read after reload
  await page.reload();
  await expect(page.getByTestId(`notification-${ANNA_CANCELLATION_T03}`)).toHaveAttribute('data-read', 'true');
});

test('F-06 K1: «Alle als gelesen markieren» (mark all as read)', async ({ page }) => {
  await login(page, 'lea');
  await page.goto('/benachrichtigungen');
  await page.getByRole('button', { name: 'Alle als gelesen markieren' }).click();
  await expect(page.getByRole('status')).toHaveText('Alle sind als gelesen markiert.');
  await expect(page.getByTestId('notifications-counter')).toHaveCount(0);
  await expect(list(page).getByText('Neu', { exact: true })).toHaveCount(0);
});

test('F-06 RR-07: a notification of another person cannot be marked as read (403)', async ({ page }) => {
  await login(page, 'anna');
  const response = await page.request.post(`/api/notifications/${LEA_CANCELLATION_T03}/read`);
  expect(response.status()).toBe(403);
});

test('F-06 K3 S5 BEN-02: late cancellation notifies Petra, not Marc', async ({ page, request }) => {
  await setClock(request, '2026-11-16T19:00:00+01:00');
  await login(page, 'anna');
  const response = await page.request.put(`/api/events/${T01}/responses/me`, { data: { status: 'DECLINED' } });
  expect(response.status()).toBe(200);

  const created = await newNotifications(request, 'LATE_CANCELLATION', T01);
  expect(recipients(created)).toEqual([PETRA]);

  await login(page, 'petra');
  await page.goto('/');
  await expect(page.getByTestId('notifications-counter')).toHaveText('1');
  await page.goto('/benachrichtigungen');
  const entry = list(page).getByRole('listitem').first();
  await expect(entry).toContainText('Anna Muster hat sich kurzfristig für Training Di 17.11. abgemeldet.');
  await entry.getByRole('link').click();
  await expect(page).toHaveURL(`/termine/${T01}`);
});

test('F-06 K4 S6 BEN-01 BEN-13: Reminder to people without response, exactly once', async ({ page, request }) => {
  await setClock(request, '2026-11-16T06:31:00+01:00');
  expect(recipients(await newNotifications(request, 'REMINDER', T01))).toEqual([NINA, ELLA].sort());
  // Repeated checks do not remind again
  await setClock(request, '2026-11-16T07:00:00+01:00');
  await setClock(request, '2026-11-16T12:00:00+01:00');
  expect(await newNotifications(request, 'REMINDER', T01)).toHaveLength(2);

  await login(page, 'nina');
  await page.goto('/benachrichtigungen');
  await expect(list(page).getByRole('listitem').first()).toContainText(
    'Erinnerung: Bitte gib bis Mo 16.11. 18:30 Bescheid, ob du bei Training Di 17.11. dabei bist.',
  );
  await expect(list(page).getByRole('listitem').first()).toHaveAttribute('data-kind', 'REMINDER');
});

test('F-06 K5 S9 BEN-03: Publishing notifies all players of the team', async ({ page, request }) => {
  await login(page, 'petra');
  await page.goto(`/termine/${T04}/aufgebot`);
  await page.getByRole('button', { name: 'Veröffentlichen' }).click();
  await page.getByRole('dialog', { name: 'Aufgebot veröffentlichen?' }).getByRole('button', { name: 'Veröffentlichen' }).click();
  await expect(page.getByTestId('call-up-status')).toContainText('veröffentlicht am');

  const created = await newNotifications(request, 'CALL_UP_PUBLISHED', T04);
  expect(recipients(created)).toEqual([ANNA, LEA, MIA, SARA, NINA, JANA, KIM].sort());

  await login(page, 'kim');
  await page.goto('/benachrichtigungen');
  await expect(list(page).getByRole('listitem').first()).toContainText(
    'Das Aufgebot für Spieltag So 22.11. ist da: Du bist nicht aufgeboten.',
  );
});

test('F-06 K6 S10 BEN-04: A called-up player declining notifies the coaches', async ({ page, request }) => {
  await login(page, 'eva');
  await page.goto(`/termine/${T05}`);
  await page.getByTestId('my-response').getByRole('button', { name: 'Nicht dabei' }).click();
  await expect(page.getByRole('dialog', { name: 'Nicht dabei' })).toBeVisible();
  await page.getByRole('button', { name: 'Überspringen' }).click();

  await expect.poll(async () => recipients(await newNotifications(request, 'CALLED_UP_PLAYER_DECLINED', T05))).toEqual([MARC]);

  await login(page, 'marc');
  await page.goto('/benachrichtigungen');
  await expect(list(page).getByRole('listitem').first()).toContainText(
    'Eva Steiner ist für Spieltag Sa 28.11. aufgeboten und hat sich abgemeldet.',
  );
});

test('F-06 K7 BEN-05: A change to a published call-up notifies only those affected', async ({ page, request }) => {
  await login(page, 'marc');
  // Event 05 is published with Petra, Eva, Lara, Ella. Ella drops out, Lea is added.
  const response = await page.request.put(`/api/events/${T05}/call-up`, {
    data: { personIds: [PETRA, EVA, LARA, LEA] },
  });
  expect(response.status()).toBe(200);

  const created = await newNotifications(request, 'CALL_UP_CHANGED', T05);
  expect(recipients(created)).toEqual([LEA, ELLA].sort());

  await login(page, 'ella');
  await page.goto('/benachrichtigungen');
  await expect(list(page).getByRole('listitem').first()).toContainText(
    'Das Aufgebot für Spieltag Sa 28.11. wurde geändert: Du bist nicht mehr aufgeboten.',
  );
});

test('F-06 K8 BEN-07: Cancellation notifies all members of the teams involved', async ({ page, request }) => {
  await login(page, 'petra');
  const response = await page.request.post(`/api/events/${T01}/cancel`, { data: { reason: 'Halle gesperrt' } });
  expect(response.status()).toBe(200);
  expect(recipients(await newNotifications(request, 'EVENT_CANCELLED', T01))).toEqual([...TEAM_1_AND_2_WITHOUT_PETRA].sort());

  await login(page, 'zoe');
  await page.goto('/benachrichtigungen');
  await expect(list(page).getByRole('listitem').first()).toContainText('Training Di 17.11. ist abgesagt: Halle gesperrt');
});

test('F-06 K8 BEN-07: Rescheduling notifies all members of the teams involved', async ({ page, request }) => {
  await login(page, 'marc');
  const response = await page.request.put(`/api/events/${T01}`, {
    data: {
      type: 'PRACTICE',
      title: 'Training',
      start: '2026-11-17T19:00:00+01:00',
      end: '2026-11-17T20:30:00+01:00',
      teamIds: [TEAM_1, TEAM_2],
      location: { name: 'Sporthalle Nord' },
    },
  });
  expect(response.status()).toBe(200);
  const created = await newNotifications(request, 'EVENT_RESCHEDULED', T01);
  expect(recipients(created)).toEqual([...TEAM_1_AND_2_WITHOUT_PETRA.filter((p) => p !== MARC), PETRA].sort());

  await login(page, 'anna');
  await page.goto('/benachrichtigungen');
  await expect(list(page).getByRole('listitem').first()).toContainText(
    'Training Di 17.11. ist verschoben auf Di 17.11. 19:00.',
  );
});

for (const scheme of ['light', 'dark'] as const) {
  test(`F-06: Bell with counter (${scheme}) easy to reach and covers nothing`, async ({ page }) => {
    await page.emulateMedia({ colorScheme: scheme });
    await login(page, 'lea');
    await page.goto('/termine');
    await expect(page.getByTestId('notifications-counter')).toHaveText('2');
    const box = (await bell(page).boundingBox())!;
    expect(box.width).toBeGreaterThanOrEqual(44);
    expect(box.height).toBeGreaterThanOrEqual(44);
    // The heading is not under the bell
    const h1 = (await page.getByRole('heading', { level: 1 }).boundingBox())!;
    const overlaps =
      box.x < h1.x + h1.width && h1.x < box.x + box.width && box.y < h1.y + h1.height && h1.y < box.y + box.height;
    expect(overlaps, 'Bell covers the heading').toBe(false);
    await page.goto('/benachrichtigungen');
    await expect(list(page).getByRole('listitem')).toHaveCount(2);
    await checkAccessibility(page);
  });
}

test('F-06 O-51: Bell covers no controls when scrolling either', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'mobile', 'The bell at the top right exists only on mobile');
  await login(page, 'petra');
  await page.goto(`/termine/${T01}`);
  const pencil = page.getByRole('button', { name: 'Antwort für Mia Frei eintragen' });
  await expect(pencil).toBeVisible();
  const box = (await bell(page).boundingBox())!;
  // Scroll the pencil button up until it is level with the bell
  const before = (await pencil.boundingBox())!;
  await page.evaluate((dy) => window.scrollBy(0, dy), before.y - box.y);
  const button = (await pencil.boundingBox())!;
  expect(Math.abs(button.y - box.y), 'Pencil is level with the bell').toBeLessThan(4);
  // Next to the bell the pencil is no longer visible: it lies under the header. Before, it was
  // visible on the left and covered by the bell on the right, a tap on the edge opened the notifications.
  const inContent = await page.evaluate(
    ([px, py]) => !!document.elementFromPoint(px, py)?.closest('main'),
    [button.x + 4, button.y + button.height / 2],
  );
  expect(inContent, 'Content visible level with the bell').toBe(false);
  // The pencil stays usable
  await pencil.click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await expect(page).toHaveURL(new RegExp(`/termine/${T01}$`));
});
