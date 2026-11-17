import type { Locator, Page } from '@playwright/test';
import { anmelden, barrierefrei, expect, reset, test } from './helpers';

/**
 * F-02 · Linien (LIN). Akzeptanzkriterien aus docs/pipeline/backlog.md, Szenarien S14 und S15 aus
 * docs/testdaten.md. IDs aus docs/testdaten.md.
 */

const TEAM_1 = '10000000-0000-4000-8000-000000000001';
const TEAM_2 = '10000000-0000-4000-8000-000000000002';
const T1_LINIE_1 = '30000000-0000-4000-8000-000000000001';
const T1_LINIE_2 = '30000000-0000-4000-8000-000000000002';
const T1_GOALIES = '30000000-0000-4000-8000-000000000003';
const T2_LINIE_1 = '30000000-0000-4000-8000-000000000004';
const T2_LINIE_2 = '30000000-0000-4000-8000-000000000005';
const T2_GOALIES = '30000000-0000-4000-8000-000000000006';
const ANNA = '20000000-0000-4000-8000-000000000005';
const LEA = '20000000-0000-4000-8000-000000000006';
const KIM = '20000000-0000-4000-8000-000000000011';

/**
 * Spielerin am Griff fassen und mit mehreren Mausbewegungen auf eine Gruppe ziehen. Chrome startet
 * HTML5-Drag-&-Drop erst nach einer Bewegung mit gedrückter Taste; `dragTo` springt direkt ans Ziel.
 */
async function ziehen(page: Page, quelle: Locator, ziel: Locator): Promise<void> {
  const von = await quelle.boundingBox();
  const nach = await ziel.boundingBox();
  if (!von || !nach) throw new Error('Quelle oder Ziel nicht sichtbar');
  await page.mouse.move(von.x + 12, von.y + von.height / 2);
  await page.mouse.down();
  await page.mouse.move(von.x + 30, von.y + von.height / 2 + 10, { steps: 5 });
  await page.mouse.move(nach.x + nach.width / 2, nach.y + nach.height / 2, { steps: 10 });
  await page.mouse.up();
}

/** Namen der Spielerinnen einer Gruppe (Listeneinträge tragen den Namen als `aria-label`). */
async function namen(gruppe: Locator): Promise<(string | null)[]> {
  return gruppe.getByRole('listitem').evaluateAll((li) => li.map((e) => e.getAttribute('aria-label')));
}

/** Überschriften der Gruppen im Linien-Screen, in Anzeigereihenfolge. */
async function gruppenTitel(verwaltung: Locator): Promise<string[]> {
  return verwaltung.getByRole('heading', { level: 2 }).evaluateAll((h) =>
    h.map((e) => (e.firstChild?.textContent ?? '').trim()),
  );
}

test.beforeEach(async ({ request }) => {
  await reset(request);
});

test('F-02 K1 LIN-01 LIN-06 LIN-09: Seed mit Linien, Goalies und «Ohne Linie»', async ({ page }) => {
  await anmelden(page, 'petra');
  await page.goto(`/teams/${TEAM_1}/linien`);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Linien');
  await expect(page).toHaveTitle('Linien · STV Bero');
  const verwaltung = page.getByTestId('linien-verwaltung');
  await expect.poll(() => gruppenTitel(verwaltung)).toEqual(['Linie 1', 'Linie 2', 'Goalies', 'Ohne Linie']);

  await expect.poll(() => namen(page.getByTestId(`gruppe-${T1_LINIE_1}`))).toEqual(['Mia Frei', 'Lea Graf', 'Anna Muster']);
  await expect.poll(() => namen(page.getByTestId(`gruppe-${T1_LINIE_2}`))).toEqual(['Sara Brun', 'Nina Roth']);
  await expect.poll(() => namen(page.getByTestId(`gruppe-${T1_GOALIES}`))).toEqual(['Jana Wyss']);
  await expect.poll(() => namen(page.getByTestId('gruppe-ohne-linie'))).toEqual(['Kim Baumann']);
  await expect(page.getByTestId(`zuweisung-${KIM}`)).toHaveValue('');
  await expect(page.getByTestId(`zuweisung-${ANNA}`)).toHaveValue(T1_LINIE_1);
  await barrierefrei(page);

  // Team-Seite: Spielerinnen nach Linie (LIN-12), Link zu den Linien für die Coach
  await page.goto(`/teams/${TEAM_1}`);
  const spielerinnen = page.getByTestId('team-spielerinnen');
  await expect(spielerinnen.getByRole('list', { name: 'Linie 1' }).getByRole('listitem')).toHaveText([
    'Mia Frei',
    'Lea Graf',
    'Anna Muster',
  ]);
  await expect(spielerinnen.getByRole('list', { name: 'Goalies' }).getByRole('listitem')).toHaveText(['Jana Wyss']);
  await expect(spielerinnen.getByRole('list', { name: 'Ohne Linie' }).getByRole('listitem')).toHaveText(['Kim Baumann']);
  await spielerinnen.getByRole('link', { name: 'Linien einteilen' }).click();
  await expect(page).toHaveURL(`/teams/${TEAM_1}/linien`);
});

test('F-02 K2 LIN-07 LIN-09: Linie hinzufügen, umbenennen, verschieben, löschen', async ({ page }) => {
  await anmelden(page, 'petra');
  await page.goto(`/teams/${TEAM_1}/linien`);
  const verwaltung = page.getByTestId('linien-verwaltung');

  // Hinzufügen
  await page.getByTestId('linie-hinzufuegen').click();
  await expect(page.getByRole('status')).toHaveText('Linie 3 ist hinzugefügt.');
  await expect.poll(() => gruppenTitel(verwaltung)).toEqual(['Linie 1', 'Linie 2', 'Linie 3', 'Goalies', 'Ohne Linie']);

  // Umbenennen
  await page.getByRole('button', { name: 'Linie 3 umbenennen' }).click();
  await page.getByLabel('Name von Linie 3').fill('Powerplay');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.getByRole('status')).toHaveText('Neuer Name: Powerplay.');

  // Leerer Name wird nicht gespeichert
  await page.getByRole('button', { name: 'Powerplay umbenennen' }).click();
  await page.getByLabel('Name von Powerplay').fill('');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.getByLabel('Name von Powerplay')).toHaveAttribute('aria-invalid', 'true');
  await expect(page.getByLabel('Name von Powerplay')).toHaveAccessibleDescription('Bitte einen Namen eingeben.');
  await page.getByRole('button', { name: 'Abbrechen' }).click();

  // Reihenfolge ändern
  await expect(page.getByRole('button', { name: 'Linie 1 nach oben' })).toBeDisabled();
  await page.getByRole('button', { name: 'Powerplay nach oben' }).click();
  await expect(page.getByRole('status')).toHaveText('Powerplay ist verschoben.');
  await page.getByRole('button', { name: 'Powerplay nach oben' }).click();
  await expect.poll(() => gruppenTitel(verwaltung)).toEqual(['Powerplay', 'Linie 1', 'Linie 2', 'Goalies', 'Ohne Linie']);
  await expect(page.getByRole('button', { name: 'Linie 2 nach unten' })).toBeDisabled();

  // Löschen mit Bestätigung: Spielerinnen sind danach «Ohne Linie» (LIN-06)
  await page.getByRole('button', { name: 'Linie 2 löschen' }).click();
  const dialog = page.getByRole('dialog', { name: 'Linie 2 löschen?' });
  await expect(dialog).toBeVisible();
  await expect(dialog).toContainText('Die Spielerinnen dieser Linie sind danach ohne Linie.');
  await barrierefrei(page);
  await dialog.getByRole('button', { name: 'Abbrechen' }).click();
  await expect(dialog).toBeHidden();
  await expect(page.getByTestId(`gruppe-${T1_LINIE_2}`)).toBeVisible();

  await page.getByRole('button', { name: 'Linie 2 löschen' }).click();
  await page.getByRole('dialog', { name: 'Linie 2 löschen?' }).getByRole('button', { name: 'Löschen' }).click();
  await expect(page.getByRole('status')).toHaveText('Linie 2 ist gelöscht.');
  await expect(page.getByTestId(`gruppe-${T1_LINIE_2}`)).toHaveCount(0);
  await expect.poll(() => namen(page.getByTestId('gruppe-ohne-linie'))).toEqual(['Kim Baumann', 'Sara Brun', 'Nina Roth']);

  // Nach dem Neuladen gleich
  await page.reload();
  await expect.poll(() => gruppenTitel(verwaltung)).toEqual(['Powerplay', 'Linie 1', 'Goalies', 'Ohne Linie']);
  await expect.poll(() => namen(page.getByTestId('gruppe-ohne-linie'))).toEqual(['Kim Baumann', 'Sara Brun', 'Nina Roth']);
});

test('F-02 K3 LIN-07 S14: Zuweisung per Auswahlfeld speichert sofort', async ({ page }) => {
  await anmelden(page, 'petra');
  await page.goto(`/teams/${TEAM_1}/linien`);

  await page.getByTestId(`zuweisung-${KIM}`).selectOption({ label: 'Linie 2' });
  await expect(page.getByRole('status')).toHaveText('Kim Baumann ist jetzt in Linie 2.');
  await expect.poll(() => namen(page.getByTestId(`gruppe-${T1_LINIE_2}`))).toEqual(['Kim Baumann', 'Sara Brun', 'Nina Roth']);
  await expect(page.getByTestId('gruppe-ohne-linie').getByRole('listitem')).toHaveCount(0);
  await expect(page.getByTestId('gruppe-ohne-linie')).toContainText('Alle Spielerinnen sind eingeteilt.');
  await expect(page.getByTestId(`zuweisung-${KIM}`)).toBeFocused();

  await page.reload();
  await expect(page.getByTestId(`zuweisung-${KIM}`)).toHaveValue(T1_LINIE_2);

  // Zurück auf «Ohne Linie»
  await page.getByTestId(`zuweisung-${KIM}`).selectOption({ label: 'Ohne Linie' });
  await expect(page.getByRole('status')).toHaveText('Kim Baumann ist jetzt ohne Linie.');
  await expect.poll(() => namen(page.getByTestId('gruppe-ohne-linie'))).toEqual(['Kim Baumann']);
});

test('F-02 K3 LIN-07: Zuweisung per Drag & Drop speichert sofort', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name === 'mobil', 'Auf dem Handy wird über das Auswahlfeld zugewiesen (LIN-07).');
  // Hoch genug, dass Quelle und Ziel gleichzeitig sichtbar sind (die Maus verlässt das Fenster nicht).
  await page.setViewportSize({ width: 1280, height: 1600 });
  await anmelden(page, 'petra');
  await page.goto(`/teams/${TEAM_1}/linien`);

  await ziehen(page, page.getByRole('listitem', { name: 'Anna Muster' }), page.getByTestId(`gruppe-${T1_GOALIES}`));
  await expect(page.getByRole('status')).toHaveText('Anna Muster ist jetzt in Goalies.');
  await expect.poll(() => namen(page.getByTestId(`gruppe-${T1_GOALIES}`))).toEqual(['Anna Muster', 'Jana Wyss']);

  await ziehen(page, page.getByRole('listitem', { name: 'Kim Baumann' }), page.getByTestId(`gruppe-${T1_LINIE_1}`));
  await expect(page.getByRole('status')).toHaveText('Kim Baumann ist jetzt in Linie 1.');

  await page.reload();
  await expect.poll(() => namen(page.getByTestId(`gruppe-${T1_LINIE_1}`))).toEqual(['Kim Baumann', 'Mia Frei', 'Lea Graf']);
  await expect.poll(() => namen(page.getByTestId(`gruppe-${T1_GOALIES}`))).toEqual(['Anna Muster', 'Jana Wyss']);
});

test('F-02 K4 LIN-02 S15: höchstens eine Gruppe pro Spielerin und Team', async ({ page }) => {
  await anmelden(page, 'petra');
  await page.goto(`/teams/${TEAM_1}/linien`);

  await page.getByTestId(`zuweisung-${ANNA}`).selectOption({ label: 'Linie 2' });
  await expect(page.getByRole('status')).toHaveText('Anna Muster ist jetzt in Linie 2.');
  await expect.poll(() => namen(page.getByTestId(`gruppe-${T1_LINIE_2}`))).toEqual(['Sara Brun', 'Anna Muster', 'Nina Roth']);
  await expect.poll(() => namen(page.getByTestId(`gruppe-${T1_LINIE_1}`))).toEqual(['Mia Frei', 'Lea Graf']);
  await expect(page.getByRole('listitem', { name: 'Anna Muster' })).toHaveCount(1);
});

test('F-02 K5 LIN-03: Lea hat in Team 1 und Team 2 verschiedene Linien', async ({ page }) => {
  await anmelden(page, 'alex');
  await page.goto(`/teams/${TEAM_1}/linien`);
  await expect(page.getByTestId(`zuweisung-${LEA}`)).toHaveValue(T1_LINIE_1);
  await page.goto(`/teams/${TEAM_2}/linien`);
  await expect(page.getByTestId(`zuweisung-${LEA}`)).toHaveValue(T2_LINIE_2);
  await expect.poll(() => namen(page.getByTestId(`gruppe-${T2_LINIE_2}`))).toEqual(['Lea Graf', 'Zoe Kaufmann']);

  // Änderung in Team 1 lässt Team 2 unverändert
  await page.goto(`/teams/${TEAM_1}/linien`);
  await page.getByTestId(`zuweisung-${LEA}`).selectOption({ label: 'Goalies' });
  await expect(page.getByRole('status')).toHaveText('Lea Graf ist jetzt in Goalies.');
  await page.goto(`/teams/${TEAM_2}/linien`);
  await expect(page.getByTestId(`zuweisung-${LEA}`)).toHaveValue(T2_LINIE_2);
  // Im Auswahlfeld stehen nur die Gruppen dieses Teams
  await expect(page.getByTestId(`zuweisung-${LEA}`).getByRole('option')).toHaveText([
    'Linie 1',
    'Linie 2',
    'Goalies',
    'Ohne Linie',
  ]);
  await expect(page.getByTestId(`gruppe-${T2_LINIE_1}`)).toBeVisible();
});

test('F-02 K6 LIN-05: Goalies gibt es genau einmal, sie lassen sich nicht löschen', async ({ page }) => {
  await anmelden(page, 'petra');
  await page.goto(`/teams/${TEAM_1}/linien`);
  const goalies = page.getByTestId(`gruppe-${T1_GOALIES}`);
  await expect(goalies.getByRole('heading', { level: 2 })).toContainText('Goalies');
  await expect(page.getByRole('heading', { level: 2, name: /^Goalies/ })).toHaveCount(1);
  await expect(goalies.getByRole('button', { name: /löschen|umbenennen|nach/ })).toHaveCount(0);

  const antwort = await page.request.delete(`/api/teams/${TEAM_1}/groups/${T1_GOALIES}`);
  expect(antwort.status()).toBe(409);
  expect((await antwort.json()).rule).toBe('LIN-05');
  const neu = await page.request.post(`/api/teams/${TEAM_1}/groups`, { data: { name: 'Goalies' } });
  expect(neu.status()).toBe(409);
  await page.reload();
  await expect(page.getByRole('heading', { level: 2, name: /^Goalies/ })).toHaveCount(1);
});

test('F-02 K7 RR-07: anna sieht die Einteilung lesend, marc darf Team 1 nicht ändern', async ({ page }) => {
  await anmelden(page, 'anna');
  await page.goto(`/teams/${TEAM_1}/linien`);
  await expect(page.getByText('Nur Coaches des Teams können die Einteilung ändern.')).toBeVisible();
  await expect.poll(() => namen(page.getByTestId(`gruppe-${T1_LINIE_1}`))).toEqual(['Mia Frei', 'Lea Graf', 'Anna Muster']);
  await expect(page.getByRole('combobox')).toHaveCount(0);
  await expect(page.getByTestId('linie-hinzufuegen')).toHaveCount(0);
  await expect(page.getByRole('button', { name: /löschen|umbenennen|nach/ })).toHaveCount(0);
  await barrierefrei(page);
  await page.goto(`/teams/${TEAM_1}`);
  await expect(page.getByTestId('team-spielerinnen').getByRole('list', { name: 'Linie 1' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Linien einteilen' })).toHaveCount(0);
  const annaAendert = await page.request.put(`/api/teams/${TEAM_1}/assignments/${ANNA}`, {
    data: { groupId: T1_LINIE_2 },
  });
  expect(annaAendert.status()).toBe(403);

  // petra ist in Team 2 nur Spielerin: lesend
  await anmelden(page, 'petra');
  await page.goto(`/teams/${TEAM_2}/linien`);
  await expect(page.getByText('Nur Coaches des Teams können die Einteilung ändern.')).toBeVisible();
  await expect(page.getByTestId(`gruppe-${T2_GOALIES}`)).toBeVisible();
  await expect(page.getByRole('combobox')).toHaveCount(0);

  await anmelden(page, 'marc');
  await page.goto(`/teams/${TEAM_1}/linien`);
  await expect(page.getByRole('alert')).toHaveText('Keine Berechtigung');
  await expect(page.getByTestId('linien-verwaltung')).toHaveCount(0);
  for (const anfrage of [
    page.request.put(`/api/teams/${TEAM_1}/assignments/${ANNA}`, { data: { groupId: T1_LINIE_2 } }),
    page.request.post(`/api/teams/${TEAM_1}/groups`, { data: { name: 'Linie 3' } }),
    page.request.put(`/api/teams/${TEAM_1}/groups/${T1_LINIE_1}`, { data: { name: 'X' } }),
    page.request.delete(`/api/teams/${TEAM_1}/groups/${T1_LINIE_1}`),
  ]) {
    expect((await anfrage).status()).toBe(403);
  }
});

test.describe('Name schon vergeben', () => {
  // Der Browser meldet die erwartete 409-Antwort in der Konsole.
  test.use({ erwarteteKonsolenfehler: [/status of 409/] });

  test('F-02 G1 LIN-09: doppelter Name wird beim Namensfeld gemeldet, Fokus bleibt im Feld', async ({ page }) => {
    await anmelden(page, 'petra');
    await page.goto(`/teams/${TEAM_1}/linien`);
    const verwaltung = page.getByTestId('linien-verwaltung');
    await page.getByTestId('linie-hinzufuegen').click();
    await expect(page.getByRole('status')).toHaveText('Linie 3 ist hinzugefügt.');

    await page.getByRole('button', { name: 'Linie 3 umbenennen' }).click();
    const feld = page.getByLabel('Name von Linie 3');
    await feld.fill('LINIE 1');
    await page.getByRole('button', { name: 'Speichern' }).click();

    await expect(feld).toHaveAttribute('aria-invalid', 'true');
    await expect(feld).toHaveAccessibleDescription(/schon eine Gruppe/);
    await expect(feld).toBeFocused();
    await expect(feld).toBeInViewport();
    await expect(feld).toHaveValue('LINIE 1');
    await expect.poll(() => gruppenTitel(verwaltung)).toEqual(['Linie 1', 'Linie 2', 'Linie 3', 'Goalies', 'Ohne Linie']);

    // Korrigieren und speichern: Feldfehler verschwindet
    await feld.fill('Powerplay');
    await page.getByRole('button', { name: 'Speichern' }).click();
    await expect(page.getByRole('status')).toHaveText('Neuer Name: Powerplay.');
    await expect.poll(() => gruppenTitel(verwaltung)).toEqual(['Linie 1', 'Linie 2', 'Powerplay', 'Goalies', 'Ohne Linie']);
    await expect(page.getByRole('alert')).toHaveCount(0);
  });
});

test('F-02 G7 LIN-09: Fokus bleibt bei der Linie, wenn sie an den Rand verschoben wird', async ({ page }) => {
  await anmelden(page, 'petra');
  await page.goto(`/teams/${TEAM_1}/linien`);
  const verwaltung = page.getByTestId('linien-verwaltung');
  await page.getByTestId('linie-hinzufuegen').click();
  await expect(page.getByRole('status')).toHaveText('Linie 3 ist hinzugefügt.');

  // Linie 2 nach ganz unten: «nach unten» ist gesperrt, Fokus wechselt auf «nach oben»
  await page.getByRole('button', { name: 'Linie 2 nach unten' }).focus();
  await page.keyboard.press('Enter');
  await expect.poll(() => gruppenTitel(verwaltung)).toEqual(['Linie 1', 'Linie 3', 'Linie 2', 'Goalies', 'Ohne Linie']);
  await expect(page.getByRole('button', { name: 'Linie 2 nach unten' })).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Linie 2 nach oben' })).toBeFocused();

  // Linie 3 nach ganz oben: Fokus wechselt auf «nach unten»
  await page.getByRole('button', { name: 'Linie 3 nach oben' }).focus();
  await page.keyboard.press('Enter');
  await expect.poll(() => gruppenTitel(verwaltung)).toEqual(['Linie 3', 'Linie 1', 'Linie 2', 'Goalies', 'Ohne Linie']);
  await expect(page.getByRole('button', { name: 'Linie 3 nach unten' })).toBeFocused();
});
