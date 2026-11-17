import type { Page } from '@playwright/test';
import { checkAccessibility, expect, login, reset, test } from './helpers';

/**
 * F-03 · Termine und Serien (TER). Akzeptanzkriterien aus docs/pipeline/backlog.md, Szenario S13 aus
 * docs/testdaten.md. IDs aus docs/testdaten.md. Standard-«Jetzt» nach Reset: Mo 16.11.2026, 06:00.
 */

const TEAM_1 = '10000000-0000-4000-8000-000000000001';
const TEAM_2 = '10000000-0000-4000-8000-000000000002';
const TEAM_3 = '10000000-0000-4000-8000-000000000003';
const T01 = '40000000-0000-4000-8000-000000000001';
const T02 = '40000000-0000-4000-8000-000000000002';
const T03 = '40000000-0000-4000-8000-000000000003';
const T05 = '40000000-0000-4000-8000-000000000005';
const T06 = '40000000-0000-4000-8000-000000000006';
const T07 = '40000000-0000-4000-8000-000000000007';
const SERIE_1 = '70000000-0000-4000-8000-000000000001';

function karte(page: Page, id: string) {
  return page.getByTestId(`event-card-${id}`);
}

/** Namen der Termin-Einträge in der Liste, in Anzeigereihenfolge. */
async function eintraege(page: Page): Promise<(string | null)[]> {
  return page
    .getByRole('region', { name: /Termine/ })
    .getByRole('listitem')
    .evaluateAll((li) => li.map((e) => e.getAttribute('aria-label')));
}

test.beforeEach(async ({ request }) => {
  await reset(request);
});

test('F-03 K1 K2 TER-13: Anna sieht die kommenden Termine ihrer Teams chronologisch', async ({
  page,
}) => {
  await login(page, 'anna');
  await page.goto('/termine');
  await expect(page).toHaveTitle('Termine · STV Bero');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Termine');
  await expect(karte(page, T01)).toBeVisible();
  await expect.poll(async () => (await eintraege(page)).slice(0, 4)).toEqual([
    'Training Di 17.11. 18:30',
    'Training Do 19.11. 19:00',
    'Spieltag So 22.11. 09:30',
    'Training Di 24.11. 18:30',
  ]);
  await expect(karte(page, T01)).toContainText('Team 1 + Team 2');
  await expect(karte(page, T01)).toContainText('Sporthalle Nord');
  await expect(karte(page, T05)).toHaveCount(0);
  await expect(karte(page, T07)).toHaveCount(0);
  // Spielerinnen erfassen keine Termine (RR-05)
  await expect(page.getByRole('link', { name: 'Termin erfassen' })).toHaveCount(0);
  await expect(page.getByRole('heading', { name: 'November 2026' })).toBeVisible();
  await checkAccessibility(page);
});

test('F-03 K2 TER-13: Tina (Team 3) sieht Termin 01 nicht', async ({ page }) => {
  await login(page, 'tina');
  await page.goto('/termine');
  await expect(karte(page, T06)).toBeVisible();
  await expect(karte(page, T01)).toHaveCount(0);
  await expect.poll(async () => (await eintraege(page))[0]).toBe('Training Mi 18.11. 18:00');
});

test('F-03 K2: Ohne Team gibt es einen leeren Zustand', async ({ page }) => {
  await login(page, 'alex');
  await page.goto('/termine');
  await expect(page.getByText('Keine kommenden Termine')).toBeVisible();
  await page.getByRole('navigation', { name: 'Ansicht' }).getByRole('link', { name: 'Vergangen' }).click();
  await expect(page.getByText('Keine vergangenen Termine')).toBeVisible();
});

test('F-03 K3 TER-01 TER-11: Petra erfasst einen Termin für Team 1 + Team 2', async ({
  page,
  browser,
}) => {
  await login(page, 'petra');
  await page.goto('/termine');
  await page.getByRole('link', { name: 'Termin erfassen' }).click();
  await expect(page).toHaveURL(/\/termine\/neu$/);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Termin erfassen');
  await checkAccessibility(page);

  await page.getByLabel('Titel', { exact: true }).fill('Zusatztraining');
  await page.getByLabel('Datum', { exact: true }).fill('2026-11-20');
  await page.getByLabel('Beginn', { exact: true }).fill('18:00');
  await page.getByLabel('Ende', { exact: true }).fill('19:30');
  const teams = page.getByRole('group', { name: 'Teams' });
  await expect(teams.getByRole('checkbox', { name: 'Team 1' })).toBeChecked();
  await teams.getByRole('checkbox', { name: 'Team 2' }).check();
  await page.getByLabel('Ort (freiwillig)').fill('Turnhalle Dorf');
  await page.getByRole('button', { name: 'Termin speichern' }).click();

  await expect(page).toHaveURL(/\/termine\/[0-9a-f-]{36}$/);
  await expect(page.getByRole('status')).toHaveText('Der Termin ist erfasst.');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Zusatztraining');
  await expect(page.getByText('Team 1 + Team 2').first()).toBeVisible();
  await checkAccessibility(page);
  const id = page.url().split('/').pop()!;

  // Erscheint bei Mitgliedern beider Teams
  for (const schluessel of ['anna', 'eva']) {
    const kontext = await browser.newContext();
    const andere = await kontext.newPage();
    await login(andere, schluessel);
    await andere.goto('/termine');
    await expect(karte(andere, id)).toHaveAttribute('aria-label', 'Zusatztraining Fr 20.11. 18:00');
    await kontext.close();
  }
});

test('F-03 K3 RR-07: Marc darf nicht für Team 1 erfassen (403)', async ({ page }) => {
  await login(page, 'marc');
  const antwort = await page.request.post('/api/events', {
    data: {
      type: 'PRACTICE',
      title: 'Training',
      start: '2026-11-20T18:00:00+01:00',
      end: '2026-11-20T19:30:00+01:00',
      teamIds: [TEAM_1],
    },
  });
  expect(antwort.status()).toBe(403);
  // In der Oberfläche bietet das Formular Marc nur seine eigenen Teams an
  await page.goto('/termine/neu');
  const teams = page.getByRole('group', { name: 'Teams' });
  await expect(teams.getByRole('checkbox', { name: 'Team 2' })).toBeVisible();
  await expect(teams.getByRole('checkbox', { name: 'Team 1' })).toHaveCount(0);
});

test('F-03 K3 RR-05: Spielerinnen sehen beim Erfassen «Keine Berechtigung»', async ({ page }) => {
  await login(page, 'anna');
  await page.goto('/termine/neu');
  await expect(page.getByRole('alert')).toHaveText('Keine Berechtigung');
  await page.goto(`/termine/${T01}/bearbeiten`);
  await expect(page.getByRole('alert')).toHaveText('Keine Berechtigung');
});

test('F-03 K4 TER-05: Petra legt eine wöchentliche Serie an', async ({ page }) => {
  await login(page, 'petra');
  await page.goto('/termine/neu');
  await page.getByRole('radio', { name: 'Serie (jede Woche)' }).check();
  await expect(page).toHaveURL(/art=serie/);
  await page.getByLabel('Titel', { exact: true }).fill('Konditionstraining');
  await page.getByLabel('Wochentag').selectOption({ label: 'Montag' });
  await page.getByLabel('Beginn', { exact: true }).fill('19:30');
  await page.getByLabel('Ende', { exact: true }).fill('21:00');
  await page.getByLabel('Erster Tag der Serie').fill('2026-11-16');
  await page.getByLabel('Letzter Tag der Serie').fill('2026-12-14');
  await page.getByRole('button', { name: 'Serie anlegen' }).click();

  await expect(page).toHaveURL(/\/serien\/[0-9a-f-]{36}$/);
  await expect(page.getByRole('status')).toContainText('Die Serie ist angelegt.');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Konditionstraining');
  await expect(page.getByText('Jeden Montag, 19:30–21:00')).toBeVisible();
  await checkAccessibility(page);

  await page.goto(`/termine?team=${TEAM_1}`);
  await expect(karte(page, T01)).toBeVisible();
  const namen = await eintraege(page);
  for (const tag of ['16.11.', '23.11.', '30.11.', '07.12.', '14.12.']) {
    expect(namen).toContain(`Konditionstraining Mo ${tag} 19:30`);
  }
  expect(namen.filter((n) => n?.startsWith('Konditionstraining'))).toHaveLength(5);
});

test('F-03 K5 TER-07: Ausnahme nimmt Termine der Serie aus', async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/serien/${SERIE_1}`);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Training');
  const ausnahmen = page.getByTestId('exceptions');
  await expect(ausnahmen.getByRole('listitem')).toHaveText(['Weihnachtsferien: 21.12.2026 – 03.01.2027']);
  await checkAccessibility(page);

  await page.getByLabel('Von').fill('2026-11-30');
  await page.getByLabel('Bis').fill('2026-12-06');
  await page.getByLabel('Grund, z.B. Herbstferien').fill('Hallenreinigung');
  await page.getByRole('button', { name: 'Ausnahme hinzufügen' }).click();
  await expect(page.getByRole('status')).toHaveText('Ausnahme «Hallenreinigung» hinzugefügt.');
  await expect(ausnahmen.getByRole('listitem')).toHaveText([
    'Hallenreinigung: 30.11.2026 – 06.12.2026',
    'Weihnachtsferien: 21.12.2026 – 03.01.2027',
  ]);

  await page.goto(`/termine?team=${TEAM_2}`);
  await expect(karte(page, T01)).toBeVisible();
  const namen = await eintraege(page);
  expect(namen).toContain('Training Di 24.11. 18:30');
  expect(namen).not.toContain('Training Di 01.12. 18:30');
  expect(namen).toContain('Training Di 08.12. 18:30');
});

test('F-03 K5 TER-07: Ausnahme ohne Grund wird nicht gespeichert', async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/serien/${SERIE_1}`);
  await page.getByLabel('Von').fill('2026-11-30');
  await page.getByLabel('Bis').fill('2026-12-06');
  await page.getByRole('button', { name: 'Ausnahme hinzufügen' }).click();
  const grund = page.getByLabel('Grund, z.B. Herbstferien');
  await expect(grund).toHaveAttribute('aria-invalid', 'true');
  await expect(grund).toBeFocused();
});

test('F-03 K6 TER-06: Einzelnen Serientermin verschieben, die Serie bleibt', async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/termine/${T02}`);
  await page.getByRole('link', { name: 'Termin bearbeiten' }).click();
  await expect(page).toHaveURL(`/termine/${T02}/bearbeiten`);
  await expect(page.getByText('Die Änderung gilt nur für diesen Termin')).toBeVisible();
  await checkAccessibility(page);
  await page.getByLabel('Datum', { exact: true }).fill('2026-11-20');
  await page.getByRole('button', { name: 'Änderungen speichern' }).click();

  await expect(page).toHaveURL(`/termine/${T02}`);
  await expect(page.getByRole('status')).toHaveText('Der Termin ist verschoben.');
  await expect(page.getByTestId('event-status')).toHaveText('Dieser Termin wurde verschoben.');

  await page.goto(`/termine?team=${TEAM_1}`);
  await expect(karte(page, T02)).toHaveAttribute('aria-label', 'Training Fr 20.11. 19:00');
  await expect(karte(page, T02).getByTestId('event-status')).toHaveText('verschoben');
  await expect(karte(page, T01)).toBeVisible();
  const namen = await eintraege(page);
  expect(namen).toContain('Training Do 26.11. 19:00');
  expect(namen).not.toContain('Training Do 19.11. 19:00');

  // Die Serie selbst ist unverändert
  await page.goto(`/termine/${T02}`);
  await page.getByRole('link', { name: 'Serie ansehen' }).click();
  await expect(page.getByText('Jeden Donnerstag, 19:00–20:30')).toBeVisible();
});

test('F-03 K7 S13 TER-06: Abgesagter Termin ist erkennbar, mit Grund', async ({ page }) => {
  await login(page, 'anna');
  await page.goto('/termine');
  await expect(karte(page, T03).getByTestId('event-status')).toHaveText('abgesagt');
  await expect(karte(page, T03)).toContainText('Halle besetzt');
  await karte(page, T03).getByRole('link').click();
  await expect(page).toHaveURL(`/termine/${T03}`);
  await expect(page.getByTestId('event-status')).toContainText('Abgesagt: Halle besetzt');
  await expect(page.getByTestId('event-status')).toContainText('Antworten ist nicht mehr möglich.');
  await expect(page.getByRole('link', { name: 'Termin bearbeiten' })).toHaveCount(0);
  await checkAccessibility(page);
});

test('F-03 K7 TER-06: Abgesagter Termin hat in der Liste ein korallfarbenes Datum', async ({ page }) => {
  await login(page, 'anna');
  await page.goto('/termine');
  // Das Datum ist das erste Element im Link der Karte (aria-hidden, darum ohne Rolle).
  const hintergrund = (id: string) =>
    karte(page, id)
      .getByRole('link')
      .evaluate((a) => getComputedStyle(a.firstElementChild as Element).backgroundColor);
  const koralle = await page.evaluate(() => {
    const probe = document.createElement('span');
    probe.style.background = 'var(--coral-soft)';
    document.body.append(probe);
    const farbe = getComputedStyle(probe).backgroundColor;
    probe.remove();
    return farbe;
  });
  expect(await hintergrund(T03)).toBe(koralle);
  expect(await hintergrund(T01)).not.toBe(koralle);
});

test('F-03 K7 TER-06: Coach sagt einen Termin mit Grund ab', async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/termine/${T01}/bearbeiten`);
  await page.getByRole('button', { name: 'Termin absagen' }).click();
  const dialog = page.getByRole('dialog', { name: 'Termin absagen' });
  await expect(dialog).toBeVisible();
  await dialog.getByRole('button', { name: 'Absagen' }).click();
  await expect(dialog.getByLabel('Grund')).toHaveAttribute('aria-invalid', 'true');
  await dialog.getByLabel('Grund').fill('Halle gesperrt');
  await dialog.getByRole('button', { name: 'Absagen' }).click();
  await expect(page).toHaveURL(`/termine/${T01}`);
  await expect(page.getByRole('status')).toHaveText('Der Termin ist abgesagt.');
  await expect(page.getByTestId('event-status')).toContainText('Abgesagt: Halle gesperrt');
});

test('F-03 K8 TER-09 TER-10: Antwortfrist pro Termin, Standard 24 Stunden', async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/termine/${T01}`);
  await expect(page.getByTestId('response-deadline')).toHaveText('Mo 16.11. 18:30 (24 Stunden vor Beginn)');

  await page.goto('/termine/neu');
  await expect(page.getByLabel('Antwortfrist: Stunden vor Beginn')).toHaveValue('24');
  await page.getByLabel('Datum', { exact: true }).fill('2026-11-21');
  await page.getByLabel('Beginn', { exact: true }).fill('10:00');
  await page.getByLabel('Ende', { exact: true }).fill('12:00');
  await page.getByLabel('Antwortfrist: Stunden vor Beginn').fill('48');
  await page.getByRole('button', { name: 'Termin speichern' }).click();
  await expect(page.getByTestId('response-deadline')).toHaveText('Do 19.11. 10:00 (48 Stunden vor Beginn)');

  // Serie: Antwortfrist für alle ihre Termine
  await page.goto(`/serien/${SERIE_1}/bearbeiten`);
  await expect(page.getByText('Änderungen gelten für alle künftigen Termine der Serie.')).toBeVisible();
  await page.getByLabel('Antwortfrist: Stunden vor Beginn').fill('12');
  await page.getByRole('button', { name: 'Änderungen speichern' }).click();
  await expect(page).toHaveURL(`/serien/${SERIE_1}`);
  await expect(page.getByText('12 Stunden vor Beginn')).toBeVisible();
  await page.goto(`/termine/${T01}`);
  await expect(page.getByTestId('response-deadline')).toHaveText('Di 17.11. 06:30 (12 Stunden vor Beginn)');
});

test('F-03 K9: Filter ?team= und Ansicht ?ansicht=vergangen stehen in der URL', async ({ page }) => {
  await login(page, 'petra');
  await page.goto('/termine');
  await page.getByLabel('Team', { exact: true }).selectOption({ label: 'Team 2' });
  await expect(page).toHaveURL(`/termine?team=${TEAM_2}`);
  await expect(karte(page, T05)).toBeVisible();
  await expect(karte(page, T02)).toHaveCount(0);

  const ansicht = page.getByRole('navigation', { name: 'Ansicht' });
  await expect(ansicht.getByRole('link', { name: 'Kommend' })).toHaveAttribute('aria-current', 'page');
  await ansicht.getByRole('link', { name: 'Vergangen' }).click();
  await expect(page).toHaveURL(`/termine?team=${TEAM_2}&ansicht=vergangen`);
  await expect(ansicht.getByRole('link', { name: 'Vergangen' })).toHaveAttribute('aria-current', 'page');
  await expect(karte(page, T07)).toBeVisible();
  await expect(karte(page, T01)).toHaveCount(0);

  // Deep Link nach dem Neuladen
  await page.reload();
  await expect(page.getByLabel('Team', { exact: true })).toHaveValue(TEAM_2);
  await expect(karte(page, T07)).toBeVisible();
  await checkAccessibility(page);

  await page.goto(`/termine?team=${TEAM_3}`);
  await expect.poll(() => eintraege(page)).toEqual(['Teamessen Fr 11.12. 19:00']);
  // Test-Befund F-03: Auswahl und Liste stimmen überein, auch für ein Team, in dem Petra nicht ist
  await expect(page.getByLabel('Team', { exact: true })).toHaveValue(TEAM_3);
  await expect
    .poll(() =>
      page
        .getByLabel('Team', { exact: true })
        .evaluate((e) => (e as HTMLSelectElement).selectedOptions[0]?.text.trim()),
    )
    .toBe('Team 3');
});

// ------------------------------------------------------------------ Befunde aus dem Test (Runde 1)

test('F-03 TER-11: Serie nennt alle ihre Teams, auch für Personen aus nur einem Team', async ({
  page,
}) => {
  for (const schluessel of ['anna', 'marc']) {
    await login(page, schluessel);
    await page.goto(`/serien/${SERIE_1}`);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Training');
    await expect(page.getByText('Team 1 + Team 2', { exact: true })).toBeVisible();
  }
});

test('F-03 TER-09 TER-10: Antwortfrist ausserhalb 0 bis 336 Stunden mit deutscher Meldung', async ({
  page,
}) => {
  await login(page, 'petra');
  await page.goto('/termine/neu');
  await page.getByLabel('Datum', { exact: true }).fill('2026-11-21');
  await page.getByLabel('Beginn', { exact: true }).fill('10:00');
  await page.getByLabel('Ende', { exact: true }).fill('12:00');
  const frist = page.getByLabel('Antwortfrist: Stunden vor Beginn');
  await frist.fill('337');
  await page.getByRole('button', { name: 'Termin speichern' }).click();
  await expect(page.getByText('Höchstens 336 Stunden (14 Tage).')).toBeVisible();
  await expect(frist).toBeFocused();
  await expect(frist).toHaveAttribute('aria-invalid', 'true');
  await frist.fill('-1');
  await page.getByRole('button', { name: 'Termin speichern' }).click();
  await expect(page.getByText('Mindestens 0 Stunden.')).toBeVisible();
  await expect(page).toHaveURL(/\/termine\/neu$/);

  await page.goto(`/serien/${SERIE_1}/bearbeiten`);
  await page.getByLabel('Antwortfrist: Stunden vor Beginn').fill('400');
  await page.getByRole('button', { name: 'Änderungen speichern' }).click();
  await expect(page.getByText('Höchstens 336 Stunden (14 Tage).')).toBeVisible();
  await expect(page).toHaveURL(`/serien/${SERIE_1}/bearbeiten`);
});

test('F-03: Lange Wörter in Titel, Ort und Hinweisen brechen auf der Termin-Seite um', async ({
  page,
}) => {
  const lang = 'https://example.ch/' + 'sehr-langer-link-ohne-leerzeichen-'.repeat(5);
  await login(page, 'petra');
  await page.goto('/termine/neu');
  await page.getByLabel('Titel', { exact: true }).fill('Zusatztraining' + 'X'.repeat(60));
  await page.getByLabel('Datum', { exact: true }).fill('2026-11-20');
  await page.getByLabel('Beginn', { exact: true }).fill('18:00');
  await page.getByLabel('Ende', { exact: true }).fill('19:30');
  await page.getByLabel('Ort (freiwillig)').fill(lang);
  await page.getByLabel(/^Hinweise/).fill(lang);
  await page.getByRole('button', { name: 'Termin speichern' }).click();
  await expect(page).toHaveURL(/\/termine\/[0-9a-f-]{36}$/);
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Zusatztraining');
  const breiten = await page.evaluate(() => ({
    seite: document.documentElement.scrollWidth,
    fenster: document.documentElement.clientWidth,
  }));
  expect(breiten.seite).toBeLessThanOrEqual(breiten.fenster);
});
