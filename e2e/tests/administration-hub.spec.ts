import { checkAccessibility, expect, login, reset, test } from './helpers';

/**
 * Administration as a hub (E-110): «Meine Teams» with direct actions for coaches, «Verein» with the club pages,
 * the team page in three areas (`?bereich=`), the compact member row and the toolbar of «Personen».
 * Seed from docs/test-data.md: petra coach Team 1 + player Team 2, marc coach Team 2, alex admin without a team.
 */

const TEAM_1 = '10000000-0000-4000-8000-000000000001';
const TEAM_2 = '10000000-0000-4000-8000-000000000002';
const MIA = '20000000-0000-4000-8000-000000000007';
const VERA = '20000000-0000-4000-8000-000000000017';
const SEASON_2025 = '60000000-0000-4000-8000-000000000001';
const SEASON_2026 = '60000000-0000-4000-8000-000000000002';

test.beforeEach(async ({ request }) => {
  await reset(request);
});

test('E-110: Petra (coach of Team 1) finds her team and its tasks on the hub', { tag: '@desktop' }, async ({ page }) => {
  await login(page, 'petra');
  await page.goto('/verwaltung');
  await expect(page).toHaveTitle('Verwaltung · STV Bero');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Verwaltung');
  await expect(page.getByTestId('manage-season')).toHaveText('Saison 2026/27');

  // Only the team she coaches, not Team 2 where she plays
  const myTeams = page.getByRole('region', { name: 'Meine Teams' });
  await expect(myTeams).toHaveAttribute('data-testid', 'manage-my-teams');
  await expect(page.getByTestId(`manage-my-team-${TEAM_2}`)).toHaveCount(0);
  const team = page.getByTestId(`manage-my-team-${TEAM_1}`);
  await expect(page.getByRole('region', { name: 'Team 1' })).toHaveAttribute('data-testid', `manage-my-team-${TEAM_1}`);
  await expect(team.getByRole('link', { name: /^Team 1/ })).toHaveAttribute('href', `/verwaltung/teams/${TEAM_1}`);
  const actions = team.getByRole('list', { name: 'Direkt zu Team 1' }).getByRole('link');
  await expect(actions).toHaveText(['Mitglieder', 'Termine überarbeiten', 'Linien einteilen', 'Fahrerliste']);
  await expect(team.getByRole('link', { name: 'Mitglieder', exact: true })).toHaveAttribute('href', `/verwaltung/teams/${TEAM_1}`);
  await expect(team.getByRole('link', { name: 'Termine überarbeiten', exact: true })).toHaveAttribute(
    'href',
    `/termine/ueberarbeiten?team=${TEAM_1}`,
  );
  await expect(team.getByRole('link', { name: 'Linien einteilen', exact: true })).toHaveAttribute('href', `/teams/${TEAM_1}/linien`);
  await expect(team.getByRole('link', { name: 'Fahrerliste', exact: true })).toHaveAttribute('href', `/teams/${TEAM_1}/fahrerliste`);

  // «Verein» without «Saisons» (RR-04)
  const club = page.getByRole('region', { name: 'Verein' });
  await expect(club.getByRole('link')).toHaveText([/^Personen/, /^Alle Teams/, /^Teamzuteilung/]);
  await expect(page.getByTestId('manage-people-state')).toHaveText('17 Personen');
  await expect(page.getByTestId('manage-teams-state')).toHaveText('3 Teams in der Saison 2026/27');
  await expect(page.getByTestId('manage-assignment-state')).toHaveText(
    'Wer spielt wo? Alle Personen und Teams in einer Tabelle',
  );
  await expect(page.getByTestId('manage-seasons-link')).toHaveCount(0);
  await checkAccessibility(page);

  // One tap to the events of her team
  await team.getByRole('link', { name: 'Termine überarbeiten', exact: true }).click();
  await expect(page).toHaveURL(`/termine/ueberarbeiten?team=${TEAM_1}`);
});

test('E-110: Marc sees only Team 2 under «Meine Teams»', async ({ page }) => {
  await login(page, 'marc');
  await page.goto('/verwaltung');
  const myTeams = page.getByTestId('manage-my-teams');
  await expect(myTeams.getByRole('region')).toHaveCount(1);
  await expect(page.getByTestId(`manage-my-team-${TEAM_2}`)).toBeVisible();
  await page.getByTestId(`manage-my-team-${TEAM_2}`).getByRole('link', { name: /^Team 2/ }).click();
  await expect(page).toHaveURL(`/verwaltung/teams/${TEAM_2}`);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Team 2');
});

test('E-110: Alex (admin without a team) sees «Verein» with «Saisons» and no «Meine Teams»', { tag: '@desktop' }, async ({ page }) => {
  await login(page, 'alex');
  await page.goto('/verwaltung');
  await expect(page.getByTestId('manage-my-teams')).toHaveCount(0);
  await expect(page.getByRole('region', { name: 'Meine Teams' })).toHaveCount(0);
  const club = page.getByRole('region', { name: 'Verein' });
  await expect(club.getByRole('link')).toHaveText([/^Personen/, /^Alle Teams/, /^Teamzuteilung/, /^Saisons/]);
  await expect(page.getByTestId('manage-seasons-state')).toHaveText('Aktuell: 2026/27');
  await checkAccessibility(page);

  await club.getByRole('link', { name: /^Alle Teams/ }).click();
  await expect(page).toHaveURL('/verwaltung/teams');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Alle Teams');
});

test('E-110: the team page has three areas, each with its own URL', { tag: '@desktop' }, async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/verwaltung/teams/${TEAM_1}`);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Team 1');
  const quick = page.getByRole('list', { name: 'Zum Team' }).getByRole('link');
  await expect(quick).toHaveText(['Team ansehen', 'Linien einteilen', 'Fahrerliste der Saison']);

  const areas = page.getByRole('navigation', { name: 'Bereich' });
  await expect(areas.getByRole('link')).toHaveText(['Mitglieder', 'Termine', 'Einstellungen']);

  // «Mitglieder» is the default: adding comes first, then coaches and players
  await expect(areas.getByRole('link', { name: 'Mitglieder' })).toHaveAttribute('aria-current', 'page');
  await expect(areas.getByRole('link', { name: 'Termine' })).not.toHaveAttribute('aria-current');
  await expect(page.getByRole('form', { name: 'Person aufnehmen' })).toBeVisible();
  await expect(page.getByTestId('team-coaches')).toBeVisible();
  await expect(page.getByTestId('team-players')).toBeVisible();
  await expect(page.getByLabel('Teamname')).toHaveCount(0);
  await expect(page.getByTestId('event-variants')).toHaveCount(0);
  await checkAccessibility(page);

  await areas.getByRole('link', { name: 'Termine' }).click();
  await expect(page).toHaveURL(`/verwaltung/teams/${TEAM_1}?bereich=termine`);
  await expect(areas.getByRole('link', { name: 'Termine' })).toHaveAttribute('aria-current', 'page');
  await expect(page.getByTestId('event-variants')).toBeVisible();
  await expect(page.getByTestId('match-day-settings')).toBeVisible();
  await expect(page.getByTestId('team-rework-link')).toHaveAttribute('href', `/termine/ueberarbeiten?team=${TEAM_1}`);
  await expect(page.getByTestId('team-players')).toHaveCount(0);
  await checkAccessibility(page);

  await areas.getByRole('link', { name: 'Einstellungen' }).click();
  await expect(page).toHaveURL(`/verwaltung/teams/${TEAM_1}?bereich=einstellungen`);
  await expect(areas.getByRole('link', { name: 'Einstellungen' })).toHaveAttribute('aria-current', 'page');
  await expect(page.getByLabel('Teamname')).toHaveValue('Team 1');
  await expect(page.getByTestId('swiss-unihockey')).toBeVisible();
  await expect(page.getByTestId('event-variants')).toHaveCount(0);
  await checkAccessibility(page);

  // Back to the default: the parameter leaves the URL
  await areas.getByRole('link', { name: 'Mitglieder' }).click();
  await expect(page).toHaveURL(`/verwaltung/teams/${TEAM_1}`);
  await expect(page.getByTestId('team-players')).toBeVisible();
});

test('E-110: a deep link opens the area and switching keeps the season', async ({ page }) => {
  await login(page, 'alex');
  await page.goto(`/verwaltung/teams/${TEAM_1}?saison=${SEASON_2026}&bereich=einstellungen`);
  await expect(page.getByLabel('Teamname')).toBeVisible();
  const areas = page.getByRole('navigation', { name: 'Bereich' });
  await expect(areas.getByRole('link', { name: 'Einstellungen' })).toHaveAttribute('aria-current', 'page');

  await areas.getByRole('link', { name: 'Termine' }).click();
  await expect(page).toHaveURL(`/verwaltung/teams/${TEAM_1}?saison=${SEASON_2026}&bereich=termine`);
  await areas.getByRole('link', { name: 'Mitglieder' }).click();
  await expect(page).toHaveURL(`/verwaltung/teams/${TEAM_1}?saison=${SEASON_2026}`);

  // An unknown area shows the members
  await page.goto(`/verwaltung/teams/${TEAM_1}?bereich=unbekannt`);
  await expect(areas.getByRole('link', { name: 'Mitglieder' })).toHaveAttribute('aria-current', 'page');
  await expect(page.getByTestId('team-players')).toBeVisible();
});

test('E-110: a message stays in the area where the action happened', async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/verwaltung/teams/${TEAM_1}?bereich=einstellungen`);
  await page.getByLabel('Teamname').fill('');
  await page.getByRole('button', { name: 'Umbenennen' }).click();
  await expect(page.getByLabel('Teamname')).toHaveAccessibleDescription('Bitte einen Namen eingeben.');
  await page.getByLabel('Teamname').fill('Damen 1');
  await page.getByRole('button', { name: 'Umbenennen' }).click();
  await expect(page.getByRole('status')).toHaveText('Name gespeichert.');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Damen 1');

  await page.getByRole('navigation', { name: 'Bereich' }).getByRole('link', { name: 'Mitglieder' }).click();
  await expect(page.getByTestId('team-players')).toBeVisible();
  await expect(page.getByRole('status')).toHaveCount(0);
});

test('E-110: each member is one row with the role and an icon button to remove', async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/verwaltung/teams/${TEAM_1}`);
  const mia = page.getByTestId('team-players').getByRole('listitem', { name: 'Mia Frei' });
  await expect(mia).toHaveAttribute('data-testid', `member-${MIA}`);
  await expect(mia.getByLabel('Funktion von Mia Frei')).toHaveValue('PLAYER');
  const remove = mia.getByRole('button', { name: 'Mia Frei entfernen' });
  await expect(remove).toHaveAttribute('title', 'Mia Frei entfernen');
  // The name is not repeated as visible text: the row stays one line
  await expect(mia).not.toContainText('entfernen');
  const row = await mia.boundingBox();
  const button = await remove.boundingBox();
  expect(row!.height).toBeLessThan(70);
  expect(button!.width).toBeGreaterThanOrEqual(44);
  expect(button!.height).toBeGreaterThanOrEqual(44);

  await remove.click();
  await expect(page.getByRole('status')).toHaveText('Mia Frei ist nicht mehr im Team.');
  await expect(page.getByTestId(`member-${MIA}`)).toHaveCount(0);
  await expect(page.getByTestId('team-players').getByRole('heading')).toHaveText('Spielerinnen 6');
});

test('E-110: the archive shows only the members, without areas', async ({ page }) => {
  await login(page, 'alex');
  await page.goto(`/verwaltung/teams/${TEAM_1}?saison=${SEASON_2025}&bereich=termine`);
  await expect(page.getByTestId('archive-notice')).toBeVisible();
  await expect(page.getByRole('navigation', { name: 'Bereich' })).toHaveCount(0);
  await expect(page.getByRole('list', { name: 'Zum Team' })).toHaveCount(0);
  await expect(page.getByTestId('team-players').getByTestId(`member-${VERA}`)).toHaveText(/Vera Studer\s*Spielerin/);
  await expect(page.getByTestId('event-variants')).toHaveCount(0);
  await expect(page.getByRole('button')).toHaveCount(0);
});

test('E-110: «Personen» counts the people, also while searching', { tag: '@desktop' }, async ({ page }) => {
  await login(page, 'alex');
  await page.goto('/verwaltung/personen');
  await expect(page.getByTestId('people-count')).toHaveText('17 Personen');
  await expect(page.getByRole('list', { name: 'Personen' }).getByRole('listitem')).toHaveCount(17);
  await checkAccessibility(page);

  await page.getByLabel('Suchen').fill('Muster');
  await expect(page).toHaveURL('/verwaltung/personen?suche=Muster');
  await expect(page.getByTestId('people-count')).toHaveText('1 von 17 Personen');

  // Opened directly with a search
  await page.goto('/verwaltung/personen?suche=Muster');
  await expect(page.getByTestId('people-count')).toHaveText('1 von 17 Personen');
  await page.getByRole('link', { name: 'Person erfassen' }).click();
  await expect(page).toHaveURL('/verwaltung/personen/neu');
});
