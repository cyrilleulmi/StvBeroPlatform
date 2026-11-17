import type { APIRequestContext, Page } from '@playwright/test';
import { anmelden, barrierefrei, expect, reset, test } from './helpers';

/**
 * F-07 · Anbindung swiss unihockey (SUH). Akzeptanzkriterien aus Issue #8 bzw. docs/pipeline/backlog.md,
 * Szenario S12 aus docs/testdaten.md. Im Profil dev/test spricht die App mit dem Simulator; nach dem Reset
 * liefert er für Team-ID 900001 (Team 1) Spiel 01 und 02 von Termin 04.
 */

const TEAM_1 = '10000000-0000-4000-8000-000000000001';
const TEAM_2 = '10000000-0000-4000-8000-000000000002';
const T04 = '40000000-0000-4000-8000-000000000004';
const SPIEL_01 = '50000000-0000-4000-8000-000000000001';
const SPIEL_02 = '50000000-0000-4000-8000-000000000002';

interface SuSpiel {
  suMatchId: number;
  startTime: string;
  homeTeam: string;
  awayTeam: string;
  hall: string;
  location?: string;
  result?: string | null;
}

const SPIEL_01_SU: SuSpiel = {
  suMatchId: 5000001,
  startTime: '2026-11-22T09:30:00+01:00',
  homeTeam: 'Team 1',
  awayTeam: 'UHC Musterdorf',
  hall: 'Sporthalle Seefeld',
  location: 'Musterdorf',
};

function spiel02(zeit: string): SuSpiel {
  return {
    suMatchId: 5000002,
    startTime: `2026-11-22T${zeit}:00+01:00`,
    homeTeam: 'Unihockey Beispielstadt',
    awayTeam: 'Team 1',
    hall: 'Sporthalle Seefeld',
    location: 'Musterdorf',
  };
}

/** Test-Hilfe: Daten vorgeben, die der Simulator für eine Team-ID liefert. */
async function simulator(
  request: APIRequestContext,
  suTeamId: number,
  spiele: SuSpiel[],
  nichtErreichbar = false,
): Promise<void> {
  const antwort = await request.put('/api/test/swiss-unihockey/matches', {
    data: { suTeamId, matches: spiele, unavailable: nichtErreichbar },
  });
  expect(antwort.status()).toBe(204);
}

function abschnitt(page: Page) {
  return page.getByRole('region', { name: 'swiss unihockey' });
}

/** Als `alex` den Abgleich von Team 1 über die Oberfläche auslösen. */
async function abgleichenAlsAlex(page: Page): Promise<void> {
  await anmelden(page, 'alex');
  await page.goto(`/verwaltung/teams/${TEAM_1}`);
  await expect(abschnitt(page).getByTestId('su-status')).toHaveText('Verknüpft mit Team-ID 900001.');
  await abschnitt(page).getByRole('button', { name: 'Jetzt abgleichen' }).click();
}

test.beforeEach(async ({ request }) => {
  await reset(request);
});

test('F-07 K1 SUH-02: Als alex Team mit swiss unihockey verknüpfen und den Abgleich auslösen', async ({ page }) => {
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
  await anmelden(page, 'alex');
  await page.goto('/verwaltung/teams');
  await expect(page.getByTestId(`su-verknuepft-${TEAM_1}`)).toHaveText('swiss unihockey');
  await expect(page.getByTestId(`su-verknuepft-${TEAM_2}`)).toHaveCount(0);

  await page.goto(`/verwaltung/teams/${TEAM_2}`);
  const su = abschnitt(page);
  await expect(su.getByTestId('su-status')).toHaveText('Nicht verknüpft.');
  await expect(su.getByRole('button', { name: 'Jetzt abgleichen' })).toHaveCount(0);
  await barrierefrei(page);

  // Pflichtfeld: nur Ziffern
  await su.getByLabel('Team-ID bei swiss unihockey').fill('abc');
  await su.getByRole('button', { name: 'Verknüpfen' }).click();
  await expect(su.getByText('Bitte eine Team-ID eingeben (nur Ziffern).')).toBeVisible();
  await expect(su.getByLabel('Team-ID bei swiss unihockey')).toHaveAttribute('aria-invalid', 'true');

  await su.getByLabel('Team-ID bei swiss unihockey').fill('900002');
  await su.getByRole('button', { name: 'Verknüpfen' }).click();
  await expect(page.getByRole('status')).toHaveText('Das Team ist mit Team-ID 900002 verknüpft.');
  await expect(su.getByTestId('su-status')).toHaveText('Verknüpft mit Team-ID 900002.');

  await su.getByRole('button', { name: 'Jetzt abgleichen' }).click();
  await expect(page.getByRole('status')).toHaveText(
    'Abgleich fertig: 1 neu, 0 geändert, 0 nicht mehr bei swiss unihockey, 0 unverändert.',
  );
  await barrierefrei(page);

  // Der neue Spieltag erscheint bei Team 2
  await anmelden(page, 'eva');
  await page.goto('/termine');
  await expect(page.getByRole('listitem', { name: 'Spieltag Sa 05.12. 13:00' })).toBeVisible();

  await anmelden(page, 'alex');
  await page.goto('/verwaltung/teams');
  await expect(page.getByTestId(`su-verknuepft-${TEAM_2}`)).toBeVisible();
});

test('F-07 K1 SUH-02: Verknüpfung lösen', async ({ page }) => {
  await anmelden(page, 'alex');
  await page.goto(`/verwaltung/teams/${TEAM_1}`);
  await abschnitt(page).getByRole('button', { name: 'Verknüpfung lösen' }).click();
  await expect(page.getByRole('status')).toHaveText('Die Verknüpfung ist gelöst. Bisherige Spieltage bleiben.');
  await expect(abschnitt(page).getByTestId('su-status')).toHaveText('Nicht verknüpft.');
  await expect(abschnitt(page).getByLabel('Team-ID bei swiss unihockey')).toHaveValue('');
});

test('F-07 K1 RR-07: Nur Admins verknüpfen und gleichen ab', async ({ page }) => {
  await anmelden(page, 'petra');
  await page.goto(`/verwaltung/teams/${TEAM_1}`);
  await expect(page.getByRole('alert')).toHaveText('Keine Berechtigung');
  await expect(abschnitt(page)).toHaveCount(0);
  const antwort = await page.request.post(`/api/teams/${TEAM_1}/swiss-unihockey/sync`);
  expect(antwort.status()).toBe(403);
});

test('F-07 K2: Im Profil dev/test liefert der Simulator die Spiele von Termin 04', async ({ page }) => {
  await abgleichenAlsAlex(page);
  await expect(page.getByRole('status')).toHaveText(
    'Abgleich fertig: 0 neu, 0 geändert, 0 nicht mehr bei swiss unihockey, 2 unverändert.',
  );
});

test('F-07 K3 SUH-04 SUH-07: Spiele am selben Tag in derselben Halle werden ein Spieltag', async ({ page }) => {
  await simulator(page.request, 900001, [
    SPIEL_01_SU,
    spiel02('11:45'),
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
  await abgleichenAlsAlex(page);
  await expect(page.getByRole('status')).toHaveText(
    'Abgleich fertig: 2 neu, 0 geändert, 0 nicht mehr bei swiss unihockey, 2 unverändert.',
  );

  await anmelden(page, 'anna');
  await page.goto('/termine');
  const eintrag = page.getByRole('listitem', {
    name: 'Spieltag So 06.12. 10:00',
  });
  await expect(eintrag).toHaveCount(1);
  await eintrag.getByRole('link').click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Spieltag');
  const spiele = page.getByTestId('spiele').getByRole('listitem');
  await expect(spiele).toHaveCount(2);
  await expect(spiele.nth(0)).toHaveAccessibleName('10:00 Team 1 – UHC Drei');
  await expect(spiele.nth(1)).toHaveAccessibleName('12:30 UHC Vier – Team 1');
  await expect(page.getByTestId('spielort-karte')).toHaveAttribute(
    'href',
    'https://www.google.com/maps/search/?api=1&query=Sporthalle+Nord+Berom%C3%BCnster',
  );
  await barrierefrei(page);
});

test('F-07 K4 S12 SUH-05 SUH-06 BEN-06: Verschiebung wird übernommen, eigene Daten bleiben, Team wird benachrichtigt', async ({
  page,
}) => {
  await simulator(page.request, 900001, [SPIEL_01_SU, spiel02('12:15')]);
  await abgleichenAlsAlex(page);
  await expect(page.getByRole('status')).toHaveText(
    'Abgleich fertig: 0 neu, 1 geändert, 0 nicht mehr bei swiss unihockey, 1 unverändert.',
  );

  await anmelden(page, 'petra');
  await page.goto(`/termine/${T04}`);
  await expect(page.getByTestId(`spiel-${SPIEL_02}`)).toHaveAccessibleName('12:15 Unihockey Beispielstadt – Team 1');
  await expect(page.getByTestId(`spiel-${SPIEL_01}`)).toHaveAccessibleName('09:30 Team 1 – UHC Musterdorf');
  await expect(page.getByTestId('treffpunkt')).toHaveText('08:00 · Parkplatz Sporthalle Nord');
  await expect(page.getByText('Weisse Trikots mitnehmen')).toBeVisible();

  // Aufgebot (Entwurf) unverändert: Anna, Lea, Mia, Sara, Jana
  const aufgebot = await page.request.get(`/api/events/${T04}/call-up`);
  const daten = (await aufgebot.json()) as {
    status: string;
    groups: {
      entries: { person: { firstName: string }; calledUp: boolean }[];
    }[];
  };
  expect(daten.status).toBe('DRAFT');
  expect(
    daten.groups
      .flatMap((g) => g.entries)
      .filter((e) => e.calledUp)
      .map((e) => e.person.firstName)
      .sort(),
  ).toEqual(['Anna', 'Jana', 'Lea', 'Mia', 'Sara']);

  // Team 1 wird benachrichtigt, Team 2 nicht
  const text = 'Spieltag So 22.11. wurde geändert: Spiel gegen Unihockey Beispielstadt neu um 12:15.';
  await anmelden(page, 'anna');
  await page.goto('/benachrichtigungen');
  await expect(page.getByRole('list', { name: 'Benachrichtigungen' }).getByText(text)).toBeVisible();
  await anmelden(page, 'eva');
  await page.goto('/benachrichtigungen');
  await expect(page.getByRole('list', { name: 'Benachrichtigungen' })).toBeVisible();
  await expect(page.getByText(text)).toHaveCount(0);
});

test('F-07 K5: Spiel fehlt bei swiss unihockey, wird markiert, nicht gelöscht', async ({ page }) => {
  await simulator(page.request, 900001, [SPIEL_01_SU]);
  await abgleichenAlsAlex(page);
  await expect(page.getByRole('status')).toHaveText(
    'Abgleich fertig: 0 neu, 0 geändert, 1 nicht mehr bei swiss unihockey, 1 unverändert.',
  );

  await anmelden(page, 'petra');
  await page.goto(`/termine/${T04}`);
  await expect(page.getByTestId(`spiel-${SPIEL_02}`)).toContainText('swiss unihockey liefert dieses Spiel nicht mehr.');
  await expect(page.getByTestId(`spiel-${SPIEL_01}`)).not.toContainText('nicht mehr');
});

test.describe('F-07 K6 SUH-09', () => {
  test.use({ erwarteteKonsolenfehler: [/status of 502/] });

  test('Simulator nicht erreichbar: Abgleich meldet den Fehler, nichts wird verändert, die App funktioniert', async ({
    page,
  }) => {
    await simulator(page.request, 900001, [], true);
    await abgleichenAlsAlex(page);
    await expect(page.getByRole('alert')).toHaveText(
      'swiss unihockey ist im Moment nicht erreichbar. Es wurde nichts geändert. Der nächste Abgleich versucht es wieder.',
    );
    await expect(abschnitt(page).getByTestId('su-status')).toHaveText('Verknüpft mit Team-ID 900001.');

    await anmelden(page, 'petra');
    await page.goto(`/termine/${T04}`);
    await expect(page.getByTestId('spiele').getByRole('listitem')).toHaveCount(2);
    await expect(page.getByTestId(`spiel-${SPIEL_02}`)).toHaveAccessibleName('11:45 Unihockey Beispielstadt – Team 1');
    await expect(page.getByTestId(`spiel-${SPIEL_02}`)).not.toContainText('nicht mehr');
    await page.goto('/termine');
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  });
});
