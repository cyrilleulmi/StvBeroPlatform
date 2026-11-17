import { checkAccessibility, expect, login, reset, test } from './helpers';

/**
 * F-12: coaches manage all teams, deleting stays with admins (RR-03, RR-04, RR-06, RR-08, E-69).
 * Seed (docs/test-data.md): petra coach Team 1 + player Team 2, marc coach Team 2, anna player Team 1,
 * lea player Team 1 + 2, alex admin.
 */

const TEAM_1 = '10000000-0000-4000-8000-000000000001';
const TEAM_2 = '10000000-0000-4000-8000-000000000002';
const ANNA = '20000000-0000-4000-8000-000000000005';
const MIA = '20000000-0000-4000-8000-000000000007';
const NINA = '20000000-0000-4000-8000-000000000009';
const T1_LINE_1 = '30000000-0000-4000-8000-000000000001';
const T1_LINE_2 = '30000000-0000-4000-8000-000000000002';
const T01 = '40000000-0000-4000-8000-000000000001';
const T02 = '40000000-0000-4000-8000-000000000002';
const SEASON_2025 = '60000000-0000-4000-8000-000000000001';

test.beforeEach(async ({ request }) => {
  await reset(request);
});

test('F-12 K1 RR-03: Marc (coach of Team 2) edits an event of Team 1', async ({ page }) => {
  await login(page, 'marc');
  await page.goto('/termine');
  await page.getByLabel('Team', { exact: true }).selectOption({ label: 'Team 1' });
  await expect(page).toHaveURL(`/termine?team=${TEAM_1}`);
  await page.getByTestId(`event-card-${T02}`).getByRole('link').first().click();
  await expect(page).toHaveURL(`/termine/${T02}`);
  await page.getByRole('link', { name: 'Termin bearbeiten' }).click();
  await expect(page).toHaveURL(`/termine/${T02}/bearbeiten`);
  await page.getByLabel('Titel').fill('Techniktraining');
  await page.getByRole('button', { name: 'Änderungen speichern' }).click();
  await expect(page).toHaveURL(`/termine/${T02}`);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Techniktraining');
  await checkAccessibility(page);
});

test('F-12 K1 RR-03: Marc assigns a player of Team 1 to a line', async ({ page }) => {
  await login(page, 'marc');
  await page.goto('/verwaltung');
  await page.getByRole('link', { name: /^Teams verwalten/ }).click();
  await page.getByRole('link', { name: 'Team 1' }).click();
  await page.getByRole('link', { name: 'Team ansehen' }).click();
  await expect(page).toHaveURL(`/teams/${TEAM_1}`);
  await page.getByRole('link', { name: 'Linien einteilen' }).click();
  await expect(page).toHaveURL(`/teams/${TEAM_1}/linien`);
  await page.getByTestId(`assignment-${ANNA}`).selectOption({ label: 'Linie 2' });
  await expect(page.getByRole('status')).toHaveText('Anna Muster ist jetzt in Linie 2.');
  await page.reload();
  await expect(page.getByTestId(`assignment-${ANNA}`)).toHaveValue(T1_LINE_2);
  await checkAccessibility(page);
});

test('F-12 K1 RR-03 RR-06: Marc responds on behalf of Nina (Team 1) and sees comments', async ({ page }) => {
  await login(page, 'marc');
  await page.goto(`/termine/${T01}`);
  const team1 = page.getByTestId(`overview-team-${TEAM_1}`);
  // Comment of Mia (Team 1) is visible to Marc (ANM-07, F-12)
  await expect(team1.getByTestId(`overview-person-${MIA}`)).toContainText('Krank');
  const nina = team1.getByTestId(`overview-person-${NINA}`);
  await nina.getByRole('button', { name: 'Antwort für Nina Roth eintragen' }).click();
  const dialog = page.getByRole('dialog', { name: 'Antwort für Nina Roth eintragen' });
  await dialog.getByRole('radio', { name: 'vielleicht' }).check();
  await dialog.getByLabel('Kommentar (bei «vielleicht» Pflicht)').fill('Kommt später');
  await dialog.getByRole('button', { name: 'Speichern' }).click();
  await expect(dialog).toBeHidden();
  await expect(nina).toHaveAttribute('data-response', 'MAYBE');
  await expect(nina).toContainText('Kommt später');
  await expect(nina).toContainText('eingetragen von Marc Huber');
});

test('F-12 K2 PTF-11: Petra sees «Verwaltung», creates a person and adds her to Team 2', async ({ page }) => {
  await login(page, 'petra');
  await page.goto('/');
  const nav = page.getByRole('navigation', { name: 'Hauptnavigation' });
  await expect(nav.getByRole('list').getByRole('link')).toHaveText(['Start', 'Termine', 'Teams', 'Profil', 'Verwaltung']);
  await nav.getByRole('link', { name: 'Verwaltung' }).click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Verwaltung');
  await checkAccessibility(page);

  await page.getByRole('link', { name: /^Personen/ }).click();
  await page.getByRole('link', { name: 'Person erfassen' }).click();
  // Only admins set the admin flag (RR-04)
  await expect(page.getByRole('checkbox', { name: /Admin/ })).toHaveCount(0);
  await page.getByLabel('Vorname').fill('Nora');
  await page.getByLabel('Nachname').fill('Lang');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.getByRole('status')).toHaveText('Nora Lang ist erfasst.');
  await checkAccessibility(page);

  await page.goto('/verwaltung/teams');
  await page.getByRole('link', { name: 'Team 2' }).click();
  await page.getByLabel('Person', { exact: true }).selectOption({ label: 'Nora Lang' });
  await page.getByLabel('Funktion', { exact: true }).selectOption({ label: 'Spielerin' });
  await page.getByRole('button', { name: 'Aufnehmen' }).click();
  await expect(page.getByRole('status')).toHaveText('Nora Lang ist jetzt Spielerin in Team 2.');
  await page.getByLabel('Funktion von Nora Lang').selectOption({ label: 'Coach' });
  await expect(page.getByRole('status')).toHaveText('Nora Lang ist jetzt Coach.');
  await checkAccessibility(page);
});

test('F-12 K3 RR-08: Petra sees no delete actions, the API answers 403', async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/teams/${TEAM_1}/linien`);
  await expect(page.getByTestId('add-line')).toBeVisible();
  await expect(page.getByRole('button', { name: /löschen/ })).toHaveCount(0);

  await page.goto(`/verwaltung/teams/${TEAM_1}`);
  await expect(page.getByLabel('Funktion von Anna Muster')).toBeVisible();
  await expect(page.getByRole('button', { name: /entfernen/ })).toHaveCount(0);

  for (const response of [
    await page.request.delete(`/api/teams/${TEAM_1}/groups/${T1_LINE_1}`),
    await page.request.delete(`/api/teams/${TEAM_1}/members/${ANNA}`),
  ]) {
    expect(response.status()).toBe(403);
    expect((await response.json()).title).toBe('Keine Berechtigung');
  }
});

test('F-12 K4 RR-04: Seasons, swiss unihockey and the admin flag stay hidden for Petra', async ({ page }) => {
  await login(page, 'petra');
  await page.goto('/verwaltung');
  await expect(page.getByRole('link', { name: /^Personen/ })).toBeVisible();
  await expect(page.getByRole('link', { name: /^Saisons/ })).toHaveCount(0);
  await page.goto('/verwaltung/saisons');
  await expect(page.getByRole('alert')).toHaveText('Keine Berechtigung');
  await page.goto(`/verwaltung/teams/${TEAM_1}`);
  await expect(page.getByLabel('Teamname')).toBeVisible();
  await expect(page.getByTestId('swiss-unihockey')).toHaveCount(0);
  await page.goto(`/verwaltung/personen/${ANNA}`);
  await expect(page.getByLabel('Vorname')).toHaveValue('Anna');
  await expect(page.getByRole('checkbox', { name: /Admin/ })).toHaveCount(0);

  for (const response of [
    await page.request.post('/api/seasons', { data: { name: '2027/28', startDate: '2027-07-01', endDate: '2028-06-30' } }),
    await page.request.post(`/api/seasons/${SEASON_2025}/activate`),
    await page.request.put(`/api/teams/${TEAM_2}/swiss-unihockey`, { data: { suTeamId: 900002 } }),
    await page.request.post(`/api/teams/${TEAM_1}/swiss-unihockey/sync`),
    await page.request.put(`/api/persons/${ANNA}`, { data: { firstName: 'Anna', lastName: 'Muster', admin: true } }),
  ]) {
    expect(response.status()).toBe(403);
  }
});

test('F-12 K5: Alex (admin) still has every delete action', async ({ page }) => {
  await login(page, 'alex');
  await page.goto('/verwaltung');
  await expect(page.getByRole('link', { name: /^Saisons/ })).toBeVisible();
  await page.goto(`/teams/${TEAM_1}/linien`);
  await expect(page.getByRole('button', { name: 'Linie 1 löschen' })).toBeVisible();
  await page.goto(`/verwaltung/teams/${TEAM_1}`);
  await expect(page.getByTestId('swiss-unihockey')).toBeVisible();
  await page.getByRole('button', { name: 'Anna Muster entfernen' }).click();
  await expect(page.getByRole('status')).toHaveText('Anna Muster ist nicht mehr im Team.');
  await page.goto(`/verwaltung/personen/${ANNA}`);
  await expect(page.getByRole('checkbox', { name: /Admin/ })).toBeVisible();
});

test('F-12 K6 RR-02: Anna (player) has no administration and no access to other teams', async ({ page }) => {
  await login(page, 'anna');
  await page.goto('/');
  await expect(page.getByRole('navigation', { name: 'Hauptnavigation' }).getByRole('link', { name: 'Verwaltung' })).toHaveCount(0);
  for (const route of ['/verwaltung', '/verwaltung/personen', `/teams/${TEAM_2}`, `/teams/${TEAM_2}/linien`]) {
    await page.goto(route);
    await expect(page.getByRole('alert')).toHaveText('Keine Berechtigung');
  }
  await page.goto(`/termine/${T01}`);
  await expect(page.getByRole('button', { name: /Antwort für .* eintragen/ })).toHaveCount(0);
  await expect(page.getByTestId(`overview-person-${MIA}`).first()).not.toContainText('Krank');
  for (const response of [
    await page.request.get('/api/persons'),
    await page.request.get(`/api/teams/${TEAM_2}`),
    await page.request.put(`/api/teams/${TEAM_1}/assignments/${ANNA}`, { data: { groupId: T1_LINE_2 } }),
  ]) {
    expect(response.status()).toBe(403);
  }
});

test('F-12 K7 RR-01: Petra (player in Team 2) has coach rights in Team 2 too', async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/teams/${TEAM_2}`);
  await page.getByRole('link', { name: 'Linien einteilen' }).click();
  await expect(page).toHaveURL(`/teams/${TEAM_2}/linien`);
  await page.getByTestId('add-line').click();
  await expect(page.getByRole('status')).toHaveText('Linie 3 ist hinzugefügt.');
  await page.goto(`/termine/${T01}`);
  // Petra responds on behalf of Marc (coach of Team 2) and sees Eva's comment in Team 2
  await expect(page.getByTestId(`overview-team-${TEAM_2}`)).toContainText('Komme evtl. später');
  await expect(page.getByTestId('coach-block').getByRole('button', { name: 'Antwort für Marc Huber eintragen' })).toBeVisible();
});
