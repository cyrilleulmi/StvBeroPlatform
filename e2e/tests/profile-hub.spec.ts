import type { Page } from '@playwright/test';
import { checkAccessibility, expect, login, reset, test } from './helpers';

/**
 * Profile as a hub (E-109): who you are in the club, the groups «Meine Saison» and «Einstellungen» with rows that
 * show their current state and lead to pages of their own, «Darstellung» and «Abmelden». Seed from
 * docs/test-data.md, default "now" Mon 16.11.2026 06:00.
 */

test.beforeEach(async ({ request }) => {
  await reset(request);
});

function row(page: Page, testId: string) {
  return page.getByTestId(testId);
}

test('E-109: the hub says who Petra is in the club and shows the state of each row', { tag: '@desktop' }, async ({ page }) => {
  await login(page, 'petra');
  await page.goto('/profil');
  await expect(page).toHaveTitle('Profil · STV Bero');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Profil');

  const identity = page.getByRole('region', { name: 'Petra Keller' });
  await expect(identity).toContainText('petra@example.ch');
  const roles = identity.getByRole('list', { name: 'Deine Rollen' });
  await expect(roles.getByRole('listitem')).toHaveText(['Team 1 · Coach', 'Team 2 · Spielerin']);

  const season = page.getByRole('region', { name: 'Meine Saison' });
  await expect(season.getByRole('link')).toHaveCount(2);
  await expect(season.getByRole('link', { name: /^Meine Statistik/ })).toHaveAttribute('href', '/profil/statistik');
  await expect(page.getByTestId('profile-statistics-state')).toHaveText('Wie oft du zugesagt hast und dabei warst.');
  await expect(row(page, 'my-absences-link')).toHaveAccessibleName(/^Abwesenheiten/);
  await expect(page.getByTestId('profile-absences-state')).toHaveText('Keine Abwesenheit eingetragen');

  const settings = page.getByRole('region', { name: 'Einstellungen' });
  await expect(settings.getByRole('link')).toHaveText([/Benachrichtigungen/, /Kalender abonnieren/, /Fahren/]);
  await expect(page.getByTestId('profile-notifications-state')).toHaveText('E-Mail an · Push auf diesem Gerät aus');
  await expect(page.getByTestId('profile-calendar-state')).toHaveText('Deine Termine im Kalender deines Handys');
  await expect(page.getByTestId('profile-driving-state')).toHaveText('Kann fahren · 5 Plätze');

  // «Meine Fahrten» lives with «Fahren» now
  await expect(page.getByTestId('my-trips-link')).toHaveCount(0);
  await expect(page.getByTestId('theme-switch').getByRole('radio')).toHaveCount(3);
  await expect(page.getByRole('button', { name: 'Abmelden' })).toBeVisible();
  await checkAccessibility(page);
});

test('E-109: the hub of an admin without a team shows «Admin» and no statistics', async ({ page }) => {
  await login(page, 'alex');
  await page.goto('/profil');
  const identity = page.getByRole('region', { name: 'Alex Admin' });
  await expect(identity.getByRole('list', { name: 'Deine Rollen' }).getByRole('listitem')).toHaveText(['Admin']);
  await expect(page.getByTestId('my-statistics-link')).toHaveCount(0);
  await expect(page.getByTestId('my-absences-link')).toBeVisible();
  await checkAccessibility(page);
});

test('E-109 ABS-01: the row «Abwesenheiten» names the next absence', async ({ page }) => {
  await login(page, 'sara');
  await page.goto('/profil');
  await expect(page.getByTestId('profile-absences-state')).toHaveText('Nächste: Ferien 14.12.2026 – 20.12.2026');
});

test('E-109 F-19: «Benachrichtigungen» opens its own page and leads back', { tag: '@desktop' }, async ({ page }) => {
  await login(page, 'anna');
  await page.goto('/profil');
  await row(page, 'my-notifications-link').click();
  await expect(page).toHaveURL('/profil/benachrichtigungen');
  await expect(page).toHaveTitle('Benachrichtigungen · STV Bero');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Benachrichtigungen');
  await expect(page.getByRole('heading', { level: 2 })).toHaveCount(0);
  const section = page.getByRole('region', { name: 'Benachrichtigungen' });
  await expect(section.getByRole('switch', { name: 'E-Mail' })).toBeChecked();
  await section.getByRole('switch', { name: 'E-Mail' }).click();
  await expect(section.getByRole('status')).toHaveText('E-Mails sind ausgeschaltet.');
  await checkAccessibility(page);

  await page.getByRole('link', { name: 'Zum Profil' }).click();
  await expect(page).toHaveURL('/profil');
  await expect(page.getByTestId('profile-notifications-state')).toHaveText('E-Mail aus · Push auf diesem Gerät aus');
});

test('E-109 F-18: «Kalender abonnieren» opens its own page and leads back', { tag: '@desktop' }, async ({ page }) => {
  await login(page, 'anna');
  await page.goto('/profil');
  await row(page, 'my-calendar-link').click();
  await expect(page).toHaveURL('/profil/kalender');
  await expect(page).toHaveTitle('Kalender abonnieren · STV Bero');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Kalender abonnieren');
  const section = page.getByRole('region', { name: 'Kalender abonnieren' });
  await expect(section.getByTestId('calendar-url')).toHaveValue(/\/api\/calendar\/.+\.ics$/);
  await expect(section.getByTestId('calendar-filter')).toBeVisible();
  await expect(section.getByRole('heading', { level: 2 })).toHaveText(['Was im Kalender erscheint', 'So gehts']);
  await checkAccessibility(page);

  await page.getByRole('link', { name: 'Zum Profil' }).click();
  await expect(page).toHaveURL('/profil');
});

test('E-109 DRV-18 F-20: «Fahren» holds the driving settings and the way to the trips', { tag: '@desktop' }, async ({ page }) => {
  await login(page, 'anna');
  await page.goto('/profil');
  await row(page, 'my-driving-link').click();
  await expect(page).toHaveURL('/profil/fahren');
  await expect(page).toHaveTitle('Fahren · STV Bero');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Fahren');
  const driving = page.getByRole('region', { name: 'Fahren' });
  await expect(driving).toHaveAttribute('data-testid', 'my-driving');
  await driving.getByTestId('my-can-drive').uncheck();
  await driving.getByRole('button', { name: 'Speichern' }).click();
  await expect(driving.getByRole('status')).toHaveText('Deine Angaben zum Fahren sind gespeichert.');
  await checkAccessibility(page);

  await page.getByTestId('my-trips-link').click();
  await expect(page).toHaveURL('/fahrten');
  await expect(page.getByRole('heading', { level: 1, name: 'Fahrten' })).toBeVisible();
  await page.getByRole('link', { name: 'Zum Profil' }).click();
  await expect(page).toHaveURL('/profil');
  await expect(page.getByTestId('profile-driving-state')).toHaveText('Fährt nicht');
});

test('E-109 NF-08: the three themes sit in one row on a phone', async ({ page }) => {
  await login(page, 'anna');
  await page.goto('/profil');
  const radios = page.getByTestId('theme-switch').getByRole('radio');
  const tops = await Promise.all(
    ['Hell', 'Dunkel', 'System'].map(async (name) => {
      const box = await page.getByTestId('theme-switch').getByText(name, { exact: true }).boundingBox();
      return Math.round(box?.y ?? -1);
    }),
  );
  await expect(radios).toHaveCount(3);
  expect(new Set(tops).size).toBe(1);
});

test('E-109: on a wide screen «Meine Saison» and «Einstellungen» sit right of the person', { tag: '@desktop' }, async ({ page }) => {
  test.skip((page.viewportSize()?.width ?? 0) < 1024, 'Desktop layout only');
  await login(page, 'petra');
  await page.goto('/profil');
  const identity = await page.getByRole('region', { name: 'Petra Keller' }).boundingBox();
  const season = await page.getByRole('region', { name: 'Meine Saison' }).boundingBox();
  const settings = await page.getByRole('region', { name: 'Einstellungen' }).boundingBox();
  expect(identity && season && settings).toBeTruthy();
  expect(season!.x).toBeGreaterThan(identity!.x + identity!.width);
  expect(settings!.x).toBe(season!.x);
  expect(settings!.y).toBeGreaterThan(season!.y);
});
