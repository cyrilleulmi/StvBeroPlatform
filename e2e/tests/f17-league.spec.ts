import type { APIRequestContext } from '@playwright/test';
import { checkAccessibility, expect, login, reset, test } from './helpers';

/**
 * F-17 · Team overview with data from swiss unihockey (SUH, SUH-09). Acceptance criteria from issue #56 and
 * docs/pipeline/backlog.md. After the reset the seed holds the league of Team 1 as loaded by the daily sync on
 * Mon 16.11.2026 04:30, and the simulator delivers the same (docs/test-data.md, league of Team 1).
 */

const TEAM_1 = '10000000-0000-4000-8000-000000000001';
const TEAM_2 = '10000000-0000-4000-8000-000000000002';
const T04 = '40000000-0000-4000-8000-000000000004';

async function simulator(request: APIRequestContext, data: object): Promise<void> {
  const response = await request.put('/api/test/swiss-unihockey/matches', { data });
  expect(response.status()).toBe(204);
}

const OTHER_LEAGUE = {
  suTeamId: 900001,
  matches: [],
  league: {
    league: 'Herren Kleinfeld 2. Liga',
    group: 'Gruppe 3',
    table: [
      { rank: 1, team: 'UHC Neu', matches: 1, wins: 1, draws: 0, losses: 0, goalsFor: 5, goalsAgainst: 3, points: 2 },
      { rank: 2, suTeamId: 900001, team: 'Team 1', matches: 1, wins: 0, draws: 0, losses: 1, goalsFor: 3, goalsAgainst: 5, points: 0 },
    ],
    matches: [],
  },
};

test.beforeEach(async ({ request }) => {
  await reset(request);
});

test('F-17 criterion 1: section «Liga» on the team page, hint for teams without a link', { tag: '@desktop' }, async ({ page }) => {
  await login(page, 'anna');
  await page.goto(`/teams/${TEAM_1}`);
  const section = page.getByRole('region', { name: 'Liga' });
  await expect(section).toBeVisible();
  await section.getByRole('link', { name: 'Liga ansehen' }).click();
  await expect(page).toHaveURL(`/teams/${TEAM_1}/liga`);
  await expect(page.getByRole('heading', { level: 1, name: 'Liga' })).toBeVisible();
  await checkAccessibility(page);

  // Team 2 is not linked: hint, for coaches and admins with a link to the administration (E-84)
  await login(page, 'eva');
  await page.goto(`/teams/${TEAM_2}`);
  const hint = page.getByRole('region', { name: 'Liga' });
  await expect(hint).toContainText('nicht mit swiss unihockey verknüpft');
  await expect(hint.getByRole('link', { name: 'In der Verwaltung verknüpfen' })).toHaveCount(0);

  await login(page, 'marc');
  await page.goto(`/teams/${TEAM_2}`);
  await page.getByRole('region', { name: 'Liga' }).getByRole('link', { name: 'In der Verwaltung verknüpfen' }).click();
  await expect(page).toHaveURL(`/verwaltung/teams/${TEAM_2}`);

  await page.goto(`/teams/${TEAM_2}/liga`);
  await expect(page.getByTestId('league-not-linked')).toContainText('nicht mit swiss unihockey verknüpft');
});

test('F-17 criterion 2 and 11: table with the own team highlighted, readable at 375px', async ({ page }) => {
  await login(page, 'anna');
  await page.goto(`/teams/${TEAM_1}/liga`);
  const table = page.getByRole('table', { name: 'Tabelle' });
  await expect(table).toBeVisible();
  await expect(table.getByRole('row')).toHaveCount(9);
  for (const column of ['Rang', 'Team', 'Punkte', 'Spiele', 'Siege', 'Tore']) {
    await expect(table.getByRole('columnheader', { name: column, exact: true })).toBeAttached();
  }
  const own = page.getByTestId('league-row-2');
  await expect(own).toHaveAttribute('data-own', 'true');
  await expect(own.getByRole('rowheader')).toContainText('Team 1');
  await expect(own).toContainText('Unser Team');
  await expect(own.getByRole('cell').filter({ hasText: '31:22' })).toBeAttached();
  await expect(page.getByTestId('league-own-team')).toHaveCount(1);
  await expect(page.getByTestId('league-row-1')).not.toHaveAttribute('data-own', 'true');

  // Rank, team and points are visible without scrolling, also on a phone
  const first = page.getByTestId('league-row-1');
  await expect(first.getByRole('cell', { name: '1', exact: true }).first()).toBeInViewport();
  await expect(first.getByRole('rowheader')).toBeInViewport();
  await expect(first.getByRole('cell', { name: '11', exact: true })).toBeInViewport();
  await checkAccessibility(page);
});

test('F-17 criterion 3: upcoming matches, those in the app link to the match day', async ({ page }) => {
  await login(page, 'anna');
  await page.goto(`/teams/${TEAM_1}/liga`);
  const list = page.getByRole('list', { name: 'Kommende Spiele' });
  await expect(list.getByRole('listitem')).toHaveCount(4);
  const first = page.getByTestId('league-upcoming-5000001');
  await expect(first).toContainText('So 22.11.2026 · 09:30');
  await expect(first).toContainText('Heim gegen UHC Musterdorf');
  await expect(first).toContainText('Sporthalle Seefeld, Musterdorf');
  await expect(page.getByTestId('league-upcoming-5000002')).toContainText('Auswärts bei Unihockey Beispielstadt');
  // Not a match day in the app: no link
  await expect(page.getByTestId('league-upcoming-5000017').getByRole('link')).toHaveCount(0);

  await first.getByRole('link').click();
  await expect(page).toHaveURL(`/termine/${T04}`);
});

test('F-17 criterion 4: the last five results', async ({ page }) => {
  await login(page, 'anna');
  await page.goto(`/teams/${TEAM_1}/liga`);
  const list = page.getByRole('list', { name: 'Letzte Resultate' });
  await expect(list.getByRole('listitem')).toHaveCount(5);
  await expect(list.getByRole('listitem').first()).toContainText('Team 1 – Unihockey Bergdorf 6:5');
  await expect(list.getByRole('listitem').first()).toContainText('Sieg');
  await expect(page.getByTestId('league-result-5000015')).toContainText('Niederlage');
  await expect(page.getByTestId('league-result-5000013')).toContainText('Unentschieden');
  await expect(page.getByTestId('league-result-5000011')).toHaveCount(0);
});

test('F-17 criterion 5: league, group and «Stand»', async ({ page }) => {
  await login(page, 'anna');
  await page.goto(`/teams/${TEAM_1}/liga`);
  await expect(page.getByRole('heading', { level: 2, name: 'Herren Kleinfeld 3. Liga · Gruppe 12' })).toBeVisible();
  await expect(page.getByTestId('league-loaded-at')).toHaveText('Stand: Mo 16.11.2026, 04:30');
  await expect(page.getByTestId('league-stale')).toHaveCount(0);
});

test('F-17 criterion 6: admins reload by hand, others cannot', async ({ page, request }) => {
  await simulator(request, OTHER_LEAGUE);
  await login(page, 'petra');
  await page.goto(`/teams/${TEAM_1}/liga`);
  await expect(page.getByRole('heading', { level: 2, name: 'Herren Kleinfeld 3. Liga · Gruppe 12' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Neu laden' })).toHaveCount(0);

  await login(page, 'alex');
  await page.goto(`/teams/${TEAM_1}/liga`);
  // Shows the cache, swiss unihockey is not asked while rendering
  await expect(page.getByRole('heading', { level: 2, name: 'Herren Kleinfeld 3. Liga · Gruppe 12' })).toBeVisible();
  await page.getByRole('button', { name: 'Neu laden' }).click();
  await expect(page.getByRole('status')).toHaveText('Die Ligadaten sind neu geladen.');
  await expect(page.getByRole('heading', { level: 2, name: 'Herren Kleinfeld 2. Liga · Gruppe 3' })).toBeVisible();
  await expect(page.getByTestId('league-loaded-at')).toHaveText('Stand: Mo 16.11.2026, 06:00');
  await expect(page.getByTestId('league-row-2')).toHaveAttribute('data-own', 'true');
  await expect(page.getByText('Keine kommenden Spiele.')).toBeVisible();
  await expect(page.getByText('Noch keine Resultate.')).toBeVisible();
});

test.describe('swiss unihockey unavailable', () => {
  test.use({ expectedConsoleErrors: [/502/] });

  test('F-17 criterion 9: last data with its date and a hint (SUH-09)', async ({ page, request }) => {
    await simulator(request, { suTeamId: 900001, matches: [], unavailable: true });
    await login(page, 'alex');
    await page.goto(`/teams/${TEAM_1}/liga`);
    await page.getByRole('button', { name: 'Neu laden' }).click();
    await expect(page.getByRole('alert')).toContainText('swiss unihockey ist im Moment nicht erreichbar');
    await expect(page.getByTestId('league-stale')).toHaveText(
      'swiss unihockey war am Mo 16.11.2026, 06:00 nicht erreichbar. Angezeigt wird der Stand vom Mo 16.11.2026, 04:30.',
    );
    await expect(page.getByRole('table', { name: 'Tabelle' }).getByRole('row')).toHaveCount(9);

    // Players see the hint too, and the rest of the app works normally
    await login(page, 'anna');
    await page.goto(`/teams/${TEAM_1}/liga`);
    await expect(page.getByTestId('league-stale')).toBeVisible();
    await page.goto(`/termine/${T04}`);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  });

  test('F-17 criterion 9: never loaded, clear empty state', async ({ page, request }) => {
    await login(page, 'alex');
    // Linking loads the league right away (SUH-02); swiss unihockey is down, so nothing was ever loaded
    await simulator(request, { suTeamId: 900002, matches: [], unavailable: true });
    const link = await page.request.post(`/api/teams/${TEAM_2}/swiss-unihockey/links`, { data: { suTeamId: 900002 } });
    expect(link.status()).toBe(201);
    await page.goto(`/teams/${TEAM_2}/liga`);
    await expect(page.getByTestId('league-never-loaded')).toHaveText(
      'Es sind noch keine Daten von swiss unihockey geladen.',
    );
    await expect(page.getByRole('table')).toHaveCount(0);

    await page.getByRole('button', { name: 'Neu laden' }).click();
    await expect(page.getByRole('alert')).toHaveText(
      'swiss unihockey ist im Moment nicht erreichbar. Bitte später nochmals versuchen.',
    );
    await expect(page.getByTestId('league-stale')).toHaveText(
      'Der letzte Versuch am Mo 16.11.2026, 06:00 hat nicht geklappt: swiss unihockey war nicht erreichbar.',
    );
    await checkAccessibility(page);
  });
});

test.describe('no permission', () => {
  test.use({ expectedConsoleErrors: [/403/] });

  test('F-17 criterion 10: everyone who may see the team, nobody else', async ({ page }) => {
    for (const key of ['petra', 'marc']) {
      await login(page, key);
      await page.goto(`/teams/${TEAM_1}/liga`);
      await expect(page.getByRole('table', { name: 'Tabelle' })).toBeVisible();
    }
    await login(page, 'eva');
    await page.goto(`/teams/${TEAM_1}/liga`);
    await expect(page.getByRole('alert')).toHaveText('Keine Berechtigung');
    await expect(page.getByRole('table')).toHaveCount(0);
    const response = await page.request.get(`/api/teams/${TEAM_1}/league`);
    expect(response.status()).toBe(403);
  });
});
