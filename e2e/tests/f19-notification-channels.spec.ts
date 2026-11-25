import type { APIRequestContext, Page } from '@playwright/test';
import { checkAccessibility, expect, login, reset, test } from './helpers';

/**
 * F-19 · Notifications by e-mail and push (BEN-10, BEN-11, E-71). Acceptance criteria from issue #58 and
 * docs/pipeline/backlog.md. Seed: Kim switched e-mails off, Lea has push on one phone («Chrome auf Android»).
 *
 * The browser cannot reach a real push service in the tests, so `fakePush` replaces the browser permission and
 * `PushManager` (what the person answers, the subscription of this device). The service worker is the real one.
 */

const T01 = '40000000-0000-4000-8000-000000000001';
const ANNA = '20000000-0000-4000-8000-000000000005';
const LEA = '20000000-0000-4000-8000-000000000006';
const KIM = '20000000-0000-4000-8000-000000000011';
const LEA_ENDPOINT = 'https://push.example.test/seed-lea-phone';
/** Browser keys of RFC 8291, appendix A (valid P-256 key, 16-byte secret). */
const P256DH = 'BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4';
const AUTH = 'BTBZMqHH6r4Tts7J_aSIgg';
const IPHONE =
  'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1';

interface Delivery {
  channel: 'EMAIL' | 'PUSH';
  address: string;
  subject: string;
  text: string;
  path: string;
  status: string;
  attempts: number;
}
interface TestNotification {
  id: string;
  type: string;
  recipient: { id: string };
  eventId: string | null;
  deliveries: Delivery[];
}
interface Settings {
  email: boolean;
  emailAddress: string | null;
  devices: { id: string; endpoint: string; label: string }[];
}

test.beforeEach(async ({ request }) => {
  await reset(request);
});

/**
 * Replaces permission and push subscription of the browser. `answer`: what the person answers when asked.
 * State survives a reload (localStorage), like a real subscription.
 */
async function fakePush(page: Page, answer: NotificationPermission = 'granted'): Promise<void> {
  await page.addInitScript(
    ({ answer, p256dh, auth }) => {
      if (!('PushManager' in window) || !('Notification' in window)) return;
      const KEY = 'e2e-fake-push';
      let state = { permission: 'default' as NotificationPermission, endpoint: null as string | null };
      try {
        state = JSON.parse(localStorage.getItem(KEY) ?? 'null') ?? state;
      } catch {
        return; // about:blank has no storage
      }
      const save = () => localStorage.setItem(KEY, JSON.stringify(state));
      Object.defineProperty(Notification, 'permission', { configurable: true, get: () => state.permission });
      Notification.requestPermission = async () => {
        state.permission = answer;
        save();
        return answer;
      };
      const subscription = (endpoint: string) =>
        ({
          endpoint,
          options: { applicationServerKey: null, userVisibleOnly: true },
          toJSON: () => ({ endpoint, expirationTime: null, keys: { p256dh, auth } }),
          unsubscribe: async () => {
            state.endpoint = null;
            save();
            return true;
          },
        }) as unknown as PushSubscription;
      PushManager.prototype.getSubscription = async () =>
        state.endpoint ? subscription(state.endpoint) : null;
      PushManager.prototype.subscribe = async () => {
        state.endpoint = `https://push.example.test/e2e-${Math.random().toString(36).slice(2)}`;
        save();
        return subscription(state.endpoint);
      };
    },
    { answer, p256dh: P256DH, auth: AUTH },
  );
}

async function settings(page: Page): Promise<Settings> {
  const response = await page.request.get('/api/me/notification-settings');
  expect(response.status()).toBe(200);
  return response.json();
}

async function notifications(request: APIRequestContext, type: string): Promise<TestNotification[]> {
  const response = await request.get('/api/test/notifications');
  expect(response.status()).toBe(200);
  const all: TestNotification[] = await response.json();
  return all.filter((n) => n.type === type && !n.id.startsWith('90000000'));
}

function of(list: TestNotification[], person: string): TestNotification {
  const found = list.find((n) => n.recipient.id === person);
  expect(found, `notification to ${person}`).toBeDefined();
  return found!;
}

/** Petra cancels event 01 (Team 1 + Team 2) through the API. */
async function cancelT01(request: APIRequestContext): Promise<void> {
  expect((await request.post('/api/test/login', { data: { loginKey: 'petra' } })).status()).toBe(204);
  const response = await request.post(`/api/events/${T01}/cancel`, { data: { reason: 'Halle gesperrt' } });
  expect(response.status()).toBe(200);
}

function section(page: Page) {
  return page.getByRole('region', { name: 'Benachrichtigungen' });
}

test('F-19 criterion 2: «Benachrichtigungen» in the profile, e-mail on by default, push off', { tag: '@desktop' }, async ({ page }) => {
  await fakePush(page);
  await login(page, 'anna');
  await page.goto('/profil/benachrichtigungen');
  const s = section(page);
  await expect(s).toBeVisible();
  await expect(s.getByText('In der App siehst du alle Benachrichtigungen')).toBeVisible();
  const email = s.getByRole('switch', { name: 'E-Mail' });
  await expect(email).toBeChecked();
  await expect(email).toHaveAccessibleDescription('An anna@example.ch');
  const push = s.getByRole('switch', { name: 'Push auf diesem Gerät' });
  await expect(push).not.toBeChecked();
  await expect(push).toBeEnabled();
  await checkAccessibility(page);
});

test('F-19 criterion 2: switch e-mails off and on', async ({ page, request }) => {
  await login(page, 'anna');
  await page.goto('/profil/benachrichtigungen');
  const email = section(page).getByRole('switch', { name: 'E-Mail' });
  await email.click();
  await expect(section(page).getByRole('status')).toHaveText('E-Mails sind ausgeschaltet.');
  await expect(email).not.toBeChecked();
  expect((await settings(page)).email).toBe(false);

  // Without e-mails Anna gets the cancellation only in the app
  await cancelT01(request);
  const cancelled = await notifications(request, 'EVENT_CANCELLED');
  expect(of(cancelled, ANNA).deliveries.filter((d) => d.channel === 'EMAIL')).toEqual([]);

  await page.reload();
  await expect(email).not.toBeChecked();
  await email.click();
  await expect(section(page).getByRole('status')).toHaveText('E-Mails sind eingeschaltet.');
  await expect(email).toBeChecked();
  expect((await settings(page)).email).toBe(true);
});

test('F-19 criterion 2: Kim switched e-mails off; without an e-mail address the switch is off', async ({
  page,
  request,
}) => {
  await login(page, 'kim');
  await page.goto('/profil/benachrichtigungen');
  await expect(section(page).getByRole('switch', { name: 'E-Mail' })).not.toBeChecked();

  // The administration removes Anna's e-mail address
  expect((await request.post('/api/test/login', { data: { loginKey: 'alex' } })).status()).toBe(204);
  const updated = await request.put(`/api/persons/${ANNA}`, {
    data: { firstName: 'Anna', lastName: 'Muster', admin: false },
  });
  expect(updated.status()).toBe(200);
  await login(page, 'anna');
  await page.goto('/profil/benachrichtigungen');
  const email = section(page).getByRole('switch', { name: 'E-Mail' });
  await expect(email).not.toBeChecked();
  await expect(email).toBeDisabled();
  await expect(email).toHaveAccessibleDescription(/Für E-Mails braucht es eine E-Mail-Adresse/);
});

test('F-19 criterion 3: switching on push asks for permission and registers this device', async ({
  page,
}) => {
  await fakePush(page, 'granted');
  await login(page, 'anna');
  await page.goto('/profil/benachrichtigungen');
  const push = section(page).getByRole('switch', { name: 'Push auf diesem Gerät' });
  await push.click();
  await expect(section(page).getByRole('status')).toHaveText('Push ist auf diesem Gerät eingeschaltet.');
  await expect(push).toBeChecked();
  const devices = (await settings(page)).devices;
  expect(devices).toHaveLength(1);
  expect(devices[0].endpoint).toMatch(/^https:\/\/push\.example\.test\/e2e-/);
  // This device is not listed under «Weitere Geräte»
  await expect(section(page).getByTestId('push-devices')).toHaveCount(0);

  // Stays on after a reload
  await page.reload();
  await expect(push).toBeChecked();

  // Off again: the device is removed
  await push.click();
  await expect(section(page).getByRole('status')).toHaveText('Push ist auf diesem Gerät ausgeschaltet.');
  await expect(push).not.toBeChecked();
  expect((await settings(page)).devices).toEqual([]);
});

test('F-19 criterion 3: several devices; Lea sees her phone and can remove it', async ({ page }) => {
  await fakePush(page, 'granted');
  await login(page, 'lea');
  await page.goto('/profil/benachrichtigungen');
  const others = section(page).getByTestId('push-devices');
  await expect(others.getByRole('listitem')).toHaveCount(1);
  await expect(others.getByRole('listitem')).toContainText('Chrome auf Android');
  await expect(others.getByRole('listitem')).toContainText(/seit \d\d\.\d\d\.\d{4}/);

  // Push on this device too: two devices
  await section(page).getByRole('switch', { name: 'Push auf diesem Gerät' }).click();
  await expect(section(page).getByRole('status')).toHaveText('Push ist auf diesem Gerät eingeschaltet.');
  expect((await settings(page)).devices.map((d) => d.endpoint)[0]).toBe(LEA_ENDPOINT);
  expect((await settings(page)).devices).toHaveLength(2);
  await checkAccessibility(page);

  await others.getByRole('button', { name: 'Chrome auf Android entfernen' }).click();
  await expect(section(page).getByRole('status')).toHaveText('Push auf «Chrome auf Android» ist ausgeschaltet.');
  await expect(others).toHaveCount(0);
  expect((await settings(page)).devices).toHaveLength(1);
});

test('F-19 criterion 3: a denied permission is explained, the switch stays off', async ({ page }) => {
  await fakePush(page, 'denied');
  await login(page, 'anna');
  await page.goto('/profil/benachrichtigungen');
  const push = section(page).getByRole('switch', { name: 'Push auf diesem Gerät' });
  await push.click();
  const hint = section(page).getByTestId('push-denied-hint');
  await expect(hint).toBeVisible();
  await expect(hint).toContainText('Mitteilungen sind für diese App blockiert');
  await expect(hint).toContainText('So erlaubst du sie wieder');
  await expect(hint).toContainText('Lade die Seite danach neu und schalte Push wieder ein.');
  await expect(push).not.toBeChecked();
  await expect(push).toBeDisabled();
  expect((await settings(page)).devices).toEqual([]);

  // Still explained after a reload
  await page.reload();
  await expect(section(page).getByTestId('push-denied-hint')).toBeVisible();
  await checkAccessibility(page);
});

test.describe('on an iPhone in Safari', () => {
  test.use({ userAgent: IPHONE });

  test('F-19 criterion 3: the hint says to add the app to the home screen first', async ({ page }) => {
    await login(page, 'anna');
    await page.goto('/profil/benachrichtigungen');
    const hint = section(page).getByTestId('push-iphone-hint');
    await expect(hint).toContainText('Auf dem iPhone zuerst zum Home-Bildschirm hinzufügen');
    await expect(hint).toContainText('«Zum Home-Bildschirm»');
    await expect(section(page).getByRole('switch', { name: 'Push auf diesem Gerät' })).toBeDisabled();
    // E-mail works as everywhere
    await expect(section(page).getByRole('switch', { name: 'E-Mail' })).toBeChecked();
  });
});

test('F-19 criterion 4: the service worker shows the push and a tap opens the event', async ({
  page,
  context,
}) => {
  await context.grantPermissions(['notifications']);
  await login(page, 'anna');
  await page.goto('/benachrichtigungen');
  await page.evaluate(() => navigator.serviceWorker.ready);
  await page.reload(); // now controlled by the service worker
  const worker = context.serviceWorkers()[0] ?? (await context.waitForEvent('serviceworker'));

  const shown = await worker.evaluate(async (t01) => {
    const sw = self as unknown as ServiceWorkerGlobalScope;
    const data = JSON.stringify({
      title: 'Abgesagt: Training Di 17.11.',
      body: 'Training Di 17.11. ist abgesagt: Halle gesperrt',
      url: `/termine/${t01}`,
      tag: 'n-1',
    });
    const event = new PushEvent('push', { data });
    const done: Promise<unknown>[] = [];
    (event as unknown as { waitUntil: (p: Promise<unknown>) => void }).waitUntil = (p) => done.push(p);
    sw.dispatchEvent(event);
    await Promise.all(done);
    const list = await sw.registration.getNotifications({ tag: 'n-1' });
    return list.map((n) => ({ title: n.title, body: n.body, url: n.data.url }));
  }, T01);
  expect(shown).toEqual([
    {
      title: 'Abgesagt: Training Di 17.11.',
      body: 'Training Di 17.11. ist abgesagt: Halle gesperrt',
      url: `/termine/${T01}`,
    },
  ]);

  // Tap: the open window of the app goes to the event
  await worker.evaluate(async () => {
    const sw = self as unknown as ServiceWorkerGlobalScope;
    const [notification] = await sw.registration.getNotifications({ tag: 'n-1' });
    const event = new NotificationEvent('notificationclick', { notification });
    const done: Promise<unknown>[] = [];
    (event as unknown as { waitUntil: (p: Promise<unknown>) => void }).waitUntil = (p) => done.push(p);
    sw.dispatchEvent(event);
    await Promise.all(done);
  });
  await expect(page).toHaveURL(new RegExp(`/termine/${T01}$`));
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Training');
});

test('F-19 criteria 1, 5, 8, 10: one notification in the app, by e-mail and on every device, recorded with the channel', async ({
  request,
}) => {
  await cancelT01(request);
  const cancelled = await notifications(request, 'EVENT_CANCELLED');
  expect(cancelled).toHaveLength(12);
  const anna = of(cancelled, ANNA);
  expect(anna.deliveries).toEqual([
    expect.objectContaining({
      channel: 'EMAIL',
      address: 'anna@example.ch',
      subject: 'Abgesagt: Training Di 17.11.',
      text: 'Training Di 17.11. ist abgesagt: Halle gesperrt',
      path: `/termine/${T01}`,
      status: 'SENT',
    }),
  ]);
  // Kim: e-mails off, only the app. Lea: e-mail and one push on her phone (criterion 10)
  expect(of(cancelled, KIM).deliveries).toEqual([]);
  expect(of(cancelled, LEA).deliveries.map((d) => [d.channel, d.address])).toEqual([
    ['EMAIL', 'lea@example.ch'],
    ['PUSH', LEA_ENDPOINT],
  ]);
});

test('F-19 criterion 6: the reminder (BEN-01) uses the channels too', async ({ request }) => {
  const clock = await request.put('/api/test/clock', { data: { now: '2026-11-16T06:31:00+01:00' } });
  expect(clock.status()).toBe(204);
  const reminders = await notifications(request, 'REMINDER');
  expect(reminders.flatMap((n) => n.deliveries.map((d) => d.subject))).toEqual([
    'Bitte gib Bescheid: Training Di 17.11.',
    'Bitte gib Bescheid: Training Di 17.11.',
  ]);
});

test('F-19 criterion 7: a failed e-mail does not block the cancellation and is retried', async ({ page, request }) => {
  expect((await request.put('/api/test/delivery', { data: { failing: ['EMAIL'] } })).status()).toBe(204);
  await cancelT01(request);
  let anna = of(await notifications(request, 'EVENT_CANCELLED'), ANNA).deliveries[0];
  expect([anna.status, anna.attempts]).toEqual(['PENDING', 1]);

  // In the app the cancellation is there right away
  await login(page, 'anna');
  await page.goto('/benachrichtigungen');
  await expect(page.getByText('Training Di 17.11. ist abgesagt: Halle gesperrt')).toBeVisible();

  expect((await request.put('/api/test/delivery', { data: {} })).status()).toBe(204);
  expect((await request.put('/api/test/clock', { data: { now: '2026-11-16T06:01:00+01:00' } })).status()).toBe(204);
  anna = of(await notifications(request, 'EVENT_CANCELLED'), ANNA).deliveries[0];
  expect([anna.status, anna.attempts]).toEqual(['SENT', 2]);
});

test('F-19 criterion 7: a push subscription reported as expired is removed', async ({ page, request }) => {
  expect(
    (await request.put('/api/test/delivery', { data: { expiredEndpoints: [LEA_ENDPOINT] } })).status(),
  ).toBe(204);
  await cancelT01(request);
  const push = of(await notifications(request, 'EVENT_CANCELLED'), LEA).deliveries.find((d) => d.channel === 'PUSH');
  expect(push?.status).toBe('DISCARDED');
  await login(page, 'lea');
  await page.goto('/profil/benachrichtigungen');
  await expect(section(page).getByRole('switch', { name: 'E-Mail' })).toBeChecked();
  await expect(section(page).getByTestId('push-devices')).toHaveCount(0);
});
