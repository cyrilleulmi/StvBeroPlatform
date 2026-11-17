import type { APIRequestContext, Page } from '@playwright/test';
import { anmelden, barrierefrei, expect, reset, test } from './helpers';

/**
 * F-06 · Benachrichtigungen (BEN). Akzeptanzkriterien aus Issue #7 bzw. docs/pipeline/backlog.md,
 * Szenarien S5, S6, S9 und S10 aus docs/testdaten.md. Standard-«Jetzt»: Mo 16.11.2026 06:00.
 * Seed: Absage von Termin 03 an alle Mitglieder von Team 1 und 2 ausser Petra (Mia gelesen), Aufgebot
 * Termin 05 an alle Spielerinnen von Team 2 (Petra gelesen).
 */

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
const TEAM_1 = '10000000-0000-4000-8000-000000000001';
const TEAM_2 = '10000000-0000-4000-8000-000000000002';
const T01 = '40000000-0000-4000-8000-000000000001';
const T03 = '40000000-0000-4000-8000-000000000003';
const T04 = '40000000-0000-4000-8000-000000000004';
const T05 = '40000000-0000-4000-8000-000000000005';
const ANNA_ABSAGE_T03 = '90000000-0000-4000-8000-000000000002';
const LEA_AUFGEBOT_T05 = '90000000-0000-4000-8000-000000000014';
const LEA_ABSAGE_T03 = '90000000-0000-4000-8000-000000000003';
const PETRA_AUFGEBOT_T05 = '90000000-0000-4000-8000-000000000013';
const TEAM_1_UND_2_OHNE_PETRA = [ANNA, LEA, MIA, SARA, NINA, JANA, KIM, MARC, EVA, LARA, ZOE, ELLA];

interface Benachrichtigung {
  id: string;
  type: string;
  recipient: { id: string };
  text: string;
  eventId: string | null;
}

/** Nach dem Reset neu entstandene Benachrichtigungen einer Art zu einem Termin (ohne Seed). */
async function neue(request: APIRequestContext, art: string, terminId: string): Promise<Benachrichtigung[]> {
  const antwort = await request.get('/api/test/notifications');
  expect(antwort.status()).toBe(200);
  const alle = (await antwort.json()) as Benachrichtigung[];
  return alle.filter((b) => b.type === art && b.eventId === terminId && !b.id.startsWith('90000000'));
}

const empfaenger = (liste: Benachrichtigung[]) => liste.map((b) => b.recipient.id).sort();

async function uhr(request: APIRequestContext, jetzt: string): Promise<void> {
  const antwort = await request.put('/api/test/clock', { data: { now: jetzt } });
  expect(antwort.status()).toBe(204);
}

function glocke(page: Page) {
  return page.getByRole('navigation', { name: 'Hauptnavigation' }).getByRole('link', { name: /^Benachrichtigungen/ });
}

function liste(page: Page) {
  return page.getByRole('list', { name: 'Benachrichtigungen' });
}

test.beforeEach(async ({ request }) => {
  await reset(request);
});

test('F-06 K1: Liste neueste zuerst, Ungelesene gekennzeichnet, Anzahl in der Navigation', async ({ page }) => {
  await anmelden(page, 'lea');
  await page.goto('/');
  await expect(page.getByTestId('benachrichtigungen-zaehler')).toHaveText('2');
  await expect(glocke(page)).toHaveAccessibleName('Benachrichtigungen 2 ungelesen');

  await glocke(page).click();
  await expect(page).toHaveURL('/benachrichtigungen');
  await expect(page).toHaveTitle('Benachrichtigungen · STV Bero');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Benachrichtigungen');
  await expect(glocke(page)).toHaveAttribute('aria-current', 'page');

  const eintraege = liste(page).getByRole('listitem');
  await expect(eintraege).toHaveCount(2);
  // Neueste zuerst: Aufgebot (15.11.) vor Absage (14.11.)
  await expect(eintraege.nth(0)).toHaveAttribute('data-testid', `benachrichtigung-${LEA_AUFGEBOT_T05}`);
  await expect(eintraege.nth(1)).toHaveAttribute('data-testid', `benachrichtigung-${LEA_ABSAGE_T03}`);
  await expect(eintraege.nth(0)).toContainText('Das Aufgebot für Spieltag Sa 28.11. ist da: Du bist nicht aufgeboten.');
  await expect(eintraege.nth(0)).toContainText('So 15.11. 21:00');
  await expect(eintraege.nth(1)).toContainText('Training Di 24.11. ist abgesagt: Halle besetzt');
  for (const e of await eintraege.all()) {
    await expect(e).toHaveAttribute('data-gelesen', 'false');
    await expect(e.getByText('Neu', { exact: true })).toBeVisible();
  }
  await barrierefrei(page);
});

test('F-06 K1: Gelesene ohne «Neu», kein Zähler, wenn alles gelesen ist', async ({ page }) => {
  await anmelden(page, 'petra');
  await page.goto('/benachrichtigungen');
  const eintrag = page.getByTestId(`benachrichtigung-${PETRA_AUFGEBOT_T05}`);
  await expect(eintrag).toHaveAttribute('data-gelesen', 'true');
  await expect(eintrag.getByText('Neu', { exact: true })).toHaveCount(0);
  await expect(page.getByTestId('benachrichtigungen-zaehler')).toHaveCount(0);
  await expect(glocke(page)).toHaveAccessibleName('Benachrichtigungen');
  await expect(page.getByRole('button', { name: 'Alle als gelesen markieren' })).toHaveCount(0);
});

test('F-06 K1: ohne Benachrichtigungen ein Hinweis', async ({ page }) => {
  await anmelden(page, 'alex');
  await page.goto('/benachrichtigungen');
  await expect(page.getByText('Keine Benachrichtigungen.')).toBeVisible();
  await expect(page.getByTestId('benachrichtigungen-zaehler')).toHaveCount(0);
});

test('F-06 K2 BEN-12: Klick führt zum Termin und markiert als gelesen', async ({ page }) => {
  await anmelden(page, 'anna');
  await page.goto('/benachrichtigungen');
  await expect(page.getByTestId('benachrichtigungen-zaehler')).toHaveText('1');

  await page.getByTestId(`benachrichtigung-${ANNA_ABSAGE_T03}`).getByRole('link').click();
  await expect(page).toHaveURL(`/termine/${T03}`);
  await expect(page.getByTestId('termin-status')).toContainText('Halle besetzt');
  await expect(page.getByTestId('benachrichtigungen-zaehler')).toHaveCount(0);

  await glocke(page).click();
  await expect(page.getByTestId(`benachrichtigung-${ANNA_ABSAGE_T03}`)).toHaveAttribute('data-gelesen', 'true');
  // Bleibt nach Neuladen gelesen
  await page.reload();
  await expect(page.getByTestId(`benachrichtigung-${ANNA_ABSAGE_T03}`)).toHaveAttribute('data-gelesen', 'true');
});

test('F-06 K1: «Alle als gelesen markieren»', async ({ page }) => {
  await anmelden(page, 'lea');
  await page.goto('/benachrichtigungen');
  await page.getByRole('button', { name: 'Alle als gelesen markieren' }).click();
  await expect(page.getByRole('status')).toHaveText('Alle sind als gelesen markiert.');
  await expect(page.getByTestId('benachrichtigungen-zaehler')).toHaveCount(0);
  await expect(liste(page).getByText('Neu', { exact: true })).toHaveCount(0);
});

test('F-06 RR-07: fremde Benachrichtigung lässt sich nicht als gelesen markieren (403)', async ({ page }) => {
  await anmelden(page, 'anna');
  const antwort = await page.request.post(`/api/notifications/${LEA_ABSAGE_T03}/read`);
  expect(antwort.status()).toBe(403);
});

test('F-06 K3 S5 BEN-02: kurzfristige Absage benachrichtigt Petra, nicht Marc', async ({ page, request }) => {
  await uhr(request, '2026-11-16T19:00:00+01:00');
  await anmelden(page, 'anna');
  const antwort = await page.request.put(`/api/events/${T01}/responses/me`, { data: { status: 'DECLINED' } });
  expect(antwort.status()).toBe(200);

  const neu = await neue(request, 'LATE_CANCELLATION', T01);
  expect(empfaenger(neu)).toEqual([PETRA]);

  await anmelden(page, 'petra');
  await page.goto('/');
  await expect(page.getByTestId('benachrichtigungen-zaehler')).toHaveText('1');
  await page.goto('/benachrichtigungen');
  const eintrag = liste(page).getByRole('listitem').first();
  await expect(eintrag).toContainText('Anna Muster hat sich kurzfristig für Training Di 17.11. abgemeldet.');
  await eintrag.getByRole('link').click();
  await expect(page).toHaveURL(`/termine/${T01}`);
});

test('F-06 K4 S6 BEN-01 BEN-13: Erinnerung an Personen ohne Antwort, genau einmal', async ({ page, request }) => {
  await uhr(request, '2026-11-16T06:31:00+01:00');
  expect(empfaenger(await neue(request, 'REMINDER', T01))).toEqual([NINA, ELLA].sort());
  // Erneute Prüfungen erinnern nicht nochmals
  await uhr(request, '2026-11-16T07:00:00+01:00');
  await uhr(request, '2026-11-16T12:00:00+01:00');
  expect(await neue(request, 'REMINDER', T01)).toHaveLength(2);

  await anmelden(page, 'nina');
  await page.goto('/benachrichtigungen');
  await expect(liste(page).getByRole('listitem').first()).toContainText(
    'Erinnerung: Bitte gib bis Mo 16.11. 18:30 Bescheid, ob du bei Training Di 17.11. dabei bist.',
  );
  await expect(liste(page).getByRole('listitem').first()).toHaveAttribute('data-art', 'REMINDER');
});

test('F-06 K5 S9 BEN-03: Veröffentlichen benachrichtigt alle Spielerinnen des Teams', async ({ page, request }) => {
  await anmelden(page, 'petra');
  await page.goto(`/termine/${T04}/aufgebot`);
  await page.getByRole('button', { name: 'Veröffentlichen' }).click();
  await page.getByRole('dialog', { name: 'Aufgebot veröffentlichen?' }).getByRole('button', { name: 'Veröffentlichen' }).click();
  await expect(page.getByTestId('aufgebot-status')).toContainText('veröffentlicht am');

  const neu = await neue(request, 'CALL_UP_PUBLISHED', T04);
  expect(empfaenger(neu)).toEqual([ANNA, LEA, MIA, SARA, NINA, JANA, KIM].sort());

  await anmelden(page, 'kim');
  await page.goto('/benachrichtigungen');
  await expect(liste(page).getByRole('listitem').first()).toContainText(
    'Das Aufgebot für Spieltag So 22.11. ist da: Du bist nicht aufgeboten.',
  );
});

test('F-06 K6 S10 BEN-04: Abmeldung einer Aufgebotenen benachrichtigt die Coaches', async ({ page, request }) => {
  await anmelden(page, 'eva');
  await page.goto(`/termine/${T05}`);
  await page.getByTestId('meine-antwort').getByRole('button', { name: 'Nicht dabei' }).click();
  await expect(page.getByRole('dialog', { name: 'Nicht dabei' })).toBeVisible();
  await page.getByRole('button', { name: 'Überspringen' }).click();

  await expect.poll(async () => empfaenger(await neue(request, 'CALLED_UP_PLAYER_DECLINED', T05))).toEqual([MARC]);

  await anmelden(page, 'marc');
  await page.goto('/benachrichtigungen');
  await expect(liste(page).getByRole('listitem').first()).toContainText(
    'Eva Steiner ist für Spieltag Sa 28.11. aufgeboten und hat sich abgemeldet.',
  );
});

test('F-06 K7 BEN-05: Änderung eines veröffentlichten Aufgebots benachrichtigt nur Betroffene', async ({ page, request }) => {
  await anmelden(page, 'marc');
  // Termin 05 ist veröffentlicht mit Petra, Eva, Lara, Ella. Ella fällt heraus, Lea kommt dazu.
  const antwort = await page.request.put(`/api/events/${T05}/call-up`, {
    data: { personIds: [PETRA, EVA, LARA, LEA] },
  });
  expect(antwort.status()).toBe(200);

  const neu = await neue(request, 'CALL_UP_CHANGED', T05);
  expect(empfaenger(neu)).toEqual([LEA, ELLA].sort());

  await anmelden(page, 'ella');
  await page.goto('/benachrichtigungen');
  await expect(liste(page).getByRole('listitem').first()).toContainText(
    'Das Aufgebot für Spieltag Sa 28.11. wurde geändert: Du bist nicht mehr aufgeboten.',
  );
});

test('F-06 K8 BEN-07: Absage benachrichtigt alle Mitglieder der beteiligten Teams', async ({ page, request }) => {
  await anmelden(page, 'petra');
  const antwort = await page.request.post(`/api/events/${T01}/cancel`, { data: { reason: 'Halle gesperrt' } });
  expect(antwort.status()).toBe(200);
  expect(empfaenger(await neue(request, 'EVENT_CANCELLED', T01))).toEqual([...TEAM_1_UND_2_OHNE_PETRA].sort());

  await anmelden(page, 'zoe');
  await page.goto('/benachrichtigungen');
  await expect(liste(page).getByRole('listitem').first()).toContainText('Training Di 17.11. ist abgesagt: Halle gesperrt');
});

test('F-06 K8 BEN-07: Verschiebung benachrichtigt alle Mitglieder der beteiligten Teams', async ({ page, request }) => {
  await anmelden(page, 'marc');
  const antwort = await page.request.put(`/api/events/${T01}`, {
    data: {
      type: 'PRACTICE',
      title: 'Training',
      start: '2026-11-17T19:00:00+01:00',
      end: '2026-11-17T20:30:00+01:00',
      teamIds: [TEAM_1, TEAM_2],
      location: { name: 'Sporthalle Nord' },
    },
  });
  expect(antwort.status()).toBe(200);
  const neu = await neue(request, 'EVENT_RESCHEDULED', T01);
  expect(empfaenger(neu)).toEqual([...TEAM_1_UND_2_OHNE_PETRA.filter((p) => p !== MARC), PETRA].sort());

  await anmelden(page, 'anna');
  await page.goto('/benachrichtigungen');
  await expect(liste(page).getByRole('listitem').first()).toContainText(
    'Training Di 17.11. ist verschoben auf Di 17.11. 19:00.',
  );
});

for (const schema of ['light', 'dark'] as const) {
  test(`F-06: Glocke mit Zähler (${schema}) gut erreichbar und verdeckt nichts`, async ({ page }) => {
    await page.emulateMedia({ colorScheme: schema });
    await anmelden(page, 'lea');
    await page.goto('/termine');
    await expect(page.getByTestId('benachrichtigungen-zaehler')).toHaveText('2');
    const box = (await glocke(page).boundingBox())!;
    expect(box.width).toBeGreaterThanOrEqual(44);
    expect(box.height).toBeGreaterThanOrEqual(44);
    // Die Überschrift liegt nicht unter der Glocke
    const h1 = (await page.getByRole('heading', { level: 1 }).boundingBox())!;
    const ueberlappt =
      box.x < h1.x + h1.width && h1.x < box.x + box.width && box.y < h1.y + h1.height && h1.y < box.y + box.height;
    expect(ueberlappt, 'Glocke verdeckt die Überschrift').toBe(false);
    await page.goto('/benachrichtigungen');
    await expect(liste(page).getByRole('listitem')).toHaveCount(2);
    await barrierefrei(page);
  });
}

test('F-06 O-51: Glocke verdeckt auch beim Scrollen keine Bedienelemente', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'mobil', 'Glocke oben rechts gibt es nur mobil');
  await anmelden(page, 'petra');
  await page.goto(`/termine/${T01}`);
  const stift = page.getByRole('button', { name: 'Antwort für Mia Frei eintragen' });
  await expect(stift).toBeVisible();
  const box = (await glocke(page).boundingBox())!;
  // Stift-Button so weit nach oben scrollen, dass er auf der Höhe der Glocke liegt
  const vorher = (await stift.boundingBox())!;
  await page.evaluate((dy) => window.scrollBy(0, dy), vorher.y - box.y);
  const knopf = (await stift.boundingBox())!;
  expect(Math.abs(knopf.y - box.y), 'Stift liegt auf der Höhe der Glocke').toBeLessThan(4);
  // Neben der Glocke ist der Stift nicht mehr sichtbar: Er liegt unter der Kopfzeile. Vorher war er
  // links sichtbar und rechts von der Glocke überdeckt, ein Tipp auf den Rand öffnete die Benachrichtigungen.
  const imInhalt = await page.evaluate(
    ([px, py]) => !!document.elementFromPoint(px, py)?.closest('main'),
    [knopf.x + 4, knopf.y + knopf.height / 2],
  );
  expect(imInhalt, 'Inhalt auf der Höhe der Glocke sichtbar').toBe(false);
  // Der Stift bleibt bedienbar
  await stift.click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await expect(page).toHaveURL(new RegExp(`/termine/${T01}$`));
});
