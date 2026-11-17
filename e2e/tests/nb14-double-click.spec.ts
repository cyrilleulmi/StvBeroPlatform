import type { Locator, Page } from '@playwright/test';
import { anmelden, expect, reset, test } from './helpers';

/**
 * Nachbesserung #14 · Schreibende Aktionen sind gesperrt, solange ihre Anfrage läuft.
 * Ein Doppelklick löst genau eine Anfrage aus (docs/zustaende.md, Abschnitt «Formulare»).
 *
 * Damit der zweite Klick sicher in die laufende Anfrage fällt, hält `bremsen` die Antwort
 * kurz zurück. Während dieser Zeit muss die Schaltfläche `disabled` und `aria-busy` sein.
 */

const TEAM_1 = '10000000-0000-4000-8000-000000000001';
const ANNA = '20000000-0000-4000-8000-000000000005';

interface Bremse {
  /** Anzahl Anfragen mit Methode und Pfad, seit `bremsen` aufgerufen wurde. */
  anzahl: number;
  /** Gibt die zurückgehaltenen Antworten frei. */
  freigeben: () => void;
}

/** Zählt Anfragen (Methode + Pfad) und hält sie zurück, bis `freigeben` aufgerufen wird. */
async function bremsen(page: Page, methode: string, pfad: RegExp): Promise<Bremse> {
  let freigeben!: () => void;
  const frei = new Promise<void>((r) => (freigeben = r));
  const bremse: Bremse = { anzahl: 0, freigeben: () => freigeben() };
  page.on('request', (r) => {
    if (r.method() === methode && pfad.test(new URL(r.url()).pathname)) bremse.anzahl++;
  });
  await page.route(
    (url) => pfad.test(url.pathname),
    async (route) => {
      if (route.request().method() === methode) await frei;
      await route.fallback();
    },
  );
  return bremse;
}

/**
 * Doppelklick, dann prüfen, dass die Schaltfläche während der Anfrage gesperrt und als
 * «läuft» markiert ist. `andere` Schaltflächen daneben sind gesperrt, aber ohne `aria-busy`
 * (docs/testbarkeit.md «Aktion läuft»).
 */
async function doppelklickWaehrendAnfrage(
  knopf: Locator,
  bremse: Bremse,
  andere: Locator[] = [],
): Promise<void> {
  await knopf.dblclick();
  await expect(knopf).toBeDisabled();
  await expect(knopf).toHaveAttribute('aria-busy', 'true');
  for (const a of andere) {
    await expect(a).toBeDisabled();
    await expect(a).not.toHaveAttribute('aria-busy');
  }
  bremse.freigeben();
}

test.beforeEach(async ({ request }) => {
  await reset(request);
});

test('Nachbesserung #14 K1: Doppelklick unter /dev-login meldet genau einmal an', async ({ page }) => {
  await page.goto('/dev-login');
  const bremse = await bremsen(page, 'POST', /^\/api\/test\/login$/);
  const anna = page.getByRole('button', { name: /Anna Muster/ });
  const petra = page.getByRole('button', { name: /Petra Keller/ });

  await anna.dblclick();
  await expect(anna).toBeDisabled();
  await expect(anna).toHaveAttribute('aria-busy', 'true');
  // Auch die anderen Personen sind gesperrt, aber nicht als «läuft» markiert.
  await expect(petra).toBeDisabled();
  await expect(petra).not.toHaveAttribute('aria-busy');
  bremse.freigeben();

  await expect(page).toHaveURL('/');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Hallo Anna');
  expect(bremse.anzahl).toBe(1);
});

test.describe('fehlerhafte Anmeldung', () => {
  // Der Browser meldet die bewusst ausgelöste 503-Antwort in der Konsole.
  test.use({ erwarteteKonsolenfehler: [/status of 503/] });

  test('Nachbesserung #14 K1: Nach einem Fehler ist die Anmeldung unter /dev-login wieder möglich', async ({ page }) => {
    await page.goto('/dev-login');
    let erster = true;
    await page.route('**/api/test/login', async (route) => {
      if (erster) {
        erster = false;
        await route.fulfill({ status: 503, body: '' });
        return;
      }
      await route.fallback();
    });
    const anna = page.getByRole('button', { name: /Anna Muster/ });

    await anna.click();
    await expect(page.getByRole('alert')).toBeVisible();
    await expect(anna).toBeEnabled();
    await expect(anna).not.toHaveAttribute('aria-busy');

    await anna.click();
    await expect(page).toHaveURL('/');
  });
});

test('Nachbesserung #14 K2: Doppelklick auf Abmelden im Profil meldet einmal ab', async ({ page }) => {
  await anmelden(page, 'anna');
  await page.goto('/profil');
  const bremse = await bremsen(page, 'POST', /^\/api\/logout$/);

  await doppelklickWaehrendAnfrage(page.getByRole('button', { name: 'Abmelden' }), bremse);

  await expect(page).toHaveURL('/dev-login');
  expect(bremse.anzahl).toBe(1);
});

test.describe('ohne aktuelle Saison', () => {
  test.use({ erwarteteKonsolenfehler: [/status of 409/] });

  test('Nachbesserung #14 K2: Doppelklick auf Abmelden im Hinweis «keine Saison» meldet einmal ab', async ({ page, request }) => {
    expect((await request.delete('/api/test/current-season')).status()).toBe(204);
    await anmelden(page, 'anna');
    await page.goto('/');
    await expect(page).toHaveURL('/keine-saison');
    const bremse = await bremsen(page, 'POST', /^\/api\/logout$/);

    await doppelklickWaehrendAnfrage(page.getByRole('button', { name: 'Abmelden' }), bremse);

    await expect(page).toHaveURL('/dev-login');
    expect(bremse.anzahl).toBe(1);
  });
});

test('Nachbesserung #14 K2: Doppelklick auf Umbenennen speichert den Namen einmal', async ({ page }) => {
  await anmelden(page, 'alex');
  await page.goto(`/verwaltung/teams/${TEAM_1}`);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Team 1');
  const bremse = await bremsen(page, 'PUT', new RegExp(`^/api/teams/${TEAM_1}$`));
  await page.getByLabel('Teamname').fill('Damen 1');

  await doppelklickWaehrendAnfrage(page.getByRole('button', { name: 'Umbenennen' }), bremse, [
    page.getByRole('button', { name: 'Aufnehmen' }),
    page.getByRole('button', { name: 'Anna Muster entfernen' }),
  ]);

  await expect(page.getByRole('status')).toHaveText('Name gespeichert.');
  expect(bremse.anzahl).toBe(1);
});

test('Nachbesserung #14 K2: Doppelklick auf Entfernen entfernt die Person einmal, ohne Fehler', async ({ page }) => {
  await anmelden(page, 'alex');
  await page.goto(`/verwaltung/teams/${TEAM_1}`);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Team 1');
  const bremse = await bremsen(page, 'DELETE', new RegExp(`/members/${ANNA}$`));

  await doppelklickWaehrendAnfrage(
    page.getByRole('button', { name: 'Anna Muster entfernen' }),
    bremse,
    [
      page.getByRole('button', { name: 'Umbenennen' }),
      page.getByRole('button', { name: 'Aufnehmen' }),
      page.getByRole('button', { name: /entfernen$/ }).filter({ hasNotText: 'Anna Muster' }).first(),
    ],
  );

  await expect(page.getByRole('status')).toHaveText('Anna Muster ist nicht mehr im Team.');
  await expect(page.getByRole('alert')).toHaveCount(0);
  expect(bremse.anzahl).toBe(1);
});

test('Nachbesserung #14 K2: Doppelklick auf Speichern einer Person speichert einmal', async ({ page }) => {
  await anmelden(page, 'alex');
  await page.goto(`/verwaltung/personen/${ANNA}`);
  await expect(page.getByLabel('Vorname')).toHaveValue('Anna');
  const bremse = await bremsen(page, 'PUT', new RegExp(`^/api/persons/${ANNA}$`));
  await page.getByLabel('Vorname').fill('Annina');

  await doppelklickWaehrendAnfrage(page.getByRole('button', { name: 'Speichern' }), bremse);

  await expect(page.getByRole('status')).toHaveText('Änderungen gespeichert.');
  expect(bremse.anzahl).toBe(1);
});

test('Nachbesserung #14 K2: Doppelklick auf «Zur aktuellen Saison machen» wechselt einmal', async ({ page }) => {
  await anmelden(page, 'alex');
  await page.goto('/verwaltung/saisons');
  await expect(page.getByLabel('Name')).toHaveValue('2027/28');
  await page.getByRole('button', { name: 'Saison anlegen' }).click();
  await expect(page.getByRole('status')).toContainText('2027/28 ist angelegt');
  const bremse = await bremsen(page, 'POST', /^\/api\/seasons\/[^/]+\/activate$/);

  const knopf = page
    .getByRole('listitem', { name: '2027/28' })
    .getByRole('button', { name: 'Zur aktuellen Saison machen' });
  await doppelklickWaehrendAnfrage(knopf, bremse, [
    page.getByRole('button', { name: 'Saison anlegen' }),
  ]);

  await expect(page.getByRole('status')).toHaveText('2027/28 ist jetzt die aktuelle Saison.');
  await expect(page.getByRole('alert')).toHaveCount(0);
  expect(bremse.anzahl).toBe(1);
});
