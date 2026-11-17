import { checkAccessibility, documentRoot, expect, login, reset, test } from './helpers';

/**
 * Themes (NF-08, E-67): the profile offers Hell, Düne, GitHub Dark and System.
 * Düne and GitHub Dark apply regardless of the system setting, survive a reload
 * and pass the accessibility check (contrast). Hell and System are checked by
 * f00-foundation.spec.ts. Plan: e2e/plans/themes.md
 */

const THEMES = [
  { name: 'Düne', id: 'dune', color: 'rgb(243, 236, 225)', system: 'dark' },
  { name: 'GitHub Dark', id: 'github-dark', color: 'rgb(13, 17, 23)', system: 'light' },
] as const;
const EVENT_01 = '/termine/40000000-0000-4000-8000-000000000001';

const background = (page: import('@playwright/test').Page) =>
  page.evaluate(() => getComputedStyle(document.body).backgroundColor);

test.beforeEach(async ({ request }) => {
  await reset(request);
});

test('NF-08: The switch offers exactly Hell, Düne, GitHub Dark and System', async ({ page }) => {
  await login(page, 'anna');
  await page.goto('/profil');

  const themeSwitch = page.getByTestId('theme-switch');
  await expect(themeSwitch.getByRole('radio')).toHaveCount(4);
  for (const name of ['Hell', 'Düne', 'GitHub Dark', 'System']) {
    await expect(themeSwitch.getByRole('radio', { name, exact: true })).toBeVisible();
  }
});

for (const t of THEMES) {
  test(`NF-08: Choosing theme «${t.name}» persists, without accessibility findings`, async ({ page }) => {
    // The system is set to the opposite: the chosen theme must still apply.
    await page.emulateMedia({ colorScheme: t.system });
    await login(page, 'anna');
    await page.goto('/profil');

    await page.getByTestId('theme-switch').getByRole('radio', { name: t.name, exact: true }).check();
    await expect(documentRoot(page)).toHaveAttribute('data-theme', t.id);
    expect(await background(page)).toBe(t.color);
    await checkAccessibility(page);

    await page.goto('/');
    await expect(page.getByTestId('tile-anwesenheit')).toBeVisible();
    await expect(documentRoot(page)).toHaveAttribute('data-theme', t.id);
    expect(await background(page)).toBe(t.color);
    await checkAccessibility(page);

    await page.goto(EVENT_01);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await checkAccessibility(page);

    await page.goto('/profil');
    await expect(
      page.getByTestId('theme-switch').getByRole('radio', { name: t.name, exact: true }),
    ).toBeChecked();
  });
}

test('NF-08: A stored theme that no longer exists falls back to «System»', async ({ page }) => {
  await page.emulateMedia({ colorScheme: 'light' });
  await login(page, 'anna');
  await page.goto('/profil');
  await page.evaluate(() => localStorage.setItem('stv-theme', 'dunkel'));
  await page.reload();

  await expect(page.getByTestId('theme-switch').getByRole('radio', { name: 'System', exact: true })).toBeChecked();
  await expect(documentRoot(page)).not.toHaveAttribute('data-theme', /.*/);
});

test('NF-40 E-68: An old German stored theme (hell, duene) loads the app with «System» and no console errors', async ({ page }) => {
  // No migration (E-68): old values from before F-11 fall back to «System».
  // The console check of the `test` fixture fails the test on any console error.
  await page.emulateMedia({ colorScheme: 'light' });
  await login(page, 'anna');
  await page.goto('/profil');
  for (const oldValue of ['hell', 'duene']) {
    // Reload so the inline script in index.html and the theme service read the old value on start.
    await page.evaluate((value) => localStorage.setItem('stv-theme', value), oldValue);
    await page.reload();

    await expect(page.getByTestId('theme-switch').getByRole('radio', { name: 'System', exact: true })).toBeChecked();
    await expect(documentRoot(page)).not.toHaveAttribute('data-theme', /.*/);

    await page.goto('/');
    await expect(page.getByTestId('tile-anwesenheit')).toBeVisible();
    await expect(documentRoot(page)).not.toHaveAttribute('data-theme', /.*/);
    await page.goto('/profil');
  }
});
