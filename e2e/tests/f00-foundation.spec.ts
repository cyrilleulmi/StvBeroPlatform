import { checkAccessibility, documentRoot, expect, headLink, login, reset, test } from './helpers';

/** F-00 · Foundation (NF-01–NF-24, RR-05). Acceptance criteria from docs/pipeline/backlog.md. */

test.beforeEach(async ({ request }) => {
  await reset(request);
});

test.describe('Sign-in', () => {
  test('F-00 K3: /dev-login shows all seed people, one click signs in and leads to /', async ({ page }) => {
    await page.goto('/dev-login');

    await expect(page.getByTestId('test-mode-notice')).toHaveText('Testmodus');
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Anmelden als …');
    // The people are grouped by team (E-85): 17 people, Petra and Lea appear in both of their teams
    await expect(page.getByRole('listitem')).toHaveCount(19);
    for (const [key, name] of [
      ['alex', 'Alex Admin'],
      ['petra', 'Petra Keller'],
      ['marc', 'Marc Huber'],
      ['ruth', 'Ruth Meier'],
      ['anna', 'Anna Muster'],
      ['vera', 'Vera Studer'],
    ]) {
      await expect(page.getByTestId(`dev-login-person-${key}`).getByRole('button', { name: new RegExp(`^${name}`) })).toBeVisible();
    }
    await checkAccessibility(page);

    await page.getByTestId('dev-login-person-anna').getByRole('button').click();

    await expect(page).toHaveURL('/');
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Hallo Anna');
  });

  test('F-00 K12: without sign-in every route redirects to sign-in and back afterwards', async ({ page }) => {
    for (const route of ['/', '/termine', '/teams', '/profil', '/verwaltung']) {
      await page.goto(route);
      await expect(page).toHaveURL(route === '/' ? '/dev-login' : `/dev-login?ziel=${encodeURIComponent(route)}`);
    }

    await page.goto('/profil');
    await page.getByTestId('dev-login-person-petra').getByRole('button').click();

    await expect(page).toHaveURL('/profil');
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Profil');
  });

  test('F-00 K12: /anmelden explains the procedure and leads to /dev-login in test mode', async ({ page }) => {
    await page.goto('/anmelden?ziel=%2Fteams');

    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Anmelden');
    await checkAccessibility(page);
    await page.getByRole('link', { name: 'Zur Anmeldung im Testmodus' }).click();
    await page.getByTestId('dev-login-person-lea').getByRole('button').click();

    await expect(page).toHaveURL('/teams');
  });

  test('F-00: Signing out ends the session', async ({ page }) => {
    await login(page, 'anna');
    await page.goto('/profil');

    await page.getByRole('button', { name: 'Abmelden' }).click();

    await expect(page).toHaveURL('/dev-login');
    expect((await page.request.get('/api/me')).status()).toBe(401);
    await page.goto('/');
    await expect(page).toHaveURL('/dev-login');
  });
});

test.describe('Start and tiles', () => {
  test('F-00 K4 NF-11 NF-12: Start shows the greeting and the tile Anwesenheit, which leads to /termine', async ({ page }) => {
    await login(page, 'anna');
    await page.goto('/');

    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Hallo Anna');
    const tile = page.getByTestId('tile-anwesenheit');
    await expect(tile).toHaveAccessibleName('Anwesenheit');
    await expect(tile).toHaveAttribute('href', '/termine');
    await checkAccessibility(page);

    await tile.click();

    await expect(page).toHaveURL('/termine');
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Termine');
    // Since F-03 the seed has events; f03-events.spec.ts checks the empty state (Alex).
    await expect(page.getByTestId('event-card-40000000-0000-4000-8000-000000000001')).toBeVisible();
    await checkAccessibility(page);
  });

  test('F-00 K4: Teams show an empty state without team membership', async ({ page }) => {
    // Since F-01 only Alex (admin) has no team membership.
    await login(page, 'alex');
    await page.goto('/teams');

    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Meine Teams');
    await expect(page.getByText('Du bist noch in keinem Team.')).toBeVisible();
    await checkAccessibility(page);
  });

  test('F-00: unknown route shows a notice instead of a blank page', async ({ page }) => {
    await login(page, 'anna');
    await page.goto('/gibt-es-nicht');

    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Diese Seite gibt es nicht.');
    await page.getByRole('link', { name: 'Zur Startseite' }).click();
    await expect(page).toHaveURL('/');
  });
});

test.describe('Navigation', () => {
  test('F-00 K5 NF-03: Navigation at the bottom on mobile, on the left from 1024px, active entry with aria-current', async ({ page, isMobile }) => {
    await login(page, 'anna');
    await page.goto('/');

    const nav = page.getByRole('navigation', { name: 'Hauptnavigation' });
    await expect(nav).toHaveAttribute('data-testid', 'main-navigation');
    await expect(nav.getByRole('list').getByRole('link')).toHaveText(['Start', 'Termine', 'Teams', 'Profil']);
    await expect(nav.getByRole('link', { name: 'Start' })).toHaveAttribute('aria-current', 'page');

    const box = (await nav.boundingBox())!;
    const viewport = page.viewportSize()!;
    if (isMobile) {
      expect(box.y + box.height).toBeCloseTo(viewport.height, 0);
      expect(box.width).toBe(viewport.width);
    } else {
      expect(box.x).toBe(0);
      expect(box.height).toBeCloseTo(viewport.height, 0);
      expect(box.width).toBeLessThan(viewport.width / 3);
    }

    for (const link of await nav.getByRole('link').all()) {
      const b = (await link.boundingBox())!;
      expect(b.height, 'Touch target at least 44px (NF-04)').toBeGreaterThanOrEqual(44);
      expect(b.width).toBeGreaterThanOrEqual(44);
    }

    await nav.getByRole('link', { name: 'Termine' }).click();

    await expect(page).toHaveURL('/termine');
    await expect(nav.getByRole('link', { name: 'Termine' })).toHaveAttribute('aria-current', 'page');
    await expect(nav.getByRole('link', { name: 'Start' })).not.toHaveAttribute('aria-current', 'page');
  });

  test('F-00 RR-05: «Verwaltung» only for admins', async ({ page }) => {
    await login(page, 'alex');
    await page.goto('/');
    const nav = page.getByRole('navigation', { name: 'Hauptnavigation' });
    await expect(nav.getByRole('list').getByRole('link')).toHaveText(['Start', 'Termine', 'Teams', 'Profil', 'Verwaltung']);

    await nav.getByRole('link', { name: 'Verwaltung' }).click();

    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Verwaltung');
    await expect(page.getByRole('alert')).toHaveCount(0);
    await checkAccessibility(page);
  });

  test('F-00 RR-05: no «Verwaltung» without admin, direct access shows «Keine Berechtigung»', async ({ page }) => {
    await login(page, 'anna');
    await page.goto('/verwaltung');

    await expect(page.getByRole('alert')).toHaveText('Keine Berechtigung');
    await expect(page.getByRole('navigation', { name: 'Hauptnavigation' }).getByRole('link', { name: 'Verwaltung' })).toHaveCount(0);
  });
});

test.describe('Design', () => {
  const LIGHT = 'rgb(233, 237, 240)';
  /** Background of Dunkel: the dark theme, also with «System» in the dark (E-90). */
  const DARK = 'rgb(20, 24, 29)';
  const background = (page: import('@playwright/test').Page) =>
    page.evaluate(() => getComputedStyle(document.body).backgroundColor);

  test('F-00 K6 NF-06 NF-07: Switch Hell/Dunkel/System, the choice stays after reload', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'light' });
    await login(page, 'anna');
    await page.goto('/profil');

    const themeSwitch = page.getByTestId('theme-switch');
    await expect(themeSwitch).toHaveAccessibleName('Darstellung');
    await expect(themeSwitch.getByRole('radio', { name: 'System' })).toBeChecked();
    expect(await background(page)).toBe(LIGHT);

    await themeSwitch.getByRole('radio', { name: 'Dunkel' }).check();
    await expect(documentRoot(page)).toHaveAttribute('data-theme', 'dark');
    expect(await background(page)).toBe(DARK);
    await checkAccessibility(page);

    await page.reload();
    await expect(page.getByTestId('theme-switch').getByRole('radio', { name: 'Dunkel' })).toBeChecked();
    await expect(documentRoot(page)).toHaveAttribute('data-theme', 'dark');
    expect(await background(page)).toBe(DARK);

    await page.getByTestId('theme-switch').getByRole('radio', { name: 'Hell' }).check();
    await page.emulateMedia({ colorScheme: 'dark' });
    expect(await background(page)).toBe(LIGHT);

    await page.getByTestId('theme-switch').getByRole('radio', { name: 'System' }).check();
    await expect(documentRoot(page)).not.toHaveAttribute('data-theme', /.*/);
    expect(await background(page)).toBe(DARK);
    await page.reload();
    await expect(page.getByTestId('theme-switch').getByRole('radio', { name: 'System' })).toBeChecked();
  });

  test('F-00 K6: Start in dark mode without accessibility findings', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'dark' });
    await login(page, 'alex');
    await page.goto('/');

    await expect(page.getByTestId('tile-anwesenheit')).toBeVisible();
    expect(await background(page)).toBe(DARK);
    await checkAccessibility(page);
  });
});

test.describe('PWA', () => {
  test('F-00 K7 NF-05: Manifest and service worker make the app installable', async ({ page }) => {
    await login(page, 'anna');
    await page.goto('/');

    const href = await headLink(page, 'manifest').getAttribute('href');
    expect(href).toBe('manifest.webmanifest');
    const manifest = await (await page.request.get('/manifest.webmanifest')).json();
    expect(manifest).toMatchObject({ name: 'STV Bero', start_url: '/', display: 'standalone', lang: 'de-CH' });
    const sizes = manifest.icons.map((i: { sizes: string }) => i.sizes);
    expect(sizes).toEqual(expect.arrayContaining(['192x192', '512x512']));
    for (const icon of manifest.icons) {
      expect((await page.request.get('/' + icon.src)).status(), icon.src).toBe(200);
    }

    const active = await page.evaluate(async () => {
      const registration = await navigator.serviceWorker.ready;
      return registration.active?.scriptURL ?? null;
    });
    expect(active).toMatch(/\/sw\.js$/);
  });
});
