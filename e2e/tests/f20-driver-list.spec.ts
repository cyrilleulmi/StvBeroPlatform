import type { APIRequestContext, Locator, Page } from '@playwright/test';
import { checkAccessibility, expect, login, reset, test } from './helpers';

/**
 * F-20 · Driver list for match days (DRV-01 to DRV-13, issue #59). Seed from docs/test-data.md: match day 04 with
 * Petra's full car (Anna, Lea, Mia), Sara's empty car (3 seats) and Jana «dabei» without a seat; the routing
 * simulator knows «Parkplatz Sporthalle Nord» → «Sporthalle Seefeld» (23.4 km, 26 min).
 * Default "now": Mon 16.11.2026 06:00.
 */

const TEAM_1 = '10000000-0000-4000-8000-000000000001';
const T01 = '40000000-0000-4000-8000-000000000001';
const T04 = '40000000-0000-4000-8000-000000000004';
const SARA = '20000000-0000-4000-8000-000000000008';
const CAR_PETRA = 'b0000000-0000-4000-8000-000000000001';
const CAR_SARA = 'b0000000-0000-4000-8000-000000000002';
const MAPS_SEEFELD = 'https://www.google.com/maps/search/?api=1&query=Sporthalle+Seefeld+Musterdorf';

function driverList(page: Page): Locator {
  return page.getByTestId('driver-list');
}

function car(page: Page, driver: string): Locator {
  return driverList(page).getByRole('listitem', { name: `Auto von ${driver}`, exact: true });
}

async function routing(request: APIRequestContext, data: object): Promise<void> {
  const response = await request.put('/api/test/routing', { data });
  expect(response.status()).toBe(204);
}

/** Changes meeting point and venue of match day 04 as Petra (same date, the matches stay). */
async function changeEvent04(page: Page, meetingPoint: string): Promise<void> {
  const response = await page.request.put(`/api/events/${T04}`, {
    data: {
      type: 'MATCH_DAY',
      title: 'Spieltag',
      start: '2026-11-22T09:30:00+01:00',
      end: '2026-11-22T13:00:00+01:00',
      teamIds: [TEAM_1],
      location: { name: 'Sporthalle Seefeld', address: 'Musterdorf', mapsUrl: MAPS_SEEFELD },
      notes: 'Weisse Trikots mitnehmen',
      meetingPoint: { time: '2026-11-22T08:00:00+01:00', location: meetingPoint },
    },
  });
  expect(response.status()).toBe(200);
}

test.beforeEach(async ({ request }) => {
  await reset(request);
});

test('DRV-13: seed match day 04 has two cars, one full, and Jana without a seat', async ({ page }) => {
  await login(page, 'anna');
  await page.goto(`/termine/${T04}`);
  const list = driverList(page);
  await expect(list.getByRole('heading', { name: 'Fahrerliste' })).toBeVisible();
  await expect(list.getByRole('list', { name: 'Autos' }).getByRole('listitem', { name: /^Auto von / })).toHaveCount(2);
  await expect(car(page, 'Petra Keller').getByTestId('car-seats')).toHaveText('voll');
  await expect(page.getByTestId(`car-${CAR_PETRA}`)).toHaveAttribute('data-free-seats', '0');
  await expect(car(page, 'Petra Keller').getByRole('list', { name: 'Mitfahrende bei Petra Keller' }).getByRole('listitem')).toHaveText([
    'Mia Frei',
    'Lea Graf',
    /Anna Muster\s+\(du\)/,
  ]);
  await expect(car(page, 'Sara Brun').getByTestId('car-seats')).toHaveText('3 von 3 Plätzen frei');
  await expect(list.getByRole('list', { name: 'Ohne Platz' }).getByRole('listitem')).toHaveText(['Jana Wyss']);
  await checkAccessibility(page);
});

test('DRV-01: the list needs meeting point and venue and exists only for match days', async ({ page }) => {
  await login(page, 'petra');
  const response = await page.request.post('/api/events', {
    data: {
      type: 'MATCH_DAY',
      title: 'Spieltag',
      start: '2026-12-06T10:00:00+01:00',
      end: '2026-12-06T14:00:00+01:00',
      teamIds: [TEAM_1],
      location: { name: 'Sporthalle Seefeld' },
    },
  });
  expect(response.status()).toBe(201);
  const id = (await response.json()).id;
  await page.goto(`/termine/${id}`);
  await expect(page.getByTestId('driver-list-missing')).toHaveText(
    'Die Fahrerliste ist bereit, sobald Treffpunkt und Spielort erfasst sind. Es fehlt: Treffpunkt.',
  );
  await expect(driverList(page).getByRole('button', { name: 'Ich fahre', exact: true })).toHaveCount(0);
  await expect(driverList(page)).toContainText('Ergänze die Angaben unter «Termin bearbeiten».');

  await page.goto(`/termine/${T01}`);
  await expect(page.getByRole('heading', { name: 'Training' })).toBeVisible();
  await expect(page.getByTestId('driver-list')).toHaveCount(0);
});

test('DRV-04: with a published call-up the called-up players without a seat are listed', async ({ page }) => {
  await login(page, 'petra');
  expect((await page.request.post(`/api/events/${T04}/call-up/publish`)).status()).toBe(200);
  await page.goto(`/termine/${T04}`);
  await expect(driverList(page)).toContainText('Aufgebotene Spielerinnen ohne Platz.');
  await expect(driverList(page).getByRole('list', { name: 'Ohne Platz' }).getByRole('listitem')).toHaveText([/Jana Wyss/]);
});

test('DRV-05: the route is shown with distance, driving time and the credit for the return trip', async ({ page }) => {
  await login(page, 'sara');
  await page.goto(`/termine/${T04}`);
  const route = page.getByTestId('driver-route');
  await expect(route).toHaveAttribute('data-route-status', 'CALCULATED');
  await expect(route).toContainText('Parkplatz Sporthalle Nord → Sporthalle Seefeld');
  await expect(page.getByTestId('driver-route-distance')).toHaveText('23.4 km · etwa 26 Min. Fahrzeit');
  await expect(page.getByTestId('driver-route-credit')).toHaveText('Gutschrift pro Auto: 46.8 km (hin und zurück)');
  await expect(car(page, 'Sara Brun')).toContainText('Gutschrift 46.8 km');
  // Players cannot enter kilometres
  await expect(route.getByRole('button', { name: 'Kilometer erfassen' })).toHaveCount(0);
});

test('DRV-06, DRV-07: without routing the list works, shows a hint and coaches enter kilometres by hand', async ({
  page,
  request,
}) => {
  await login(page, 'petra');
  await routing(request, { unavailable: true });
  await changeEvent04(page, 'Bahnhof Beromünster');
  await page.goto(`/termine/${T04}`);
  const route = page.getByTestId('driver-route');
  await expect(route).toHaveAttribute('data-route-status', 'UNAVAILABLE');
  await expect(route).toContainText('Die Strecke lässt sich gerade nicht berechnen');
  await expect(car(page, 'Sara Brun')).not.toContainText('Gutschrift');

  await route.getByRole('button', { name: 'Kilometer erfassen' }).click();
  const dialog = page.getByRole('dialog', { name: 'Kilometer erfassen' });
  await dialog.getByLabel('Kilometer (einfache Strecke)').fill('abc');
  await dialog.getByRole('button', { name: 'Kilometer speichern' }).click();
  await expect(dialog.getByLabel('Kilometer (einfache Strecke)')).toHaveAttribute('aria-invalid', 'true');
  await expect(dialog).toContainText('Bitte eine Zahl von 0.1 bis 1000 eingeben.');
  await dialog.getByLabel('Kilometer (einfache Strecke)').fill('21,5');
  await dialog.getByRole('button', { name: 'Kilometer speichern' }).click();
  await expect(driverList(page).getByRole('status')).toHaveText('Die Kilometer sind gespeichert.');
  await expect(route).toHaveAttribute('data-route-status', 'MANUAL');
  await expect(page.getByTestId('driver-route-distance')).toHaveText(/^21\.5 km\s*von Hand erfasst$/);
  await expect(page.getByTestId('driver-route-credit')).toHaveText('Gutschrift pro Auto: 43.0 km (hin und zurück)');

  // DRV-06: the simulator is controlled through the test endpoint
  await routing(request, {
    routes: [{ from: 'Bahnhof Beromünster', to: 'Sporthalle Seefeld', distanceKm: 19.8, durationMinutes: 22 }],
  });
  await route.getByRole('button', { name: 'Strecke neu berechnen' }).click();
  await expect(driverList(page).getByRole('status')).toHaveText('Die Strecke ist neu berechnet.');
  await expect(page.getByTestId('driver-route-distance')).toHaveText('19.8 km · etwa 22 Min. Fahrzeit');
});

test('DRV-08: changing the meeting point recalculates route and credits', async ({ page, request }) => {
  await login(page, 'petra');
  await page.goto(`/termine/${T04}`);
  await expect(page.getByTestId('driver-route-distance')).toHaveText('23.4 km · etwa 26 Min. Fahrzeit');
  await routing(request, {
    routes: [{ from: 'Bahnhof Beromünster', to: 'Sporthalle Seefeld', distanceKm: 19.8, durationMinutes: 22 }],
  });
  await changeEvent04(page, 'Bahnhof Beromünster');
  await page.reload();
  await expect(page.getByTestId('driver-route')).toContainText('Bahnhof Beromünster → Sporthalle Seefeld');
  await expect(page.getByTestId('driver-route-credit')).toHaveText('Gutschrift pro Auto: 39.6 km (hin und zurück)');
  await expect(car(page, 'Petra Keller')).toContainText('Gutschrift 39.6 km');
});

test('DRV-09: the season summary shows everyone to coaches and only themselves to members', async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/termine/${T04}`);
  await page.getByTestId('driver-summary-link').click();
  await expect(page).toHaveURL(/\/fahrten$/);
  await expect(page.getByRole('heading', { level: 1, name: 'Fahrten' })).toBeVisible();
  await expect(page.getByTestId('trips-season')).toHaveText('Saison 2026/27');
  const table = page.getByRole('table', { name: 'Fahrten pro Person' });
  await expect(table.getByRole('row')).toHaveCount(3);
  await expect(table.getByRole('row').nth(1)).toHaveText(/Sara Brun\s*1\s*46\.8 km/);
  await expect(table.getByRole('row').nth(2)).toHaveText(/Petra Keller\s*1\s*46\.8 km/);
  await checkAccessibility(page);

  await login(page, 'anna');
  await page.goto('/profil');
  await page.getByTestId('my-trips-link').click();
  await expect(page.getByRole('heading', { level: 2, name: 'Deine Fahrten' })).toBeVisible();
  await expect(page.getByRole('table')).toHaveCount(0);
  await expect(page.getByTestId('trips').getByRole('definition').first()).toHaveText('0');
  await expect(page.getByTestId('trips')).toContainText('0.0 km');
  await checkAccessibility(page);
});

test('DRV-10: a cancelled match day keeps its list but credits no kilometres', async ({ page }) => {
  await login(page, 'petra');
  const response = await page.request.post(`/api/events/${T04}/cancel`, { data: { reason: 'Halle gesperrt' } });
  expect(response.status()).toBe(200);
  await page.goto(`/termine/${T04}`);
  await expect(page.getByTestId('driver-list-cancelled')).toBeVisible();
  await expect(car(page, 'Petra Keller')).toBeVisible();
  await expect(car(page, 'Petra Keller')).not.toContainText('Gutschrift');
  await expect(page.getByTestId('driver-route-credit')).toHaveCount(0);
  await expect(driverList(page).getByRole('button', { name: 'Eintragen' })).toHaveCount(0);
  await page.goto('/fahrten');
  await expect(page.getByTestId('trips')).toContainText('In dieser Saison ist noch niemand gefahren.');
});

test('DRV-11: the driver is notified in the app when someone rides along', async ({ page }) => {
  await login(page, 'jana');
  await page.goto(`/teams/${TEAM_1}/fahrerliste`);
  const board = page.getByTestId(`seat-board-${T04}`);
  await board.getByTestId(`seat-enter-${SARA}`).click();
  await expect(board.getByTestId(`seat-car-${SARA}`)).toContainText('Jana');

  await login(page, 'sara');
  await page.goto('/benachrichtigungen');
  await expect(page.getByRole('list', { name: 'Benachrichtigungen' })).toContainText(
    'Jana Wyss fährt am Spieltag So 22.11. bei dir mit.',
  );
});

