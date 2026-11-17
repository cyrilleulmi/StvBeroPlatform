import type { Page } from '@playwright/test';
import { anmelden, expect, reset, test } from './hilfen';

/**
 * Nachbesserung #26 · Abmelden ist idempotent. Zwei (auch gleichzeitige) `POST /api/logout`
 * mit demselben Sitzungs-Cookie antworten beide mit 204 und löschen das Cookie, nie mit 500.
 * Die Browser-Konsole prüft `hilfen.ts` nach jedem Test.
 */

/** Sammelt alle Antworten mit Status 5xx der Seite. */
function serverfehler(page: Page): string[] {
  const fehler: string[] = [];
  page.on('response', (r) => {
    if (r.status() >= 500) fehler.push(`${r.status()} ${r.request().method()} ${new URL(r.url()).pathname}`);
  });
  return fehler;
}

/** Konsolenfehler eines weiteren Tabs (`hilfen.ts` prüft nur `page`). */
function konsolenfehler(tab: Page, erlaubt: RegExp[] = []): string[] {
  const fehler: string[] = [];
  tab.on('console', (m) => {
    if (m.type() === 'error' && !erlaubt.some((e) => e.test(m.text()))) fehler.push(m.text());
  });
  tab.on('pageerror', (e) => fehler.push(e.message));
  return fehler;
}

test.beforeEach(async ({ request }) => {
  await reset(request);
});

test('Nachbesserung #26 K1: zwei gleichzeitige Abmeldungen mit demselben Cookie antworten beide mit 204', async ({ page }) => {
  await anmelden(page, 'anna');

  const antworten = await Promise.all(
    Array.from({ length: 4 }, () => page.request.post('/api/logout')),
  );

  for (const antwort of antworten) {
    expect(antwort.status()).toBe(204);
    expect(antwort.headers()['set-cookie']).toContain('stv_session=;');
  }
  expect((await (await page.request.get('/api/session')).json()).signedIn).toBe(false);
});

test('Nachbesserung #26 K2: Doppelklick auf Abmelden im Profil führt zur Anmeldung, ohne 5xx', async ({ page }) => {
  const fehler = serverfehler(page);
  await anmelden(page, 'anna');
  await page.goto('/profil');

  await page.getByRole('button', { name: 'Abmelden' }).dblclick();

  await expect(page).toHaveURL('/dev-login');
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  expect(fehler).toEqual([]);
});

test('Nachbesserung #26 K2: gleichzeitig in zwei Tabs abmelden führt beide zur Anmeldung, ohne 5xx', async ({ page, context }) => {
  await anmelden(page, 'anna');
  const zweiter = await context.newPage();
  const fehler = [page, zweiter].map(serverfehler);
  const zweiterKonsole = konsolenfehler(zweiter);
  await page.goto('/profil');
  await zweiter.goto('/profil');

  await Promise.all([
    page.getByRole('button', { name: 'Abmelden' }).click(),
    zweiter.getByRole('button', { name: 'Abmelden' }).click(),
  ]);

  await expect(page).toHaveURL('/dev-login');
  await expect(zweiter).toHaveURL('/dev-login');
  expect(fehler.flat()).toEqual([]);
  expect(zweiterKonsole, 'Fehler in der Konsole des zweiten Tabs').toEqual([]);
  await zweiter.close();
});

test.describe('ohne aktuelle Saison', () => {
  test.use({ erwarteteKonsolenfehler: [/status of 409/] });

  test('Nachbesserung #26 K2: Doppelklick auf Abmelden im Hinweis «keine Saison» führt zur Anmeldung, ohne 5xx', async ({ page, request }) => {
    expect((await request.delete('/api/test/current-season')).status()).toBe(204);
    const fehler = serverfehler(page);
    await anmelden(page, 'anna');
    await page.goto('/');
    await expect(page).toHaveURL('/keine-saison');

    await page.getByRole('button', { name: 'Abmelden' }).dblclick();

    await expect(page).toHaveURL('/dev-login');
    expect(fehler).toEqual([]);
  });

  test('Nachbesserung #26 K2: gleichzeitig in zwei Tabs vom Hinweis «keine Saison» abmelden, ohne 5xx', async ({ page, context, request }) => {
    expect((await request.delete('/api/test/current-season')).status()).toBe(204);
    await anmelden(page, 'anna');
    const zweiter = await context.newPage();
    const fehler = [page, zweiter].map(serverfehler);
    const zweiterKonsole = konsolenfehler(zweiter, [/status of 409/]);
    await page.goto('/');
    await zweiter.goto('/');
    await expect(page).toHaveURL('/keine-saison');
    await expect(zweiter).toHaveURL('/keine-saison');

    await Promise.all([
      page.getByRole('button', { name: 'Abmelden' }).click(),
      zweiter.getByRole('button', { name: 'Abmelden' }).click(),
    ]);

    await expect(page).toHaveURL('/dev-login');
    await expect(zweiter).toHaveURL('/dev-login');
    expect(fehler.flat()).toEqual([]);
    expect(zweiterKonsole, 'Fehler in der Konsole des zweiten Tabs').toEqual([]);
    await zweiter.close();
  });
});
