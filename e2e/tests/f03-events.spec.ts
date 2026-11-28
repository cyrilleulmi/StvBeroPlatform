import type { Page } from '@playwright/test';
import { checkAccessibility, expect, login, reset, test } from './helpers';

/**
 * F-03 · Events and series (TER). Acceptance criteria from docs/pipeline/backlog.md, scenario S13 from
 * docs/test-data.md. IDs from docs/test-data.md. Default "now" after reset: Mon 16.11.2026, 06:00.
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
const SERIES_1 = '70000000-0000-4000-8000-000000000001';

function card(page: Page, id: string) {
  return page.getByTestId(`event-card-${id}`);
}

/** Names of the event entries in the list, in display order. */
async function entries(page: Page): Promise<(string | null)[]> {
  return page
    .getByRole('region', { name: /Termine/ })
    .getByRole('listitem')
    .evaluateAll((li) => li.map((e) => e.getAttribute('aria-label')));
}

test.beforeEach(async ({ request }) => {
  await reset(request);
});

test('F-03 K1 K2 TER-13: Anna sees the upcoming events of her teams in chronological order', { tag: '@desktop' }, async ({
  page,
}) => {
  await login(page, 'anna');
  await page.goto('/termine');
  await expect(page).toHaveTitle('Termine · STV Bero');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Termine');
  await expect(card(page, T01)).toBeVisible();
  await expect.poll(async () => (await entries(page)).slice(0, 4)).toEqual([
    'Training Di 17.11. 18:30',
    'Training Do 19.11. 19:00',
    'Spieltag So 22.11. 09:30',
    'Training Di 24.11. 18:30',
  ]);
  await expect(card(page, T01)).toContainText('Team 1 + Team 2');
  await expect(card(page, T01)).toContainText('Sporthalle Nord');
  await expect(card(page, T05)).toHaveCount(0);
  await expect(card(page, T07)).toHaveCount(0);
  // Players do not create events (RR-05)
  await expect(page.getByRole('link', { name: 'Termin erfassen' })).toHaveCount(0);
  await expect(page.getByRole('heading', { name: 'November 2026' })).toBeVisible();
  await checkAccessibility(page);
});

test('F-03 K2 TER-13: Tina (Team 3) does not see event 01', async ({ page }) => {
  await login(page, 'tina');
  await page.goto('/termine');
  await expect(card(page, T06)).toBeVisible();
  await expect(card(page, T01)).toHaveCount(0);
  await expect.poll(async () => (await entries(page))[0]).toBe('Training Mi 18.11. 18:00');
});

test('F-03 K2: Without a team there is an empty state', async ({ page }) => {
  await login(page, 'alex');
  await page.goto('/termine');
  await expect(page.getByText('Keine kommenden Termine')).toBeVisible();
  await page.getByRole('navigation', { name: 'Ansicht' }).getByRole('link', { name: 'Vergangen' }).click();
  await expect(page.getByText('Keine vergangenen Termine')).toBeVisible();
});

test('F-03 K3 TER-01 TER-11: Petra creates an event for Team 1 + Team 2', async ({
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
  const teams = page.getByRole('group', { name: 'Teams', exact: true });
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

  // Appears for members of both teams
  for (const key of ['anna', 'eva']) {
    const context = await browser.newContext();
    const other = await context.newPage();
    await login(other, key);
    await other.goto('/termine');
    await expect(card(other, id)).toHaveAttribute('aria-label', 'Zusatztraining Fr 20.11. 18:00');
    await context.close();
  }
});

test('F-03 K3 F-12: Marc (coach of Team 2) may create for Team 1, Lea (player) may not (403)', async ({ page }) => {
  const data = {
    type: 'PRACTICE',
    title: 'Training',
    start: '2026-11-20T18:00:00+01:00',
    end: '2026-11-20T19:30:00+01:00',
    teamIds: [TEAM_1],
  };
  await login(page, 'lea');
  expect((await page.request.post('/api/events', { data })).status()).toBe(403);
  await login(page, 'marc');
  // In the UI the form offers coaches all teams (F-12)
  await page.goto('/termine/neu');
  const teams = page.getByRole('group', { name: 'Teams', exact: true });
  await expect(teams.getByRole('checkbox', { name: 'Team 2' })).toBeVisible();
  await expect(teams.getByRole('checkbox', { name: 'Team 1' })).toBeVisible();
  await expect(teams.getByRole('checkbox', { name: 'Team 3' })).toBeVisible();
  expect((await page.request.post('/api/events', { data })).status()).toBe(201);
});

test('F-03 K3 RR-05: Players see «Keine Berechtigung» when creating', async ({ page }) => {
  await login(page, 'anna');
  await page.goto('/termine/neu');
  await expect(page.getByRole('alert')).toHaveText('Keine Berechtigung');
  await page.goto(`/termine/${T01}/bearbeiten`);
  await expect(page.getByRole('alert')).toHaveText('Keine Berechtigung');
});

test('F-03 K4 TER-05: Petra creates a weekly series', { tag: '@desktop' }, async ({ page }) => {
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
  await expect(card(page, T01)).toBeVisible();
  const names = await entries(page);
  for (const day of ['16.11.', '23.11.', '30.11.', '07.12.', '14.12.']) {
    expect(names).toContain(`Konditionstraining Mo ${day} 19:30`);
  }
  expect(names.filter((n) => n?.startsWith('Konditionstraining'))).toHaveLength(5);
});

test('F-03 K5 TER-07: A series exception excludes events of the series', async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/serien/${SERIES_1}`);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Training');
  const exceptions = page.getByTestId('exceptions');
  await expect(exceptions.getByRole('listitem')).toHaveText(['Weihnachtsferien: 21.12.2026 – 03.01.2027']);
  await checkAccessibility(page);

  await page.getByLabel('Von').fill('2026-11-30');
  await page.getByLabel('Bis').fill('2026-12-06');
  await page.getByLabel('Grund, z.B. Herbstferien').fill('Hallenreinigung');
  await page.getByRole('button', { name: 'Ausnahme hinzufügen' }).click();
  await expect(page.getByRole('status')).toHaveText('Ausnahme «Hallenreinigung» hinzugefügt.');
  await expect(exceptions.getByRole('listitem')).toHaveText([
    'Hallenreinigung: 30.11.2026 – 06.12.2026',
    'Weihnachtsferien: 21.12.2026 – 03.01.2027',
  ]);

  await page.goto(`/termine?team=${TEAM_2}`);
  await expect(card(page, T01)).toBeVisible();
  const names = await entries(page);
  expect(names).toContain('Training Di 24.11. 18:30');
  expect(names).not.toContain('Training Di 01.12. 18:30');
  expect(names).toContain('Training Di 08.12. 18:30');
});

test('F-03 K5 TER-07: A series exception without reason is not saved', async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/serien/${SERIES_1}`);
  await page.getByLabel('Von').fill('2026-11-30');
  await page.getByLabel('Bis').fill('2026-12-06');
  await page.getByRole('button', { name: 'Ausnahme hinzufügen' }).click();
  const reason = page.getByLabel('Grund, z.B. Herbstferien');
  await expect(reason).toHaveAttribute('aria-invalid', 'true');
  await expect(reason).toBeFocused();
});

test('F-03 K6 TER-06: Reschedule a single series event, the series stays', async ({ page }) => {
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
  await expect(card(page, T02)).toHaveAttribute('aria-label', 'Training Fr 20.11. 19:00');
  await expect(card(page, T02).getByTestId('event-status')).toHaveText('verschoben');
  await expect(card(page, T01)).toBeVisible();
  const names = await entries(page);
  expect(names).toContain('Training Do 26.11. 19:00');
  expect(names).not.toContain('Training Do 19.11. 19:00');

  // The series itself is unchanged
  await page.goto(`/termine/${T02}`);
  await page.getByRole('link', { name: 'Serie ansehen' }).click();
  await expect(page.getByText('Jeden Donnerstag, 19:00–20:30')).toBeVisible();
});

test('F-03 K7 S13 TER-06: A cancelled event is recognizable, with reason', async ({ page }) => {
  await login(page, 'anna');
  await page.goto('/termine');
  await expect(card(page, T03).getByTestId('event-status')).toHaveText('abgesagt');
  await expect(card(page, T03)).toContainText('Halle besetzt');
  await card(page, T03).getByRole('link').click();
  await expect(page).toHaveURL(`/termine/${T03}`);
  await expect(page.getByTestId('event-status')).toContainText('Abgesagt: Halle besetzt');
  await expect(page.getByTestId('event-status')).toContainText('Antworten ist nicht mehr möglich.');
  await expect(page.getByRole('link', { name: 'Termin bearbeiten' })).toHaveCount(0);
  await checkAccessibility(page);
});

test('F-03 K7 TER-06: A cancelled event has a coral line on top of its card in the list', async ({ page }) => {
  await login(page, 'anna');
  await page.goto('/termine');
  // Since E-105 every card carries the colour of its kind on its top edge; a cancelled one is coral instead.
  const lineOnTop = (id: string) => card(page, id).evaluate((li) => getComputedStyle(li).borderTopColor);
  const coral = await page.evaluate(() => {
    const probe = document.createElement('span');
    probe.style.color = 'var(--coral)';
    document.body.append(probe);
    const color = getComputedStyle(probe).color;
    probe.remove();
    return color;
  });
  expect(await lineOnTop(T03)).toBe(coral);
  expect(await lineOnTop(T01)).not.toBe(coral);
  await expect(card(page, T03).getByTestId('event-status')).toHaveText(/^abgesagt$/i);
});

test('F-03 K7 TER-06: Coach cancels an event with reason', async ({ page }) => {
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

test('F-03 K8 TER-09 TER-10: Response deadline per event, default 24 hours', { tag: '@desktop' }, async ({ page }) => {
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

  // Series: response deadline for all its events
  await page.goto(`/serien/${SERIES_1}/bearbeiten`);
  await expect(page.getByText('Änderungen gelten für alle künftigen Termine der Serie.')).toBeVisible();
  await page.getByLabel('Antwortfrist: Stunden vor Beginn').fill('12');
  await page.getByRole('button', { name: 'Änderungen speichern' }).click();
  await expect(page).toHaveURL(`/serien/${SERIES_1}`);
  await expect(page.getByText('12 Stunden vor Beginn')).toBeVisible();
  await page.goto(`/termine/${T01}`);
  await expect(page.getByTestId('response-deadline')).toHaveText('Di 17.11. 06:30 (12 Stunden vor Beginn)');
});

test('F-03 K9 F-12 E-111: Team chips and view ?ansicht=vergangen are in the URL', async ({ page }) => {
  await login(page, 'petra');
  await page.goto('/termine');
  // Her own teams are on, the URL carries nothing (E-111)
  const teams = page.getByRole('group', { name: 'Teams', exact: true });
  await expect(teams.getByRole('checkbox')).toHaveCount(3);
  await expect(page.getByTestId(`team-chip-${TEAM_1}`)).toBeChecked();
  await page.getByTestId(`team-chip-${TEAM_1}`).uncheck();
  await expect(page).toHaveURL(`/termine?teams=${TEAM_2}`);
  await expect(card(page, T05)).toBeVisible();
  await expect(card(page, T02)).toHaveCount(0);

  const view = page.getByRole('navigation', { name: 'Ansicht' });
  await expect(view.getByRole('link', { name: 'Kommend' })).toHaveAttribute('aria-current', 'page');
  await view.getByRole('link', { name: 'Vergangen' }).click();
  await expect(page).toHaveURL(`/termine?teams=${TEAM_2}&ansicht=vergangen`);
  await expect(view.getByRole('link', { name: 'Vergangen' })).toHaveAttribute('aria-current', 'page');
  await expect(card(page, T07)).toBeVisible();
  await expect(card(page, T01)).toHaveCount(0);

  // Deep link after reload
  await page.reload();
  await expect(page.getByTestId(`team-chip-${TEAM_2}`)).toBeChecked();
  await expect(page.getByTestId(`team-chip-${TEAM_1}`)).not.toBeChecked();
  await expect(card(page, T07)).toBeVisible();
  await checkAccessibility(page);

  // F-12: as a coach Petra sees all events of Team 3, although she is not a member there; the old ?team= still works
  await page.goto(`/termine?team=${TEAM_3}`);
  await expect.poll(async () => (await entries(page)).slice(0, 2)).toEqual([
    'Training Mi 18.11. 18:00',
    'Training Mi 25.11. 18:00',
  ]);
  // Test finding F-03: chips and list match, also for a team Petra is not in
  await expect(page.getByTestId(`team-chip-${TEAM_3}`)).toBeChecked();
  await expect(page.getByTestId(`team-chip-${TEAM_3}`)).toBeDisabled();
  await expect(page.getByTestId(`team-chip-${TEAM_1}`)).not.toBeChecked();

  // A player (Lea, Team 1 + 2) only sees the shared events of Team 3 (TER-13)
  await login(page, 'lea');
  await page.goto(`/termine?team=${TEAM_3}`);
  await expect.poll(() => entries(page)).toEqual(['Teamessen Fr 11.12. 19:00']);
  await expect(page.getByTestId(`team-chip-${TEAM_3}`)).toBeChecked();
  await expect(page.getByRole('group', { name: 'Teams', exact: true }).getByRole('checkbox')).toHaveCount(3);
});

// ------------------------------------------------------------------ Findings from testing (round 1)

test('F-03 TER-11: A series names all its teams, also for people from only one team', async ({
  page,
}) => {
  for (const key of ['anna', 'marc']) {
    await login(page, key);
    await page.goto(`/serien/${SERIES_1}`);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Training');
    await expect(page.getByText('Team 1 + Team 2', { exact: true })).toBeVisible();
  }
});

test('F-03 TER-09 TER-10: Response deadline outside 0 to 336 hours with a German message', async ({
  page,
}) => {
  await login(page, 'petra');
  await page.goto('/termine/neu');
  await page.getByLabel('Datum', { exact: true }).fill('2026-11-21');
  await page.getByLabel('Beginn', { exact: true }).fill('10:00');
  await page.getByLabel('Ende', { exact: true }).fill('12:00');
  const deadline = page.getByLabel('Antwortfrist: Stunden vor Beginn');
  await deadline.fill('337');
  await page.getByRole('button', { name: 'Termin speichern' }).click();
  await expect(page.getByText('Höchstens 336 Stunden (14 Tage).')).toBeVisible();
  await expect(deadline).toBeFocused();
  await expect(deadline).toHaveAttribute('aria-invalid', 'true');
  await deadline.fill('-1');
  await page.getByRole('button', { name: 'Termin speichern' }).click();
  await expect(page.getByText('Mindestens 0 Stunden.')).toBeVisible();
  await expect(page).toHaveURL(/\/termine\/neu$/);

  await page.goto(`/serien/${SERIES_1}/bearbeiten`);
  await page.getByLabel('Antwortfrist: Stunden vor Beginn').fill('400');
  await page.getByRole('button', { name: 'Änderungen speichern' }).click();
  await expect(page.getByText('Höchstens 336 Stunden (14 Tage).')).toBeVisible();
  await expect(page).toHaveURL(`/serien/${SERIES_1}/bearbeiten`);
});

test('F-03: Long words in title, location and notes wrap on the event page', async ({
  page,
}) => {
  const longText = 'https://example.ch/' + 'very-long-link-without-any-spaces-'.repeat(5);
  await login(page, 'petra');
  await page.goto('/termine/neu');
  await page.getByLabel('Titel', { exact: true }).fill('Zusatztraining' + 'X'.repeat(60));
  await page.getByLabel('Datum', { exact: true }).fill('2026-11-20');
  await page.getByLabel('Beginn', { exact: true }).fill('18:00');
  await page.getByLabel('Ende', { exact: true }).fill('19:30');
  await page.getByLabel('Ort (freiwillig)').fill(longText);
  await page.getByLabel(/^Hinweise/).fill(longText);
  await page.getByRole('button', { name: 'Termin speichern' }).click();
  await expect(page).toHaveURL(/\/termine\/[0-9a-f-]{36}$/);
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Zusatztraining');
  const widths = await page.evaluate(() => ({
    scroll: document.documentElement.scrollWidth,
    window: document.documentElement.clientWidth,
  }));
  expect(widths.scroll).toBeLessThanOrEqual(widths.window);
});
