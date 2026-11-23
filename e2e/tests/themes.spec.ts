import { checkAccessibility, documentRoot, expect, login, reset, test } from './helpers';

/**
 * Themes (NF-08, E-90): the profile offers Hell, Dunkel and System. Dunkel applies regardless of the
 * system setting, survives a reload
 * and pass the accessibility check (contrast). Hell and System are checked by
 * f00-foundation.spec.ts. Plan: e2e/plans/themes.md
 */

const THEMES = [
  { name: 'Dunkel', id: 'dark', color: 'rgb(20, 24, 29)', system: 'light' },
] as const;
const EVENT_01 = '/termine/40000000-0000-4000-8000-000000000001';

const background = (page: import('@playwright/test').Page) =>
  page.evaluate(() => getComputedStyle(document.body).backgroundColor);

test.beforeEach(async ({ request }) => {
  await reset(request);
});

test('NF-08: The switch offers exactly Hell, Dunkel and System', async ({ page }) => {
  await login(page, 'anna');
  await page.goto('/profil');

  const themeSwitch = page.getByTestId('theme-switch');
  await expect(themeSwitch.getByRole('radio')).toHaveCount(3);
  for (const name of ['Hell', 'Dunkel', 'System']) {
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

test('NF-08 E-98: A stored theme that no longer exists falls back to «Hell»', async ({ page }) => {
  await page.emulateMedia({ colorScheme: 'light' });
  await login(page, 'anna');
  await page.goto('/profil');
  await page.evaluate(() => localStorage.setItem('stv-theme', 'dunkel'));
  await page.reload();

  await expect(page.getByTestId('theme-switch').getByRole('radio', { name: 'Hell', exact: true })).toBeChecked();
  await expect(documentRoot(page)).toHaveAttribute('data-theme', 'light');
});

test('NF-40 E-68 E-90: An old stored theme (hell, duene, dune, github-dark) loads the app with «Hell» and no console errors', async ({ page }) => {
  // No migration (E-68): old values from before F-11 fall back to «Hell» (E-98).
  // The console check of the `test` fixture fails the test on any console error.
  await page.emulateMedia({ colorScheme: 'light' });
  await login(page, 'anna');
  await page.goto('/profil');
  for (const oldValue of ['hell', 'duene', 'dune', 'github-dark']) {
    // Reload so the inline script in index.html and the theme service read the old value on start.
    await page.evaluate((value) => localStorage.setItem('stv-theme', value), oldValue);
    await page.reload();

    await expect(page.getByTestId('theme-switch').getByRole('radio', { name: 'Hell', exact: true })).toBeChecked();
    await expect(documentRoot(page)).toHaveAttribute('data-theme', 'light');

    await page.goto('/');
    await expect(page.getByTestId('tile-anwesenheit')).toBeVisible();
    await expect(documentRoot(page)).toHaveAttribute('data-theme', 'light');
    await page.goto('/profil');
  }
});
