import { anmelden, barrierefrei, dokumentWurzel, expect, kopfLink, reset, test } from './hilfen';

/** F-00 · Grundgerüst (NF-01–NF-24, RR-05). Akzeptanzkriterien aus docs/pipeline/backlog.md. */

test.beforeEach(async ({ request }) => {
  await reset(request);
});

test.describe('Anmeldung', () => {
  test('F-00 K3: /dev-login zeigt alle Seed-Personen, ein Klick meldet an und führt auf /', async ({ page }) => {
    await page.goto('/dev-login');

    await expect(page.getByTestId('testmodus-hinweis')).toHaveText('Testmodus');
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Anmelden als …');
    const liste = page.getByRole('list', { name: 'Testpersonen' });
    await expect(liste.getByRole('listitem')).toHaveCount(17);
    for (const name of ['Alex Admin', 'Petra Keller', 'Marc Huber', 'Ruth Meier', 'Anna Muster', 'Vera Studer']) {
      await expect(liste.getByRole('button', { name: new RegExp(`^${name}`) })).toBeVisible();
    }
    await barrierefrei(page);

    await liste.getByRole('button', { name: /^Anna Muster/ }).click();

    await expect(page).toHaveURL('/');
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Hallo Anna');
  });

  test('F-00 K12: ohne Anmeldung leitet jede Route auf die Anmeldung und danach zurück', async ({ page }) => {
    for (const route of ['/', '/termine', '/teams', '/profil', '/verwaltung']) {
      await page.goto(route);
      await expect(page).toHaveURL(route === '/' ? '/dev-login' : `/dev-login?ziel=${encodeURIComponent(route)}`);
    }

    await page.goto('/profil');
    await page.getByRole('button', { name: /^Petra Keller/ }).click();

    await expect(page).toHaveURL('/profil');
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Profil');
  });

  test('F-00 K12: /anmelden erklärt das Verfahren und führt im Testmodus zu /dev-login', async ({ page }) => {
    await page.goto('/anmelden?ziel=%2Fteams');

    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Anmelden');
    await barrierefrei(page);
    await page.getByRole('link', { name: 'Zur Anmeldung im Testmodus' }).click();
    await page.getByRole('button', { name: /^Lea Graf/ }).click();

    await expect(page).toHaveURL('/teams');
  });

  test('F-00: Abmelden beendet die Sitzung', async ({ page }) => {
    await anmelden(page, 'anna');
    await page.goto('/profil');

    await page.getByRole('button', { name: 'Abmelden' }).click();

    await expect(page).toHaveURL('/dev-login');
    expect((await page.request.get('/api/me')).status()).toBe(401);
    await page.goto('/');
    await expect(page).toHaveURL('/dev-login');
  });
});

test.describe('Start und Kacheln', () => {
  test('F-00 K4 NF-11 NF-12: Start zeigt Begrüssung und die Kachel Anwesenheit, die auf /termine führt', async ({ page }) => {
    await anmelden(page, 'anna');
    await page.goto('/');

    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Hallo Anna');
    const kachel = page.getByTestId('kachel-anwesenheit');
    await expect(kachel).toHaveAccessibleName('Anwesenheit');
    await expect(kachel).toHaveAttribute('href', '/termine');
    await barrierefrei(page);

    await kachel.click();

    await expect(page).toHaveURL('/termine');
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Termine');
    // Seit F-03 gibt es Termine im Seed; den leeren Zustand prüft f03-termine.spec.ts (Alex).
    await expect(page.getByTestId('termin-karte-40000000-0000-4000-8000-000000000001')).toBeVisible();
    await barrierefrei(page);
  });

  test('F-00 K4: Teams zeigen ohne Teamzugehörigkeit einen leeren Zustand', async ({ page }) => {
    // Seit F-01 hat nur Alex (Admin) keine Teamzugehörigkeit.
    await anmelden(page, 'alex');
    await page.goto('/teams');

    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Meine Teams');
    await expect(page.getByText('Du bist noch in keinem Team.')).toBeVisible();
    await barrierefrei(page);
  });

  test('F-00: unbekannte Route zeigt einen Hinweis statt einer leeren Seite', async ({ page }) => {
    await anmelden(page, 'anna');
    await page.goto('/gibt-es-nicht');

    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Diese Seite gibt es nicht.');
    await page.getByRole('link', { name: 'Zur Startseite' }).click();
    await expect(page).toHaveURL('/');
  });
});

test.describe('Navigation', () => {
  test('F-00 K5 NF-03: Navigation mobil unten bzw. ab 1024px links, aktiver Eintrag mit aria-current', async ({ page, isMobile }) => {
    await anmelden(page, 'anna');
    await page.goto('/');

    const nav = page.getByRole('navigation', { name: 'Hauptnavigation' });
    await expect(nav).toHaveAttribute('data-testid', 'hauptnavigation');
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
      expect(b.height, 'Touch-Ziel mindestens 44px (NF-04)').toBeGreaterThanOrEqual(44);
      expect(b.width).toBeGreaterThanOrEqual(44);
    }

    await nav.getByRole('link', { name: 'Termine' }).click();

    await expect(page).toHaveURL('/termine');
    await expect(nav.getByRole('link', { name: 'Termine' })).toHaveAttribute('aria-current', 'page');
    await expect(nav.getByRole('link', { name: 'Start' })).not.toHaveAttribute('aria-current', 'page');
  });

  test('F-00 RR-05: «Verwaltung» nur für Admins', async ({ page }) => {
    await anmelden(page, 'alex');
    await page.goto('/');
    const nav = page.getByRole('navigation', { name: 'Hauptnavigation' });
    await expect(nav.getByRole('list').getByRole('link')).toHaveText(['Start', 'Termine', 'Teams', 'Profil', 'Verwaltung']);

    await nav.getByRole('link', { name: 'Verwaltung' }).click();

    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Verwaltung');
    await expect(page.getByRole('alert')).toHaveCount(0);
    await barrierefrei(page);
  });

  test('F-00 RR-05: ohne Admin keine «Verwaltung», direkter Aufruf zeigt «Keine Berechtigung»', async ({ page }) => {
    await anmelden(page, 'anna');
    await page.goto('/verwaltung');

    await expect(page.getByRole('alert')).toHaveText('Keine Berechtigung');
    await expect(page.getByRole('navigation', { name: 'Hauptnavigation' }).getByRole('link', { name: 'Verwaltung' })).toHaveCount(0);
  });
});

test.describe('Design', () => {
  const HELL = 'rgb(240, 245, 242)';
  /** Hintergrund von GitHub Dark: die dunkle Darstellung, auch bei «System» im Dunkeln (E-67). */
  const DUNKEL = 'rgb(13, 17, 23)';
  const hintergrund = (page: import('@playwright/test').Page) =>
    page.evaluate(() => getComputedStyle(document.body).backgroundColor);

  test('F-00 K6 NF-06 NF-07: Hell/GitHub Dark/System umschalten, die Wahl bleibt nach Neuladen', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'light' });
    await anmelden(page, 'anna');
    await page.goto('/profil');

    const umschalter = page.getByTestId('theme-umschalter');
    await expect(umschalter).toHaveAccessibleName('Darstellung');
    await expect(umschalter.getByRole('radio', { name: 'System' })).toBeChecked();
    expect(await hintergrund(page)).toBe(HELL);

    await umschalter.getByRole('radio', { name: 'GitHub Dark' }).check();
    await expect(dokumentWurzel(page)).toHaveAttribute('data-theme', 'github-dark');
    expect(await hintergrund(page)).toBe(DUNKEL);
    await barrierefrei(page);

    await page.reload();
    await expect(page.getByTestId('theme-umschalter').getByRole('radio', { name: 'GitHub Dark' })).toBeChecked();
    await expect(dokumentWurzel(page)).toHaveAttribute('data-theme', 'github-dark');
    expect(await hintergrund(page)).toBe(DUNKEL);

    await page.getByTestId('theme-umschalter').getByRole('radio', { name: 'Hell' }).check();
    await page.emulateMedia({ colorScheme: 'dark' });
    expect(await hintergrund(page)).toBe(HELL);

    await page.getByTestId('theme-umschalter').getByRole('radio', { name: 'System' }).check();
    await expect(dokumentWurzel(page)).not.toHaveAttribute('data-theme', /.*/);
    expect(await hintergrund(page)).toBe(DUNKEL);
    await page.reload();
    await expect(page.getByTestId('theme-umschalter').getByRole('radio', { name: 'System' })).toBeChecked();
  });

  test('F-00 K6: Start im Darkmode ohne Barrierefreiheits-Befunde', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'dark' });
    await anmelden(page, 'alex');
    await page.goto('/');

    await expect(page.getByTestId('kachel-anwesenheit')).toBeVisible();
    expect(await hintergrund(page)).toBe(DUNKEL);
    await barrierefrei(page);
  });
});

test.describe('PWA', () => {
  test('F-00 K7 NF-05: Manifest und Service Worker machen die App installierbar', async ({ page }) => {
    await anmelden(page, 'anna');
    await page.goto('/');

    const href = await kopfLink(page, 'manifest').getAttribute('href');
    expect(href).toBe('manifest.webmanifest');
    const manifest = await (await page.request.get('/manifest.webmanifest')).json();
    expect(manifest).toMatchObject({ name: 'STV Bero', start_url: '/', display: 'standalone', lang: 'de-CH' });
    const groessen = manifest.icons.map((i: { sizes: string }) => i.sizes);
    expect(groessen).toEqual(expect.arrayContaining(['192x192', '512x512']));
    for (const icon of manifest.icons) {
      expect((await page.request.get('/' + icon.src)).status(), icon.src).toBe(200);
    }

    const aktiv = await page.evaluate(async () => {
      const registrierung = await navigator.serviceWorker.ready;
      return registrierung.active?.scriptURL ?? null;
    });
    expect(aktiv).toMatch(/\/sw\.js$/);
  });
});
