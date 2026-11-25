import type { Locator, Page } from '@playwright/test';
import { checkAccessibility, expect, login, reset, test } from './helpers';

/**
 * Batch «event variants» (E-86 to E-89): event variants per team with colour, call-up or responses only, sections
 * of the event page and automatic departure (VAR, TML-09), table ranks with the matches (AUF-34), proposed
 * driver list, driving settings and travelling on one's own (DRV-18 to DRV-20, ANM-13), special line-ups
 * (LIN-13 to LIN-16) and teams that are invited without obligation (TER-15 to TER-17).
 * All against the test data set of docs/test-data.md with the simulators. Default "now": Mon 16.11.2026 06:00.
 */

const TEAM_1 = '10000000-0000-4000-8000-000000000001';
const TEAM_2 = '10000000-0000-4000-8000-000000000002';
const T01 = '40000000-0000-4000-8000-000000000001';
const T04 = '40000000-0000-4000-8000-000000000004';
const ANNA = '20000000-0000-4000-8000-000000000005';

function card(page: Page, id: string): Locator {
  return page.getByTestId(`event-card-${id}`);
}

function driverList(page: Page): Locator {
  return page.getByTestId('driver-list');
}

function variants(page: Page): Locator {
  return page.getByTestId('event-variants');
}

/** Headings of the sections of the event page, in the order they are shown. */
async function sections(page: Page): Promise<string[]> {
  const all = await page.getByRole('heading', { level: 2 }).allTextContents();
  return all.map((text) => text.trim());
}

/** The standard variant of a kind of Team 1, read as the logged-in person. */
async function standardVariant(page: Page, kind: string): Promise<{ id: string; name: string; color: string }> {
  const response = await page.request.get(`/api/teams/${TEAM_1}/event-variants`);
  expect(response.status()).toBe(200);
  return (await response.json()).find((v: { kind: string }) => v.kind === kind);
}

async function updateVariant(page: Page, kind: string, change: object): Promise<void> {
  const variant = await standardVariant(page, kind);
  const response = await page.request.put(`/api/teams/${TEAM_1}/event-variants/${variant.id}`, {
    data: { name: variant.name, color: variant.color, ...change },
  });
  expect(response.status()).toBe(200);
}

/** A match day of Team 1 with a meeting point without a time and one match at 10:00. */
async function createMatchDay(page: Page, day: string): Promise<string> {
  const response = await page.request.post('/api/events', {
    data: {
      type: 'MATCH_DAY',
      title: 'Spieltag',
      start: `${day}T10:00:00+01:00`,
      end: `${day}T14:00:00+01:00`,
      teamIds: [TEAM_1],
      location: { name: 'Sporthalle Seefeld' },
      meetingPoint: { time: null, location: 'Parkplatz Sporthalle Nord' },
    },
  });
  expect(response.status()).toBe(201);
  const id = (await response.json()).id;
  const added = await page.request.post(`/api/events/${id}/matches`, {
    data: { opponent: 'UHC Drei', startTime: `${day}T10:00:00+01:00`, home: false },
  });
  expect(added.status()).toBe(201);
  return id;
}

/** The cars of a match day behind «Plätze zuweisen» (DRV-32). */
async function openSeats(page: Page, eventId: string): Promise<Locator> {
  await page.goto(`/termine/${eventId}`);
  await page.getByTestId('driver-assign').click();
  const board = page.getByTestId(`seat-board-${eventId}`);
  await expect(board).toBeVisible();
  return board;
}

test.beforeEach(async ({ request }) => {
  await reset(request);
});

// ------------------------------------------------------------------ Colours and names (VAR-04, VAR-05, TER-14)

test('VAR-05: the event list shows the variant of every event by name and colour', { tag: '@desktop' }, async ({ page }) => {
  await login(page, 'anna');
  await page.goto('/termine');

  await expect(card(page, T01).getByTestId('event-variant')).toHaveText('Training');
  await expect(card(page, T01).getByTestId('event-variant')).toHaveAttribute('data-tag', 'GREEN');
  await expect(card(page, T04).getByTestId('event-variant')).toHaveText('swiss unihockey Spieltag');
  await expect(card(page, T04).getByTestId('event-variant')).toHaveAttribute('data-tag', 'BLUE');
  await checkAccessibility(page);

  await page.goto(`/termine/${T04}`);
  await expect(page.getByTestId('event-variant')).toHaveText('swiss unihockey Spieltag');
  await checkAccessibility(page);
});

test('VAR-04: a coach changes name and colour of a variant, and the events follow', async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/verwaltung/teams/${TEAM_1}`);
  await expect(variants(page).getByRole('listitem')).toHaveCount(4);
  await expect(variants(page).getByTestId('variant-PRACTICE')).toContainText('Training · Grün · Standard');
  await expect(variants(page).getByTestId('variant-SU_MATCH_DAY')).toContainText('mit Aufgebot · Abfahrt automatisch');
  await checkAccessibility(page);

  await variants(page).getByRole('button', { name: 'Variante «Training» bearbeiten' }).click();
  const dialog = page.getByRole('dialog', { name: 'Terminvariante «Training»' });
  await dialog.getByLabel('Name').fill('');
  await dialog.getByRole('button', { name: 'Speichern' }).click();
  await expect(dialog.getByText('Bitte einen Namen eingeben.')).toBeVisible();
  await dialog.getByLabel('Name').fill('Teamtraining');
  await dialog.getByRole('radio', { name: 'Orange' }).check();
  await checkAccessibility(page);
  await dialog.getByRole('button', { name: 'Speichern' }).click();

  await expect(variants(page).getByRole('status')).toHaveText('Variante «Teamtraining» ist gespeichert.');
  await expect(variants(page).getByTestId('variant-PRACTICE')).toHaveAttribute('data-color', 'ORANGE');

  await page.goto('/termine');
  await expect(card(page, T01).getByTestId('event-variant')).toHaveText('Teamtraining');
  await expect(card(page, T01).getByTestId('event-variant')).toHaveAttribute('data-tag', 'ORANGE');
});

test('VAR-04: every colour of the palette has enough contrast', async ({ page }) => {
  await login(page, 'petra');
  // Nine variants, one per colour: the four standard ones and five of the team's own
  await updateVariant(page, 'PRACTICE', { color: 'TEAL' });
  for (const color of ['GREEN', 'RED', 'ORANGE', 'YELLOW', 'GREY']) {
    const created = await page.request.post(`/api/teams/${TEAM_1}/event-variants`, {
      data: { name: `Farbe ${color}`, eventType: 'OTHER', color },
    });
    expect(created.status()).toBe(201);
  }

  await page.goto(`/verwaltung/teams/${TEAM_1}`);
  await expect(variants(page).getByRole('listitem')).toHaveCount(9);
  for (const color of ['TEAL', 'VIOLET', 'BLUE', 'PINK', 'GREEN', 'RED', 'ORANGE', 'YELLOW', 'GREY']) {
    const colors = await variants(page)
      .getByRole('listitem')
      .evaluateAll((items) => items.map((item) => item.getAttribute('data-color')));
    expect(colors).toContain(color);
  }
  await checkAccessibility(page);

  // The date box of the event list uses the same colours
  await page.goto('/termine');
  await expect(card(page, T01).getByTestId('event-variant')).toHaveAttribute('data-tag', 'TEAL');
  await checkAccessibility(page);
});

// ------------------------------------------------------------------ Call-up or responses only (VAR-06)

test('VAR-06: without a call-up in the variant the match day works with the responses alone', async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/termine/${T04}`);
  await expect(page.getByRole('heading', { level: 2, name: 'Aufgebot' })).toBeVisible();

  await page.goto(`/verwaltung/teams/${TEAM_1}`);
  await variants(page).getByRole('button', { name: 'Variante «swiss unihockey Spieltag» bearbeiten' }).click();
  const dialog = page.getByRole('dialog', { name: 'Terminvariante «swiss unihockey Spieltag»' });
  await dialog.getByRole('checkbox', { name: 'Aufgebot machen' }).uncheck();
  await dialog.getByRole('button', { name: 'Speichern' }).click();
  await expect(variants(page).getByTestId('variant-SU_MATCH_DAY')).toContainText('nur An- und Abmeldungen');

  await page.goto(`/termine/${T04}`);
  await expect(page.getByRole('heading', { level: 2, name: 'Spiele' })).toBeVisible();
  await expect(page.getByRole('heading', { level: 2, name: 'Aufgebot' })).toHaveCount(0);
  // The driver list counts who responded «dabei»: Jana still waits for a seat
  await page.getByTestId('driver-assign').click();
  await expect(driverList(page).getByTestId('seat-waiting')).toContainText('Jana Wyss');

  // Players see no call-up status either
  await login(page, 'anna');
  await page.goto(`/termine/${T04}`);
  await expect(page.getByRole('heading', { level: 2, name: 'Spiele' })).toBeVisible();
  await expect(page.getByTestId('my-call-up')).toHaveCount(0);
});

// ------------------------------------------------------------------ Sections of the event page (VAR-07)

test('VAR-07: the responses come first, and a coach changes order and visibility of the sections', async ({
  page,
}) => {
  await login(page, 'petra');
  await page.goto(`/termine/${T04}`);
  await expect(driverList(page)).toBeVisible();
  expect(await sections(page)).toEqual([
    'Meine Antwort',
    'Anmeldungen',
    'Spiele',
    'Aufgebot',
    'Zeitplan',
    'Fahrerliste',
    'Angaben',
  ]);

  await page.goto(`/verwaltung/teams/${TEAM_1}`);
  await variants(page).getByRole('button', { name: 'Variante «swiss unihockey Spieltag» bearbeiten' }).click();
  const dialog = page.getByRole('dialog', { name: 'Terminvariante «swiss unihockey Spieltag»' });
  // «Fahrerliste» to the top: four steps up
  for (let i = 0; i < 4; i++) {
    await dialog.getByRole('button', { name: '«Fahrerliste» nach oben' }).click();
  }
  await dialog.getByRole('checkbox', { name: '«Zeitplan» anzeigen' }).uncheck();
  // «Angaben» cannot be hidden
  await expect(dialog.getByRole('checkbox', { name: '«Angaben» anzeigen' })).toBeDisabled();
  await dialog.getByRole('button', { name: 'Speichern' }).click();
  await expect(variants(page).getByTestId('variant-SU_MATCH_DAY')).toContainText(
    'Fahrerliste · Anmeldungen · Spiele · Aufgebot · Aufstellungen · Präsenz · Angaben',
  );

  await page.goto(`/termine/${T04}`);
  await expect(driverList(page)).toBeVisible();
  expect(await sections(page)).toEqual([
    'Meine Antwort',
    'Fahrerliste',
    'Anmeldungen',
    'Spiele',
    'Aufgebot',
    'Angaben',
  ]);
});

// ------------------------------------------------------------------ Own variants (VAR-02, VAR-03)

test('VAR-02: a variant of the team`s own can be chosen for an event', async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/verwaltung/teams/${TEAM_1}`);
  await variants(page).getByRole('button', { name: 'Variante hinzufügen' }).click();
  const dialog = page.getByRole('dialog', { name: 'Neue Terminvariante' });
  await dialog.getByLabel('Name').fill('Cupspiel');
  await dialog.getByLabel('Art des Termins').selectOption({ label: 'Spieltag' });
  await dialog.getByRole('radio', { name: 'Rot' }).check();
  await dialog.getByRole('button', { name: 'Speichern' }).click();
  await expect(variants(page).getByRole('status')).toHaveText('Variante «Cupspiel» ist hinzugefügt.');
  await expect(variants(page).getByRole('listitem')).toHaveCount(5);

  await page.goto('/termine/neu');
  await page.getByLabel('Art', { exact: true }).selectOption({ label: 'Spieltag' });
  await page.getByLabel('Titel', { exact: true }).fill('Cup-Achtelfinal');
  await page.getByLabel('Datum', { exact: true }).fill('2026-12-06');
  await page.getByLabel('Beginn', { exact: true }).fill('14:00');
  await page.getByLabel('Ende', { exact: true }).fill('16:00');
  await page.getByLabel('Gegner').fill('UHC Cupgegner');
  await page.getByLabel('Variante').selectOption({ label: 'Cupspiel' });
  await page.getByRole('button', { name: 'Termin speichern' }).click();

  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Cup-Achtelfinal');
  await expect(page.getByTestId('event-variant')).toHaveText('Cupspiel');
  await expect(page.getByTestId('event-variant')).toHaveAttribute('data-tag', 'RED');

  // Deleted, the event falls back to the standard variant
  await page.goto(`/verwaltung/teams/${TEAM_1}`);
  await variants(page).getByRole('button', { name: 'Variante «Cupspiel» löschen' }).click();
  await expect(variants(page).getByRole('status')).toHaveText('Variante «Cupspiel» ist gelöscht.');
  await page.goto('/termine');
  await expect(page.getByRole('listitem', { name: /Cup-Achtelfinal/ }).getByTestId('event-variant')).toHaveText(
    'Spieltag',
  );
});

test('RR-07: players see no variants to manage', async ({ page }) => {
  await login(page, 'anna');
  await page.goto(`/verwaltung/teams/${TEAM_1}`);
  await expect(page.getByRole('alert')).toHaveText('Keine Berechtigung');
  await expect(variants(page)).toHaveCount(0);
});

// ------------------------------------------------------------------ Automatic departure (TML-09, VAR-08)

test('TML-09: the meeting time is set to the suggested departure by itself', async ({ page }) => {
  await login(page, 'petra');
  const matchDay = await createMatchDay(page, '2026-12-06');

  await page.goto(`/termine/${matchDay}`);
  // 10:00 - 70 - 26 = 08:24, rounded down to 08:20
  await expect(page.getByTestId('meeting-point')).toHaveText('08:20 · Parkplatz Sporthalle Nord');
  const departure = page.getByTestId('timeline-items').getByRole('listitem').first();
  await expect(departure).toContainText('08:20');
  await expect(departure.getByTestId('departure-auto')).toHaveText('automatisch gesetzt');
  await checkAccessibility(page);
});

// ------------------------------------------------------------------ Table ranks (AUF-34)

test('AUF-34: the match day and the call-up show the ranks of the league table', async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/termine/${T04}`);
  await expect(page.getByTestId('standing')).toContainText('2. Rang von 8');
  await expect(page.getByTestId('standing')).toContainText('Herren Kleinfeld 3. Liga Gruppe 12');
  await expect(page.getByTestId('opponent-rank')).toHaveText(['Gegner: 1. Rang', 'Gegner: 3. Rang']);
  await page.getByTestId('standing').getByRole('link', { name: 'Tabelle ansehen' }).click();
  await expect(page).toHaveURL(`/teams/${TEAM_1}/liga`);

  await page.goto(`/termine/${T04}/aufgebot`);
  const ranks = page.getByTestId('call-up-ranks');
  await expect(ranks).toContainText('2. Rang von 8');
  await expect(ranks).toContainText('UHC Musterdorf');
  await expect(ranks).toContainText('Gegner: 1. Rang');
  await checkAccessibility(page);
});

// ------------------------------------------------------------------ Proposed driver list (DRV-19)

test('DRV-19 DRV-32: «Alle verteilen» gives everyone a seat, for coaches and players alike', async ({ page }) => {
  await login(page, 'anna');
  const board = await openSeats(page, T04);
  await expect(board.getByTestId('seat-waiting')).toContainText('Jana Wyss');
  // Petra's car is full; Jana gets a seat in Sara's car, which is already entered
  await board.getByTestId('seat-fill').click();
  await expect(driverList(page).getByRole('status')).toHaveText('Alle haben einen Platz.');
  await expect(board.getByTestId('seat-waiting')).toHaveCount(0);
  await expect(board.getByRole('listitem', { name: 'Auto von Sara Brun' })).toContainText('Jana Wyss');
  await checkAccessibility(page);
});

// ------------------------------------------------------------------ Driving settings (DRV-18)

test('DRV-18: a person says whether she can drive, and coaches see and change it in the season plan', async ({
  page,
}) => {
  await login(page, 'anna');
  await page.goto('/profil/fahren');
  const driving = page.getByTestId('my-driving');
  await expect(driving.getByRole('checkbox', { name: 'Kann fahren' })).toBeChecked();
  await expect(driving.getByLabel('Plätze im Auto (mit dir)')).toHaveValue('5');
  await driving.getByLabel('Plätze im Auto (mit dir)').fill('1');
  await driving.getByRole('button', { name: 'Speichern' }).click();
  await expect(driving.getByText('Bitte eine Zahl von 2 bis 9 eingeben.')).toBeVisible();
  await driving.getByLabel('Plätze im Auto (mit dir)').fill('7');
  await driving.getByRole('button', { name: 'Speichern' }).click();
  await expect(driving.getByRole('status')).toHaveText('Deine Angaben zum Fahren sind gespeichert.');
  await checkAccessibility(page);

  // Seven places are the driver and six others
  const board = await openSeats(page, T04);
  const form = board.getByRole('form', { name: 'Fahrerin oder Fahrer hinzufügen' });
  await form.getByLabel('Wer fährt').selectOption({ label: 'Anna Muster (bei Petra Keller)' });
  await expect(form.getByRole('group', { name: 'Freie Plätze' })).toContainText('6');

  // In the driver list the grid shows the places, and coaches change them under «Anpassen»
  await login(page, 'petra');
  await page.goto(`/teams/${TEAM_1}/fahrerliste`);
  await expect(page.getByTestId(`plan-row-${ANNA}`).getByTestId('plan-car-seats')).toContainText('7');
  await page.getByTestId('fairness-edit').click();
  const row = page.getByTestId(`fair-${ANNA}`);
  await expect(row.getByRole('group', { name: 'Plätze im Auto von Anna Muster (mit Fahrerin oder Fahrer)' })).toContainText('7');
  await row.getByRole('checkbox', { name: 'Anna Muster kann fahren' }).uncheck();
  await expect(row).toHaveAttribute('data-can-drive', 'false');
  await expect(page.getByTestId(`plan-row-${ANNA}`).getByTestId('plan-car-seats')).toHaveText('fährt nicht');
  await checkAccessibility(page);
});

// ------------------------------------------------------------------ Travelling on one's own (DRV-20, ANM-13)

test('ANM-13: «dabei» and travelling on one`s own needs no seat', async ({ page }) => {
  await login(page, 'jana');
  await page.goto(`/termine/${T04}`);
  const summary = page.getByTestId('driver-seat-summary');
  await expect(summary).toHaveAttribute('data-people', '6');

  await page.getByTestId('travels-individually').check();
  await expect(page.getByTestId('my-response').getByRole('status')).toHaveText(
    'Du reist individuell an und brauchst keinen Platz.',
  );
  await expect(page.getByTestId('individual')).toContainText('Jana Wyss');
  // Who travels on her own does not count for the places
  await expect(summary).toHaveAttribute('data-people', '5');
  // Everyone who sees the responses sees it
  await expect(page.getByTestId('overview').getByTestId('overview-individual')).toHaveText(
    'reist individuell an',
  );
  await checkAccessibility(page);

  // Taken back in the driver list
  await driverList(page).getByRole('button', { name: 'Entfernen: Ich reise individuell an' }).click();
  await expect(driverList(page).getByRole('status')).toHaveText('Du reist nicht mehr individuell an.');
  await expect(summary).toHaveAttribute('data-people', '6');
  await expect(page.getByTestId('travels-individually')).not.toBeChecked();
});

test('DRV-20: a coach marks someone without a seat as travelling on her own', async ({ page }) => {
  await login(page, 'petra');
  const board = await openSeats(page, T04);
  await board.getByTestId('seat-waiting').getByRole('button', { name: 'Jana Wyss' }).click();
  await board.getByTestId('seat-individual').click();
  await expect(driverList(page).getByRole('status')).toHaveText(
    'Jana Wyss reist individuell an und braucht keinen Platz.',
  );
  await expect(page.getByTestId('individual')).toContainText('Jana Wyss');
  await expect(board.getByTestId('seat-waiting')).toHaveCount(0);

  // A practice offers no such choice
  await page.goto(`/termine/${T01}`);
  await expect(page.getByTestId('my-response')).toBeVisible();
  await expect(page.getByTestId('travels-individually')).toHaveCount(0);
});

// ------------------------------------------------------------------ Special line-ups (LIN-13 to LIN-16)

test.describe('Special line-ups in the dialog', () => {
  // The same player twice is refused on purpose (422)
  test.use({ expectedConsoleErrors: [/status of 422/] });

  test('LIN-13: a coach sets up a power play line-up, and the match day shows who is missing', async ({ page }) => {
    await login(page, 'petra');
    await page.goto(`/teams/${TEAM_1}/linien`);
    const section = page.getByTestId('special-lineups');
    await expect(section.getByText('Noch keine Spezialaufstellung.')).toBeVisible();

    await section.getByRole('button', { name: 'Aufstellung hinzufügen' }).click();
    const dialog = page.getByRole('dialog', { name: 'Neue Aufstellung' });
    await dialog.getByRole('button', { name: 'Name «Überzahl» übernehmen' }).click();
    await expect(dialog.getByLabel('Name', { exact: true })).toHaveValue('Überzahl');
    await dialog.getByLabel('Anzahl Plätze').fill('2');
    await dialog.getByLabel('Anzahl Plätze').blur();
    await expect(dialog.getByRole('listitem')).toHaveCount(2);
    await dialog.getByLabel('Position Platz 1').fill('Center');
    await dialog.getByLabel('Spielerin oder Spieler Platz 1').selectOption({ label: 'Anna Muster' });
    // The same player twice is refused at the place
    await dialog.getByLabel('Spielerin oder Spieler Platz 2').selectOption({ label: 'Anna Muster' });
    await dialog.getByRole('button', { name: 'Speichern' }).click();
    await expect(dialog.getByText('steht höchstens einmal in einer Aufstellung')).toBeVisible();
    await dialog.getByLabel('Spielerin oder Spieler Platz 2').selectOption({ label: 'Kim Baumann' });
    await checkAccessibility(page);
    await dialog.getByRole('button', { name: 'Speichern' }).click();

    await expect(section.getByRole('status')).toHaveText('Aufstellung «Überzahl» ist gespeichert.');
    const lineup = section.getByRole('listitem', { name: 'Überzahl' });
    await expect(lineup).toContainText('Center');
    await expect(lineup).toContainText('Anna Muster');
    await expect(lineup).toContainText('Kim Baumann');
    await checkAccessibility(page);

    // Match day 04: Anna comes, Kim declined
    await page.goto(`/termine/${T04}`);
    const onMatchDay = page.getByTestId('event-lineups');
    await expect(onMatchDay.getByRole('heading', { level: 2 })).toHaveText('Aufstellungen');
    await expect(onMatchDay.getByRole('listitem').filter({ hasText: 'Anna Muster' })).toContainText('dabei');
    await expect(onMatchDay.getByRole('listitem').filter({ hasText: 'Kim Baumann' })).toContainText('fehlt');
    await checkAccessibility(page);

    // More places, another name; then gone
    await page.goto(`/teams/${TEAM_1}/linien`);
    await section.getByRole('button', { name: 'Aufstellung «Überzahl» bearbeiten' }).click();
    const edit = page.getByRole('dialog', { name: 'Aufstellung «Überzahl»' });
    await edit.getByLabel('Name', { exact: true }).fill('Überzahl 1');
    await edit.getByLabel('Anzahl Plätze').fill('3');
    await edit.getByLabel('Anzahl Plätze').blur();
    await edit.getByLabel('Spielerin oder Spieler Platz 3').selectOption({ label: 'Lea Graf' });
    await edit.getByRole('button', { name: 'Speichern' }).click();
    await expect(section.getByRole('listitem', { name: 'Überzahl 1' })).toContainText('Lea Graf');
    await section.getByRole('button', { name: 'Aufstellung «Überzahl 1» löschen' }).click();
    await expect(section.getByRole('status')).toHaveText('Aufstellung «Überzahl 1» ist gelöscht.');
  });
});

test('LIN-15: players see the special line-ups but cannot change them', async ({ page }) => {
  await login(page, 'petra');
  const created = await page.request.post(`/api/teams/${TEAM_1}/special-lineups`, {
    data: { name: 'Unterzahl', places: [{ label: null, personId: ANNA }, { label: null, personId: null }] },
  });
  expect(created.status()).toBe(201);

  await login(page, 'anna');
  await page.goto(`/teams/${TEAM_1}/linien`);
  const section = page.getByTestId('special-lineups');
  await expect(section.getByRole('listitem', { name: 'Unterzahl' })).toContainText('Anna Muster');
  await expect(section.getByRole('listitem', { name: 'Unterzahl' })).toContainText('frei');
  await expect(section.getByRole('button')).toHaveCount(0);
});

// ------------------------------------------------------------------ Expected or invited (TER-15 to TER-17)

test('TER-15: a practice expects one team and invites another, which is marked and can be hidden', async ({
  page,
}) => {
  await login(page, 'petra');
  await page.goto('/termine/neu');
  await page.getByLabel('Titel', { exact: true }).fill('Donnerstagstraining');
  await page.getByLabel('Datum', { exact: true }).fill('2026-11-19');
  await page.getByLabel('Beginn', { exact: true }).fill('19:00');
  await page.getByLabel('Ende', { exact: true }).fill('20:30');
  const teams = page.getByRole('group', { name: 'Teams' });
  await teams.getByRole('checkbox', { name: 'Team 1' }).check();
  await teams.getByRole('checkbox', { name: 'Team 2' }).check();
  const participation = page.getByTestId('participation');
  await expect(participation).toBeVisible();
  // Not every team can be voluntary
  await participation.getByLabel('Teilnahme Team 1').selectOption({ label: 'freiwillig' });
  await participation.getByLabel('Teilnahme Team 2').selectOption({ label: 'freiwillig' });
  await page.getByRole('button', { name: 'Termin speichern' }).click();
  await expect(participation.getByRole('alert')).toHaveText('Mindestens ein Team muss erwartet sein.');
  await participation.getByLabel('Teilnahme Team 1').selectOption({ label: 'erwartet' });
  await checkAccessibility(page);
  await page.getByRole('button', { name: 'Termin speichern' }).click();

  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Donnerstagstraining');
  await expect(page.getByText('Team 1 + Team 2 (freiwillig)').first()).toBeVisible();
  await expect(page.getByTestId('overview-team-optional')).toHaveText('freiwillig');
  // Petra coaches Team 1: she is expected
  await expect(page.getByTestId('event-optional')).toHaveCount(0);

  // Eva plays in Team 2 only: voluntary for her
  await login(page, 'eva');
  await page.goto('/termine');
  const entry = page.getByRole('listitem', { name: /Donnerstagstraining/ });
  await expect(entry.getByTestId('event-optional')).toHaveText('Freiwillig');
  await expect(entry).toContainText('Team 1 + Team 2 (freiwillig)');
  await checkAccessibility(page);

  await page.getByRole('checkbox', { name: 'Freiwillige Termine anzeigen' }).uncheck();
  await expect(page).toHaveURL(/freiwillig=aus/);
  await expect(entry).toHaveCount(0);
  await expect(page.getByTestId('optional-hidden')).toHaveText('1 freiwilliger Termin ist ausgeblendet.');
  await expect(card(page, T01)).toBeVisible();
  // The choice is in the address
  await page.reload();
  await expect(card(page, T01)).toBeVisible();
  await expect(entry).toHaveCount(0);
  await page.getByRole('checkbox', { name: 'Freiwillige Termine anzeigen' }).check();
  await expect(page).not.toHaveURL(/freiwillig/);
  await entry.getByRole('link').click();

  await expect(page.getByTestId('event-optional')).toContainText('Für dich freiwillig');
  await page.getByTestId('response-attending').click();
  await expect(page.getByTestId('my-response')).toHaveAttribute('data-response', 'ATTENDING');
  await checkAccessibility(page);

  // Anna plays in Team 1: no mark for her
  await login(page, 'anna');
  await page.goto('/termine');
  await expect(page.getByRole('listitem', { name: /Donnerstagstraining/ }).getByTestId('event-optional')).toHaveCount(0);
  await expect(page.getByRole('checkbox', { name: 'Freiwillige Termine anzeigen' })).toHaveCount(0);
});

test('TER-16: players of an invited team are not counted as «offen»', async ({ page }) => {
  await login(page, 'petra');
  const created = await page.request.post('/api/events', {
    data: {
      type: 'PRACTICE',
      title: 'Donnerstagstraining',
      start: '2026-11-19T19:00:00+01:00',
      end: '2026-11-19T20:30:00+01:00',
      teamIds: [TEAM_1, TEAM_2],
      optionalTeamIds: [TEAM_2],
    },
  });
  expect(created.status()).toBe(201);
  const id = (await created.json()).id;

  await page.goto(`/termine/${id}`);
  // Seven players of Team 1 are pending; the players of Team 2 are not counted until they respond
  await expect(page.getByTestId('overview-total')).toContainText('7 offen');
  await expect(page.getByTestId(`overview-team-${TEAM_2}-counter`)).toContainText('0 offen');
});
