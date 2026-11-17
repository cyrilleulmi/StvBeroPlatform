import type { Locator, Page } from '@playwright/test';
import { checkAccessibility, expect, login, reset, test } from './helpers';

/**
 * F-04 · An- und Abmeldung mit Übersicht (ANM, RR-02, RR-03, RR-06). Akzeptanzkriterien aus Issue #5 bzw.
 * docs/pipeline/backlog.md, Szenarien S1–S5, S7, S8 aus docs/testdaten.md. Standard-«Jetzt»: Mo 16.11.2026 06:00.
 */

const TEAM_1 = '10000000-0000-4000-8000-000000000001';
const TEAM_2 = '10000000-0000-4000-8000-000000000002';
const PETRA = '20000000-0000-4000-8000-000000000002';
const MARC = '20000000-0000-4000-8000-000000000003';
const ANNA = '20000000-0000-4000-8000-000000000005';
const LEA = '20000000-0000-4000-8000-000000000006';
const MIA = '20000000-0000-4000-8000-000000000007';
const SARA = '20000000-0000-4000-8000-000000000008';
const NINA = '20000000-0000-4000-8000-000000000009';
const JANA = '20000000-0000-4000-8000-000000000010';
const KIM = '20000000-0000-4000-8000-000000000011';
const EVA = '20000000-0000-4000-8000-000000000012';
const LARA = '20000000-0000-4000-8000-000000000013';
const ZOE = '20000000-0000-4000-8000-000000000014';
const ELLA = '20000000-0000-4000-8000-000000000015';
const T01 = '40000000-0000-4000-8000-000000000001';
const T02 = '40000000-0000-4000-8000-000000000002';
const T03 = '40000000-0000-4000-8000-000000000003';
const LINIE_1_T1 = '30000000-0000-4000-8000-000000000001';
const LINIE_2_T1 = '30000000-0000-4000-8000-000000000002';
const GOALIES_T1 = '30000000-0000-4000-8000-000000000003';
const LINIE_1_T2 = '30000000-0000-4000-8000-000000000004';
const LINIE_2_T2 = '30000000-0000-4000-8000-000000000005';
const GOALIES_T2 = '30000000-0000-4000-8000-000000000006';

function karte(page: Page, id: string): Locator {
  return page.getByTestId(`event-card-${id}`);
}

function knopf(bereich: Locator, wahl: 'dabei' | 'nicht-dabei' | 'vielleicht'): Locator {
  return bereich.getByTestId('my-response').getByTestId(`response-${wahl}`);
}

/** Zählertext, z.B. «7 dabei · 2 nicht dabei · 1 vielleicht · 2 offen». */
function z(dabei: number, nichtDabei: number, vielleicht: number, offen: number): string {
  return `${dabei} dabei · ${nichtDabei} nicht dabei · ${vielleicht} vielleicht · ${offen} offen`;
}

/** Personen einer Gruppe in Anzeigereihenfolge, als «<personId> <Antwort>». */
async function personen(gruppe: Locator): Promise<string[]> {
  return gruppe
    .getByRole('listitem')
    .evaluateAll((li) =>
      li.map(
        (e) =>
          `${e.getAttribute('data-testid')?.replace('uebersicht-person-', '')} ${e.getAttribute('data-antwort')}`,
      ),
    );
}

test.beforeEach(async ({ request }) => {
  await reset(request);
});

test('F-04 K1 K2 S1 ANM-20 bis ANM-32: Übersicht von Termin 01 wie in testdaten.md', async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/termine/${T01}`);
  const uebersicht = page.getByTestId('overview');
  await expect(uebersicht.getByRole('heading', { level: 2, name: 'Übersicht' })).toBeVisible();

  // ANM-23, ANM-32: Gesamttotal, Lea zählt einmal
  await expect(page.getByTestId('overview-total')).toContainText(z(7, 2, 1, 2));
  await expect(page.getByTestId(`overview-team-${TEAM_1}-counter`)).toContainText(z(5, 1, 0, 1));
  await expect(page.getByTestId(`overview-team-${TEAM_2}-counter`)).toContainText(z(3, 1, 1, 1));

  // ANM-20: Teams nach Name; ANM-21: Linien, Goalies, «Ohne Linie»
  await expect(uebersicht.getByRole('heading', { level: 3 })).toHaveText(['Team 1', 'Team 2', 'Coaches']);
  const team1 = page.getByTestId(`overview-team-${TEAM_1}`);
  await expect(team1.getByRole('heading', { level: 4 })).toHaveText([
    'Linie 1',
    'Linie 2',
    'Goalies',
    'Ohne Linie',
  ]);
  expect(await personen(page.getByTestId(`overview-group-${LINIE_1_T1}`))).toEqual([
    `${MIA} DECLINED`,
    `${LEA} ATTENDING`,
    `${ANNA} ATTENDING`,
  ]);
  expect(await personen(page.getByTestId(`overview-group-${LINIE_2_T1}`))).toEqual([
    `${SARA} ATTENDING`,
    `${NINA} PENDING`,
  ]);
  expect(await personen(page.getByTestId(`overview-group-${GOALIES_T1}`))).toEqual([`${JANA} ATTENDING`]);
  expect(await personen(page.getByTestId(`overview-no-line-${TEAM_1}`))).toEqual([`${KIM} ATTENDING`]);

  // ANM-31: Petra als Spielerin in Team 2, Lea in beiden Teams; Team 2 ohne «Ohne Linie» (leer)
  const team2 = page.getByTestId(`overview-team-${TEAM_2}`);
  await expect(team2.getByRole('heading', { level: 4 })).toHaveText(['Linie 1', 'Linie 2', 'Goalies']);
  expect(await personen(page.getByTestId(`overview-group-${LINIE_1_T2}`))).toEqual([
    `${PETRA} ATTENDING`,
    `${EVA} MAYBE`,
    `${LARA} ATTENDING`,
  ]);
  expect(await personen(page.getByTestId(`overview-group-${LINIE_2_T2}`))).toEqual([
    `${LEA} ATTENDING`,
    `${ZOE} DECLINED`,
  ]);
  expect(await personen(page.getByTestId(`overview-group-${GOALIES_T2}`))).toEqual([`${ELLA} PENDING`]);
  await expect(page.getByTestId(`overview-no-line-${TEAM_2}`)).toHaveCount(0);

  // ANM-22: Coach-Block mit Team, Petra nicht unter den Spielerinnen von Team 1
  const coaches = page.getByTestId('coach-block');
  expect(await personen(coaches)).toEqual([`${PETRA} ATTENDING`, `${MARC} DECLINED`]);
  await expect(coaches.getByTestId(`overview-person-${PETRA}`)).toContainText('(Team 1)');
  await expect(coaches.getByTestId(`overview-person-${MARC}`)).toContainText('(Team 2)');
  await expect(team1.getByTestId(`overview-person-${PETRA}`)).toHaveCount(0);

  // ANM-24: Status als Text, nicht nur als Farbe
  await expect(team1.getByTestId(`overview-person-${MIA}`)).toContainText('nicht dabei');
  await expect(team1.getByTestId(`overview-person-${NINA}`)).toContainText('offen');
  await checkAccessibility(page);
});

test('F-04 K3 S2 ANM-08: Antwort mit einem Tap aus der Terminliste, Zähler ohne Neuladen', async ({
  page,
}) => {
  await login(page, 'nina');
  await page.goto('/termine');
  const k = karte(page, T01);
  await expect(knopf(k, 'dabei')).toHaveAttribute('aria-pressed', 'false');
  await expect(k.getByTestId('event-counter')).toContainText(z(7, 2, 1, 2));
  await knopf(k, 'dabei').click();
  await expect(k.getByRole('status')).toHaveText('Gespeichert: dabei.');
  await expect(knopf(k, 'dabei')).toHaveAttribute('aria-pressed', 'true');
  await expect(k.getByTestId('my-response')).toHaveAttribute('data-antwort', 'ATTENDING');
  await expect(k.getByTestId('event-counter')).toContainText(z(8, 2, 1, 1));
  // Kein Dialog bei «dabei»: wirklich ein Tap
  await expect(page.getByRole('dialog')).toHaveCount(0);

  // Übersicht: Team 1 zeigt 6 dabei, 0 offen
  await k.getByRole('link').click();
  await expect(page).toHaveURL(new RegExp(`/termine/${T01}$`));
  await expect(page.getByTestId(`overview-team-${TEAM_1}-counter`)).toContainText(z(6, 1, 0, 0));
  await checkAccessibility(page);
});

test('F-04 K3 K5 ANM-08 ANM-05: «nicht dabei» aus dem Detail, Grund überspringen', async ({ page }) => {
  await login(page, 'nina');
  await page.goto(`/termine/${T01}`);
  const team1Zaehler = page.getByTestId(`overview-team-${TEAM_1}-counter`);
  await expect(team1Zaehler).toContainText(z(5, 1, 0, 1));
  let navigationen = 0;
  page.on('framenavigated', () => navigationen++);

  await knopf(page.getByRole('main'), 'nicht-dabei').click();
  // Die Antwort ist schon gespeichert, der Grund ist freiwillig (ANM-05)
  const dialog = page.getByRole('dialog', { name: 'Nicht dabei' });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByLabel('Grund (freiwillig)')).toBeVisible();
  await expect(team1Zaehler).toContainText(z(5, 2, 0, 0));
  await dialog.getByRole('button', { name: 'Überspringen' }).click();
  await expect(dialog).toBeHidden();
  await expect(knopf(page.getByRole('main'), 'nicht-dabei')).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByTestId(`overview-person-${NINA}`)).toHaveAttribute('data-antwort', 'DECLINED');
  expect(navigationen).toBe(0);

  // K10: nach Neuladen unverändert
  await page.reload();
  await expect(knopf(page.getByRole('main'), 'nicht-dabei')).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByTestId(`overview-person-${NINA}`)).toHaveAttribute('data-antwort', 'DECLINED');
});

test('F-04 K5 ANM-05: «nicht dabei» mit Grund', async ({ page }) => {
  await login(page, 'nina');
  await page.goto(`/termine/${T01}`);
  await knopf(page.getByRole('main'), 'nicht-dabei').click();
  const dialog = page.getByRole('dialog', { name: 'Nicht dabei' });
  await dialog.getByLabel('Grund (freiwillig)').fill('Prüfung');
  await checkAccessibility(page);
  await dialog.getByRole('button', { name: 'Grund speichern' }).click();
  await expect(dialog).toBeHidden();
  await expect(page.getByRole('status')).toContainText('Grund gespeichert.');
  await expect(page.getByTestId('my-response')).toContainText('Dein Kommentar: Prüfung');
  // Erneut «nicht dabei»: Grund ändern, ohne die Antwort neu zu setzen
  await knopf(page.getByRole('main'), 'nicht-dabei').click();
  await expect(dialog.getByLabel('Grund (freiwillig)')).toHaveValue('Prüfung');
  await dialog.getByRole('button', { name: 'Überspringen' }).click();
  await page.reload();
  await expect(page.getByTestId('my-response')).toContainText('Dein Kommentar: Prüfung');
});

test('F-04 K4 S3 ANM-04: «vielleicht» ohne Kommentar nicht möglich, Fehlermeldung beim Feld', async ({
  page,
}) => {
  await login(page, 'nina');
  await page.goto(`/termine/${T01}`);
  await knopf(page.getByRole('main'), 'vielleicht').click();
  const dialog = page.getByRole('dialog', { name: 'Vielleicht – bitte kurz begründen' });
  await expect(dialog).toBeVisible();
  await dialog.getByRole('button', { name: 'Speichern' }).click();
  const feld = dialog.getByLabel('Kommentar');
  await expect(feld).toHaveAttribute('aria-invalid', 'true');
  await expect(feld).toHaveAccessibleDescription('Bei «vielleicht» bitte kurz begründen.');
  await expect(feld).toBeFocused();
  await checkAccessibility(page);
  // Nichts gespeichert
  await dialog.getByRole('button', { name: 'Abbrechen' }).click();
  await expect(knopf(page.getByRole('main'), 'vielleicht')).toHaveAttribute('aria-pressed', 'false');
  const api = await page.request.get(`/api/events/${T01}`);
  expect((await api.json()).myResponse.status).toBe('PENDING');

  // Mit Kommentar geht es
  await knopf(page.getByRole('main'), 'vielleicht').click();
  await feld.fill('Knie');
  await expect(feld).not.toHaveAttribute('aria-invalid', 'true');
  await dialog.getByRole('button', { name: 'Speichern' }).click();
  await expect(dialog).toBeHidden();
  await expect(knopf(page.getByRole('main'), 'vielleicht')).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByTestId('my-response')).toContainText('Dein Kommentar: Knie');
});

test('F-04 K4 S3 ANM-04: API lehnt «vielleicht» ohne Kommentar mit 422 ab', async ({ page }) => {
  await login(page, 'nina');
  const antwort = await page.request.put(`/api/events/${T01}/responses/me`, {
    data: { status: 'MAYBE' },
  });
  expect(antwort.status()).toBe(422);
  const fehler = await antwort.json();
  expect(fehler.rule).toBe('ANM-04');
  expect(fehler.fields[0].field).toBe('comment');
});

test('F-04 K6 S4 ANM-07: Kommentare nur für Coaches des Teams und die Person selbst', async ({
  page,
  browser,
}) => {
  await login(page, 'anna');
  await page.goto(`/termine/${T01}`);
  await expect(page.getByTestId(`overview-person-${MIA}`)).toHaveAttribute('data-antwort', 'DECLINED');
  await expect(page.getByTestId('overview')).not.toContainText('Krank');
  await expect(page.getByTestId('overview')).not.toContainText('Komme evtl. später');
  await expect(page.getByTestId('overview')).not.toContainText('Ferien');
  // Auch nicht in der API-Antwort
  const json = JSON.stringify(await (await page.request.get(`/api/events/${T01}/overview`)).json());
  expect(json).not.toContain('comment');

  const petraKontext = await browser.newContext();
  const petra = await petraKontext.newPage();
  await login(petra, 'petra');
  await petra.goto(`/termine/${T01}`);
  await expect(petra.getByTestId(`overview-person-${MIA}`)).toContainText('«Krank»');
  // RR-06: In Team 2 ist Petra Spielerin, Evas und Marcs Kommentare sieht sie nicht
  await expect(petra.getByTestId(`overview-person-${EVA}`)).not.toContainText('Komme evtl. später');
  await expect(petra.getByTestId('coach-block')).not.toContainText('Ferien');
  await petraKontext.close();

  const marcKontext = await browser.newContext();
  const marc = await marcKontext.newPage();
  await login(marc, 'marc');
  await marc.goto(`/termine/${T01}`);
  await expect(marc.getByTestId(`overview-person-${EVA}`)).toContainText('«Komme evtl. später»');
  await expect(marc.getByTestId(`overview-person-${MIA}`)).not.toContainText('Krank');
  await marcKontext.close();
});

test('F-04 K7 S5 ANM-10: Kurzfristige Absage nach der Antwortfrist', async ({ page, browser }) => {
  const uhr = await page.request.put('/api/test/clock', { data: { now: '2026-11-16T19:00:00+01:00' } });
  expect(uhr.status()).toBe(204);
  await login(page, 'anna');
  await page.goto(`/termine/${T01}`);
  await knopf(page.getByRole('main'), 'nicht-dabei').click();
  await page.getByRole('dialog', { name: 'Nicht dabei' }).getByRole('button', { name: 'Überspringen' }).click();
  await expect(page.getByTestId('my-response')).toContainText('kurzfristig abgemeldet');
  const api = await page.request.get(`/api/events/${T01}`);
  expect((await api.json()).myResponse.lateCancellation).toBe(true);

  const petraKontext = await browser.newContext();
  const petra = await petraKontext.newPage();
  await login(petra, 'petra');
  await petra.goto(`/termine/${T01}`);
  await expect(petra.getByTestId(`overview-person-${ANNA}`)).toContainText('kurzfristig abgemeldet');
  await petraKontext.close();
});

test('F-04 K8 S7 ANM-11 ANM-12: Petra trägt für Nina «nicht dabei» ein', async ({ page, browser }) => {
  await login(page, 'petra');
  await page.goto(`/termine/${T01}`);
  const nina = page.getByTestId(`overview-team-${TEAM_1}`).getByTestId(`overview-person-${NINA}`);
  await nina.getByRole('button', { name: 'Antwort für Nina Roth eintragen' }).click();
  const dialog = page.getByRole('dialog', { name: 'Antwort für Nina Roth eintragen' });
  await expect(dialog).toBeVisible();
  await dialog.getByRole('radio', { name: 'nicht dabei' }).check();
  await dialog.getByLabel('Kommentar (bei «vielleicht» Pflicht)').fill('Hat angerufen');
  await checkAccessibility(page);
  await dialog.getByRole('button', { name: 'Speichern' }).click();
  await expect(dialog).toBeHidden();
  await expect(page.getByRole('status').filter({ hasText: 'Antwort für Nina Roth' })).toHaveText(
    'Antwort für Nina Roth gespeichert: nicht dabei.',
  );
  await expect(nina).toHaveAttribute('data-antwort', 'DECLINED');
  await expect(nina).toContainText('eingetragen von Petra Keller');
  await expect(nina).toContainText('«Hat angerufen»');
  await expect(page.getByTestId(`overview-team-${TEAM_1}-counter`)).toContainText(z(5, 2, 0, 0));
  // Coaches tragen nicht für andere Coaches und nicht für sich selbst ein
  await expect(
    page.getByTestId('coach-block').getByRole('button', { name: /Antwort für .* eintragen/ }),
  ).toHaveCount(0);
  // RR-06: In Team 2 ist Petra Spielerin
  await expect(
    page.getByTestId(`overview-team-${TEAM_2}`).getByRole('button', { name: /Antwort für .* eintragen/ }),
  ).toHaveCount(0);

  // Nina sieht, wer eingetragen hat
  const ninaKontext = await browser.newContext();
  const ninaSeite = await ninaKontext.newPage();
  await login(ninaSeite, 'nina');
  await ninaSeite.goto(`/termine/${T01}`);
  await expect(ninaSeite.getByTestId('my-response')).toContainText('eingetragen von Petra Keller');
  await expect(knopf(ninaSeite.getByRole('main'), 'nicht-dabei')).toHaveAttribute('aria-pressed', 'true');
  await ninaKontext.close();
});

test('F-04 K8 S7 ANM-04: Stellvertretend «vielleicht» verlangt einen Kommentar', async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/termine/${T01}`);
  await page.getByRole('button', { name: 'Antwort für Nina Roth eintragen' }).click();
  const dialog = page.getByRole('dialog', { name: 'Antwort für Nina Roth eintragen' });
  await dialog.getByRole('radio', { name: 'vielleicht' }).check();
  await dialog.getByRole('button', { name: 'Speichern' }).click();
  const feld = dialog.getByLabel('Kommentar (bei «vielleicht» Pflicht)');
  await expect(feld).toHaveAttribute('aria-invalid', 'true');
  await expect(feld).toHaveAccessibleDescription('Bei «vielleicht» bitte kurz begründen.');
  await dialog.getByRole('button', { name: 'Abbrechen' }).click();
  await expect(page.getByTestId(`overview-person-${NINA}`)).toHaveAttribute('data-antwort', 'PENDING');
});

test.describe('Stellvertretende Antwort mit Feldfehler der API', () => {
  test.use({ expectedConsoleErrors: [/status of 422/] });

  test('F-04 K8 S7 ANM-04: Feldfehler der API zum Kommentar erscheint im Dialog beim Feld', async ({ page }) => {
    // Die API meldet Feldfehler mit englischem Feldnamen «comment» (Problem Details, NF-40).
    await page.route(
      (url) => url.pathname === `/api/events/${T01}/responses/${NINA}`,
      async (route) => {
        if (route.request().method() !== 'PUT') return route.fallback();
        await route.fulfill({
          status: 422,
          contentType: 'application/problem+json',
          body: JSON.stringify({
            status: 422,
            title: 'Unprocessable Entity',
            detail: 'Bei «vielleicht» bitte kurz begründen.',
            rule: 'ANM-04',
            fields: [{ field: 'comment', message: 'Kommentar vom Server abgelehnt.' }],
          }),
        });
      },
    );
    await login(page, 'petra');
    await page.goto(`/termine/${T01}`);
    await page.getByRole('button', { name: 'Antwort für Nina Roth eintragen' }).click();
    const dialog = page.getByRole('dialog', { name: 'Antwort für Nina Roth eintragen' });
    await dialog.getByRole('radio', { name: 'vielleicht' }).check();
    const feld = dialog.getByLabel('Kommentar (bei «vielleicht» Pflicht)');
    await feld.fill('Vielleicht');
    await dialog.getByRole('button', { name: 'Speichern' }).click();
    await expect(feld).toHaveAttribute('aria-invalid', 'true');
    await expect(feld).toHaveAccessibleDescription('Kommentar vom Server abgelehnt.');
    await expect(dialog).toBeVisible();
  });
});

test('F-04 K8 S8 RR-06: Marc kann nicht für Anna antworten', async ({ page }) => {
  await login(page, 'marc');
  await page.goto(`/termine/${T01}`);
  await expect(page.getByTestId(`overview-person-${ANNA}`)).toBeVisible();
  await expect(page.getByRole('button', { name: 'Antwort für Anna Muster eintragen' })).toHaveCount(0);
  // Für Lea (auch Spielerin in Team 2) schon
  await expect(
    page
      .getByTestId(`overview-team-${TEAM_2}`)
      .getByTestId(`overview-person-${LEA}`)
      .getByRole('button', { name: 'Antwort für Lea Graf eintragen' }),
  ).toBeVisible();
  const antwort = await page.request.put(`/api/events/${T01}/responses/${ANNA}`, {
    data: { status: 'DECLINED' },
  });
  expect(antwort.status()).toBe(403);
});

test('F-04 K8 RR-02: Spielerinnen tragen für niemanden ein', async ({ page }) => {
  await login(page, 'anna');
  await page.goto(`/termine/${T01}`);
  await expect(page.getByTestId(`overview-person-${MIA}`)).toBeVisible();
  await expect(page.getByRole('button', { name: /Antwort für .* eintragen/ })).toHaveCount(0);
  const antwort = await page.request.put(`/api/events/${T01}/responses/${MIA}`, {
    data: { status: 'ATTENDING' },
  });
  expect(antwort.status()).toBe(403);
});

test('F-04 K9: Start-Screen zeigt den nächsten eigenen Termin mit Antwort-Buttons', async ({ page }) => {
  await login(page, 'anna');
  await page.goto('/');
  const naechster = page.getByTestId('next-event');
  await expect(naechster.getByRole('heading', { name: 'Nächster Termin' })).toBeVisible();
  await expect(naechster.getByRole('link')).toContainText('Training');
  await expect(naechster).toContainText('Di 17.11.');
  await expect(naechster).toContainText('18:30–20:00');
  await expect(
    naechster.getByRole('group', { name: 'Meine Antwort für Training Di 17.11. 18:30' }),
  ).toBeVisible();
  await expect(knopf(naechster, 'dabei')).toHaveAttribute('aria-pressed', 'true');
  await checkAccessibility(page);

  await knopf(naechster, 'nicht-dabei').click();
  await page.getByRole('dialog', { name: 'Nicht dabei' }).getByRole('button', { name: 'Überspringen' }).click();
  await expect(knopf(naechster, 'nicht-dabei')).toHaveAttribute('aria-pressed', 'true');
  await naechster.getByRole('link').click();
  await expect(page).toHaveURL(new RegExp(`/termine/${T01}$`));
  await expect(page.getByTestId(`overview-person-${ANNA}`)).toHaveAttribute('data-antwort', 'DECLINED');
});

test('F-04 K9: Ohne Team zeigt der Start-Screen keinen Termin', async ({ page }) => {
  await login(page, 'alex');
  await page.goto('/');
  await expect(page.getByTestId('next-event')).toContainText('Keine kommenden Termine');
  await expect(page.getByTestId('my-response')).toHaveCount(0);
});

test('F-04 K10 TER-06: Abgesagte Termine nehmen keine Antworten an', async ({ page }) => {
  await login(page, 'anna');
  await page.goto('/termine');
  const k = karte(page, T03);
  for (const w of ['dabei', 'nicht-dabei', 'vielleicht'] as const) {
    await expect(knopf(k, w)).toBeDisabled();
    await expect(knopf(k, w)).toHaveAccessibleDescription(
      'Der Termin ist abgesagt. Antworten sind nicht mehr möglich.',
    );
  }
  await page.goto(`/termine/${T03}`);
  await expect(knopf(page.getByRole('main'), 'dabei')).toBeDisabled();
  await expect(page.getByTestId('my-response')).toContainText(
    'Der Termin ist abgesagt. Antworten sind nicht mehr möglich.',
  );
  await checkAccessibility(page);
  const antwort = await page.request.put(`/api/events/${T03}/responses/me`, { data: { status: 'ATTENDING' } });
  expect(antwort.status()).toBe(409);
});

test('F-04 K1 K10: Seed-Antworten erscheinen in der Liste und bleiben nach Neuladen', async ({ page }) => {
  await login(page, 'anna');
  await page.goto('/termine');
  await expect(knopf(karte(page, T01), 'dabei')).toHaveAttribute('aria-pressed', 'true');
  await expect(knopf(karte(page, T02), 'dabei')).toHaveAttribute('aria-pressed', 'true');
  await knopf(karte(page, T02), 'vielleicht').click();
  const dialog = page.getByRole('dialog', { name: 'Vielleicht – bitte kurz begründen' });
  await dialog.getByLabel('Kommentar').fill('Komme später');
  await dialog.getByRole('button', { name: 'Speichern' }).click();
  await expect(knopf(karte(page, T02), 'vielleicht')).toHaveAttribute('aria-pressed', 'true');
  await page.reload();
  await expect(knopf(karte(page, T02), 'vielleicht')).toHaveAttribute('aria-pressed', 'true');
  await expect(knopf(karte(page, T01), 'dabei')).toHaveAttribute('aria-pressed', 'true');
  await checkAccessibility(page);
});
