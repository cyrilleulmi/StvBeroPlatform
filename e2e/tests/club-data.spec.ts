import type { APIRequestContext, Locator, Page } from '@playwright/test';
import { checkAccessibility, expect, login, reset, test } from './helpers';

/**
 * Batch «club data» (E-85): links to swiss unihockey through a dialog (SUH-02, SUH-11), warnings for changed
 * matches (SUH-12), hint when swiss unihockey cannot be reached (SUH-13), timeline of a match day (TML), driver
 * plan of the season (DRV-14, DRV-17), match day settings of a team and the test mode login grouped by team.
 * All against the test data set of docs/test-data.md with the simulators. Default "now": Mon 16.11.2026 06:00.
 */

const TEAM_1 = '10000000-0000-4000-8000-000000000001';
const TEAM_2 = '10000000-0000-4000-8000-000000000002';
const T04 = '40000000-0000-4000-8000-000000000004';
const KIM = '20000000-0000-4000-8000-000000000011';
const PETRA = '20000000-0000-4000-8000-000000000002';

const MATCH_01 = {
  suMatchId: 5000001,
  startTime: '2026-11-22T09:30:00+01:00',
  homeTeam: 'Team 1',
  awayTeam: 'UHC Musterdorf',
  hall: 'Sporthalle Seefeld',
  location: 'Musterdorf',
  mapsUrl: 'https://www.google.com/maps/search/?api=1&query=Sporthalle+Seefeld+Musterdorf',
};

function match02(time: string) {
  return {
    suMatchId: 5000002,
    startTime: `2026-11-22T${time}:00+01:00`,
    homeTeam: 'Unihockey Beispielstadt',
    awayTeam: 'Team 1',
    hall: 'Sporthalle Seefeld',
    location: 'Musterdorf',
  };
}

async function simulator(request: APIRequestContext, data: object): Promise<void> {
  expect((await request.put('/api/test/swiss-unihockey/matches', { data })).status()).toBe(204);
}

/** Runs the sync of Team 1 as the logged-in person. */
async function sync(page: Page): Promise<number> {
  return (await page.request.post(`/api/teams/${TEAM_1}/swiss-unihockey/sync`)).status();
}

/** A further match day of Team 1 with the meeting point of match day 04, created by the logged-in coach. */
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
  return (await response.json()).id;
}

function suSection(page: Page): Locator {
  return page.getByTestId('swiss-unihockey');
}

function timeline(page: Page): Locator {
  return page.getByTestId('timeline');
}

test.beforeEach(async ({ request }) => {
  await reset(request);
});

// ------------------------------------------------------------------ Test mode login

test('Dev login: people are grouped by team, coaches come first and are marked', async ({ page }) => {
  await page.goto('/dev-login');

  await expect(page.getByTestId('data-set-name')).toHaveText('Testdaten');
  await expect(page.getByRole('button', { name: 'Vereinsdaten laden' })).toBeVisible();

  const team1 = page.getByTestId(`dev-login-group-${TEAM_1}`);
  await expect(team1.getByRole('heading', { name: /Team 1/ })).toBeVisible();
  // Petra coaches Team 1: first entry, marked «Coach», with her other team
  const first = team1.getByRole('listitem').first();
  await expect(first).toHaveAttribute('data-role', 'coach');
  await expect(first).toContainText('Petra Keller');
  await expect(first).toContainText('Coach');
  await expect(first).toContainText('auch in Team 2');
  // In Team 2 she appears again, as a player; Marc is the coach there
  const team2 = page.getByTestId(`dev-login-group-${TEAM_2}`);
  await expect(team2.getByTestId(`dev-login-person-petra-${TEAM_2}`)).toHaveAttribute('data-role', 'player');
  await expect(team2.getByRole('listitem').first()).toContainText('Marc Huber');
  await expect(page.getByTestId('dev-login-group-admins')).toContainText('Alex Admin');
  // Vera is only in the archive: no team this season
  await expect(page.getByTestId('dev-login-group-none')).toContainText('Vera Studer');

  await page.getByLabel('Person oder Team suchen').fill('team 3');
  await expect(page.getByTestId('dev-login-person-tina')).toBeVisible();
  await expect(team1).toBeHidden();
  await page.getByLabel('Person oder Team suchen').fill('zzz');
  await expect(page.getByTestId('dev-login-no-match')).toBeVisible();
  await page.getByLabel('Person oder Team suchen').fill('');
  await checkAccessibility(page);

  await page.getByTestId('dev-login-person-petra').getByRole('button').click();
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Petra');
});

// ------------------------------------------------------------------ Linking (SUH-02, SUH-11)

test('SUH-02: a coach links a team through the dialog with the teams of the club', async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/verwaltung/teams/${TEAM_2}`);
  await expect(page.getByTestId('su-status')).toHaveText('Nicht verknüpft.');

  await suSection(page).getByRole('button', { name: 'Team verknüpfen' }).click();
  const dialog = page.getByRole('dialog', { name: 'Team 2 mit swiss unihockey verknüpfen' });
  await expect(dialog).toBeVisible();
  const select = dialog.getByLabel('Team bei swiss unihockey');
  // The team of Team 1 is taken and cannot be chosen
  await expect(select.getByRole('option', { name: 'Herren KF 3. Liga (schon verknüpft mit Team 1)' })).toBeDisabled();
  // Nothing chosen: the dialog says so and stays open
  await dialog.getByRole('button', { name: 'Verknüpfen', exact: true }).click();
  await expect(dialog.getByText('Bitte ein Team wählen oder eine Team-ID eingeben (nur Ziffern).')).toBeVisible();
  await checkAccessibility(page);
  await select.selectOption({ label: 'Damen KF 2. Liga' });
  await dialog.getByRole('button', { name: 'Verknüpfen', exact: true }).click();

  await expect(dialog).toBeHidden();
  await expect(suSection(page).getByRole('status')).toHaveText(
    'Verknüpft mit Damen KF 2. Liga (Team-ID 900002). Die Spiele sind geladen.',
  );
  const link = page.getByTestId('su-link-900002');
  await expect(link).toHaveAttribute('data-sync', 'ok');
  await expect(link.getByTestId('su-link-sync')).toContainText('Letzter Abgleich: Mo 16.11. 06:00');
  await expect(suSection(page).getByRole('button', { name: 'Jetzt abgleichen' })).toBeVisible();

  await link.getByRole('button', { name: 'Verknüpfung lösen: Damen KF 2. Liga (Team-ID 900002)' }).click();
  await expect(page.getByTestId('su-status')).toHaveText('Nicht verknüpft.');
});

test.describe('swiss unihockey not reachable', () => {
  test.use({ expectedConsoleErrors: [/502 \(Bad Gateway\)/] });

  test('SUH-09: without the list of teams the dialog takes the team ID by hand', async ({ page, request }) => {
    expect((await request.put('/api/test/swiss-unihockey/club', { data: { unavailable: true } })).status()).toBe(204);
    await login(page, 'alex');
    await page.goto(`/verwaltung/teams/${TEAM_2}`);

    await suSection(page).getByRole('button', { name: 'Team verknüpfen' }).click();
    const dialog = page.getByRole('dialog');
    await expect(page.getByTestId('su-club-error')).toContainText('swiss unihockey ist im Moment nicht erreichbar');
    await dialog.getByLabel('Team-ID bei swiss unihockey').fill('900002');
    await dialog.getByRole('button', { name: 'Verknüpfen', exact: true }).click();

    await expect(dialog).toBeHidden();
    await expect(page.getByTestId('su-link-900002')).toContainText('Team-ID 900002');
  });

  test('SUH-13: a failed sync shows on the link and as a hint on the start page', async ({ page, request }) => {
    await simulator(request, { suTeamId: 900001, unavailable: true, matches: [] });
    await login(page, 'petra');
    await page.goto(`/verwaltung/teams/${TEAM_1}`);

    await suSection(page).getByRole('button', { name: 'Jetzt abgleichen' }).click();
    await expect(suSection(page).getByRole('alert')).toContainText('swiss unihockey ist im Moment nicht erreichbar');
    const link = page.getByTestId('su-link-900001');
    await expect(link).toHaveAttribute('data-sync', 'failed');
    await expect(link).toContainText('Letzter Abgleich am Mo 16.11. 06:00 fehlgeschlagen.');

    // The app keeps working and tells coaches and admins
    await page.goto('/');
    const notice = page.getByTestId('su-status-notice');
    await expect(notice).toContainText('swiss unihockey war beim letzten Abgleich nicht erreichbar (Team 1,');
    await expect(notice.getByRole('link', { name: 'Zur Verknüpfung: Team 1' })).toBeVisible();
    await checkAccessibility(page);
    // Players are not bothered with it
    await login(page, 'anna');
    await page.goto('/');
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Anna');
    await expect(page.getByTestId('su-status-notice')).toHaveCount(0);
  });
});

// ------------------------------------------------------------------ Warnings (SUH-12)

test('SUH-12: a match changed by swiss unihockey shows before and after until a coach has seen it', async ({
  page,
  request,
}) => {
  await simulator(request, { suTeamId: 900001, matches: [MATCH_01, match02('12:15')] });
  await login(page, 'petra');
  expect(await sync(page)).toBe(200);

  await page.goto(`/termine/${T04}`);
  const match = page.getByTestId('matches').getByRole('listitem', { name: /12:15/ });
  const change = match.getByTestId('match-su-change');
  await expect(change).toContainText('Von swiss unihockey geändert:');
  await expect(change).toContainText('Zeit: So 22.11. 11:45 → So 22.11. 12:15');
  await expect(page.getByTestId('event-warnings')).toContainText('1 Spiel wurde von swiss unihockey geändert');
  await checkAccessibility(page);

  await match.getByRole('button', { name: /^Änderung gesehen:/ }).click();
  await expect(page.getByText(/ist als gesehen markiert\.$/)).toBeVisible();
  await expect(page.getByTestId('match-su-change')).toHaveCount(0);
  await expect(page.getByTestId('event-warnings')).not.toContainText('swiss unihockey geändert');
});

test('SUH-12: players see what changed but cannot confirm it', async ({ page, request }) => {
  await simulator(request, { suTeamId: 900001, matches: [MATCH_01, match02('12:15')] });
  await login(page, 'petra');
  expect(await sync(page)).toBe(200);

  await login(page, 'anna');
  await page.goto(`/termine/${T04}`);
  await expect(page.getByTestId('match-su-change')).toContainText('Zeit: So 22.11. 11:45 → So 22.11. 12:15');
  await expect(page.getByRole('button', { name: /^Änderung gesehen:/ })).toHaveCount(0);
});

// ------------------------------------------------------------------ Timeline (TML)

test('TML-01: the timeline of match day 04 runs from the departure to the return', async ({ page }) => {
  await login(page, 'anna');
  await page.goto(`/termine/${T04}`);

  await expect(page.getByTestId('timeline-settings')).toContainText(
    'Ein Spiel dauert 50 Minuten. Ankunft 70 Minuten vor dem ersten Spiel.',
  );
  await expect(page.getByTestId('timeline-settings')).toContainText('23.4 km, etwa 26 Min. Fahrt pro Weg');
  const items = page.getByTestId('timeline-items').getByRole('listitem');
  await expect(items).toHaveText([
    /08:00.*Abfahrt.*26 Min\. Fahrt/s,
    // Arrival and the time before the first match are one point
    /08:26.*Ankunft.*64 Min\. vor dem 1\. Spiel/s,
    /09:30.*Spiel 1: Team 1 – UHC Musterdorf.*09:30–10:20 · 50 Min\./s,
    /10:20.*Zeit zwischen den Spielen.*10:20–11:45 · 85 Min\./s,
    /11:45.*Spiel 2: Unihockey Beispielstadt – Team 1.*11:45–12:35 · 50 Min\./s,
    /12:35.*Auslaufen und Duschen.*12:35–12:55 · 20 Min\./s,
    /13:21.*Rückkehr \(ungefähr\).*26 Min\. Fahrt/s,
  ]);
  // 08:26 at the venue is later than 70 minutes before the first match
  await expect(page.getByTestId('timeline-hint')).toContainText(
    'weniger als 70 Minuten vor dem ersten Spiel in der Halle',
  );
  // Players cannot change the meeting time
  await expect(timeline(page).getByRole('button')).toHaveCount(0);
  await checkAccessibility(page);
});

test('TML-04: a coach takes over the suggested departure as meeting time', async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/termine/${T04}`);

  await expect(page.getByTestId('timeline-suggestion')).toHaveText('Vorschlag für die Abfahrt: 07:50');
  await timeline(page).getByRole('button', { name: 'Vorschlag als Treffpunkt-Zeit übernehmen' }).click();

  await expect(timeline(page).getByRole('status')).toHaveText('Die Treffpunkt-Zeit ist jetzt 07:50.');
  await expect(page.getByTestId('timeline-items').getByRole('listitem').first()).toContainText('07:50');
  await expect(page.getByTestId('timeline-hint')).toHaveCount(0);
  await expect(page.getByTestId('meeting-point')).toContainText('07:50 · Parkplatz Sporthalle Nord');
  await expect(timeline(page).getByRole('button')).toHaveCount(0);
});

test('TML-04: without a meeting time the departure is a suggestion', async ({ page }) => {
  await login(page, 'petra');
  // With the automatic departure switched off for «Spieltag», the meeting time stays open (VAR-08)
  const variants = await (await page.request.get(`/api/teams/${TEAM_1}/event-variants`)).json();
  const matchDayVariant = variants.find((v: { kind: string }) => v.kind === 'MATCH_DAY');
  const switchedOff = await page.request.put(`/api/teams/${TEAM_1}/event-variants/${matchDayVariant.id}`, {
    data: { name: matchDayVariant.name, color: matchDayVariant.color, autoDeparture: false },
  });
  expect(switchedOff.status()).toBe(200);
  const matchDay = await createMatchDay(page, '2026-12-06');
  const added = await page.request.post(`/api/events/${matchDay}/matches`, {
    data: { opponent: 'UHC Drei', startTime: '2026-12-06T10:00:00+01:00', home: false },
  });
  expect(added.status()).toBe(201);

  await page.goto(`/termine/${matchDay}`);
  await expect(page.getByTestId('meeting-point')).toHaveText('Zeit offen · Parkplatz Sporthalle Nord');
  const departure = page.getByTestId('timeline-items').getByRole('listitem').first();
  // 10:00 - 70 - 26 = 08:24, rounded down to 08:20
  await expect(departure).toContainText('08:20');
  await expect(departure).toContainText('Vorschlag');
  await expect(departure).toHaveAttribute('data-suggested', 'true');
  await expect(timeline(page)).toContainText('Die Abfahrt ist ein Vorschlag.');
});

test('TML-02: match duration, arrival lead and the usual meeting point are settings of the team', async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/verwaltung/teams/${TEAM_1}`);
  const form = page.getByTestId('match-day-settings');
  await expect(form.getByLabel('Dauer eines Spiels (Minuten)')).toHaveValue('50');
  await expect(form.getByLabel('Ankunft vor dem ersten Spiel (Minuten)')).toHaveValue('70');

  await form.getByLabel('Dauer eines Spiels (Minuten)').fill('5');
  await form.getByRole('button', { name: 'Angaben speichern' }).click();
  await expect(form.getByText('Bitte eine Zahl von 10 bis 240 eingeben.')).toBeVisible();
  await form.getByLabel('Dauer eines Spiels (Minuten)').fill('60');
  await form.getByLabel('Ankunft vor dem ersten Spiel (Minuten)').fill('45');
  await form.getByLabel('Üblicher Treffpunkt').fill('Schulhaus Dorf');
  await form.getByRole('button', { name: 'Angaben speichern' }).click();
  await expect(page.getByText('Die Angaben für die Spieltage sind gespeichert.')).toBeVisible();
  await checkAccessibility(page);

  await page.goto(`/termine/${T04}`);
  await expect(page.getByTestId('timeline-settings')).toContainText(
    'Ein Spiel dauert 60 Minuten. Ankunft 45 Minuten vor dem ersten Spiel.',
  );
  await expect(page.getByTestId('timeline-items').getByRole('listitem', { name: /Spiel 1/ })).toContainText(
    '09:30–10:30 · 60 Min.',
  );
});

// ------------------------------------------------------------------ Driver plan of the season (DRV-14, DRV-17)

test('DRV-14: one person enters herself for several match days of the season at once', async ({ page }) => {
  await login(page, 'petra');
  const december = await createMatchDay(page, '2026-12-06');
  const january = await createMatchDay(page, '2027-01-10');

  await login(page, 'kim');
  await page.goto(`/teams/${TEAM_1}`);
  await page.getByTestId('team-driver-plan-link').click();
  await expect(page.getByRole('heading', { level: 1, name: 'Fahrerliste' })).toBeVisible();
  await expect(page.getByTestId('driver-plan-season')).toHaveText('Team 1 · Saison 2026/27');

  // Match day 04: distance in the head, Petra's full car in her row
  await expect(page.getByTestId(`plan-day-km-${T04}`)).toHaveText('23.4 km');
  await expect(page.getByRole('img', { name: 'Petra Keller fährt am So 22.11.' })).toBeVisible();
  // Members change only their own row
  await expect(page.getByTestId(`cell-${PETRA}-${december}`)).toHaveCount(0);

  await page.getByTestId(`cell-${KIM}-${december}`).click();
  await expect(page.getByTestId(`cell-${KIM}-${december}`)).toHaveAttribute('aria-pressed', 'true');
  await page.getByTestId(`cell-${KIM}-${january}`).click();
  await expect(page.getByTestId(`cell-${KIM}-${january}`)).toHaveAttribute('aria-pressed', 'true');
  // The trips are shared visibly (DRV-17)
  await expect(page.getByTestId(`plan-row-${KIM}`).getByTestId('plan-total')).toHaveAttribute('data-trips', '2');
  await expect(page.getByTestId(`plan-row-${PETRA}`).getByTestId('plan-total')).toHaveAttribute('data-trips', '1');
  await checkAccessibility(page);

  // And she can take herself off again
  await page.getByTestId(`cell-${KIM}-${january}`).click();
  await expect(page.getByRole('status')).toContainText('Kim Baumann fährt am So 10.01.2027 nicht mehr.');
  await expect(page.getByTestId(`cell-${KIM}-${january}`)).toHaveAttribute('aria-pressed', 'false');
  await expect(page.getByTestId(`plan-row-${KIM}`).getByTestId('plan-total')).toHaveAttribute('data-trips', '1');
});

test('DRV-14: a coach plans the season for someone else', async ({ page }) => {
  await login(page, 'petra');
  const december = await createMatchDay(page, '2026-12-06');
  await page.goto(`/teams/${TEAM_1}/fahrerliste`);

  await page.getByTestId(`cell-${KIM}-${T04}`).click();
  await expect(page.getByTestId(`cell-${KIM}-${T04}`)).toHaveAttribute('aria-pressed', 'true');
  await page.getByTestId(`cell-${KIM}-${december}`).click();
  await expect(page.getByTestId(`cell-${KIM}-${december}`)).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByTestId(`plan-row-${KIM}`).getByTestId('plan-total')).toHaveAttribute('data-trips', '2');

  // The match day itself shows the same car, and links back to the plan
  await page.getByTestId(`plan-day-${december}`).click();
  await page.getByTestId(`plan-editor-${december}`).getByRole('link', { name: 'Zum Spieltag' }).click();
  await page.getByTestId('driver-assign').click();
  await expect(page.getByTestId(`seat-board-${december}`)).toContainText('Kim Baumann');
  await page.getByTestId('driver-plan-link').click();
  await expect(page.getByRole('heading', { level: 1, name: 'Fahrerliste' })).toBeVisible();
});

test.describe('No permission', () => {
  test.use({ expectedConsoleErrors: [/403 \(Forbidden\)/] });

  test('DRV-14: the season plan is for the team, coaches and admins', async ({ page }) => {
    // Eva plays only in Team 2
    await login(page, 'eva');
    await page.goto(`/teams/${TEAM_1}/fahrerliste`);
    await expect(page.getByRole('alert')).toBeVisible();
    await expect(page.getByTestId('driver-plan-match-days')).toHaveCount(0);
  });
});
