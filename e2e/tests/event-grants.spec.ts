import { checkAccessibility, expect, login, reset, test } from './helpers';

/**
 * Special permissions per event and series (RR-09 to RR-14, E-96): a coach lets Kim (player of Team 1) manage
 * event 01 like a coach.
 */

const T01 = '40000000-0000-4000-8000-000000000001';
const T02 = '40000000-0000-4000-8000-000000000002';
const SERIES_1 = '70000000-0000-4000-8000-000000000001';
const KIM = '20000000-0000-4000-8000-000000000011';
const MIA = '20000000-0000-4000-8000-000000000007';

test.beforeEach(async ({ request }) => {
  await reset(request);
});

test('RR-09 RR-11: a coach gives Kim a special permission, Kim edits and sees the reasons', async ({ page }) => {
  await login(page, 'kim');
  await page.goto(`/termine/${T01}`);
  await expect(page.getByRole('link', { name: 'Bearbeiten' })).toHaveCount(0);

  await login(page, 'petra');
  await page.goto(`/termine/${T01}`);
  const grants = page.getByTestId('grants');
  await expect(grants.getByTestId('grants-empty')).toBeVisible();
  await grants.getByRole('button', { name: 'Personen wählen' }).click();
  const dialog = page.getByRole('dialog', { name: 'Sonderberechtigungen' });
  await dialog.getByRole('checkbox', { name: 'Kim Baumann' }).check();
  await checkAccessibility(page);
  await dialog.getByRole('button', { name: 'Speichern' }).click();
  await expect(dialog).toBeHidden();
  await expect(grants.getByTestId('grants-list')).toContainText('Kim Baumann');

  await login(page, 'kim');
  await page.goto(`/termine/${T01}`);
  await expect(page.getByTestId('grants-mine')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Personen wählen' })).toHaveCount(0);
  // Reasons of others are visible (Mia «Krank»)
  await expect(page.getByTestId(`overview-person-${MIA}`)).toContainText('Krank');
  await page.getByRole('link', { name: 'Bearbeiten' }).click();
  await expect(page).toHaveURL(new RegExp(`/termine/${T01}/bearbeiten$`));
  await expect(page.getByText('Die Teams eines Termins ändern nur Coaches.')).toBeVisible();
  await expect(page.getByRole('group', { name: 'Teams' }).getByRole('checkbox').first()).toBeDisabled();

  // Not for other events
  await page.goto(`/termine/${T02}`);
  await expect(page.getByRole('link', { name: 'Bearbeiten' })).toHaveCount(0);

  // The notification (BEN-08)
  await page.goto('/benachrichtigungen');
  await expect(page.getByRole('main')).toContainText('Sonderberechtigung');
});

test('RR-10: a special permission on the series applies to its events', async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/serien/${SERIES_1}`);
  const grants = page.getByTestId('grants');
  await grants.getByRole('button', { name: 'Personen wählen' }).click();
  const dialog = page.getByRole('dialog', { name: 'Sonderberechtigungen' });
  await dialog.getByTestId(`grant-${KIM}`).check();
  await dialog.getByRole('button', { name: 'Speichern' }).click();
  await expect(grants.getByTestId('grants-list')).toContainText('Kim Baumann');

  await page.goto(`/termine/${T01}`);
  await expect(page.getByTestId('grants-list')).toContainText('über die Serie');

  await login(page, 'kim');
  await page.goto(`/termine/${T01}`);
  await expect(page.getByRole('link', { name: 'Bearbeiten' })).toBeVisible();
});
