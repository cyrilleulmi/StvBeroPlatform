import type { Locator, Page } from '@playwright/test';
import { checkAccessibility, expect, login, reset, test } from './helpers';

/**
 * «Teamzuteilung» (PTF-16, PTF-17): everyone of the club and every team in one table. Seed from docs/test-data.md:
 * 17 people; Team 2 has six players and the coach Marc; Vera Studer was in Team 1 in the season 2025/26 and is in no
 * team now; Alex Admin is in no team either.
 */

const TEAM_1 = '10000000-0000-4000-8000-000000000001';
const TEAM_2 = '10000000-0000-4000-8000-000000000002';
const TEAM_3 = '10000000-0000-4000-8000-000000000003';
const ANNA = '20000000-0000-4000-8000-000000000005';
const VERA = '20000000-0000-4000-8000-000000000017';

test.beforeEach(async ({ request }) => {
  await reset(request);
});

function cell(page: Page, personId: string, teamId: string): Locator {
  return page.getByTestId(`membership-cell-${personId}-${teamId}`);
}

test('PTF-16 PTF-17: at the end of a season an admin moves people on in one table', async ({ page }) => {
  await login(page, 'alex');
  await page.goto('/verwaltung');
  await page.getByRole('link', { name: /Teamzuteilung/ }).click();
  await expect(page).toHaveURL(/\/verwaltung\/zuteilung$/);
  const table = page.getByTestId('membership-table');
  const rows = table.locator('[data-testid^="membership-row-"]');
  const team2 = page.getByTestId(`membership-team-${TEAM_2}`);
  await expect(rows).toHaveCount(17);
  await expect(team2).toHaveAttribute('data-players', '6');
  await expect(team2).toHaveAttribute('data-coaches', '1');
  await checkAccessibility(page);

  // Who is in no team this season
  await table.getByLabel('Team', { exact: true }).selectOption('none');
  await expect(page.getByTestId('membership-count')).toContainText('2 von 17 Personen');
  // Who was in Team 1 in the season before: Vera
  await table.getByLabel('Team', { exact: true }).selectOption(`prev:${TEAM_1}`);
  await expect(rows).toHaveText([/Vera Studer/]);

  // Everyone shown joins Team 2 at once, after a question
  await table.getByLabel('Team für alle Angezeigten').selectOption({ label: 'Team 2' });
  await page.getByTestId('membership-bulk-add').click();
  await expect(page.getByTestId('membership-confirm')).toContainText('1 Person in Team 2 aufnehmen?');
  await page.getByTestId('membership-confirm-confirm').click();
  await expect(page.getByTestId('membership-status')).toHaveText('1 Person ist neu in Team 2.');
  await expect(cell(page, VERA, TEAM_2)).toHaveAttribute('data-role', 'PLAYER');
  await expect(team2).toHaveAttribute('data-players', '7');

  // One tap puts her into Team 3 as well, another one takes her out again (admins, RR-08)
  await cell(page, VERA, TEAM_3).click();
  await expect(cell(page, VERA, TEAM_3)).toHaveAttribute('data-role', 'PLAYER');
  await expect(page.getByTestId('membership-status')).toHaveText('Vera Studer ist Spielerin oder Spieler in Team 3.');
  await cell(page, VERA, TEAM_3).click();
  await expect(cell(page, VERA, TEAM_3)).toHaveAttribute('data-role', 'none');
  await expect(page.getByTestId('membership-status')).toHaveText('Vera Studer ist nicht mehr in Team 3.');
  await expect(rows).toHaveText([/Vera Studer/]);

  // And everyone shown leaves Team 2 again
  await page.getByTestId('membership-bulk-remove').click();
  await expect(page.getByTestId('membership-confirm')).toContainText('1 Person aus Team 2 entfernen?');
  await page.getByTestId('membership-confirm-confirm').click();
  await expect(team2).toHaveAttribute('data-players', '6');
  await expect(cell(page, VERA, TEAM_2)).toHaveAttribute('data-role', 'none');
});

test('PTF-16 E-101: coaches put people into teams, change team roles and take people out', async ({
  page,
}) => {
  await login(page, 'petra');
  await page.goto('/verwaltung/zuteilung');
  const table = page.getByTestId('membership-table');
  await expect(page.getByTestId('membership-bulk-add')).toBeVisible();
  await expect(page.getByTestId('membership-bulk-remove')).toBeVisible();

  await cell(page, ANNA, TEAM_3).click();
  await expect(cell(page, ANNA, TEAM_3)).toHaveAttribute('data-role', 'PLAYER');
  await expect(page.getByTestId('membership-status')).toHaveText('Anna Muster ist Spielerin oder Spieler in Team 3.');
  // It is saved
  await page.reload();
  await expect(cell(page, ANNA, TEAM_3)).toHaveAttribute('data-role', 'PLAYER');
  // Another tap takes her out, one more puts her back
  await cell(page, ANNA, TEAM_3).click();
  await expect(cell(page, ANNA, TEAM_3)).toHaveAttribute('data-role', 'none');
  await cell(page, ANNA, TEAM_3).click();
  await expect(cell(page, ANNA, TEAM_3)).toHaveAttribute('data-role', 'PLAYER');
  // As a coach instead
  await table.getByRole('radio', { name: 'Coach' }).check();
  await cell(page, ANNA, TEAM_3).click();
  await expect(cell(page, ANNA, TEAM_3)).toHaveAttribute('data-role', 'COACH');
  await expect(cell(page, ANNA, TEAM_3)).toHaveAccessibleName('Anna Muster in Team 3: Coach');
  await checkAccessibility(page);
});

test('PTF-16: players have no access to «Teamzuteilung»', async ({ page }) => {
  await login(page, 'anna');
  await page.goto('/verwaltung/zuteilung');
  await expect(page.getByRole('alert')).toHaveText('Keine Berechtigung');
  await expect(page.getByTestId('membership-table')).toHaveCount(0);
});
