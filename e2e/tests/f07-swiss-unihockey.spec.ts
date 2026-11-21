import type { APIRequestContext, Page } from '@playwright/test';
import { checkAccessibility, expect, login, reset, test } from './helpers';

/**
 * F-07 · swiss unihockey connection (SUH). Acceptance criteria from issue #8 and docs/pipeline/backlog.md,
 * scenario S12 from docs/test-data.md. In profile dev/test the app talks to the simulator; after the reset
 * it delivers match 01 and 02 of event 04 for team ID 900001 (Team 1).
 */

const TEAM_1 = '10000000-0000-4000-8000-000000000001';
const TEAM_2 = '10000000-0000-4000-8000-000000000002';
const T04 = '40000000-0000-4000-8000-000000000004';
const MATCH_01 = '50000000-0000-4000-8000-000000000001';
const MATCH_02 = '50000000-0000-4000-8000-000000000002';

interface SuMatch {
  suMatchId: number;
  startTime: string;
  homeTeam: string;
  awayTeam: string;
  hall: string;
  location?: string;
  result?: string | null;
}

const MATCH_01_SU: SuMatch = {
  suMatchId: 5000001,
  startTime: '2026-11-22T09:30:00+01:00',
  homeTeam: 'Team 1',
  awayTeam: 'UHC Musterdorf',
  hall: 'Sporthalle Seefeld',
  location: 'Musterdorf',
};

function match02(time: string): SuMatch {
  return {
    suMatchId: 5000002,
    startTime: `2026-11-22T${time}:00+01:00`,
    homeTeam: 'Unihockey Beispielstadt',
    awayTeam: 'Team 1',
    hall: 'Sporthalle Seefeld',
    location: 'Musterdorf',
  };
}

/** Test helper: set the data the simulator delivers for a team ID. */
async function simulator(
  request: APIRequestContext,
  suTeamId: number,
  matches: SuMatch[],
  unavailable = false,
): Promise<void> {
  const response = await request.put('/api/test/swiss-unihockey/matches', {
    data: { suTeamId, matches, unavailable },
  });
  expect(response.status()).toBe(204);
}

function section(page: Page) {
  return page.getByRole('region', { name: 'swiss unihockey' });
}

/** Trigger the sync of Team 1 via the UI as `alex`. */
async function syncAsAlex(page: Page): Promise<void> {
  await login(page, 'alex');
  await page.goto(`/verwaltung/teams/${TEAM_1}`);
  await expect(section(page).getByTestId('su-link-900001')).toContainText('Herren KF 3. Liga (Team-ID 900001)');
  await section(page).getByRole('button', { name: 'Jetzt abgleichen' }).click();
}

test.beforeEach(async ({ request }) => {
  await reset(request);
});

test('F-07 K1 SUH-02: As alex link a team with swiss unihockey and trigger the sync', async ({ page }) => {
  await simulator(page.request, 900002, [
    {
      suMatchId: 6000001,
      startTime: '2026-12-05T13:00:00+01:00',
      homeTeam: 'UHC Fünf',
      awayTeam: 'Team 2',
      hall: 'Turnhalle Weid',
      location: 'Beispielstadt',
    },
  ]);
  await login(page, 'alex');
  await page.goto('/verwaltung/teams');
  await expect(page.getByTestId(`su-linked-${TEAM_1}`)).toHaveText('swiss unihockey');
  await expect(page.getByTestId(`su-linked-${TEAM_2}`)).toHaveCount(0);

  await page.goto(`/verwaltung/teams/${TEAM_2}`);
  const su = section(page);
  await expect(su.getByTestId('su-status')).toHaveText('Nicht verknüpft.');
  await expect(su.getByRole('button', { name: 'Jetzt abgleichen' })).toHaveCount(0);
  await checkAccessibility(page);

  // Linking happens in a dialog (E-84); the team ID can still be typed by hand. Digits only
  await su.getByRole('button', { name: 'Team verknüpfen' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Team-ID von Hand eingeben').check();
  await dialog.getByLabel('Team-ID bei swiss unihockey').fill('abc');
  await dialog.getByRole('button', { name: 'Verknüpfen', exact: true }).click();
  await expect(dialog.getByText('Bitte ein Team wählen oder eine Team-ID eingeben (nur Ziffern).')).toBeVisible();
  await expect(dialog.getByLabel('Team-ID bei swiss unihockey')).toHaveAttribute('aria-invalid', 'true');

  await dialog.getByLabel('Team-ID bei swiss unihockey').fill('900002');
  await dialog.getByRole('button', { name: 'Verknüpfen', exact: true }).click();
  // Linking loads the matches right away (SUH-02)
  await expect(page.getByRole('status')).toHaveText(
    'Verknüpft mit Damen KF 2. Liga (Team-ID 900002). Die Spiele sind geladen.',
  );
  await expect(su.getByTestId('su-link-900002')).toBeVisible();

  await su.getByRole('button', { name: 'Jetzt abgleichen' }).click();
  await expect(page.getByRole('status')).toHaveText(
    'Abgleich fertig: 0 neu, 0 geändert, 0 nicht mehr bei swiss unihockey, 1 unverändert.',
  );
  await checkAccessibility(page);

  // The new match day appears for Team 2
  await login(page, 'eva');
  await page.goto('/termine');
  await expect(page.getByRole('listitem', { name: 'Spieltag Sa 05.12. 13:00' })).toBeVisible();

  await login(page, 'alex');
  await page.goto('/verwaltung/teams');
  await expect(page.getByTestId(`su-linked-${TEAM_2}`)).toBeVisible();
});

test('F-07 K1 SUH-02: Unlink', async ({ page }) => {
  await login(page, 'alex');
  await page.goto(`/verwaltung/teams/${TEAM_1}`);
  await section(page).getByRole('button', { name: /^Verknüpfung lösen/ }).click();
  await expect(page.getByRole('status')).toHaveText('Die Verknüpfung ist gelöst. Bisherige Spieltage bleiben.');
  await expect(section(page).getByTestId('su-status')).toHaveText('Nicht verknüpft.');
  await expect(section(page).getByRole('button', { name: 'Jetzt abgleichen' })).toHaveCount(0);
});

test('F-07 K1 RR-07: Coaches and admins link and sync, players do not', async ({ page }) => {
  // Since E-84 coaches (of any team) link and sync too
  await login(page, 'petra');
  await page.goto(`/verwaltung/teams/${TEAM_1}`);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Team 1');
  await expect(section(page).getByRole('button', { name: 'Team verknüpfen' })).toBeVisible();
  expect((await page.request.post(`/api/teams/${TEAM_1}/swiss-unihockey/sync`)).status()).toBe(200);

  await login(page, 'anna');
  expect(
    (await page.request.post(`/api/teams/${TEAM_1}/swiss-unihockey/links`, { data: { suTeamId: 900002 } })).status(),
  ).toBe(403);
  expect((await page.request.post(`/api/teams/${TEAM_1}/swiss-unihockey/sync`)).status()).toBe(403);
});

test('F-07 K2: In profile dev/test the simulator delivers the matches of event 04', async ({ page }) => {
  await syncAsAlex(page);
  await expect(page.getByRole('status')).toHaveText(
    'Abgleich fertig: 0 neu, 0 geändert, 0 nicht mehr bei swiss unihockey, 2 unverändert.',
  );
});

test('F-07 K3 SUH-04 SUH-07: Matches on the same day in the same hall become one match day', async ({ page }) => {
  await simulator(page.request, 900001, [
    MATCH_01_SU,
    match02('11:45'),
    {
      suMatchId: 5000020,
      startTime: '2026-12-06T10:00:00+01:00',
      homeTeam: 'Team 1',
      awayTeam: 'UHC Drei',
      hall: 'Sporthalle Nord',
      location: 'Beromünster',
    },
    {
      suMatchId: 5000021,
      startTime: '2026-12-06T12:30:00+01:00',
      homeTeam: 'UHC Vier',
      awayTeam: 'Team 1',
      hall: 'Sporthalle Nord',
      location: 'Beromünster',
    },
  ]);
  await syncAsAlex(page);
  await expect(page.getByRole('status')).toHaveText(
    'Abgleich fertig: 2 neu, 0 geändert, 0 nicht mehr bei swiss unihockey, 2 unverändert.',
  );

  await login(page, 'anna');
  await page.goto('/termine');
  const entry = page.getByRole('listitem', {
    name: 'Spieltag So 06.12. 10:00',
  });
  await expect(entry).toHaveCount(1);
  await entry.getByRole('link').click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Spieltag');
  const matches = page.getByTestId('matches').getByRole('listitem');
  await expect(matches).toHaveCount(2);
  await expect(matches.nth(0)).toHaveAccessibleName('10:00 Team 1 – UHC Drei');
  await expect(matches.nth(1)).toHaveAccessibleName('12:30 UHC Vier – Team 1');
  await expect(page.getByTestId('venue-map')).toHaveAttribute(
    'href',
    'https://www.google.com/maps/search/?api=1&query=Sporthalle+Nord+Berom%C3%BCnster',
  );
  await checkAccessibility(page);
});

test('F-07 K4 S12 SUH-05 SUH-06 BEN-06: Rescheduling is taken over, own data stays, team is notified', async ({
  page,
}) => {
  await simulator(page.request, 900001, [MATCH_01_SU, match02('12:15')]);
  await syncAsAlex(page);
  await expect(page.getByRole('status')).toHaveText(
    'Abgleich fertig: 0 neu, 1 geändert, 0 nicht mehr bei swiss unihockey, 1 unverändert.',
  );

  await login(page, 'petra');
  await page.goto(`/termine/${T04}`);
  await expect(page.getByTestId(`match-${MATCH_02}`)).toHaveAccessibleName('12:15 Unihockey Beispielstadt – Team 1');
  await expect(page.getByTestId(`match-${MATCH_01}`)).toHaveAccessibleName('09:30 Team 1 – UHC Musterdorf');
  await expect(page.getByTestId('meeting-point')).toHaveText('08:00 · Parkplatz Sporthalle Nord');
  await expect(page.getByText('Weisse Trikots mitnehmen')).toBeVisible();

  // Call-up (draft) unchanged: Anna, Lea, Mia, Sara, Jana
  const callUp = await page.request.get(`/api/events/${T04}/call-up`);
  const data = (await callUp.json()) as {
    status: string;
    groups: {
      entries: { person: { firstName: string }; calledUp: boolean }[];
    }[];
  };
  expect(data.status).toBe('DRAFT');
  expect(
    data.groups
      .flatMap((g) => g.entries)
      .filter((e) => e.calledUp)
      .map((e) => e.person.firstName)
      .sort(),
  ).toEqual(['Anna', 'Jana', 'Lea', 'Mia', 'Sara']);

  // Team 1 is notified, Team 2 is not
  const text = 'Spieltag So 22.11. wurde geändert: Spiel gegen Unihockey Beispielstadt neu um 12:15.';
  await login(page, 'anna');
  await page.goto('/benachrichtigungen');
  await expect(page.getByRole('list', { name: 'Benachrichtigungen' }).getByText(text)).toBeVisible();
  await login(page, 'eva');
  await page.goto('/benachrichtigungen');
  await expect(page.getByRole('list', { name: 'Benachrichtigungen' })).toBeVisible();
  await expect(page.getByText(text)).toHaveCount(0);
});

test('F-07 K5: A match missing at swiss unihockey is marked, not deleted', async ({ page }) => {
  await simulator(page.request, 900001, [MATCH_01_SU]);
  await syncAsAlex(page);
  await expect(page.getByRole('status')).toHaveText(
    'Abgleich fertig: 0 neu, 0 geändert, 1 nicht mehr bei swiss unihockey, 1 unverändert.',
  );

  await login(page, 'petra');
  await page.goto(`/termine/${T04}`);
  await expect(page.getByTestId(`match-${MATCH_02}`)).toContainText('swiss unihockey liefert dieses Spiel nicht mehr.');
  await expect(page.getByTestId(`match-${MATCH_01}`)).not.toContainText('nicht mehr');
});

test.describe('F-07 K6 SUH-09', () => {
  test.use({ expectedConsoleErrors: [/status of 502/] });

  test('Simulator unreachable: the sync reports the error, nothing is changed, the app works', async ({
    page,
  }) => {
    await simulator(page.request, 900001, [], true);
    await syncAsAlex(page);
    await expect(page.getByRole('alert')).toHaveText(
      'swiss unihockey ist im Moment nicht erreichbar. Es wurde nichts geändert. Der nächste Abgleich versucht es wieder.',
    );
    // The link stays and says that its last sync failed (SUH-13)
    await expect(section(page).getByTestId('su-link-900001')).toHaveAttribute('data-sync', 'failed');

    await login(page, 'petra');
    await page.goto(`/termine/${T04}`);
    await expect(page.getByTestId('matches').getByRole('listitem')).toHaveCount(2);
    await expect(page.getByTestId(`match-${MATCH_02}`)).toHaveAccessibleName('11:45 Unihockey Beispielstadt – Team 1');
    await expect(page.getByTestId(`match-${MATCH_02}`)).not.toContainText('nicht mehr');
    await page.goto('/termine');
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  });
});
