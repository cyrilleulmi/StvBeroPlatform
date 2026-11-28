import { checkAccessibility, expect, login, reset, test } from './helpers';

/**
 * SUH-16, E-116 · The page «swiss unihockey» opens the overview of the current team. Seed from docs/test-data.md:
 * Team 1 is linked with swiss unihockey (900001) and has league data, Team 2 and Team 3 are not linked. Anna plays
 * in Team 1, Eva in Team 2, Petra coaches Team 1 and plays in Team 2, Marc coaches Team 2, Alex is admin without a
 * team.
 */

const TEAM_1 = '10000000-0000-4000-8000-000000000001';
const TEAM_2 = '10000000-0000-4000-8000-000000000002';

test.beforeEach(async ({ request }) => {
  await reset(request);
});

test('SUH-16: the page opens the overview of the own team with the link to swissunihockey.ch', { tag: '@desktop' }, async ({ page }) => {
  await login(page, 'anna');
  await page.goto('/swissunihockey');
  await expect(page.getByRole('heading', { level: 1, name: 'swiss unihockey' })).toBeVisible();
  await expect(page.getByTestId('league-info')).toBeVisible();
  await expect(page.getByRole('heading', { level: 2, name: 'Herren Kleinfeld 3. Liga · Gruppe 12' })).toBeVisible();
  await expect(page.getByTestId('league-table')).toBeVisible();
  await expect(page.getByTestId('league-upcoming')).toBeVisible();
  await expect(page.getByTestId('league-results')).toBeVisible();
  await expect(page.getByTestId('su-open-at-su-900001')).toHaveAttribute(
    'href',
    'https://www.swissunihockey.ch/de/team-detail/?team_id=900001',
  );
  // One team only: no choice; players do not manage the connection
  await expect(page.getByTestId('su-team-select')).toHaveCount(0);
  await expect(page.getByTestId('su-manage')).toHaveCount(0);
  await expect(page.getByTestId('su-not-linked')).toHaveCount(0);
  await checkAccessibility(page);
});

test('SUH-16: the choice «Team» is in the URL, unknown teams fall back to the current team', async ({ page }) => {
  // Alex is admin without a team: the first team by name is shown, every team can be chosen
  await login(page, 'alex');
  await page.goto('/swissunihockey');
  const select = page.getByRole('combobox', { name: 'Team' });
  await expect(select).toBeVisible();
  await expect(page.getByTestId('league-info')).toBeVisible();

  await select.selectOption({ label: 'Team 2' });
  await expect(page).toHaveURL(new RegExp(`/swissunihockey\\?team=${TEAM_2}$`));
  await expect(page.getByTestId('su-not-linked')).toBeVisible();
  await expect(page.getByTestId('league-info')).toHaveCount(0);

  // The choice survives a reload
  await page.reload();
  await expect(page.getByRole('combobox', { name: 'Team' })).toHaveValue(TEAM_2);
  await expect(page.getByTestId('su-not-linked')).toBeVisible();

  await page.getByRole('combobox', { name: 'Team' }).selectOption({ label: 'Team 1' });
  await expect(page).toHaveURL(new RegExp(`/swissunihockey\\?team=${TEAM_1}$`));
  await expect(page.getByTestId('league-info')).toBeVisible();
  await expect(page.getByTestId('su-manage')).toBeVisible();

  // An unknown id falls back to the current team (Team 1)
  await page.goto('/swissunihockey?team=does-not-exist');
  await expect(page.getByRole('combobox', { name: 'Team' })).toHaveValue(TEAM_1);
  await expect(page.getByTestId('league-info')).toBeVisible();

  // Petra plays in Team 2 and coaches Team 1: the team as a player comes first
  await login(page, 'petra');
  await page.goto('/swissunihockey');
  await expect(page.getByRole('combobox', { name: 'Team' })).toHaveValue(TEAM_2);
  await expect(page.getByTestId('su-not-linked')).toBeVisible();

  // A player cannot choose a foreign team through the URL
  await login(page, 'anna');
  await page.goto(`/swissunihockey?team=${TEAM_2}`);
  await expect(page.getByTestId('league-info')).toBeVisible();
  await expect(page.getByTestId('su-not-linked')).toHaveCount(0);
});

test('SUH-16: a player of a team that is not linked reads how it works, without a button', async ({ page }) => {
  await login(page, 'eva');
  await page.goto('/swissunihockey');
  const card = page.getByTestId('su-not-linked');
  await expect(card.getByRole('heading', { level: 2, name: 'Noch nicht mit swiss unihockey verbunden' })).toBeVisible();
  await expect(card.getByRole('listitem')).toHaveCount(3);
  await expect(card).toContainText('Frag deinen Coach oder einen Admin.');
  await expect(page.getByTestId('su-link-now')).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Team verknüpfen' })).toHaveCount(0);
  await expect(page.getByTestId('su-manage')).toHaveCount(0);
  await expect(page.getByTestId('league-info')).toHaveCount(0);
  await checkAccessibility(page);
});

test('SUH-16 SUH-02: a coach links the team in the dialog right there and lands in the overview', async ({ page }) => {
  await login(page, 'marc');
  await page.goto('/swissunihockey');
  await expect(page.getByTestId('su-not-linked')).toBeVisible();
  await expect(page.getByTestId('su-not-linked')).not.toContainText('Frag deinen Coach');

  await page.getByTestId('su-link-now').click();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible();
  await dialog.getByLabel('Team-ID von Hand eingeben').check();
  await dialog.getByLabel('Team-ID bei swiss unihockey').fill('900002');
  await dialog.getByRole('button', { name: 'Verknüpfen', exact: true }).click();

  await expect(page.getByTestId('su-not-linked')).toHaveCount(0);
  await expect(page.getByTestId('league-info')).toBeVisible();
  await expect(page.getByTestId('su-open-at-su-900002')).toHaveAttribute(
    'href',
    'https://www.swissunihockey.ch/de/team-detail/?team_id=900002',
  );
  // Links, unlinking and sync are below the overview
  const manage = page.getByTestId('su-manage');
  await manage.getByText('Verbindung verwalten').click();
  await expect(manage.getByTestId('su-link-900002')).toBeVisible();
});
