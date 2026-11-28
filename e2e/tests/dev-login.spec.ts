import { checkAccessibility, expect, reset, test } from './helpers';

/**
 * Test mode login (E-112): «Oft gebraucht» with the people most needed for trying things out, chosen by role in both
 * data sets, then everybody once in one list with a search (`?suche=`). Seed from docs/test-data.md.
 */

test.beforeEach(async ({ request }) => {
  await reset(request);
});

test('E-112: the test data set pins an admin, a coach, a player, a coach who plays and a person without a team', { tag: '@desktop' }, async ({ page }) => {
  await page.goto('/dev-login');
  await expect(page.getByTestId('data-set-name')).toHaveText('Testdaten');

  const quick = page.getByRole('region', { name: 'Oft gebraucht' });
  await expect(quick.getByRole('listitem')).toHaveCount(5);
  await expect(page.getByTestId('dev-login-quick-admin')).toContainText('Alex Admin');
  await expect(page.getByTestId('dev-login-quick-coach')).toContainText('Marc Huber');
  await expect(page.getByTestId('dev-login-quick-coach')).toContainText('Coach Team 2');
  // A player of the coach's team
  await expect(page.getByTestId('dev-login-quick-player')).toContainText('Spielerin Team 2');
  await expect(page.getByTestId('dev-login-quick-coach-player').getByRole('button')).toHaveAccessibleName(
    'Petra Keller Coach Team 1, Spielerin Team 2',
  );
  await expect(page.getByTestId('dev-login-quick-no-team')).toContainText('Vera Studer');
  await expect(page.getByTestId('dev-login-quick-no-team')).toContainText('Ohne Team');

  // Below: everybody once, by last name, with teams and roles
  const all = page.getByRole('list', { name: 'Alle Personen' });
  await expect(all.getByRole('listitem')).toHaveCount(17);
  await expect(all.getByRole('listitem').first()).toContainText('Alex Admin');
  await expect(page.getByTestId('dev-login-person-petra')).toHaveAttribute('data-role', 'coach');
  await expect(page.getByTestId('dev-login-person-lea')).toContainText('Spielerin Team 1, Spielerin Team 2');
  await checkAccessibility(page);

  // One tap on a tile logs in
  await page.getByTestId('dev-login-quick-coach-player').getByRole('button').click();
  await expect(page).toHaveURL('/');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Hallo Petra');
});

test('E-112: the search filters the list by name while typing and is in the URL', async ({ page }) => {
  await page.goto('/dev-login?ziel=%2Fprofil');
  const all = page.getByRole('list', { name: 'Alle Personen' });
  await page.getByLabel('Person suchen').fill('mus');
  await expect(all.getByRole('listitem')).toHaveCount(1);
  await expect(page.getByTestId('dev-login-person-anna')).toBeVisible();
  await expect(page).toHaveURL('/dev-login?ziel=%2Fprofil&suche=mus');

  await page.getByLabel('Person suchen').fill('zzz');
  await expect(page.getByTestId('dev-login-no-match')).toHaveText('Keine Person gefunden.');

  // Deep link with the search; ?ziel= still leads back
  await page.goto('/dev-login?ziel=%2Fprofil&suche=lea');
  await expect(page.getByLabel('Person suchen')).toHaveValue('lea');
  await expect(all.getByRole('listitem')).toHaveCount(1);
  await page.getByTestId('dev-login-person-lea').getByRole('button').click();
  await expect(page).toHaveURL('/profil');
});

test('E-112: the club data set pins the same roles, chosen from its people', async ({ page }) => {
  await page.goto('/dev-login');
  await page.getByRole('button', { name: 'Vereinsdaten laden' }).click();
  await expect(page.getByTestId('data-set-name')).toHaveText('Vereinsdaten');
  await expect(page.getByRole('list', { name: 'Alle Personen' }).getByRole('listitem')).toHaveCount(106);

  await expect(page.getByTestId('dev-login-quick-admin')).toContainText('Alex Admin');
  await expect(page.getByTestId('dev-login-quick-coach')).toContainText('Reto Amrein');
  await expect(page.getByTestId('dev-login-quick-player')).toContainText('Herren KF');
  await expect(page.getByTestId('dev-login-quick-coach-player')).toContainText(
    'Petra Keller',
  );
  // Everybody in the club data has a team: no tile for that
  await expect(page.getByTestId('dev-login-quick-no-team')).toHaveCount(0);

  // The choice is the same after a reload
  const player = await page.getByTestId('dev-login-quick-player').getByRole('button').textContent();
  await page.reload();
  await expect(page.getByTestId('dev-login-quick-player').getByRole('button')).toHaveText(player ?? '');
});
