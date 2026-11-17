import type { Locator, Page } from '@playwright/test';
import { checkAccessibility, expect, login, reset, test } from './helpers';

/**
 * F-04 · Responses with overview (ANM, RR-02, RR-03, RR-06). Acceptance criteria from issue #5 and
 * docs/pipeline/backlog.md, scenarios S1–S5, S7, S8 from docs/test-data.md. Default "now": Mon 16.11.2026 06:00.
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
const LINE_1_T1 = '30000000-0000-4000-8000-000000000001';
const LINE_2_T1 = '30000000-0000-4000-8000-000000000002';
const GOALIES_T1 = '30000000-0000-4000-8000-000000000003';
const LINE_1_T2 = '30000000-0000-4000-8000-000000000004';
const LINE_2_T2 = '30000000-0000-4000-8000-000000000005';
const GOALIES_T2 = '30000000-0000-4000-8000-000000000006';

function card(page: Page, id: string): Locator {
  return page.getByTestId(`event-card-${id}`);
}

function responseButton(area: Locator, choice: 'attending' | 'declined' | 'maybe'): Locator {
  return area.getByTestId('my-response').getByTestId(`response-${choice}`);
}

/** Counter text, e.g. «7 dabei · 2 nicht dabei · 1 vielleicht · 2 offen». */
function counterText(attending: number, declined: number, maybe: number, pending: number): string {
  return `${attending} dabei · ${declined} nicht dabei · ${maybe} vielleicht · ${pending} offen`;
}

/** People of a group in display order, as "<personId> <response>". */
async function people(group: Locator): Promise<string[]> {
  return group
    .getByRole('listitem')
    .evaluateAll((li) =>
      li.map(
        (e) =>
          `${e.getAttribute('data-testid')?.replace('overview-person-', '')} ${e.getAttribute('data-response')}`,
      ),
    );
}

test.beforeEach(async ({ request }) => {
  await reset(request);
});

test('F-04 K1 K2 S1 ANM-20 to ANM-32: Overview of event 01 as in test-data.md', async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/termine/${T01}`);
  const overview = page.getByTestId('overview');
  await expect(overview.getByRole('heading', { level: 2, name: 'Übersicht' })).toBeVisible();

  // ANM-23, ANM-32: grand total, Lea counts once
  await expect(page.getByTestId('overview-total')).toContainText(counterText(7, 2, 1, 2));
  await expect(page.getByTestId(`overview-team-${TEAM_1}-counter`)).toContainText(counterText(5, 1, 0, 1));
  await expect(page.getByTestId(`overview-team-${TEAM_2}-counter`)).toContainText(counterText(3, 1, 1, 1));

  // ANM-20: teams by name; ANM-21: lines, goalies, «Ohne Linie». Coaches see the warnings first (F-16, ANM-40)
  await expect(overview.getByRole('heading', { level: 3 })).toHaveText(['Warnungen', 'Team 1', 'Team 2', 'Coaches']);
  const team1 = page.getByTestId(`overview-team-${TEAM_1}`);
  await expect(team1.getByRole('heading', { level: 4 })).toHaveText([
    'Linie 1',
    'Linie 2',
    'Goalies',
    'Ohne Linie',
  ]);
  expect(await people(page.getByTestId(`overview-group-${LINE_1_T1}`))).toEqual([
    `${MIA} DECLINED`,
    `${LEA} ATTENDING`,
    `${ANNA} ATTENDING`,
  ]);
  expect(await people(page.getByTestId(`overview-group-${LINE_2_T1}`))).toEqual([
    `${SARA} ATTENDING`,
    `${NINA} PENDING`,
  ]);
  expect(await people(page.getByTestId(`overview-group-${GOALIES_T1}`))).toEqual([`${JANA} ATTENDING`]);
  expect(await people(page.getByTestId(`overview-no-line-${TEAM_1}`))).toEqual([`${KIM} ATTENDING`]);

  // ANM-31: Petra as a player in Team 2, Lea in both teams; Team 2 without «Ohne Linie» (empty)
  const team2 = page.getByTestId(`overview-team-${TEAM_2}`);
  await expect(team2.getByRole('heading', { level: 4 })).toHaveText(['Linie 1', 'Linie 2', 'Goalies']);
  expect(await people(page.getByTestId(`overview-group-${LINE_1_T2}`))).toEqual([
    `${PETRA} ATTENDING`,
    `${EVA} MAYBE`,
    `${LARA} ATTENDING`,
  ]);
  expect(await people(page.getByTestId(`overview-group-${LINE_2_T2}`))).toEqual([
    `${LEA} ATTENDING`,
    `${ZOE} DECLINED`,
  ]);
  expect(await people(page.getByTestId(`overview-group-${GOALIES_T2}`))).toEqual([`${ELLA} PENDING`]);
  await expect(page.getByTestId(`overview-no-line-${TEAM_2}`)).toHaveCount(0);

  // ANM-22: coach block with team, Petra not among the players of Team 1
  const coaches = page.getByTestId('coach-block');
  expect(await people(coaches)).toEqual([`${PETRA} ATTENDING`, `${MARC} DECLINED`]);
  await expect(coaches.getByTestId(`overview-person-${PETRA}`)).toContainText('(Team 1)');
  await expect(coaches.getByTestId(`overview-person-${MARC}`)).toContainText('(Team 2)');
  await expect(team1.getByTestId(`overview-person-${PETRA}`)).toHaveCount(0);

  // ANM-24: status as text, not only as color
  await expect(team1.getByTestId(`overview-person-${MIA}`)).toContainText('nicht dabei');
  await expect(team1.getByTestId(`overview-person-${NINA}`)).toContainText('offen');
  await checkAccessibility(page);
});

test('F-04 K3 S2 ANM-08: Response with one tap from the event list, counter without reload', async ({
  page,
}) => {
  await login(page, 'nina');
  await page.goto('/termine');
  const k = card(page, T01);
  await expect(responseButton(k, 'attending')).toHaveAttribute('aria-pressed', 'false');
  await expect(k.getByTestId('event-counter')).toContainText(counterText(7, 2, 1, 2));
  await responseButton(k, 'attending').click();
  await expect(k.getByRole('status')).toHaveText('Gespeichert: dabei.');
  await expect(responseButton(k, 'attending')).toHaveAttribute('aria-pressed', 'true');
  await expect(k.getByTestId('my-response')).toHaveAttribute('data-response', 'ATTENDING');
  await expect(k.getByTestId('event-counter')).toContainText(counterText(8, 2, 1, 1));
  // No dialog for «dabei»: really one tap
  await expect(page.getByRole('dialog')).toHaveCount(0);

  // Overview: Team 1 shows 6 attending, 0 pending
  await k.getByRole('link').click();
  await expect(page).toHaveURL(new RegExp(`/termine/${T01}$`));
  await expect(page.getByTestId(`overview-team-${TEAM_1}-counter`)).toContainText(counterText(6, 1, 0, 0));
  await checkAccessibility(page);
});

test('F-04 K3 K5 ANM-08 ANM-05: «nicht dabei» from the detail, skip the reason', async ({ page }) => {
  await login(page, 'nina');
  await page.goto(`/termine/${T01}`);
  const team1Counter = page.getByTestId(`overview-team-${TEAM_1}-counter`);
  await expect(team1Counter).toContainText(counterText(5, 1, 0, 1));
  let navigations = 0;
  page.on('framenavigated', () => navigations++);

  await responseButton(page.getByRole('main'), 'declined').click();
  // The response is already saved, the reason is optional (ANM-05)
  const dialog = page.getByRole('dialog', { name: 'Nicht dabei' });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByLabel('Grund (freiwillig)')).toBeVisible();
  await expect(team1Counter).toContainText(counterText(5, 2, 0, 0));
  await dialog.getByRole('button', { name: 'Überspringen' }).click();
  await expect(dialog).toBeHidden();
  await expect(responseButton(page.getByRole('main'), 'declined')).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByTestId(`overview-person-${NINA}`)).toHaveAttribute('data-response', 'DECLINED');
  expect(navigations).toBe(0);

  // K10: unchanged after reload
  await page.reload();
  await expect(responseButton(page.getByRole('main'), 'declined')).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByTestId(`overview-person-${NINA}`)).toHaveAttribute('data-response', 'DECLINED');
});

test('F-04 K5 ANM-05: «nicht dabei» with reason', async ({ page }) => {
  await login(page, 'nina');
  await page.goto(`/termine/${T01}`);
  await responseButton(page.getByRole('main'), 'declined').click();
  const dialog = page.getByRole('dialog', { name: 'Nicht dabei' });
  await dialog.getByLabel('Grund (freiwillig)').fill('Prüfung');
  await checkAccessibility(page);
  await dialog.getByRole('button', { name: 'Grund speichern' }).click();
  await expect(dialog).toBeHidden();
  await expect(page.getByRole('status')).toContainText('Grund gespeichert.');
  await expect(page.getByTestId('my-response')).toContainText('Dein Kommentar: Prüfung');
  // «nicht dabei» again: change the reason without setting the response again
  await responseButton(page.getByRole('main'), 'declined').click();
  await expect(dialog.getByLabel('Grund (freiwillig)')).toHaveValue('Prüfung');
  await dialog.getByRole('button', { name: 'Überspringen' }).click();
  await page.reload();
  await expect(page.getByTestId('my-response')).toContainText('Dein Kommentar: Prüfung');
});

test('F-04 K4 S3 ANM-04: «vielleicht» without comment not possible, error message at the field', async ({
  page,
}) => {
  await login(page, 'nina');
  await page.goto(`/termine/${T01}`);
  await responseButton(page.getByRole('main'), 'maybe').click();
  const dialog = page.getByRole('dialog', { name: 'Vielleicht – bitte kurz begründen' });
  await expect(dialog).toBeVisible();
  await dialog.getByRole('button', { name: 'Speichern' }).click();
  const field = dialog.getByLabel('Kommentar');
  await expect(field).toHaveAttribute('aria-invalid', 'true');
  await expect(field).toHaveAccessibleDescription('Bei «vielleicht» bitte kurz begründen.');
  await expect(field).toBeFocused();
  await checkAccessibility(page);
  // Nothing saved
  await dialog.getByRole('button', { name: 'Abbrechen' }).click();
  await expect(responseButton(page.getByRole('main'), 'maybe')).toHaveAttribute('aria-pressed', 'false');
  const api = await page.request.get(`/api/events/${T01}`);
  expect((await api.json()).myResponse.status).toBe('PENDING');

  // With a comment it works
  await responseButton(page.getByRole('main'), 'maybe').click();
  await field.fill('Knie');
  await expect(field).not.toHaveAttribute('aria-invalid', 'true');
  await dialog.getByRole('button', { name: 'Speichern' }).click();
  await expect(dialog).toBeHidden();
  await expect(responseButton(page.getByRole('main'), 'maybe')).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByTestId('my-response')).toContainText('Dein Kommentar: Knie');
});

test('F-04 K4 S3 ANM-04: API rejects «vielleicht» without comment with 422', async ({ page }) => {
  await login(page, 'nina');
  const response = await page.request.put(`/api/events/${T01}/responses/me`, {
    data: { status: 'MAYBE' },
  });
  expect(response.status()).toBe(422);
  const error = await response.json();
  expect(error.rule).toBe('ANM-04');
  expect(error.fields[0].field).toBe('comment');
});

test('F-04 K6 S4 ANM-07 F-12: Comments only for coaches and the person themselves', async ({
  page,
  browser,
}) => {
  await login(page, 'anna');
  await page.goto(`/termine/${T01}`);
  await expect(page.getByTestId(`overview-person-${MIA}`)).toHaveAttribute('data-response', 'DECLINED');
  await expect(page.getByTestId('overview')).not.toContainText('Krank');
  await expect(page.getByTestId('overview')).not.toContainText('Komme evtl. später');
  await expect(page.getByTestId('overview')).not.toContainText('Ferien');
  // Not in the API response either
  const json = JSON.stringify(await (await page.request.get(`/api/events/${T01}/overview`)).json());
  expect(json).not.toContain('comment');

  const petraContext = await browser.newContext();
  const petra = await petraContext.newPage();
  await login(petra, 'petra');
  await petra.goto(`/termine/${T01}`);
  await expect(petra.getByTestId(`overview-person-${MIA}`)).toContainText('«Krank»');
  // F-12: Petra is coach (of Team 1) and sees the comments of all teams, also Eva's and Marc's
  await expect(petra.getByTestId(`overview-person-${EVA}`)).toContainText('«Komme evtl. später»');
  await expect(petra.getByTestId('coach-block')).toContainText('Ferien');
  await petraContext.close();

  const marcContext = await browser.newContext();
  const marc = await marcContext.newPage();
  await login(marc, 'marc');
  await marc.goto(`/termine/${T01}`);
  await expect(marc.getByTestId(`overview-person-${EVA}`)).toContainText('«Komme evtl. später»');
  await expect(marc.getByTestId(`overview-person-${MIA}`)).toContainText('«Krank»');
  await marcContext.close();
});

test('F-04 K7 S5 ANM-10: Late cancellation after the response deadline', async ({ page, browser }) => {
  const clock = await page.request.put('/api/test/clock', { data: { now: '2026-11-16T19:00:00+01:00' } });
  expect(clock.status()).toBe(204);
  await login(page, 'anna');
  await page.goto(`/termine/${T01}`);
  await responseButton(page.getByRole('main'), 'declined').click();
  await page.getByRole('dialog', { name: 'Nicht dabei' }).getByRole('button', { name: 'Überspringen' }).click();
  await expect(page.getByTestId('my-response')).toContainText('kurzfristig abgemeldet');
  const api = await page.request.get(`/api/events/${T01}`);
  expect((await api.json()).myResponse.lateCancellation).toBe(true);

  const petraContext = await browser.newContext();
  const petra = await petraContext.newPage();
  await login(petra, 'petra');
  await petra.goto(`/termine/${T01}`);
  await expect(petra.getByTestId(`overview-person-${ANNA}`)).toContainText('kurzfristig abgemeldet');
  await petraContext.close();
});

test('F-04 K8 S7 ANM-11 ANM-12: Petra enters «nicht dabei» for Nina', async ({ page, browser }) => {
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
  await expect(nina).toHaveAttribute('data-response', 'DECLINED');
  await expect(nina).toContainText('eingetragen von Petra Keller');
  await expect(nina).toContainText('«Hat angerufen»');
  await expect(page.getByTestId(`overview-team-${TEAM_1}-counter`)).toContainText(counterText(5, 2, 0, 0));
  // Coaches do not respond on behalf of themselves, but for other coaches and in all teams (F-12)
  await expect(
    page.getByTestId('coach-block').getByRole('button', { name: /Antwort für .* eintragen/ }),
  ).toHaveCount(1);
  await expect(
    page.getByTestId('coach-block').getByRole('button', { name: 'Antwort für Marc Huber eintragen' }),
  ).toBeVisible();
  await expect(
    page.getByTestId(`overview-team-${TEAM_2}`).getByRole('button', { name: 'Antwort für Eva Steiner eintragen' }),
  ).toBeVisible();

  // Nina sees who entered it
  const ninaContext = await browser.newContext();
  const ninaPage = await ninaContext.newPage();
  await login(ninaPage, 'nina');
  await ninaPage.goto(`/termine/${T01}`);
  await expect(ninaPage.getByTestId('my-response')).toContainText('eingetragen von Petra Keller');
  await expect(responseButton(ninaPage.getByRole('main'), 'declined')).toHaveAttribute('aria-pressed', 'true');
  await ninaContext.close();
});

test('F-04 K8 S7 ANM-04: «vielleicht» on behalf requires a comment', async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/termine/${T01}`);
  await page.getByRole('button', { name: 'Antwort für Nina Roth eintragen' }).click();
  const dialog = page.getByRole('dialog', { name: 'Antwort für Nina Roth eintragen' });
  await dialog.getByRole('radio', { name: 'vielleicht' }).check();
  await dialog.getByRole('button', { name: 'Speichern' }).click();
  const field = dialog.getByLabel('Kommentar (bei «vielleicht» Pflicht)');
  await expect(field).toHaveAttribute('aria-invalid', 'true');
  await expect(field).toHaveAccessibleDescription('Bei «vielleicht» bitte kurz begründen.');
  await dialog.getByRole('button', { name: 'Abbrechen' }).click();
  await expect(page.getByTestId(`overview-person-${NINA}`)).toHaveAttribute('data-response', 'PENDING');
});

test.describe('Response on behalf with a field error from the API', () => {
  test.use({ expectedConsoleErrors: [/status of 422/] });

  test('F-04 K8 S7 ANM-04: API field error for the comment appears in the dialog at the field', async ({ page }) => {
    // The API reports field errors with the English field name "comment" (Problem Details, NF-40).
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
    const field = dialog.getByLabel('Kommentar (bei «vielleicht» Pflicht)');
    await field.fill('Vielleicht');
    await dialog.getByRole('button', { name: 'Speichern' }).click();
    await expect(field).toHaveAttribute('aria-invalid', 'true');
    await expect(field).toHaveAccessibleDescription('Kommentar vom Server abgelehnt.');
    await expect(dialog).toBeVisible();
  });
});

test('F-04 K8 S8 RR-06 F-12: Marc (coach of Team 2) can respond for Anna (Team 1)', async ({ page }) => {
  await login(page, 'marc');
  await page.goto(`/termine/${T01}`);
  await expect(page.getByRole('button', { name: 'Antwort für Anna Muster eintragen' })).toBeVisible();
  await expect(
    page
      .getByTestId(`overview-team-${TEAM_2}`)
      .getByTestId(`overview-person-${LEA}`)
      .getByRole('button', { name: 'Antwort für Lea Graf eintragen' }),
  ).toBeVisible();
  const response = await page.request.put(`/api/events/${T01}/responses/${ANNA}`, {
    data: { status: 'DECLINED' },
  });
  expect(response.status()).toBe(200);
});

test('F-04 K8 RR-02: Players respond on behalf of nobody', async ({ page }) => {
  await login(page, 'anna');
  await page.goto(`/termine/${T01}`);
  await expect(page.getByTestId(`overview-person-${MIA}`)).toBeVisible();
  await expect(page.getByRole('button', { name: /Antwort für .* eintragen/ })).toHaveCount(0);
  const response = await page.request.put(`/api/events/${T01}/responses/${MIA}`, {
    data: { status: 'ATTENDING' },
  });
  expect(response.status()).toBe(403);
});

test('F-04 K9: Start screen shows the next own event with response buttons', async ({ page }) => {
  await login(page, 'anna');
  await page.goto('/');
  const nextEvent = page.getByTestId('next-event');
  await expect(nextEvent.getByRole('heading', { name: 'Nächster Termin' })).toBeVisible();
  await expect(nextEvent.getByRole('link')).toContainText('Training');
  await expect(nextEvent).toContainText('Di 17.11.');
  await expect(nextEvent).toContainText('18:30–20:00');
  await expect(
    nextEvent.getByRole('group', { name: 'Meine Antwort für Training Di 17.11. 18:30' }),
  ).toBeVisible();
  await expect(responseButton(nextEvent, 'attending')).toHaveAttribute('aria-pressed', 'true');
  await checkAccessibility(page);

  await responseButton(nextEvent, 'declined').click();
  await page.getByRole('dialog', { name: 'Nicht dabei' }).getByRole('button', { name: 'Überspringen' }).click();
  await expect(responseButton(nextEvent, 'declined')).toHaveAttribute('aria-pressed', 'true');
  await nextEvent.getByRole('link').click();
  await expect(page).toHaveURL(new RegExp(`/termine/${T01}$`));
  await expect(page.getByTestId(`overview-person-${ANNA}`)).toHaveAttribute('data-response', 'DECLINED');
});

test('F-04 K9: Without a team the start screen shows no event', async ({ page }) => {
  await login(page, 'alex');
  await page.goto('/');
  await expect(page.getByTestId('next-event')).toContainText('Keine kommenden Termine');
  await expect(page.getByTestId('my-response')).toHaveCount(0);
});

test('F-04 K10 TER-06: Cancelled events accept no responses', async ({ page }) => {
  await login(page, 'anna');
  await page.goto('/termine');
  const k = card(page, T03);
  for (const w of ['attending', 'declined', 'maybe'] as const) {
    await expect(responseButton(k, w)).toBeDisabled();
    await expect(responseButton(k, w)).toHaveAccessibleDescription(
      'Der Termin ist abgesagt. Antworten sind nicht mehr möglich.',
    );
  }
  await page.goto(`/termine/${T03}`);
  await expect(responseButton(page.getByRole('main'), 'attending')).toBeDisabled();
  await expect(page.getByTestId('my-response')).toContainText(
    'Der Termin ist abgesagt. Antworten sind nicht mehr möglich.',
  );
  await checkAccessibility(page);
  const response = await page.request.put(`/api/events/${T03}/responses/me`, { data: { status: 'ATTENDING' } });
  expect(response.status()).toBe(409);
});

test('F-04 K1 K10: Seed responses appear in the list and stay after reload', async ({ page }) => {
  await login(page, 'anna');
  await page.goto('/termine');
  await expect(responseButton(card(page, T01), 'attending')).toHaveAttribute('aria-pressed', 'true');
  await expect(responseButton(card(page, T02), 'attending')).toHaveAttribute('aria-pressed', 'true');
  await responseButton(card(page, T02), 'maybe').click();
  const dialog = page.getByRole('dialog', { name: 'Vielleicht – bitte kurz begründen' });
  await dialog.getByLabel('Kommentar').fill('Komme später');
  await dialog.getByRole('button', { name: 'Speichern' }).click();
  await expect(responseButton(card(page, T02), 'maybe')).toHaveAttribute('aria-pressed', 'true');
  await page.reload();
  await expect(responseButton(card(page, T02), 'maybe')).toHaveAttribute('aria-pressed', 'true');
  await expect(responseButton(card(page, T01), 'attending')).toHaveAttribute('aria-pressed', 'true');
  await checkAccessibility(page);
});
