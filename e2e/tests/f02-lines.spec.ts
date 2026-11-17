import type { Locator, Page } from '@playwright/test';
import { checkAccessibility, expect, login, reset, test } from './helpers';

/**
 * F-02 · Lines (LIN). Acceptance criteria from docs/pipeline/backlog.md, scenarios S14 and S15 from
 * docs/test-data.md. IDs from docs/test-data.md.
 */

const TEAM_1 = '10000000-0000-4000-8000-000000000001';
const TEAM_2 = '10000000-0000-4000-8000-000000000002';
const T1_LINE_1 = '30000000-0000-4000-8000-000000000001';
const T1_LINE_2 = '30000000-0000-4000-8000-000000000002';
const T1_GOALIES = '30000000-0000-4000-8000-000000000003';
const T2_LINE_1 = '30000000-0000-4000-8000-000000000004';
const T2_LINE_2 = '30000000-0000-4000-8000-000000000005';
const T2_GOALIES = '30000000-0000-4000-8000-000000000006';
const ANNA = '20000000-0000-4000-8000-000000000005';
const LEA = '20000000-0000-4000-8000-000000000006';
const KIM = '20000000-0000-4000-8000-000000000011';

/**
 * Grab a player by the handle and drag her onto a group with several mouse moves. Chrome starts
 * HTML5 drag and drop only after a move with the button pressed; `dragTo` jumps straight to the target.
 */
async function drag(page: Page, source: Locator, target: Locator): Promise<void> {
  const from = await source.boundingBox();
  const to = await target.boundingBox();
  if (!from || !to) throw new Error('Source or target not visible');
  await page.mouse.move(from.x + 12, from.y + from.height / 2);
  await page.mouse.down();
  await page.mouse.move(from.x + 30, from.y + from.height / 2 + 10, { steps: 5 });
  await page.mouse.move(to.x + to.width / 2, to.y + to.height / 2, { steps: 10 });
  await page.mouse.up();
}

/** Names of the players in a group (list items carry the name as `aria-label`). */
async function names(group: Locator): Promise<(string | null)[]> {
  return group.getByRole('listitem').evaluateAll((li) => li.map((e) => e.getAttribute('aria-label')));
}

/** Headings of the groups on the lines screen, in display order. */
async function groupTitles(management: Locator): Promise<string[]> {
  return management.getByRole('heading', { level: 2 }).evaluateAll((h) =>
    h.map((e) => (e.firstChild?.textContent ?? '').trim()),
  );
}

test.beforeEach(async ({ request }) => {
  await reset(request);
});

test('F-02 K1 LIN-01 LIN-06 LIN-09: Seed with lines, goalies and «Ohne Linie»', async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/teams/${TEAM_1}/linien`);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Linien');
  await expect(page).toHaveTitle('Linien · STV Bero');
  const management = page.getByTestId('lines-management');
  await expect.poll(() => groupTitles(management)).toEqual(['Linie 1', 'Linie 2', 'Goalies', 'Ohne Linie']);

  await expect.poll(() => names(page.getByTestId(`group-${T1_LINE_1}`))).toEqual(['Mia Frei', 'Lea Graf', 'Anna Muster']);
  await expect.poll(() => names(page.getByTestId(`group-${T1_LINE_2}`))).toEqual(['Sara Brun', 'Nina Roth']);
  await expect.poll(() => names(page.getByTestId(`group-${T1_GOALIES}`))).toEqual(['Jana Wyss']);
  await expect.poll(() => names(page.getByTestId('group-no-line'))).toEqual(['Kim Baumann']);
  await expect(page.getByTestId(`assignment-${KIM}`)).toHaveValue('');
  await expect(page.getByTestId(`assignment-${ANNA}`)).toHaveValue(T1_LINE_1);
  await checkAccessibility(page);

  // Team page: players by line (LIN-12), link to the lines for the coach
  await page.goto(`/teams/${TEAM_1}`);
  const players = page.getByTestId('team-players');
  await expect(players.getByRole('list', { name: 'Linie 1' }).getByRole('listitem')).toHaveText([
    'Mia Frei',
    'Lea Graf',
    'Anna Muster',
  ]);
  await expect(players.getByRole('list', { name: 'Goalies' }).getByRole('listitem')).toHaveText(['Jana Wyss']);
  await expect(players.getByRole('list', { name: 'Ohne Linie' }).getByRole('listitem')).toHaveText(['Kim Baumann']);
  await players.getByRole('link', { name: 'Linien einteilen' }).click();
  await expect(page).toHaveURL(`/teams/${TEAM_1}/linien`);
});

test('F-02 K2 LIN-07 LIN-09 RR-08: Add, rename, move and delete a line', async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/teams/${TEAM_1}/linien`);
  const management = page.getByTestId('lines-management');

  // Add
  await page.getByTestId('add-line').click();
  await expect(page.getByRole('status')).toHaveText('Linie 3 ist hinzugefügt.');
  await expect.poll(() => groupTitles(management)).toEqual(['Linie 1', 'Linie 2', 'Linie 3', 'Goalies', 'Ohne Linie']);

  // Rename
  await page.getByRole('button', { name: 'Linie 3 umbenennen' }).click();
  await page.getByLabel('Name von Linie 3').fill('Powerplay');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.getByRole('status')).toHaveText('Neuer Name: Powerplay.');

  // An empty name is not saved
  await page.getByRole('button', { name: 'Powerplay umbenennen' }).click();
  await page.getByLabel('Name von Powerplay').fill('');
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.getByLabel('Name von Powerplay')).toHaveAttribute('aria-invalid', 'true');
  await expect(page.getByLabel('Name von Powerplay')).toHaveAccessibleDescription('Bitte einen Namen eingeben.');
  await page.getByRole('button', { name: 'Abbrechen' }).click();

  // Change the order
  await expect(page.getByRole('button', { name: 'Linie 1 nach oben' })).toBeDisabled();
  await page.getByRole('button', { name: 'Powerplay nach oben' }).click();
  await expect(page.getByRole('status')).toHaveText('Powerplay ist verschoben.');
  await page.getByRole('button', { name: 'Powerplay nach oben' }).click();
  await expect.poll(() => groupTitles(management)).toEqual(['Powerplay', 'Linie 1', 'Linie 2', 'Goalies', 'Ohne Linie']);
  await expect(page.getByRole('button', { name: 'Linie 2 nach unten' })).toBeDisabled();

  // Deleting is for admins only (RR-08, F-12): Petra sees no «löschen»
  await expect(page.getByRole('button', { name: /löschen/ })).toHaveCount(0);
  await login(page, 'alex');
  await page.reload();
  await expect.poll(() => groupTitles(management)).toEqual(['Powerplay', 'Linie 1', 'Linie 2', 'Goalies', 'Ohne Linie']);

  // Delete with confirmation: the players are «Ohne Linie» afterwards (LIN-06)
  await page.getByRole('button', { name: 'Linie 2 löschen' }).click();
  const dialog = page.getByRole('dialog', { name: 'Linie 2 löschen?' });
  await expect(dialog).toBeVisible();
  await expect(dialog).toContainText('Die Spielerinnen dieser Linie sind danach ohne Linie.');
  await checkAccessibility(page);
  await dialog.getByRole('button', { name: 'Abbrechen' }).click();
  await expect(dialog).toBeHidden();
  await expect(page.getByTestId(`group-${T1_LINE_2}`)).toBeVisible();

  await page.getByRole('button', { name: 'Linie 2 löschen' }).click();
  await page.getByRole('dialog', { name: 'Linie 2 löschen?' }).getByRole('button', { name: 'Löschen' }).click();
  await expect(page.getByRole('status')).toHaveText('Linie 2 ist gelöscht.');
  await expect(page.getByTestId(`group-${T1_LINE_2}`)).toHaveCount(0);
  await expect.poll(() => names(page.getByTestId('group-no-line'))).toEqual(['Kim Baumann', 'Sara Brun', 'Nina Roth']);

  // Same after reload
  await page.reload();
  await expect.poll(() => groupTitles(management)).toEqual(['Powerplay', 'Linie 1', 'Goalies', 'Ohne Linie']);
  await expect.poll(() => names(page.getByTestId('group-no-line'))).toEqual(['Kim Baumann', 'Sara Brun', 'Nina Roth']);
});

test('F-02 K3 LIN-07 S14: Assignment via select field saves immediately', async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/teams/${TEAM_1}/linien`);

  await page.getByTestId(`assignment-${KIM}`).selectOption({ label: 'Linie 2' });
  await expect(page.getByRole('status')).toHaveText('Kim Baumann ist jetzt in Linie 2.');
  await expect.poll(() => names(page.getByTestId(`group-${T1_LINE_2}`))).toEqual(['Kim Baumann', 'Sara Brun', 'Nina Roth']);
  await expect(page.getByTestId('group-no-line').getByRole('listitem')).toHaveCount(0);
  await expect(page.getByTestId('group-no-line')).toContainText('Alle Spielerinnen sind eingeteilt.');
  await expect(page.getByTestId(`assignment-${KIM}`)).toBeFocused();

  await page.reload();
  await expect(page.getByTestId(`assignment-${KIM}`)).toHaveValue(T1_LINE_2);

  // Back to «Ohne Linie»
  await page.getByTestId(`assignment-${KIM}`).selectOption({ label: 'Ohne Linie' });
  await expect(page.getByRole('status')).toHaveText('Kim Baumann ist jetzt ohne Linie.');
  await expect.poll(() => names(page.getByTestId('group-no-line'))).toEqual(['Kim Baumann']);
});

test('F-02 K3 LIN-07: Assignment via drag and drop saves immediately', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name === 'mobile', 'On the phone the assignment uses the select field (LIN-07).');
  // Tall enough that source and target are visible at the same time (the mouse does not leave the window).
  await page.setViewportSize({ width: 1280, height: 1600 });
  await login(page, 'petra');
  await page.goto(`/teams/${TEAM_1}/linien`);

  await drag(page, page.getByRole('listitem', { name: 'Anna Muster' }), page.getByTestId(`group-${T1_GOALIES}`));
  await expect(page.getByRole('status')).toHaveText('Anna Muster ist jetzt in Goalies.');
  await expect.poll(() => names(page.getByTestId(`group-${T1_GOALIES}`))).toEqual(['Anna Muster', 'Jana Wyss']);

  await drag(page, page.getByRole('listitem', { name: 'Kim Baumann' }), page.getByTestId(`group-${T1_LINE_1}`));
  await expect(page.getByRole('status')).toHaveText('Kim Baumann ist jetzt in Linie 1.');

  await page.reload();
  await expect.poll(() => names(page.getByTestId(`group-${T1_LINE_1}`))).toEqual(['Kim Baumann', 'Mia Frei', 'Lea Graf']);
  await expect.poll(() => names(page.getByTestId(`group-${T1_GOALIES}`))).toEqual(['Anna Muster', 'Jana Wyss']);
});

test('F-02 K4 LIN-02 S15: at most one group per player and team', async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/teams/${TEAM_1}/linien`);

  await page.getByTestId(`assignment-${ANNA}`).selectOption({ label: 'Linie 2' });
  await expect(page.getByRole('status')).toHaveText('Anna Muster ist jetzt in Linie 2.');
  await expect.poll(() => names(page.getByTestId(`group-${T1_LINE_2}`))).toEqual(['Sara Brun', 'Anna Muster', 'Nina Roth']);
  await expect.poll(() => names(page.getByTestId(`group-${T1_LINE_1}`))).toEqual(['Mia Frei', 'Lea Graf']);
  await expect(page.getByRole('listitem', { name: 'Anna Muster' })).toHaveCount(1);
});

test('F-02 K5 LIN-03: Lea has different lines in Team 1 and Team 2', async ({ page }) => {
  await login(page, 'alex');
  await page.goto(`/teams/${TEAM_1}/linien`);
  await expect(page.getByTestId(`assignment-${LEA}`)).toHaveValue(T1_LINE_1);
  await page.goto(`/teams/${TEAM_2}/linien`);
  await expect(page.getByTestId(`assignment-${LEA}`)).toHaveValue(T2_LINE_2);
  await expect.poll(() => names(page.getByTestId(`group-${T2_LINE_2}`))).toEqual(['Lea Graf', 'Zoe Kaufmann']);

  // A change in Team 1 leaves Team 2 unchanged
  await page.goto(`/teams/${TEAM_1}/linien`);
  await page.getByTestId(`assignment-${LEA}`).selectOption({ label: 'Goalies' });
  await expect(page.getByRole('status')).toHaveText('Lea Graf ist jetzt in Goalies.');
  await page.goto(`/teams/${TEAM_2}/linien`);
  await expect(page.getByTestId(`assignment-${LEA}`)).toHaveValue(T2_LINE_2);
  // The select field lists only the groups of this team
  await expect(page.getByTestId(`assignment-${LEA}`).getByRole('option')).toHaveText([
    'Linie 1',
    'Linie 2',
    'Goalies',
    'Ohne Linie',
  ]);
  await expect(page.getByTestId(`group-${T2_LINE_1}`)).toBeVisible();
});

test('F-02 K6 LIN-05: Goalies exist exactly once and cannot be deleted', async ({ page }) => {
  await login(page, 'alex');
  await page.goto(`/teams/${TEAM_1}/linien`);
  const goalies = page.getByTestId(`group-${T1_GOALIES}`);
  await expect(goalies.getByRole('heading', { level: 2 })).toContainText('Goalies');
  await expect(page.getByRole('heading', { level: 2, name: /^Goalies/ })).toHaveCount(1);
  await expect(goalies.getByRole('button', { name: /löschen|umbenennen|nach/ })).toHaveCount(0);

  const response = await page.request.delete(`/api/teams/${TEAM_1}/groups/${T1_GOALIES}`);
  expect(response.status()).toBe(409);
  expect((await response.json()).rule).toBe('LIN-05');
  const created = await page.request.post(`/api/teams/${TEAM_1}/groups`, { data: { name: 'Goalies' } });
  expect(created.status()).toBe(409);
  await page.reload();
  await expect(page.getByRole('heading', { level: 2, name: /^Goalies/ })).toHaveCount(1);
});

test('F-02 K7 RR-07 F-12: anna sees the line-up read-only, coaches of other teams may change it', async ({ page }) => {
  await login(page, 'anna');
  await page.goto(`/teams/${TEAM_1}/linien`);
  await expect(page.getByText('Nur Coaches können die Einteilung ändern.')).toBeVisible();
  await expect.poll(() => names(page.getByTestId(`group-${T1_LINE_1}`))).toEqual(['Mia Frei', 'Lea Graf', 'Anna Muster']);
  await expect(page.getByRole('combobox')).toHaveCount(0);
  await expect(page.getByTestId('add-line')).toHaveCount(0);
  await expect(page.getByRole('button', { name: /löschen|umbenennen|nach/ })).toHaveCount(0);
  await checkAccessibility(page);
  await page.goto(`/teams/${TEAM_1}`);
  await expect(page.getByTestId('team-players').getByRole('list', { name: 'Linie 1' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Linien einteilen' })).toHaveCount(0);
  const annaChanges = await page.request.put(`/api/teams/${TEAM_1}/assignments/${ANNA}`, {
    data: { groupId: T1_LINE_2 },
  });
  expect(annaChanges.status()).toBe(403);

  // lea is a player only, also in Team 2: read-only
  await login(page, 'lea');
  await page.goto(`/teams/${TEAM_2}/linien`);
  await expect(page.getByText('Nur Coaches können die Einteilung ändern.')).toBeVisible();
  await expect(page.getByTestId(`group-${T2_GOALIES}`)).toBeVisible();
  await expect(page.getByRole('combobox')).toHaveCount(0);

  // petra is only a player in Team 2, but coach in Team 1: she may change it (F-12 K7)
  await login(page, 'petra');
  await page.goto(`/teams/${TEAM_2}/linien`);
  await expect(page.getByTestId('add-line')).toBeVisible();
  await expect(page.getByRole('button', { name: /löschen/ })).toHaveCount(0);

  // marc is coach of Team 2 and changes Team 1, without deleting (F-12 K1, K3)
  await login(page, 'marc');
  await page.goto(`/teams/${TEAM_1}/linien`);
  await expect(page.getByTestId('lines-management')).toBeVisible();
  await expect(page.getByRole('button', { name: /löschen/ })).toHaveCount(0);
  await page.getByTestId(`assignment-${ANNA}`).selectOption({ label: 'Linie 2' });
  await expect(page.getByRole('status')).toHaveText('Anna Muster ist jetzt in Linie 2.');
  await expect.poll(() => names(page.getByTestId(`group-${T1_LINE_2}`))).toContain('Anna Muster');
  expect((await page.request.delete(`/api/teams/${TEAM_1}/groups/${T1_LINE_1}`)).status()).toBe(403);
});

test.describe('Name already taken', () => {
  // The browser reports the expected 409 response in the console.
  test.use({ expectedConsoleErrors: [/status of 409/] });

  test('F-02 G1 LIN-09: a duplicate name is reported at the name field, focus stays in the field', async ({ page }) => {
    await login(page, 'petra');
    await page.goto(`/teams/${TEAM_1}/linien`);
    const management = page.getByTestId('lines-management');
    await page.getByTestId('add-line').click();
    await expect(page.getByRole('status')).toHaveText('Linie 3 ist hinzugefügt.');

    await page.getByRole('button', { name: 'Linie 3 umbenennen' }).click();
    const field = page.getByLabel('Name von Linie 3');
    await field.fill('LINIE 1');
    await page.getByRole('button', { name: 'Speichern' }).click();

    await expect(field).toHaveAttribute('aria-invalid', 'true');
    await expect(field).toHaveAccessibleDescription(/schon eine Gruppe/);
    await expect(field).toBeFocused();
    await expect(field).toBeInViewport();
    await expect(field).toHaveValue('LINIE 1');
    await expect.poll(() => groupTitles(management)).toEqual(['Linie 1', 'Linie 2', 'Linie 3', 'Goalies', 'Ohne Linie']);

    // Correct and save: the field error disappears
    await field.fill('Powerplay');
    await page.getByRole('button', { name: 'Speichern' }).click();
    await expect(page.getByRole('status')).toHaveText('Neuer Name: Powerplay.');
    await expect.poll(() => groupTitles(management)).toEqual(['Linie 1', 'Linie 2', 'Powerplay', 'Goalies', 'Ohne Linie']);
    await expect(page.getByRole('alert')).toHaveCount(0);
  });
});

test('F-02 G7 LIN-09: Focus stays with the line when it is moved to the edge', async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/teams/${TEAM_1}/linien`);
  const management = page.getByTestId('lines-management');
  await page.getByTestId('add-line').click();
  await expect(page.getByRole('status')).toHaveText('Linie 3 ist hinzugefügt.');

  // Linie 2 to the very bottom: «nach unten» is disabled, focus moves to «nach oben»
  await page.getByRole('button', { name: 'Linie 2 nach unten' }).focus();
  await page.keyboard.press('Enter');
  await expect.poll(() => groupTitles(management)).toEqual(['Linie 1', 'Linie 3', 'Linie 2', 'Goalies', 'Ohne Linie']);
  await expect(page.getByRole('button', { name: 'Linie 2 nach unten' })).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Linie 2 nach oben' })).toBeFocused();

  // Linie 3 to the very top: focus moves to «nach unten»
  await page.getByRole('button', { name: 'Linie 3 nach oben' }).focus();
  await page.keyboard.press('Enter');
  await expect.poll(() => groupTitles(management)).toEqual(['Linie 3', 'Linie 1', 'Linie 2', 'Goalies', 'Ohne Linie']);
  await expect(page.getByRole('button', { name: 'Linie 3 nach unten' })).toBeFocused();
});
