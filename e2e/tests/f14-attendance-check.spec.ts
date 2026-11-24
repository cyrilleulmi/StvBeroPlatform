import type { APIRequestContext, Locator, Page } from '@playwright/test';
import { checkAccessibility, expect, login, reset, test } from './helpers';

/**
 * F-14 · Attendance check per event (PRS-01 to PRS-10, E-70). Acceptance criteria from issue #53,
 * seed events 09 (switch on, nothing recorded) and 10 (recorded by Petra) from docs/test-data.md.
 * Default "now": Mon 16.11.2026 06:00.
 */

const PETRA = '20000000-0000-4000-8000-000000000002';
const ANNA = '20000000-0000-4000-8000-000000000005';
const MIA = '20000000-0000-4000-8000-000000000007';
const SARA = '20000000-0000-4000-8000-000000000008';
const KIM = '20000000-0000-4000-8000-000000000011';
const LEA = '20000000-0000-4000-8000-000000000006';
const TEAM_1 = '10000000-0000-4000-8000-000000000001';
const TEAM_2 = '10000000-0000-4000-8000-000000000002';
const T01 = '40000000-0000-4000-8000-000000000001';
const T09 = '40000000-0000-4000-8000-000000000009';
const T10 = '40000000-0000-4000-8000-000000000010';

function person(page: Page, personId: string): Locator {
  return page.getByTestId(`attendance-person-${personId}`);
}

function option(page: Page, personId: string, label: string): Locator {
  return person(page, personId).getByRole('radio', { name: label });
}

async function setClock(request: APIRequestContext, now: string): Promise<void> {
  const response = await request.put('/api/test/clock', { data: { now } });
  expect(response.status()).toBe(204);
}

/** Practice of Team 1 on Fri 20.11.2026 18:00–20:00, created via the API as Petra. */
async function createEvent(page: Page, attendanceCheck: boolean): Promise<string> {
  const response = await page.request.post('/api/events', {
    data: {
      type: 'PRACTICE',
      title: 'Zusatztraining',
      start: '2026-11-20T18:00:00+01:00',
      end: '2026-11-20T20:00:00+01:00',
      teamIds: [TEAM_1],
      attendanceCheck,
    },
  });
  expect(response.status()).toBe(201);
  return (await response.json()).id;
}

test.beforeEach(async ({ request }) => {
  await reset(request);
});

test('PRS-01: the switch «Präsenz erfassen» is offered for events and series and is off by default', async ({
  page,
}) => {
  await login(page, 'petra');
  await page.goto('/termine/neu');
  const check = page.getByRole('checkbox', { name: 'Präsenz erfassen' });
  await expect(check).not.toBeChecked();
  await expect(page.getByText('Nach Beginn des Termins hakst du ab, wer wirklich da war.')).toBeVisible();

  await page.getByRole('radio', { name: 'Serie (jede Woche)' }).check();
  await expect(page).toHaveURL(/art=serie/);
  await expect(check).not.toBeChecked();
  await expect(page.getByText('Gilt für alle Termine der Serie. Einzelne Termine kannst du abweichend einstellen.')).toBeVisible();

  // Single event with the switch on
  await page.getByRole('radio', { name: 'Einzelner Termin' }).check();
  await page.getByLabel('Datum').fill('2026-11-20');
  await page.getByRole('checkbox', { name: 'Team 1' }).check();
  await check.check();
  await page.getByRole('button', { name: 'Termin speichern' }).click();
  await expect(page.getByRole('status')).toHaveText('Der Termin ist erfasst.');
  // PRS-02: before the start not offered, only the hint
  await expect(page.getByTestId('attendance-section')).toContainText('Die Präsenz lässt sich ab Beginn des Termins erfassen.');
  await expect(page.getByRole('link', { name: 'Präsenz erfassen' })).toHaveCount(0);

  await page.getByRole('link', { name: 'Termin bearbeiten' }).click();
  await expect(page.getByRole('checkbox', { name: 'Präsenz erfassen' })).toBeChecked();

  // Series events of the seed are off
  await page.goto(`/termine/${T01}/bearbeiten`);
  await expect(page.getByRole('checkbox', { name: 'Präsenz erfassen' })).not.toBeChecked();
  await checkAccessibility(page);
});

test('PRS-01: a series passes the switch on to its events', async ({ page }) => {
  await login(page, 'petra');
  await page.goto('/termine/neu?art=serie');
  await page.getByLabel('Titel').fill('Konditionstraining');
  await page.getByLabel('Wochentag').selectOption('FRIDAY');
  await page.getByLabel('Erster Tag der Serie').fill('2026-11-20');
  await page.getByLabel('Letzter Tag der Serie').fill('2026-12-04');
  await page.getByRole('checkbox', { name: 'Team 1' }).check();
  await page.getByRole('checkbox', { name: 'Präsenz erfassen' }).check();
  await page.getByRole('button', { name: 'Serie anlegen' }).click();
  await expect(page).toHaveURL(/\/serien\//);
  const seriesId = page.url().split('/serien/')[1];
  const series = await (await page.request.get(`/api/series/${seriesId}`)).json();
  expect(series.attendanceCheck).toBe(true);
  const events = await (await page.request.get(`/api/events?team=${TEAM_1}&from=2026-11-20&to=2026-12-04`)).json();
  const ofSeries = events.filter((e: { seriesId?: string }) => e.seriesId === seriesId);
  expect(ofSeries.length).toBe(3);
  for (const e of ofSeries) {
    expect((await (await page.request.get(`/api/events/${e.id}`)).json()).attendanceCheck).toBe(true);
  }
  await page.getByRole('link', { name: 'Serie bearbeiten' }).click();
  await expect(page.getByRole('checkbox', { name: 'Präsenz erfassen' })).toBeChecked();
});

test('PRS-02 PRS-03 PRS-05: Petra sees the players of event 09 by line with the preselection', async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/termine/${T09}`);
  const section = page.getByTestId('attendance-section');
  await expect(section).toContainText('Noch keine Präsenz erfasst.');
  await section.getByRole('link', { name: 'Präsenz erfassen' }).click();
  await expect(page).toHaveURL(`/termine/${T09}/praesenz`);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Präsenz erfassen');

  const list = page.getByTestId('attendance-list');
  await expect(list.getByRole('heading', { level: 3 })).toHaveText(['Team 1']);
  await expect(list.getByRole('heading', { level: 4 })).toHaveText(['Linie 1', 'Linie 2', 'Goalies', 'Ohne Linie']);
  await expect(page.getByTestId('attendance-state')).toHaveText('Noch keine Präsenz erfasst.');
  // «dabei» → anwesend, «nicht dabei», «vielleicht», «offen» → abwesend
  await expect(option(page, ANNA, 'anwesend')).toBeChecked();
  await expect(person(page, ANNA)).toHaveAttribute('data-response', 'ATTENDING');
  await expect(option(page, MIA, 'abwesend')).toBeChecked();
  await expect(person(page, MIA)).toContainText('nicht dabei');
  await expect(person(page, MIA)).toContainText('«Krank»');
  await expect(option(page, KIM, 'abwesend')).toBeChecked();
  await expect(person(page, KIM)).toHaveAttribute('data-response', 'PENDING');
  await expect(page.getByTestId('attendance-summary')).toHaveText('4 anwesend · 3 abwesend · 0 entschuldigt');
  // PRS-05: coaches are not part of the list
  await expect(person(page, PETRA)).toHaveCount(0);
  await expect(list.getByRole('listitem', { name: 'Petra Keller' })).toHaveCount(0);
  await checkAccessibility(page);

  // Nothing is saved until «Präsenz speichern»
  await option(page, MIA, 'entschuldigt').check();
  await expect(page.getByTestId('attendance-unsaved')).toHaveText('Noch nicht gespeichert.');
  await expect(page.getByTestId('attendance-summary')).toHaveText('4 anwesend · 2 abwesend · 1 entschuldigt');
  await page.reload();
  await expect(option(page, MIA, 'abwesend')).toBeChecked();
  const event = await (await page.request.get(`/api/events/${T09}`)).json();
  expect(event.attendanceRecorded).toBe(false);
});

test('PRS-03: a shared event shows the players by team, then by line, as in the overview', { tag: '@desktop' }, async ({
  page,
  request,
}) => {
  await login(page, 'petra');
  const response = await page.request.post('/api/events', {
    data: {
      type: 'PRACTICE',
      title: 'Gemeinsames Training',
      start: '2026-11-20T18:00:00+01:00',
      end: '2026-11-20T20:00:00+01:00',
      teamIds: [TEAM_2, TEAM_1],
      attendanceCheck: true,
    },
  });
  expect(response.status()).toBe(201);
  const id = (await response.json()).id;
  await setClock(request, '2026-11-20T19:00:00+01:00');
  await page.goto(`/termine/${id}/praesenz`);

  const list = page.getByTestId('attendance-list');
  await expect(list.getByRole('heading', { level: 3 })).toHaveText(['Team 1', 'Team 2']);
  const team1 = page.getByTestId(`attendance-team-${TEAM_1}`);
  const team2 = page.getByTestId(`attendance-team-${TEAM_2}`);
  await expect(team1.getByRole('heading', { level: 4 })).toHaveText(['Linie 1', 'Linie 2', 'Goalies', 'Ohne Linie']);
  await expect(page.getByRole('region', { name: 'Team 1' })).toHaveCount(1);
  await expect(page.getByRole('region', { name: 'Team 2' })).toHaveCount(1);
  // Lea plays in both teams, in a different line in each: one status for both entries
  await expect(team1.getByRole('region', { name: 'Linie 1' }).getByTestId(`attendance-person-${LEA}`)).toHaveCount(1);
  await expect(team2.getByRole('region', { name: 'Linie 2' }).getByTestId(`attendance-person-${LEA}`)).toHaveCount(1);
  await team1.getByTestId(`attendance-person-${LEA}`).getByRole('radio', { name: 'entschuldigt' }).check();
  await expect(team2.getByTestId(`attendance-person-${LEA}`).getByRole('radio', { name: 'entschuldigt' })).toBeChecked();
  await checkAccessibility(page);

  await page.getByRole('button', { name: 'Präsenz speichern' }).click();
  await expect(page.getByRole('status')).toHaveText('Die Präsenz ist gespeichert.');
  await page.reload();
  await expect(team2.getByTestId(`attendance-person-${LEA}`)).toHaveAttribute('data-attendance', 'EXCUSED');
});

test('PRS-04: saving stores who and when, a later correction counts', async ({ page, request }) => {
  await setClock(request, '2026-11-12T21:00:00+01:00');
  await login(page, 'petra');
  await page.goto(`/termine/${T09}/praesenz`);
  await option(page, MIA, 'entschuldigt').check();
  await option(page, SARA, 'abwesend').check();
  const save = page.getByRole('button', { name: 'Präsenz speichern' });
  await save.click();
  await expect(page.getByRole('status')).toHaveText('Die Präsenz ist gespeichert.');
  await expect(page.getByTestId('attendance-state')).toHaveText('Zuletzt gespeichert von Petra Keller am Do 12.11. 21:00.');
  await expect(page.getByTestId('attendance-unsaved')).toHaveCount(0);

  await page.reload();
  await expect(option(page, MIA, 'entschuldigt')).toBeChecked();
  await expect(option(page, SARA, 'abwesend')).toBeChecked();
  await expect(option(page, ANNA, 'anwesend')).toBeChecked();

  // Correction by Marc (coach of another team, E-69)
  await setClock(request, '2026-11-13T08:00:00+01:00');
  await login(page, 'marc');
  await page.goto(`/termine/${T09}`);
  await expect(page.getByTestId('attendance-recorded')).toHaveText('Präsenz ist erfasst.');
  await page.getByRole('link', { name: 'Präsenz korrigieren' }).click();
  await option(page, SARA, 'anwesend').check();
  await save.click();
  await expect(page.getByRole('status')).toHaveText('Die Präsenz ist gespeichert.');
  await expect(page.getByTestId('attendance-state')).toHaveText('Zuletzt gespeichert von Marc Huber am Fr 13.11. 08:00.');

  // The response stays what it was
  await login(page, 'mia');
  await page.goto(`/termine/${T09}`);
  await expect(page.getByTestId('my-response')).toHaveAttribute('data-response', 'DECLINED');
  await expect(page.getByTestId('my-attendance')).toHaveAttribute('data-attendance', 'EXCUSED');
});

test('PRS-06: players see only their own attendance', async ({ page }) => {
  await login(page, 'sara');
  await page.goto(`/termine/${T10}`);
  const mine = page.getByTestId('my-attendance');
  await expect(mine.getByRole('heading', { name: 'Deine Präsenz' })).toBeVisible();
  await expect(mine).toContainText('Der Coach hat dich als «abwesend» erfasst.');
  await expect(mine).toHaveAttribute('data-attendance', 'ABSENT');
  await expect(page.getByTestId('attendance-section')).toHaveCount(0);
  await expect(page.getByText('entschuldigt')).toHaveCount(0);
  await checkAccessibility(page);

  // Nothing recorded for event 09: nothing shown
  await page.goto(`/termine/${T09}`);
  await expect(page.getByRole('heading', { name: 'Meine Antwort' })).toBeVisible();
  await expect(page.getByTestId('my-attendance')).toHaveCount(0);

  // No access to the list of others
  await page.goto(`/termine/${T10}/praesenz`);
  await expect(page.getByRole('alert')).toHaveText('Keine Berechtigung');
  expect((await page.request.get(`/api/events/${T10}/attendance`)).status()).toBe(403);
});

test('PRS-07: without the switch no attendance can be recorded', async ({ page, request }) => {
  await setClock(request, '2026-11-17T19:00:00+01:00');
  await login(page, 'petra');
  await page.goto(`/termine/${T01}`);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Training');
  await expect(page.getByTestId('attendance-section')).toHaveCount(0);
  await expect(page.getByRole('link', { name: 'Präsenz erfassen' })).toHaveCount(0);

  await page.goto(`/termine/${T01}/praesenz`);
  await expect(page.getByRole('alert')).toHaveText('Für diesen Termin lässt sich keine Präsenz erfassen.');
  const response = await page.request.put(`/api/events/${T01}/attendance`, {
    data: { entries: [{ personId: ANNA, status: 'PRESENT' }] },
  });
  expect(response.status()).toBe(409);
  expect((await response.json()).rule).toBe('PRS-07');
});

test('PRS-09: a cancelled event takes no attendance', async ({ page, request }) => {
  await login(page, 'petra');
  const id = await createEvent(page, true);
  expect((await page.request.post(`/api/events/${id}/cancel`, { data: { reason: 'Halle besetzt' } })).status()).toBe(200);
  await setClock(request, '2026-11-20T19:00:00+01:00');
  await login(page, 'petra');
  await page.goto(`/termine/${id}`);
  await expect(page.getByTestId('event-status')).toContainText('Halle besetzt');
  await expect(page.getByTestId('attendance-section')).toHaveCount(0);
  const response = await page.request.get(`/api/events/${id}/attendance`);
  expect(response.status()).toBe(409);
  expect((await response.json()).rule).toBe('PRS-09');
});

test('PRS-08: switching off with recorded attendance asks and keeps the data', async ({ page, request }) => {
  await login(page, 'petra');
  const id = await createEvent(page, true);
  await setClock(request, '2026-11-20T19:00:00+01:00');
  await login(page, 'petra');
  await page.goto(`/termine/${id}/praesenz`);
  await option(page, ANNA, 'entschuldigt').check();
  await page.getByRole('button', { name: 'Präsenz speichern' }).click();
  await expect(page.getByRole('status')).toHaveText('Die Präsenz ist gespeichert.');

  await page.goto(`/termine/${id}/bearbeiten`);
  await page.getByRole('checkbox', { name: 'Präsenz erfassen' }).uncheck();
  await page.getByRole('button', { name: 'Änderungen speichern' }).click();
  const dialog = page.getByRole('dialog', { name: 'Präsenz ausschalten?' });
  await expect(dialog).toBeVisible();
  await expect(dialog).toContainText('Sie bleibt gespeichert');
  // Cancel keeps the form open and saves nothing
  await dialog.getByRole('button', { name: 'Abbrechen' }).click();
  await expect(dialog).toBeHidden();
  expect((await (await page.request.get(`/api/events/${id}`)).json()).attendanceCheck).toBe(true);

  await page.getByRole('button', { name: 'Änderungen speichern' }).click();
  await dialog.getByRole('button', { name: 'Ausschalten und speichern' }).click();
  await expect(page).toHaveURL(`/termine/${id}`);
  await expect(page.getByRole('status')).toHaveText('Änderungen gespeichert.');
  await expect(page.getByTestId('attendance-section')).toHaveCount(0);
  const event = await (await page.request.get(`/api/events/${id}`)).json();
  expect(event.attendanceCheck).toBe(false);
  expect(event.attendanceRecorded).toBe(true);

  // Switched on again: the data is still there
  await page.goto(`/termine/${id}/bearbeiten`);
  await page.getByRole('checkbox', { name: 'Präsenz erfassen' }).check();
  await page.getByRole('button', { name: 'Änderungen speichern' }).click();
  await expect(dialog).toBeHidden();
  await expect(page.getByRole('status')).toHaveText('Änderungen gespeichert.');
  await page.getByRole('link', { name: 'Präsenz korrigieren' }).click();
  await expect(option(page, ANNA, 'entschuldigt')).toBeChecked();
});

test('PRS-10: seed event 10 is recorded by Petra; Anna only sees her own value', async ({ page }) => {
  await login(page, 'alex');
  await page.goto(`/termine/${T10}/praesenz`);
  await expect(page.getByTestId('attendance-state')).toHaveText('Zuletzt gespeichert von Petra Keller am Do 05.11. 21:00.');
  await expect(page.getByTestId('attendance-summary')).toHaveText('4 anwesend · 2 abwesend · 1 entschuldigt');
  await expect(option(page, SARA, 'abwesend')).toBeChecked();
  await expect(person(page, SARA)).toHaveAttribute('data-response', 'ATTENDING');

  await login(page, 'anna');
  await page.goto(`/termine/${T10}`);
  await expect(page.getByTestId('my-attendance')).toHaveAttribute('data-attendance', 'PRESENT');
});
