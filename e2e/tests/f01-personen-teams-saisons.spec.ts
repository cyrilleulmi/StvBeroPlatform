import { anmelden, barrierefrei, expect, reset, test } from './hilfen';

/**
 * F-01 · Personen, Teams, Saisons (PTF, SAI, RR-04). Akzeptanzkriterien aus docs/pipeline/backlog.md.
 * IDs aus docs/testdaten.md.
 */

const TEAM_1 = '10000000-0000-4000-8000-000000000001';
const TEAM_2 = '10000000-0000-4000-8000-000000000002';
const TEAM_3 = '10000000-0000-4000-8000-000000000003';
const PETRA = '20000000-0000-4000-8000-000000000002';
const MARC = '20000000-0000-4000-8000-000000000003';
const ANNA = '20000000-0000-4000-8000-000000000005';
const TINA = '20000000-0000-4000-8000-000000000016';
const VERA = '20000000-0000-4000-8000-000000000017';
const SAISON_2025 = '60000000-0000-4000-8000-000000000001';
const SAISON_2026 = '60000000-0000-4000-8000-000000000002';

test.beforeEach(async ({ request }) => {
  await reset(request);
});

test('F-01 K1: Seed mit Saisons, Teams, Personen und Teamzugehörigkeiten', async ({ page }) => {
  await anmelden(page, 'alex');

  await page.goto('/verwaltung/personen');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Personen');
  await expect(page.getByRole('list', { name: 'Personen' }).getByRole('listitem')).toHaveCount(17);
  await barrierefrei(page);

  await page.goto('/verwaltung/teams');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Teams verwalten');
  await expect(page.getByLabel('Saison')).toHaveValue(SAISON_2026);
  const teams = page.getByRole('list', { name: 'Teams' });
  await expect(teams.getByRole('link')).toHaveText(['Team 1', 'Team 2', 'Team 3']);
  await barrierefrei(page);

  await page.goto('/verwaltung/saisons');
  const saisons = page.getByRole('list', { name: 'Saisons' });
  await expect(saisons.getByRole('listitem', { name: '2026/27' }).getByTestId('saison-status')).toHaveText('aktuell');
  await expect(saisons.getByRole('listitem', { name: '2025/26' }).getByTestId('saison-status')).toHaveText('Archiv');
  await barrierefrei(page);
});

test('F-01 K2 PTF-11: Admin erfasst und bearbeitet eine Person', async ({ page }) => {
  await anmelden(page, 'alex');
  await page.goto('/verwaltung');
  await page.getByRole('link', { name: /^Personen/ }).click();
  await page.getByRole('link', { name: 'Person erfassen' }).click();
  await expect(page).toHaveURL('/verwaltung/personen/neu');
  await barrierefrei(page);

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

test('F-01 K3 PTF-04: Person mit Funktion ins Team aufnehmen, zweite Aufnahme abgelehnt', async ({ page }) => {
  await anmelden(page, 'alex');
  await page.goto('/verwaltung/teams');
  await page.getByRole('link', { name: 'Team 3' }).click();
  await expect(page).toHaveURL(`/verwaltung/teams/${TEAM_3}?saison=${SAISON_2026}`);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Team 3');
  await barrierefrei(page);

  await page.getByLabel('Person', { exact: true }).selectOption({ label: 'Anna Muster' });
  await page.getByLabel('Funktion', { exact: true }).selectOption({ label: 'Coach' });
  await page.getByRole('button', { name: 'Aufnehmen' }).click();

  await expect(page.getByRole('status')).toHaveText('Anna Muster ist jetzt Coach in Team 3.');
  await expect(page.getByRole('list', { name: 'Coaches' }).getByRole('listitem')).toHaveCount(2);
  await expect(page.getByRole('list', { name: 'Coaches' }).getByTestId(`mitglied-${ANNA}`)).toBeVisible();
  await expect(page.getByRole('list', { name: 'Spielerinnen' }).getByTestId(`mitglied-${ANNA}`)).toHaveCount(0);

  // Die Oberfläche bietet die zweite Aufnahme nicht an, das Backend lehnt sie ab (PTF-04).
  await expect(page.getByRole('option', { name: 'Anna Muster (schon im Team)' })).toBeDisabled();
  const zweite = await page.request.post(`/api/teams/${TEAM_3}/mitglieder`, {
    data: { personId: ANNA, funktion: 'SPIELERIN' },
  });
  expect(zweite.status()).toBe(409);
  expect((await zweite.json()).regel).toBe('PTF-04');

  // Funktion ändern und entfernen
  await page.getByLabel('Funktion von Tina Bucher').selectOption({ label: 'Coach' });
  await expect(page.getByRole('status')).toHaveText('Tina Bucher ist jetzt Coach.');
  await expect(page.getByRole('list', { name: 'Coaches' }).getByTestId(`mitglied-${TINA}`)).toBeVisible();
  await page.getByRole('button', { name: 'Tina Bucher entfernen' }).click();
  await expect(page.getByRole('status')).toHaveText('Tina Bucher ist nicht mehr im Team.');
  await expect(page.getByTestId(`mitglied-${TINA}`)).toHaveCount(0);
});

test('F-01 K4 PTF-12: Petra sieht beide Teams mit ihrer jeweiligen Funktion', async ({ page }) => {
  await anmelden(page, 'petra');
  await page.goto('/teams');

  const liste = page.getByRole('list', { name: 'Meine Teams' });
  await expect(liste.getByRole('listitem')).toHaveCount(2);
  await expect(page.getByTestId(`team-karte-${TEAM_1}`)).toHaveText(/Team 1\s*Coach/);
  await expect(page.getByTestId(`team-karte-${TEAM_2}`)).toHaveText(/Team 2\s*Spielerin/);
  await barrierefrei(page);

  await page.getByRole('link', { name: /Team 2/ }).click();
  await expect(page).toHaveURL(`/teams/${TEAM_2}`);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Team 2');
});

test('F-01 K5 PTF-07: /teams/:id zeigt Coaches und Spielerinnen getrennt', async ({ page }) => {
  await anmelden(page, 'petra');

  await page.goto(`/teams/${TEAM_1}`);
  const coaches = page.getByTestId('team-coaches');
  const spielerinnen = page.getByTestId('team-spielerinnen');
  await expect(coaches.getByRole('listitem')).toHaveText(['Petra Keller']);
  await expect(spielerinnen.getByRole('listitem')).toHaveText([
    'Kim Baumann',
    'Sara Brun',
    'Mia Frei',
    'Lea Graf',
    'Anna Muster',
    'Nina Roth',
    'Jana Wyss',
  ]);
  await expect(spielerinnen.getByTestId(`team-person-${PETRA}`)).toHaveCount(0);
  await barrierefrei(page);

  await page.goto(`/teams/${TEAM_2}`);
  await expect(page.getByTestId('team-coaches').getByRole('listitem')).toHaveText(['Marc Huber']);
  await expect(page.getByTestId('team-spielerinnen').getByTestId(`team-person-${PETRA}`)).toBeVisible();
  await expect(page.getByTestId('team-spielerinnen').getByTestId(`team-person-${MARC}`)).toHaveCount(0);
  await expect(page.getByTestId('team-coaches').getByTestId(`team-person-${PETRA}`)).toHaveCount(0);
});

test('F-01 K6 SAI-04 SAI-06: neue Saison mit Übernahme anlegen und zur aktuellen machen', async ({ page }) => {
  await anmelden(page, 'alex');
  await page.goto('/verwaltung/saisons');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Saisons');

  // Vorschlag: die Saison nach der neuesten, Übernahme aus der aktuellen
  await expect(page.getByLabel('Name')).toHaveValue('2027/28');
  await expect(page.getByLabel('Beginn')).toHaveValue('2027-07-01');
  await expect(page.getByLabel('Ende')).toHaveValue('2028-06-30');
  await expect(page.getByLabel('Teams und Zugehörigkeiten übernehmen aus')).toHaveValue(SAISON_2026);
  await page.getByRole('button', { name: 'Saison anlegen' }).click();

  await expect(page.getByRole('status')).toHaveText('Saison 2027/28 ist angelegt. Du kannst sie jetzt anpassen.');
  const neu = page.getByRole('listitem', { name: '2027/28' });
  await expect(neu.getByTestId('saison-status')).toHaveText('geplant');
  await barrierefrei(page);

  // Übernommen: Teams und Zugehörigkeiten
  await neu.getByRole('link', { name: 'Teams ansehen' }).click();
  await expect(page.getByRole('list', { name: 'Teams' }).getByRole('link')).toHaveText(['Team 1', 'Team 2', 'Team 3']);
  await page.getByRole('link', { name: 'Team 1' }).click();
  await expect(page.getByTestId('team-coaches').getByRole('listitem')).toHaveCount(1);
  await expect(page.getByTestId('team-spielerinnen').getByRole('listitem')).toHaveCount(7);

  await page.goto('/verwaltung/saisons');
  await page.getByRole('listitem', { name: '2027/28' }).getByRole('button', { name: 'Zur aktuellen Saison machen' }).click();
  await expect(page.getByRole('status')).toHaveText('2027/28 ist jetzt die aktuelle Saison.');
  await expect(page.getByRole('listitem', { name: '2027/28' }).getByTestId('saison-status')).toHaveText('aktuell');
  await expect(page.getByRole('listitem', { name: '2026/27' }).getByTestId('saison-status')).toHaveText('Archiv');

  // Petra sieht ihre Teams in der neuen Saison
  await anmelden(page, 'petra');
  await page.goto('/teams');
  await expect(page.getByTestId(`team-karte-${TEAM_1}`)).toHaveText(/Coach/);
  await expect(page.getByTestId(`team-karte-${TEAM_2}`)).toHaveText(/Spielerin/);
});

test('F-01 K7 S16 SAI-02: Archiv ist lesend sichtbar', async ({ page }) => {
  await anmelden(page, 'alex');
  await page.goto('/verwaltung/saisons');
  await page.getByRole('listitem', { name: '2025/26' }).getByRole('link', { name: 'Teams ansehen' }).click();

  await expect(page).toHaveURL(`/verwaltung/teams?saison=${SAISON_2025}`);
  await expect(page.getByTestId('archiv-hinweis')).toHaveText('Saison 2025/26 im Archiv: nur lesend.');
  await expect(page.getByRole('button', { name: 'Team anlegen' })).toHaveCount(0);
  await page.getByRole('link', { name: 'Team 1' }).click();

  await expect(page).toHaveURL(`/verwaltung/teams/${TEAM_1}?saison=${SAISON_2025}`);
  await expect(page.getByTestId('archiv-hinweis')).toBeVisible();
  await expect(page.getByTestId('team-spielerinnen').getByTestId(`mitglied-${VERA}`)).toHaveText(/Vera Studer\s*Spielerin/);
  await expect(page.getByRole('button')).toHaveCount(0);
  await expect(page.getByRole('combobox')).toHaveCount(0);
  await barrierefrei(page);

  const aendern = await page.request.post(`/api/teams/${TEAM_1}/mitglieder?saison=${SAISON_2025}`, {
    data: { personId: ANNA, funktion: 'SPIELERIN' },
  });
  expect(aendern.status()).toBe(409);
});

test('F-01 K8 RR-04 RR-07: Verwaltung ist für Nicht-Admins gesperrt', async ({ page }) => {
  await anmelden(page, 'petra');

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
  await barrierefrei(page);

  const anfragen = [
    page.request.get('/api/personen'),
    page.request.post('/api/personen', { data: { vorname: 'X', nachname: 'Y' } }),
    page.request.post('/api/teams', { data: { name: 'Team 9' } }),
    page.request.post(`/api/teams/${TEAM_1}/mitglieder`, { data: { personId: TINA, funktion: 'SPIELERIN' } }),
    page.request.post(`/api/saisons/${SAISON_2025}/aktivieren`),
  ];
  for (const antwort of await Promise.all(anfragen)) {
    expect(antwort.status()).toBe(403);
  }
});

test('F-01 RR-02: Fremdes Team zeigt «Keine Berechtigung»', async ({ page }) => {
  await anmelden(page, 'anna');
  await page.goto(`/teams/${TEAM_2}`);
  await expect(page.getByRole('alert')).toHaveText('Keine Berechtigung');
  expect((await page.request.get(`/api/teams/${TEAM_2}`)).status()).toBe(403);
});
