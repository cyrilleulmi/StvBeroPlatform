import type { Locator, Page } from '@playwright/test';
import { checkAccessibility, expect, login, reset, test } from './helpers';

/**
 * F-05 · Match days and call-up (AUF, RR-03). Acceptance criteria from issue #6 and docs/pipeline/backlog.md,
 * scenarios S9 and S11 from docs/test-data.md. Default "now": Mon 16.11.2026 06:00.
 */

const PETRA = '20000000-0000-4000-8000-000000000002';
const ANNA = '20000000-0000-4000-8000-000000000005';
const LEA = '20000000-0000-4000-8000-000000000006';
const MIA = '20000000-0000-4000-8000-000000000007';
const NINA = '20000000-0000-4000-8000-000000000009';
const KIM = '20000000-0000-4000-8000-000000000011';
const T01 = '40000000-0000-4000-8000-000000000001';
const T04 = '40000000-0000-4000-8000-000000000004';
const T05 = '40000000-0000-4000-8000-000000000005';
const MATCH_01 = '50000000-0000-4000-8000-000000000001';
const MATCH_02 = '50000000-0000-4000-8000-000000000002';
const MATCH_03 = '50000000-0000-4000-8000-000000000003';

/** Matches in display order, as the entry name («09:30 Team 1 – UHC Musterdorf»). */
async function matches(page: Page): Promise<(string | null)[]> {
  return page
    .getByTestId('matches')
    .getByRole('listitem')
    .evaluateAll((li) => li.map((e) => e.getAttribute('aria-label')));
}

function entry(page: Page, personId: string): Locator {
  return page.getByTestId(`call-up-person-${personId}`);
}

test.beforeEach(async ({ request }) => {
  await reset(request);
});

test('F-05 K1 K3 AUF-10 AUF-12 AUF-14: Event 04 shows matches by face-off time, meeting point and map', async ({
  page,
}) => {
  await login(page, 'anna');
  await page.goto(`/termine/${T04}`);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Spieltag');
  await expect(page.getByTestId(`match-${MATCH_01}`)).toBeVisible();
  await expect.poll(() => matches(page)).toEqual([
    '09:30 Team 1 – UHC Musterdorf',
    '11:45 Unihockey Beispielstadt – Team 1',
  ]);
  await expect(page.getByTestId(`match-${MATCH_01}`)).toContainText('Heim');
  await expect(page.getByTestId(`match-${MATCH_02}`)).toContainText('Auswärts');
  await expect(page.getByTestId('meeting-point')).toHaveText('08:00 · Parkplatz Sporthalle Nord');
  const map = page.getByTestId('venue-map');
  await expect(map).toHaveText('In Google Maps öffnen');
  await expect(map).toHaveAttribute(
    'href',
    'https://www.google.com/maps/search/?api=1&query=Sporthalle+Seefeld+Musterdorf',
  );
  await expect(map).toHaveAttribute('target', '_blank');
  await expect(page.getByText('Weisse Trikots mitnehmen')).toBeVisible();
  // AUF-23: a draft is not visible to players
  await expect(page.getByTestId('my-call-up')).toHaveText('noch kein Aufgebot');
  await expect(page.getByRole('link', { name: 'Aufgebot bearbeiten' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: /Resultat/ })).toHaveCount(0);
  await checkAccessibility(page);
});

test('F-05 K1 AUF-24: Event 05 is published – Eva called up, Lea not', async ({ page }) => {
  await login(page, 'eva');
  await page.goto(`/termine/${T05}`);
  await expect(page.getByTestId('my-call-up')).toHaveText('aufgeboten');
  await expect.poll(() => matches(page)).toEqual(['14:00 Team 2 – UH Testingen']);
  await expect(page.getByTestId(`match-${MATCH_03}`)).toBeVisible();

  await login(page, 'lea');
  await page.goto(`/termine/${T05}`);
  await expect(page.getByTestId('my-call-up')).toHaveText('nicht aufgeboten');
});

test('F-05 K2 AUF-10 to AUF-16: Petra creates a match day with two matches', { tag: '@desktop' }, async ({ page }) => {
  await login(page, 'petra');
  await page.goto('/termine/neu');
  await page.getByLabel('Art', { exact: true }).selectOption({ label: 'Spieltag' });
  await expect(page.getByLabel('Titel', { exact: true })).toHaveValue('Spieltag');
  await page.getByLabel('Datum', { exact: true }).fill('2026-12-06');
  await page.getByLabel('Beginn', { exact: true }).fill('10:00');
  await page.getByLabel('Ende', { exact: true }).fill('15:00');
  await expect(page.getByRole('group', { name: 'Teams' }).getByRole('checkbox', { name: 'Team 1' })).toBeChecked();
  await page.getByLabel('Spielort').fill('Halle Ost');
  await page.getByLabel('Google-Maps-Link (freiwillig)').fill('https://maps.google.com/?q=Halle+Ost');
  await page.getByLabel('Hinweise (freiwillig)').fill('Rote Trikots');
  const meetingPoint = page.getByRole('group', { name: 'Treffpunkt' });
  await meetingPoint.getByLabel('Zeit').fill('09:00');
  await meetingPoint.getByLabel('Ort').fill('Bahnhof');

  // Match 1 is already there; the second match starts earlier and is listed first afterwards.
  const match1 = page.getByRole('group', { name: 'Spiel 1' });
  await match1.getByLabel('Gegner').fill('UHC Zwei');
  await match1.getByLabel('Anspielzeit').fill('13:00');
  await match1.getByLabel('Heim oder auswärts').selectOption({ label: 'Auswärts' });
  await page.getByRole('button', { name: 'Spiel hinzufügen' }).click();
  const match2 = page.getByRole('group', { name: 'Spiel 2' });
  await match2.getByLabel('Gegner').fill('UHC Eins');
  await match2.getByLabel('Anspielzeit').fill('10:00');
  await checkAccessibility(page);
  await page.getByRole('button', { name: 'Termin speichern' }).click();

  await expect(page).toHaveURL(/\/termine\/[0-9a-f-]{36}$/);
  await expect(page.getByRole('status')).toHaveText('Der Termin ist erfasst.');
  await expect.poll(() => matches(page)).toEqual(['10:00 Team 1 – UHC Eins', '13:00 UHC Zwei – Team 1']);
  await expect(page.getByTestId('meeting-point')).toHaveText('09:00 · Bahnhof');
  await expect(page.getByTestId('venue-map')).toHaveAttribute('href', 'https://maps.google.com/?q=Halle+Ost');
  await expect(page.getByTestId('venue-map')).toHaveAttribute('target', '_blank');
  await expect(page.getByText('Rote Trikots')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Aufgebot bearbeiten' })).toBeVisible();
});

test('F-05 K2 AUF-14: Matches without opponent are reported at the field', async ({ page }) => {
  await login(page, 'petra');
  await page.goto('/termine/neu');
  await page.getByLabel('Art', { exact: true }).selectOption({ label: 'Spieltag' });
  await page.getByLabel('Datum', { exact: true }).fill('2026-12-06');
  await page.getByRole('button', { name: 'Termin speichern' }).click();
  const opponent = page.getByRole('group', { name: 'Spiel 1' }).getByLabel('Gegner');
  await expect(opponent).toHaveAttribute('aria-invalid', 'true');
  await expect(opponent).toBeFocused();
  await expect(page).toHaveURL(/\/termine\/neu$/);
});

test('F-05 K4 AUF-21 AUF-22: Call-up by lines, response status next to it, pending and maybe also selectable', { tag: '@desktop' }, async ({
  page,
}) => {
  await login(page, 'petra');
  await page.goto(`/termine/${T04}`);
  await page.getByRole('link', { name: 'Aufgebot bearbeiten' }).click();
  await expect(page).toHaveURL(`/termine/${T04}/aufgebot`);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Aufgebot');
  await expect(page.getByTestId('call-up-status')).toHaveText('Entwurf');
  await expect(page.getByTestId('call-up-count')).toHaveText('5 aufgeboten');

  const selection = page.getByTestId('call-up-selection');
  const groups = await selection.getByRole('group').evaluateAll((g) =>
    g.map((e) => e.querySelector('legend')?.textContent?.trim()),
  );
  expect(groups).toEqual(['Linie 1', 'Linie 2', 'Goalies', 'Ohne Linie']);
  await expect(selection.getByRole('group', { name: 'Linie 1' }).getByRole('checkbox')).toHaveCount(3);

  // The response status is shown next to it
  await expect(entry(page, MIA)).toHaveAttribute('data-response', 'MAYBE');
  await expect(entry(page, MIA)).toContainText('vielleicht');
  await expect(entry(page, MIA)).toContainText('«Muss evtl. arbeiten»');
  await expect(entry(page, NINA)).toHaveAttribute('data-response', 'PENDING');
  await expect(entry(page, NINA)).toContainText('offen');
  await expect(entry(page, MIA).getByRole('checkbox', { name: 'Mia Frei' })).toBeChecked();

  // Call up Nina (pending) and Kim (declined), remove Lea
  await entry(page, NINA).getByRole('checkbox', { name: 'Nina Roth' }).check();
  await expect(page.getByRole('status')).toHaveText('Nina Roth ist aufgeboten.');
  await entry(page, KIM).getByRole('checkbox', { name: 'Kim Baumann' }).check();
  await expect(page.getByRole('status')).toHaveText('Kim Baumann ist aufgeboten.');
  await entry(page, LEA).getByRole('checkbox', { name: 'Lea Graf' }).uncheck();
  await expect(page.getByRole('status')).toHaveText('Lea Graf ist nicht mehr aufgeboten.');
  await expect(page.getByTestId('call-up-count')).toHaveText('6 aufgeboten');
  await checkAccessibility(page);

  await page.reload();
  await expect(entry(page, NINA).getByRole('checkbox')).toBeChecked();
  await expect(entry(page, KIM).getByRole('checkbox')).toBeChecked();
  await expect(entry(page, LEA).getByRole('checkbox')).not.toBeChecked();
});

test('F-05 K7 AUF-28: Coaches do not appear in the selection', async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/termine/${T04}/aufgebot`);
  const selection = page.getByTestId('call-up-selection');
  await expect(selection.getByRole('checkbox')).toHaveCount(7);
  await expect(entry(page, PETRA)).toHaveCount(0);
  await expect(selection.getByText('Petra Keller')).toHaveCount(0);
});

test('F-05 K5 S9 AUF-23 AUF-24: Publish with confirmation, then players see their status', async ({
  page,
}) => {
  await login(page, 'anna');
  await page.goto(`/termine/${T04}`);
  await expect(page.getByTestId('my-call-up')).toHaveText('noch kein Aufgebot');

  await login(page, 'petra');
  await page.goto(`/termine/${T04}/aufgebot`);
  await page.getByTestId('call-up-publish').click();
  const dialog = page.getByRole('dialog', { name: 'Aufgebot veröffentlichen?' });
  await expect(dialog).toBeVisible();
  await expect(dialog).toContainText('5 Spielerinnen sind aufgeboten');
  await checkAccessibility(page);
  await dialog.getByRole('button', { name: 'Abbrechen' }).click();
  await expect(dialog).toBeHidden();
  await expect(page.getByTestId('call-up-status')).toHaveText('Entwurf');

  await page.getByTestId('call-up-publish').click();
  await dialog.getByRole('button', { name: 'Veröffentlichen' }).click();
  await expect(dialog).toBeHidden();
  await expect(page.getByRole('status')).toHaveText('Das Aufgebot ist veröffentlicht.');
  await expect(page.getByTestId('call-up-status')).toHaveText('veröffentlicht am Mo 16.11. 06:00');
  await expect(page.getByTestId('call-up-publish')).toHaveCount(0);

  await login(page, 'anna');
  await page.goto(`/termine/${T04}`);
  await expect(page.getByTestId('my-call-up')).toHaveText('aufgeboten');
  await login(page, 'kim');
  await page.goto(`/termine/${T04}`);
  await expect(page.getByTestId('my-call-up')).toHaveText('nicht aufgeboten');
});

test('F-05 K5 AUF-23: Nothing can be published without a selection', async ({ page }) => {
  await login(page, 'petra');
  const response = await page.request.put(`/api/events/${T04}/call-up`, { data: { personIds: [] } });
  expect(response.status()).toBe(200);
  await page.goto(`/termine/${T04}/aufgebot`);
  await expect(page.getByTestId('call-up-publish')).toBeDisabled();
  await expect(page.getByText('Wähle zuerst mindestens eine Spielerin aus.')).toBeVisible();
});

test('F-05 K6 S11 RR-03 F-12: Call-up screen only for coaches and admins', async ({ page }) => {
  await login(page, 'anna');
  await page.goto(`/termine/${T04}/aufgebot`);
  await expect(page.getByRole('alert')).toHaveText('Keine Berechtigung');
  await expect(page.getByTestId('call-up-selection')).toHaveCount(0);
  for (const request of [
    page.request.get(`/api/events/${T04}/call-up`),
    page.request.put(`/api/events/${T04}/call-up`, { data: { personIds: [ANNA] } }),
    page.request.post(`/api/events/${T04}/call-up/publish`),
  ]) {
    expect((await request).status()).toBe(403);
  }

  // Lea is a player only (Team 1 and 2); Marc is coach of Team 2 and since F-12 manages Team 1's call-up too
  await login(page, 'lea');
  expect((await page.request.get(`/api/events/${T04}/call-up`)).status()).toBe(403);
  await login(page, 'marc');
  expect((await page.request.get(`/api/events/${T04}/call-up`)).status()).toBe(200);
});

test('F-05 AUF-02: A practice has no call-up', async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/termine/${T01}/aufgebot`);
  await expect(page.getByRole('alert')).toHaveText('Nur ein Spieltag hat ein Aufgebot.');
});

test('F-05 AUF-14 AUF-16: Marc changes the matches of event 05 when editing', async ({ page }) => {
  await login(page, 'marc');
  await page.goto(`/termine/${T05}/bearbeiten`);
  const match1 = page.getByRole('group', { name: 'Spiel 1' });
  await expect(match1.getByLabel('Gegner')).toHaveValue('UH Testingen');
  await expect(page.getByRole('group', { name: 'Treffpunkt' }).getByLabel('Ort')).toHaveValue('Sporthalle Nord');
  await match1.getByLabel('Gegner').fill('UH Testingen II');
  await page.getByRole('button', { name: 'Spiel hinzufügen' }).click();
  const match2 = page.getByRole('group', { name: 'Spiel 2' });
  await match2.getByLabel('Gegner').fill('UHC Abend');
  await match2.getByLabel('Anspielzeit').fill('15:15');
  await match2.getByLabel('Heim oder auswärts').selectOption({ label: 'Auswärts' });
  await page.getByRole('button', { name: 'Änderungen speichern' }).click();

  await expect(page).toHaveURL(`/termine/${T05}`);
  await expect(page.getByRole('status')).toHaveText('Änderungen gespeichert.');
  await expect.poll(() => matches(page)).toEqual(['14:00 Team 2 – UH Testingen II', '15:15 UHC Abend – Team 2']);

  await page.goto(`/termine/${T05}/bearbeiten`);
  await page.getByRole('button', { name: 'Spiel 1 entfernen' }).click();
  await page.getByRole('button', { name: 'Änderungen speichern' }).click();
  await expect(page).toHaveURL(`/termine/${T05}`);
  await expect.poll(() => matches(page)).toEqual(['15:15 UHC Abend – Team 2']);
});

test('F-05 SUH-06: Matches from swiss unihockey cannot be changed in the form', async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/termine/${T04}/bearbeiten`);
  const match1 = page.getByRole('group', { name: 'Spiel 1' });
  await expect(match1).toContainText('Von swiss unihockey, nicht änderbar.');
  await expect(match1.getByLabel('Gegner')).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Spiel 1 entfernen' })).toHaveCount(0);
});

test.describe('Rescheduling an imported match day', () => {
  test.use({ expectedConsoleErrors: [/status of 422/] });

  test('F-05 SUH-05 AUF-15 O-48: Imported match day can only be rescheduled on the same day, result can still be entered', async ({
    page,
  }) => {
    await login(page, 'petra');
    await page.goto(`/termine/${T04}/bearbeiten`);
    const date = page.getByLabel('Datum', { exact: true });
    await date.fill('2026-11-29');
    await page.getByRole('button', { name: 'Änderungen speichern' }).click();
    await expect(date).toHaveAttribute('aria-invalid', 'true');
    await expect(date).toBeFocused();
    await expect(date).toHaveAccessibleDescription(
      'Der Spieltag hat Spiele von swiss unihockey. Er lässt sich nur am selben Tag verschieben; das Datum kommt von swiss unihockey.',
    );
    await expect(page).toHaveURL(`/termine/${T04}/bearbeiten`);

    await page.goto(`/termine/${T04}`);
    await page.getByRole('button', { name: 'Resultat erfassen: Team 1 – UHC Musterdorf' }).click();
    const dialog = page.getByRole('dialog', { name: 'Resultat: Team 1 – UHC Musterdorf' });
    await dialog.getByLabel('Resultat (Heim:Gast, z.B. 5:3)').fill('2:1');
    await dialog.getByRole('button', { name: 'Resultat speichern' }).click();
    await expect(dialog).toBeHidden();
    await expect(page.getByTestId(`match-${MATCH_01}`)).toContainText('Resultat 2:1');
  });
});

test('F-05 K8 AUF-15: Enter a result per match', async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/termine/${T04}`);
  await page.getByRole('button', { name: 'Resultat erfassen: Team 1 – UHC Musterdorf' }).click();
  const dialog = page.getByRole('dialog', { name: 'Resultat: Team 1 – UHC Musterdorf' });
  await expect(dialog).toBeVisible();
  const field = dialog.getByLabel('Resultat (Heim:Gast, z.B. 5:3)');
  await field.fill('5-3');
  await dialog.getByRole('button', { name: 'Resultat speichern' }).click();
  await expect(field).toHaveAttribute('aria-invalid', 'true');
  await expect(dialog).toContainText('Resultat bitte als Heim:Gast, z.B. 5:3.');
  await checkAccessibility(page);

  await field.fill('5:3');
  await dialog.getByRole('button', { name: 'Resultat speichern' }).click();
  await expect(dialog).toBeHidden();
  await expect(page.getByRole('status')).toHaveText('Resultat für Team 1 – UHC Musterdorf gespeichert.');
  await expect(page.getByTestId(`match-${MATCH_01}`)).toContainText('Resultat 5:3');

  await login(page, 'anna');
  await page.goto(`/termine/${T04}`);
  await expect(page.getByTestId(`match-${MATCH_01}`)).toContainText('Resultat 5:3');
});
