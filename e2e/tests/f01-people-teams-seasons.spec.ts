import { checkAccessibility, expect, login, reset, test } from './helpers';

/**
 * F-01 · People, teams, seasons (PTF, SAI, RR-04). Acceptance criteria from docs/pipeline/backlog.md.
 * IDs from docs/test-data.md.
 */

const TEAM_1 = '10000000-0000-4000-8000-000000000001';
const TEAM_2 = '10000000-0000-4000-8000-000000000002';
const TEAM_3 = '10000000-0000-4000-8000-000000000003';
const PETRA = '20000000-0000-4000-8000-000000000002';
const MARC = '20000000-0000-4000-8000-000000000003';
const ANNA = '20000000-0000-4000-8000-000000000005';
const TINA = '20000000-0000-4000-8000-000000000016';
const VERA = '20000000-0000-4000-8000-000000000017';
const SEASON_2025 = '60000000-0000-4000-8000-000000000001';
const SEASON_2026 = '60000000-0000-4000-8000-000000000002';

test.beforeEach(async ({ request }) => {
  await reset(request);
});

test('F-01 K1: Seed with seasons, teams, people and team memberships', async ({ page }) => {
  await login(page, 'alex');

  await page.goto('/verwaltung/personen');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Personen');
  await expect(page.getByRole('list', { name: 'Personen' }).getByRole('listitem')).toHaveCount(17);
  await checkAccessibility(page);

  await page.goto('/verwaltung/teams');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Teams verwalten');
  await expect(page.getByLabel('Saison')).toHaveValue(SEASON_2026);
  const teams = page.getByRole('list', { name: 'Teams' });
  await expect(teams.getByRole('link')).toHaveText(['Team 1', 'Team 2', 'Team 3']);
  await checkAccessibility(page);

  await page.goto('/verwaltung/saisons');
  const seasons = page.getByRole('list', { name: 'Saisons' });
  await expect(seasons.getByRole('listitem', { name: '2026/27' }).getByTestId('season-status')).toHaveText('aktuell');
  await expect(seasons.getByRole('listitem', { name: '2025/26' }).getByTestId('season-status')).toHaveText('Archiv');
  await checkAccessibility(page);
});

test('F-01 K2 PTF-11: Admin creates and edits a person', async ({ page }) => {
  await login(page, 'alex');
  await page.goto('/verwaltung');
  await page.getByRole('link', { name: /^Personen/ }).click();
  await page.getByRole('link', { name: 'Person erfassen' }).click();
  await expect(page).toHaveURL('/verwaltung/personen/neu');
  await checkAccessibility(page);

  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.getByLabel('Vorname')).toHaveAttribute('aria-invalid', 'true');
  await expect(page.getByLabel('Vorname')).toHaveAccessibleDescription('Bitte einen Vornamen eingeben.');

  await page.getByLabel('Vorname').fill('Nora');
  await page.getByLabel('Nachname').fill('Lang');
  await page.getByLabel('E-Mail (freiwillig)').fill('nora@example.ch');
  await page.getByRole('button', { name: 'Speichern' }).click();

  await expect(page.getByRole('status')).toHaveText('Nora Lang ist erfasst.');
  await expect(page).toHaveURL(/\/verwaltung\/personen\/[0-9a-f-]{36}$/);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Person bearbeiten');
  await expect(page.getByText('In keinem Team.')).toBeVisible();

  await page.getByLabel('Nachname').fill('Lang-Meier');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.getByRole('status')).toHaveText('Änderungen gespeichert.');

  await page.getByRole('link', { name: 'Alle Personen' }).click();
  await page.getByLabel('Suchen').fill('lang');
  await expect(page).toHaveURL('/verwaltung/personen?suche=lang');
  await expect(page.getByRole('list', { name: 'Personen' }).getByRole('link')).toHaveText(['Nora Lang-Meier']);
});

test('F-01 K3 PTF-04: Add a person with a team role to the team, second addition rejected', async ({ page }) => {
  await login(page, 'alex');
  await page.goto('/verwaltung/teams');
  await page.getByRole('link', { name: 'Team 3' }).click();
  await expect(page).toHaveURL(`/verwaltung/teams/${TEAM_3}?saison=${SEASON_2026}`);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Team 3');
  await checkAccessibility(page);

  await page.getByLabel('Person', { exact: true }).selectOption({ label: 'Anna Muster' });
  await page.getByLabel('Funktion', { exact: true }).selectOption({ label: 'Coach' });
  await page.getByRole('button', { name: 'Aufnehmen' }).click();

  await expect(page.getByRole('status')).toHaveText('Anna Muster ist jetzt Coach in Team 3.');
  await expect(page.getByRole('list', { name: 'Coaches' }).getByRole('listitem')).toHaveCount(2);
  await expect(page.getByRole('list', { name: 'Coaches' }).getByTestId(`member-${ANNA}`)).toBeVisible();
  await expect(page.getByRole('list', { name: 'Spielerinnen' }).getByTestId(`member-${ANNA}`)).toHaveCount(0);

  // The UI does not offer the second addition, the backend rejects it (PTF-04).
  await expect(page.getByRole('option', { name: 'Anna Muster (schon im Team)' })).toBeDisabled();
  const second = await page.request.post(`/api/teams/${TEAM_3}/members`, {
    data: { personId: ANNA, teamRole: 'PLAYER' },
  });
  expect(second.status()).toBe(409);
  expect((await second.json()).rule).toBe('PTF-04');

  // Change team role and remove
  await page.getByLabel('Funktion von Tina Bucher').selectOption({ label: 'Coach' });
  await expect(page.getByRole('status')).toHaveText('Tina Bucher ist jetzt Coach.');
  await expect(page.getByRole('list', { name: 'Coaches' }).getByTestId(`member-${TINA}`)).toBeVisible();
  await page.getByRole('button', { name: 'Tina Bucher entfernen' }).click();
  await expect(page.getByRole('status')).toHaveText('Tina Bucher ist nicht mehr im Team.');
  await expect(page.getByTestId(`member-${TINA}`)).toHaveCount(0);
});

test('F-01 K4 PTF-12: Petra sees both teams with her respective team role', async ({ page }) => {
  await login(page, 'petra');
  await page.goto('/teams');

  const list = page.getByRole('list', { name: 'Meine Teams' });
  await expect(list.getByRole('listitem')).toHaveCount(2);
  await expect(page.getByTestId(`team-card-${TEAM_1}`)).toHaveText(/Team 1\s*Coach/);
  await expect(page.getByTestId(`team-card-${TEAM_2}`)).toHaveText(/Team 2\s*Spielerin/);
  await checkAccessibility(page);

  await page.getByRole('link', { name: /Team 2/ }).click();
  await expect(page).toHaveURL(`/teams/${TEAM_2}`);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Team 2');
});

test('F-01 K5 PTF-07: /teams/:id shows coaches and players separately', async ({ page }) => {
  await login(page, 'petra');

  await page.goto(`/teams/${TEAM_1}`);
  const coaches = page.getByTestId('team-coaches');
  const players = page.getByTestId('team-players');
  await expect(coaches.getByRole('listitem')).toHaveText(['Petra Keller']);
  // Since F-02 by line: Linie 1, Linie 2, Goalies, Ohne Linie; within by last name (LIN-09)
  await expect(players.getByRole('listitem')).toHaveText([
    'Mia Frei',
    'Lea Graf',
    'Anna Muster',
    'Sara Brun',
    'Nina Roth',
    'Jana Wyss',
    'Kim Baumann',
  ]);
  await expect(players.getByTestId(`team-person-${PETRA}`)).toHaveCount(0);
  await checkAccessibility(page);

  await page.goto(`/teams/${TEAM_2}`);
  await expect(page.getByTestId('team-coaches').getByRole('listitem')).toHaveText(['Marc Huber']);
  await expect(page.getByTestId('team-players').getByTestId(`team-person-${PETRA}`)).toBeVisible();
  await expect(page.getByTestId('team-players').getByTestId(`team-person-${MARC}`)).toHaveCount(0);
  await expect(page.getByTestId('team-coaches').getByTestId(`team-person-${PETRA}`)).toHaveCount(0);
});

test('F-01 K6 SAI-04 SAI-06: create a new season with carry-over and make it the current one', async ({ page }) => {
  await login(page, 'alex');
  await page.goto('/verwaltung/saisons');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Saisons');

  // Suggestion: the season after the newest one, carry-over from the current one
  await expect(page.getByLabel('Name')).toHaveValue('2027/28');
  await expect(page.getByLabel('Beginn')).toHaveValue('2027-07-01');
  await expect(page.getByLabel('Ende')).toHaveValue('2028-06-30');
  await expect(page.getByLabel('Teams und Zugehörigkeiten übernehmen aus')).toHaveValue(SEASON_2026);
  await page.getByRole('button', { name: 'Saison anlegen' }).click();

  await expect(page.getByRole('status')).toHaveText('Saison 2027/28 ist angelegt. Du kannst sie jetzt anpassen.');
  const created = page.getByRole('listitem', { name: '2027/28' });
  await expect(created.getByTestId('season-status')).toHaveText('geplant');
  await checkAccessibility(page);

  // Carried over: teams and memberships
  await created.getByRole('link', { name: 'Teams ansehen' }).click();
  await expect(page.getByRole('list', { name: 'Teams' }).getByRole('link')).toHaveText(['Team 1', 'Team 2', 'Team 3']);
  await page.getByRole('link', { name: 'Team 1' }).click();
  await expect(page.getByTestId('team-coaches').getByRole('listitem')).toHaveCount(1);
  await expect(page.getByTestId('team-players').getByRole('listitem')).toHaveCount(7);

  await page.goto('/verwaltung/saisons');
  await page.getByRole('listitem', { name: '2027/28' }).getByRole('button', { name: 'Zur aktuellen Saison machen' }).click();
  await expect(page.getByRole('status')).toHaveText('2027/28 ist jetzt die aktuelle Saison.');
  await expect(page.getByRole('listitem', { name: '2027/28' }).getByTestId('season-status')).toHaveText('aktuell');
  await expect(page.getByRole('listitem', { name: '2026/27' }).getByTestId('season-status')).toHaveText('Archiv');

  // Petra sees her teams in the new season
  await login(page, 'petra');
  await page.goto('/teams');
  await expect(page.getByTestId(`team-card-${TEAM_1}`)).toHaveText(/Coach/);
  await expect(page.getByTestId(`team-card-${TEAM_2}`)).toHaveText(/Spielerin/);
});

test('F-01 K7 S16 SAI-02: Archive is visible read-only', async ({ page }) => {
  await login(page, 'alex');
  await page.goto('/verwaltung/saisons');
  await page.getByRole('listitem', { name: '2025/26' }).getByRole('link', { name: 'Teams ansehen' }).click();

  await expect(page).toHaveURL(`/verwaltung/teams?saison=${SEASON_2025}`);
  await expect(page.getByTestId('archive-notice')).toHaveText('Saison 2025/26 im Archiv: nur lesend.');
  await expect(page.getByRole('button', { name: 'Team anlegen' })).toHaveCount(0);
  await page.getByRole('link', { name: 'Team 1' }).click();

  await expect(page).toHaveURL(`/verwaltung/teams/${TEAM_1}?saison=${SEASON_2025}`);
  await expect(page.getByTestId('archive-notice')).toBeVisible();
  await expect(page.getByTestId('team-players').getByTestId(`member-${VERA}`)).toHaveText(/Vera Studer\s*Spielerin/);
  await expect(page.getByRole('button')).toHaveCount(0);
  await expect(page.getByRole('combobox')).toHaveCount(0);
  await checkAccessibility(page);

  const change = await page.request.post(`/api/teams/${TEAM_1}/members?season=${SEASON_2025}`, {
    data: { personId: ANNA, teamRole: 'PLAYER' },
  });
  expect(change.status()).toBe(409);
});

test('F-01 K8 RR-04 RR-07: Administration is locked for non-admins', async ({ page }) => {
  await login(page, 'petra');

  for (const route of [
    '/verwaltung',
    '/verwaltung/personen',
    '/verwaltung/personen/neu',
    `/verwaltung/personen/${ANNA}`,
    '/verwaltung/teams',
    `/verwaltung/teams/${TEAM_1}`,
    '/verwaltung/saisons',
  ]) {
    await page.goto(route);
    await expect(page.getByRole('alert')).toHaveText('Keine Berechtigung');
  }
  await expect(page.getByRole('navigation', { name: 'Hauptnavigation' }).getByRole('link', { name: 'Verwaltung' })).toHaveCount(0);
  await checkAccessibility(page);

  const requests = [
    page.request.get('/api/persons'),
    page.request.post('/api/persons', { data: { firstName: 'X', lastName: 'Y' } }),
    page.request.post('/api/teams', { data: { name: 'Team 9' } }),
    page.request.post(`/api/teams/${TEAM_1}/members`, { data: { personId: TINA, teamRole: 'PLAYER' } }),
    page.request.post(`/api/seasons/${SEASON_2025}/activate`),
  ];
  for (const response of await Promise.all(requests)) {
    expect(response.status()).toBe(403);
  }
});

test('F-01 RR-02: Another team shows «Keine Berechtigung»', async ({ page }) => {
  await login(page, 'anna');
  await page.goto(`/teams/${TEAM_2}`);
  await expect(page.getByRole('alert')).toHaveText('Keine Berechtigung');
  expect((await page.request.get(`/api/teams/${TEAM_2}`)).status()).toBe(403);
});

/** Counts the POST requests to an API path (a double click may trigger only one). */
function countPosts(page: import('@playwright/test').Page, path: RegExp): { count: number } {
  const counter = { count: 0 };
  page.on('request', (r) => {
    if (r.method() === 'POST' && path.test(new URL(r.url()).pathname)) counter.count++;
  });
  return counter;
}

test('F-01 K2 PTF-11: Double click on Speichern creates the person only once', async ({ page }) => {
  await login(page, 'alex');
  await page.goto('/verwaltung/personen/neu');
  const posts = countPosts(page, /^\/api\/persons$/);
  await page.getByLabel('Vorname').fill('Flurina');
  await page.getByLabel('Nachname').fill('Testerin');
  await page.getByRole('button', { name: 'Speichern' }).dblclick();

  await expect(page.getByRole('status')).toHaveText('Flurina Testerin ist erfasst.');
  await page.goto('/verwaltung/personen?suche=Flurina');
  await expect(page.getByRole('list', { name: 'Personen' }).getByRole('link')).toHaveText(['Flurina Testerin']);
  expect(posts.count).toBe(1);
});

test('F-01 K3: Double click on Team anlegen creates the team only once', async ({ page }) => {
  await login(page, 'alex');
  await page.goto('/verwaltung/teams');
  await expect(page.getByRole('list', { name: 'Teams' }).getByRole('link')).toHaveCount(3);
  const posts = countPosts(page, /^\/api\/teams$/);
  await page.getByLabel('Name des neuen Teams').fill('Team 4');
  await page.getByRole('button', { name: 'Team anlegen' }).dblclick();

  await expect(page.getByRole('status')).toHaveText(/Team 4/);
  await expect(page.getByRole('list', { name: 'Teams' }).getByRole('link')).toHaveText([
    'Team 1',
    'Team 2',
    'Team 3',
    'Team 4',
  ]);
  expect(posts.count).toBe(1);
});

test('F-01 K3 PTF-04: Double click on Aufnehmen adds the person once, without errors', async ({ page }) => {
  await login(page, 'alex');
  await page.goto(`/verwaltung/teams/${TEAM_1}`);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Team 1');
  const posts = countPosts(page, /\/members$/);
  await page.getByLabel('Person', { exact: true }).selectOption({ label: 'Ruth Meier' });
  await page.getByLabel('Funktion', { exact: true }).selectOption({ label: 'Coach' });
  await page.getByRole('button', { name: 'Aufnehmen' }).dblclick();

  await expect(page.getByRole('status')).toHaveText('Ruth Meier ist jetzt Coach in Team 1.');
  await expect(page.getByRole('alert')).toHaveCount(0);
  await expect(page.getByRole('list', { name: 'Coaches' }).getByRole('listitem', { name: 'Ruth Meier' })).toHaveCount(1);
  expect(posts.count).toBe(1);
});

test('F-01 K6 SAI-01: Double click on Saison anlegen creates the season once, without errors', async ({ page }) => {
  await login(page, 'alex');
  await page.goto('/verwaltung/saisons');
  await expect(page.getByLabel('Name')).toHaveValue('2027/28');
  const posts = countPosts(page, /^\/api\/seasons$/);
  await page.getByRole('button', { name: 'Saison anlegen' }).dblclick();

  await expect(page.getByRole('status')).toHaveText('Saison 2027/28 ist angelegt. Du kannst sie jetzt anpassen.');
  await expect(page.getByRole('alert')).toHaveCount(0);
  await expect(page.getByRole('listitem', { name: '2027/28' })).toHaveCount(1);
  expect(posts.count).toBe(1);
});

test('F-01 K7: Team without coach shows «noch keinen Coach»', async ({ page }) => {
  await login(page, 'alex');
  await page.goto(`/verwaltung/teams/${TEAM_1}?saison=${SEASON_2025}`);
  await expect(page.getByText('Dieses Team hat noch keinen Coach.')).toBeVisible();
});

test('F-01 K2 PTF-11: Long name without spaces wraps in the list', async ({ page }) => {
  await login(page, 'alex');
  const longName = ('Maximiliane'.repeat(6)).slice(0, 60); // maximum, without spaces
  const response = await page.request.post('/api/persons', {
    data: { firstName: longName, lastName: 'Lang', email: null, admin: false },
  });
  expect(response.status()).toBe(201);
  await page.goto('/verwaltung/personen');
  await expect(page.getByRole('list', { name: 'Personen' }).getByText(/^Maximiliane/)).toBeVisible();
  const width = await page.evaluate(() => ({
    scroll: document.documentElement.scrollWidth,
    window: document.documentElement.clientWidth,
  }));
  expect(width.scroll).toBeLessThanOrEqual(width.window);
});
