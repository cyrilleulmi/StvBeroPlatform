import type { Locator, Page } from '@playwright/test';
import { anmelden, barrierefrei, expect, reset, test } from './hilfen';

/**
 * F-05 · Spieltage und Aufgebot (AUF, RR-03). Akzeptanzkriterien aus Issue #6 bzw. docs/pipeline/backlog.md,
 * Szenarien S9 und S11 aus docs/testdaten.md. Standard-«Jetzt»: Mo 16.11.2026 06:00.
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
const SPIEL_01 = '50000000-0000-4000-8000-000000000001';
const SPIEL_02 = '50000000-0000-4000-8000-000000000002';
const SPIEL_03 = '50000000-0000-4000-8000-000000000003';

/** Spiele in Anzeigereihenfolge, als Name des Eintrags («09:30 Team 1 – UHC Musterdorf»). */
async function spiele(page: Page): Promise<(string | null)[]> {
  return page
    .getByTestId('spiele')
    .getByRole('listitem')
    .evaluateAll((li) => li.map((e) => e.getAttribute('aria-label')));
}

function eintrag(page: Page, personId: string): Locator {
  return page.getByTestId(`aufgebot-person-${personId}`);
}

test.beforeEach(async ({ request }) => {
  await reset(request);
});

test('F-05 K1 K3 AUF-10 AUF-12 AUF-14: Termin 04 zeigt Spiele nach Anspielzeit, Treffpunkt und Karte', async ({
  page,
}) => {
  await anmelden(page, 'anna');
  await page.goto(`/termine/${T04}`);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Spieltag');
  await expect(page.getByTestId(`spiel-${SPIEL_01}`)).toBeVisible();
  expect(await spiele(page)).toEqual([
    '09:30 Team 1 – UHC Musterdorf',
    '11:45 Unihockey Beispielstadt – Team 1',
  ]);
  await expect(page.getByTestId(`spiel-${SPIEL_01}`)).toContainText('Heim');
  await expect(page.getByTestId(`spiel-${SPIEL_02}`)).toContainText('Auswärts');
  await expect(page.getByTestId('treffpunkt')).toHaveText('08:00 · Parkplatz Sporthalle Nord');
  const karte = page.getByTestId('spielort-karte');
  await expect(karte).toHaveText('In Google Maps öffnen');
  await expect(karte).toHaveAttribute(
    'href',
    'https://www.google.com/maps/search/?api=1&query=Sporthalle+Seefeld+Musterdorf',
  );
  await expect(karte).toHaveAttribute('target', '_blank');
  await expect(page.getByText('Weisse Trikots mitnehmen')).toBeVisible();
  // AUF-23: Entwurf ist für Spielerinnen nicht sichtbar
  await expect(page.getByTestId('mein-aufgebot')).toHaveText('noch kein Aufgebot');
  await expect(page.getByRole('link', { name: 'Aufgebot bearbeiten' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: /Resultat/ })).toHaveCount(0);
  await barrierefrei(page);
});

test('F-05 K1 AUF-24: Termin 05 ist veröffentlicht – Eva aufgeboten, Lea nicht', async ({ page }) => {
  await anmelden(page, 'eva');
  await page.goto(`/termine/${T05}`);
  await expect(page.getByTestId('mein-aufgebot')).toHaveText('aufgeboten');
  expect(await spiele(page)).toEqual(['14:00 Team 2 – UH Testingen']);
  await expect(page.getByTestId(`spiel-${SPIEL_03}`)).toBeVisible();

  await anmelden(page, 'lea');
  await page.goto(`/termine/${T05}`);
  await expect(page.getByTestId('mein-aufgebot')).toHaveText('nicht aufgeboten');
});

test('F-05 K2 AUF-10 bis AUF-16: Petra erfasst einen Spieltag mit zwei Spielen', async ({ page }) => {
  await anmelden(page, 'petra');
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
  const treffpunkt = page.getByRole('group', { name: 'Treffpunkt' });
  await treffpunkt.getByLabel('Zeit').fill('09:00');
  await treffpunkt.getByLabel('Ort').fill('Bahnhof');

  // Spiel 1 ist schon da; das zweite Spiel beginnt früher und steht danach zuerst.
  const spiel1 = page.getByRole('group', { name: 'Spiel 1' });
  await spiel1.getByLabel('Gegner').fill('UHC Zwei');
  await spiel1.getByLabel('Anspielzeit').fill('13:00');
  await spiel1.getByLabel('Heim oder auswärts').selectOption({ label: 'Auswärts' });
  await page.getByRole('button', { name: 'Spiel hinzufügen' }).click();
  const spiel2 = page.getByRole('group', { name: 'Spiel 2' });
  await spiel2.getByLabel('Gegner').fill('UHC Eins');
  await spiel2.getByLabel('Anspielzeit').fill('10:00');
  await barrierefrei(page);
  await page.getByRole('button', { name: 'Termin speichern' }).click();

  await expect(page).toHaveURL(/\/termine\/[0-9a-f-]{36}$/);
  await expect(page.getByRole('status')).toHaveText('Der Termin ist erfasst.');
  expect(await spiele(page)).toEqual(['10:00 Team 1 – UHC Eins', '13:00 UHC Zwei – Team 1']);
  await expect(page.getByTestId('treffpunkt')).toHaveText('09:00 · Bahnhof');
  await expect(page.getByTestId('spielort-karte')).toHaveAttribute('href', 'https://maps.google.com/?q=Halle+Ost');
  await expect(page.getByTestId('spielort-karte')).toHaveAttribute('target', '_blank');
  await expect(page.getByText('Rote Trikots')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Aufgebot bearbeiten' })).toBeVisible();
});

test('F-05 K2 AUF-14: Spiele ohne Gegner werden beim Feld gemeldet', async ({ page }) => {
  await anmelden(page, 'petra');
  await page.goto('/termine/neu');
  await page.getByLabel('Art', { exact: true }).selectOption({ label: 'Spieltag' });
  await page.getByLabel('Datum', { exact: true }).fill('2026-12-06');
  await page.getByRole('button', { name: 'Termin speichern' }).click();
  const gegner = page.getByRole('group', { name: 'Spiel 1' }).getByLabel('Gegner');
  await expect(gegner).toHaveAttribute('aria-invalid', 'true');
  await expect(gegner).toBeFocused();
  await expect(page).toHaveURL(/\/termine\/neu$/);
});

test('F-05 K4 AUF-21 AUF-22: Aufgebot nach Linien, Antwortstatus daneben, auch offen und vielleicht wählbar', async ({
  page,
}) => {
  await anmelden(page, 'petra');
  await page.goto(`/termine/${T04}`);
  await page.getByRole('link', { name: 'Aufgebot bearbeiten' }).click();
  await expect(page).toHaveURL(`/termine/${T04}/aufgebot`);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Aufgebot');
  await expect(page.getByTestId('aufgebot-status')).toHaveText('Entwurf');
  await expect(page.getByTestId('aufgebot-anzahl')).toHaveText('5 aufgeboten');

  const auswahl = page.getByTestId('aufgebot-auswahl');
  const gruppen = await auswahl.getByRole('group').evaluateAll((g) =>
    g.map((e) => e.querySelector('legend')?.textContent?.trim()),
  );
  expect(gruppen).toEqual(['Linie 1', 'Linie 2', 'Goalies', 'Ohne Linie']);
  await expect(auswahl.getByRole('group', { name: 'Linie 1' }).getByRole('checkbox')).toHaveCount(3);

  // Antwortstatus steht daneben
  await expect(eintrag(page, MIA)).toHaveAttribute('data-antwort', 'VIELLEICHT');
  await expect(eintrag(page, MIA)).toContainText('vielleicht');
  await expect(eintrag(page, MIA)).toContainText('«Muss evtl. arbeiten»');
  await expect(eintrag(page, NINA)).toHaveAttribute('data-antwort', 'OFFEN');
  await expect(eintrag(page, NINA)).toContainText('offen');
  await expect(eintrag(page, MIA).getByRole('checkbox', { name: 'Mia Frei' })).toBeChecked();

  // Nina (offen) und Kim (nicht dabei) aufbieten, Lea herausnehmen
  await eintrag(page, NINA).getByRole('checkbox', { name: 'Nina Roth' }).check();
  await expect(page.getByRole('status')).toHaveText('Nina Roth ist aufgeboten.');
  await eintrag(page, KIM).getByRole('checkbox', { name: 'Kim Baumann' }).check();
  await expect(page.getByRole('status')).toHaveText('Kim Baumann ist aufgeboten.');
  await eintrag(page, LEA).getByRole('checkbox', { name: 'Lea Graf' }).uncheck();
  await expect(page.getByRole('status')).toHaveText('Lea Graf ist nicht mehr aufgeboten.');
  await expect(page.getByTestId('aufgebot-anzahl')).toHaveText('6 aufgeboten');
  await barrierefrei(page);

  await page.reload();
  await expect(eintrag(page, NINA).getByRole('checkbox')).toBeChecked();
  await expect(eintrag(page, KIM).getByRole('checkbox')).toBeChecked();
  await expect(eintrag(page, LEA).getByRole('checkbox')).not.toBeChecked();
});

test('F-05 K7 AUF-28: Coaches erscheinen nicht in der Auswahl', async ({ page }) => {
  await anmelden(page, 'petra');
  await page.goto(`/termine/${T04}/aufgebot`);
  const auswahl = page.getByTestId('aufgebot-auswahl');
  await expect(auswahl.getByRole('checkbox')).toHaveCount(7);
  await expect(eintrag(page, PETRA)).toHaveCount(0);
  await expect(auswahl.getByText('Petra Keller')).toHaveCount(0);
});

test('F-05 K5 S9 AUF-23 AUF-24: Veröffentlichen mit Bestätigung, danach sehen Spielerinnen ihren Status', async ({
  page,
}) => {
  await anmelden(page, 'anna');
  await page.goto(`/termine/${T04}`);
  await expect(page.getByTestId('mein-aufgebot')).toHaveText('noch kein Aufgebot');

  await anmelden(page, 'petra');
  await page.goto(`/termine/${T04}/aufgebot`);
  await page.getByTestId('aufgebot-veroeffentlichen').click();
  const dialog = page.getByRole('dialog', { name: 'Aufgebot veröffentlichen?' });
  await expect(dialog).toBeVisible();
  await expect(dialog).toContainText('5 Spielerinnen sind aufgeboten');
  await barrierefrei(page);
  await dialog.getByRole('button', { name: 'Abbrechen' }).click();
  await expect(dialog).toBeHidden();
  await expect(page.getByTestId('aufgebot-status')).toHaveText('Entwurf');

  await page.getByTestId('aufgebot-veroeffentlichen').click();
  await dialog.getByRole('button', { name: 'Veröffentlichen' }).click();
  await expect(dialog).toBeHidden();
  await expect(page.getByRole('status')).toHaveText('Das Aufgebot ist veröffentlicht.');
  await expect(page.getByTestId('aufgebot-status')).toHaveText('veröffentlicht am Mo 16.11. 06:00');
  await expect(page.getByTestId('aufgebot-veroeffentlichen')).toHaveCount(0);

  await anmelden(page, 'anna');
  await page.goto(`/termine/${T04}`);
  await expect(page.getByTestId('mein-aufgebot')).toHaveText('aufgeboten');
  await anmelden(page, 'kim');
  await page.goto(`/termine/${T04}`);
  await expect(page.getByTestId('mein-aufgebot')).toHaveText('nicht aufgeboten');
});

test('F-05 K5 AUF-23: Ohne Auswahl lässt sich nichts veröffentlichen', async ({ page }) => {
  await anmelden(page, 'petra');
  const antwort = await page.request.put(`/api/termine/${T04}/aufgebot`, { data: { personIds: [] } });
  expect(antwort.status()).toBe(200);
  await page.goto(`/termine/${T04}/aufgebot`);
  await expect(page.getByTestId('aufgebot-veroeffentlichen')).toBeDisabled();
  await expect(page.getByText('Wähle zuerst mindestens eine Spielerin aus.')).toBeVisible();
});

test('F-05 K6 S11 RR-03: Aufgebot-Screen nur für Coaches', async ({ page }) => {
  await anmelden(page, 'anna');
  await page.goto(`/termine/${T04}/aufgebot`);
  await expect(page.getByRole('alert')).toHaveText('Keine Berechtigung');
  await expect(page.getByTestId('aufgebot-auswahl')).toHaveCount(0);
  for (const anfrage of [
    page.request.get(`/api/termine/${T04}/aufgebot`),
    page.request.put(`/api/termine/${T04}/aufgebot`, { data: { personIds: [ANNA] } }),
    page.request.post(`/api/termine/${T04}/aufgebot/veroeffentlichen`),
  ]) {
    expect((await anfrage).status()).toBe(403);
  }

  // Marc ist Coach von Team 2, nicht von Team 1
  await anmelden(page, 'marc');
  expect((await page.request.get(`/api/termine/${T04}/aufgebot`)).status()).toBe(403);
});

test('F-05 AUF-02: Ein Training hat kein Aufgebot', async ({ page }) => {
  await anmelden(page, 'petra');
  await page.goto(`/termine/${T01}/aufgebot`);
  await expect(page.getByRole('alert')).toHaveText('Nur ein Spieltag hat ein Aufgebot.');
});

test('F-05 AUF-14 AUF-16: Marc ändert die Spiele von Termin 05 beim Bearbeiten', async ({ page }) => {
  await anmelden(page, 'marc');
  await page.goto(`/termine/${T05}/bearbeiten`);
  const spiel1 = page.getByRole('group', { name: 'Spiel 1' });
  await expect(spiel1.getByLabel('Gegner')).toHaveValue('UH Testingen');
  await expect(page.getByRole('group', { name: 'Treffpunkt' }).getByLabel('Ort')).toHaveValue('Sporthalle Nord');
  await spiel1.getByLabel('Gegner').fill('UH Testingen II');
  await page.getByRole('button', { name: 'Spiel hinzufügen' }).click();
  const spiel2 = page.getByRole('group', { name: 'Spiel 2' });
  await spiel2.getByLabel('Gegner').fill('UHC Abend');
  await spiel2.getByLabel('Anspielzeit').fill('15:15');
  await spiel2.getByLabel('Heim oder auswärts').selectOption({ label: 'Auswärts' });
  await page.getByRole('button', { name: 'Änderungen speichern' }).click();

  await expect(page).toHaveURL(`/termine/${T05}`);
  await expect(page.getByRole('status')).toHaveText('Änderungen gespeichert.');
  expect(await spiele(page)).toEqual(['14:00 Team 2 – UH Testingen II', '15:15 UHC Abend – Team 2']);

  await page.goto(`/termine/${T05}/bearbeiten`);
  await page.getByRole('button', { name: 'Spiel 1 entfernen' }).click();
  await page.getByRole('button', { name: 'Änderungen speichern' }).click();
  await expect(page).toHaveURL(`/termine/${T05}`);
  expect(await spiele(page)).toEqual(['15:15 UHC Abend – Team 2']);
});

test('F-05 SUH-06: Spiele aus swiss unihockey sind im Formular nicht änderbar', async ({ page }) => {
  await anmelden(page, 'petra');
  await page.goto(`/termine/${T04}/bearbeiten`);
  const spiel1 = page.getByRole('group', { name: 'Spiel 1' });
  await expect(spiel1).toContainText('Von swiss unihockey, nicht änderbar.');
  await expect(spiel1.getByLabel('Gegner')).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Spiel 1 entfernen' })).toHaveCount(0);
});

test.describe('Importierter Spieltag verschieben', () => {
  test.use({ erwarteteKonsolenfehler: [/status of 422/] });

  test('F-05 SUH-05 AUF-15 O-48: Importierter Spieltag nur am selben Tag verschiebbar, Resultat bleibt erfassbar', async ({
    page,
  }) => {
    await anmelden(page, 'petra');
    await page.goto(`/termine/${T04}/bearbeiten`);
    const datum = page.getByLabel('Datum', { exact: true });
    await datum.fill('2026-11-29');
    await page.getByRole('button', { name: 'Änderungen speichern' }).click();
    await expect(datum).toHaveAttribute('aria-invalid', 'true');
    await expect(datum).toBeFocused();
    await expect(datum).toHaveAccessibleDescription(
      'Der Spieltag hat Spiele von swiss unihockey. Er lässt sich nur am selben Tag verschieben; das Datum kommt von swiss unihockey.',
    );
    await expect(page).toHaveURL(`/termine/${T04}/bearbeiten`);

    await page.goto(`/termine/${T04}`);
    await page.getByRole('button', { name: 'Resultat für Team 1 – UHC Musterdorf erfassen' }).click();
    const dialog = page.getByRole('dialog', { name: 'Resultat: Team 1 – UHC Musterdorf' });
    await dialog.getByLabel('Resultat (Heim:Gast, z.B. 5:3)').fill('2:1');
    await dialog.getByRole('button', { name: 'Resultat speichern' }).click();
    await expect(dialog).toBeHidden();
    await expect(page.getByTestId(`spiel-${SPIEL_01}`)).toContainText('Resultat 2:1');
  });
});

test('F-05 K8 AUF-15: Resultat pro Spiel erfassen', async ({ page }) => {
  await anmelden(page, 'petra');
  await page.goto(`/termine/${T04}`);
  await page.getByRole('button', { name: 'Resultat für Team 1 – UHC Musterdorf erfassen' }).click();
  const dialog = page.getByRole('dialog', { name: 'Resultat: Team 1 – UHC Musterdorf' });
  await expect(dialog).toBeVisible();
  const feld = dialog.getByLabel('Resultat (Heim:Gast, z.B. 5:3)');
  await feld.fill('5-3');
  await dialog.getByRole('button', { name: 'Resultat speichern' }).click();
  await expect(feld).toHaveAttribute('aria-invalid', 'true');
  await expect(dialog).toContainText('Resultat bitte als Heim:Gast, z.B. 5:3.');
  await barrierefrei(page);

  await feld.fill('5:3');
  await dialog.getByRole('button', { name: 'Resultat speichern' }).click();
  await expect(dialog).toBeHidden();
  await expect(page.getByRole('status')).toHaveText('Resultat für Team 1 – UHC Musterdorf gespeichert.');
  await expect(page.getByTestId(`spiel-${SPIEL_01}`)).toContainText('Resultat 5:3');

  await anmelden(page, 'anna');
  await page.goto(`/termine/${T04}`);
  await expect(page.getByTestId(`spiel-${SPIEL_01}`)).toContainText('Resultat 5:3');
});
