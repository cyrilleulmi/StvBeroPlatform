import type { Locator, Page } from '@playwright/test';
import { checkAccessibility, expect, login, reset, test } from './helpers';

/**
 * E-99 · Assistant «Neues Team» (PTF-13, PTF-14) and «Termine überarbeiten» (TER-18 to TER-21). Seed from
 * docs/test-data.md: series 02 «Training» Thu 19:00–20:30 of Team 1 (20.08.2026–29.04.2027); its event 02 on Thu
 * 19.11. has Anna's response. Default "now": Mon 16.11.2026 06:00.
 */

const TEAM_1 = '10000000-0000-4000-8000-000000000001';
const RUTH = '20000000-0000-4000-8000-000000000004';
const KIM = '20000000-0000-4000-8000-000000000011';
const SERIES_2 = '70000000-0000-4000-8000-000000000002';

test.beforeEach(async ({ request }) => {
  await reset(request);
});

function step(page: Page, key: string): Locator {
  return page.getByTestId(`wizard-${key}`);
}

async function next(page: Page, key: string): Promise<void> {
  await page.getByTestId('wizard-next').click();
  await expect(page).toHaveURL(new RegExp(`schritt=${key}`));
  await expect(step(page, key)).toBeVisible();
}

test('PTF-13 PTF-14: the assistant leads a coach from the name to a team that is ready', async ({ page }) => {
  await login(page, 'petra');
  await page.goto('/verwaltung/teams');
  await page.getByTestId('team-wizard-link').click();
  await expect(page).toHaveURL(/\/verwaltung\/teams\/neu$/);
  await expect(page.getByTestId('wizard-progress')).toHaveText('Schritt 1 von 7');
  await checkAccessibility(page);

  // 1 Name: creates the team
  await page.getByRole('button', { name: 'Team erstellen' }).click();
  await expect(page.getByLabel('Name des Teams')).toHaveAttribute('aria-invalid', 'true');
  await page.getByLabel('Name des Teams').fill('Team 4');
  await page.getByRole('button', { name: 'Team erstellen' }).click();
  await expect(page).toHaveURL(/team=.*schritt=personen/);
  await expect(page.getByTestId('wizard-team')).toHaveText('Team 4');
  await expect(page.getByTestId('wizard-progress')).toHaveText('Schritt 2 von 7');

  // 2 People (PTF-15): everyone as a table, women first, then by year of birth; one tap puts someone in
  const people = step(page, 'personen');
  const teamId = new URL(page.url()).searchParams.get('team')!;
  const cell = (personId: string) => page.getByTestId(`membership-cell-${personId}-${teamId}`);
  const rows = people.locator('[data-testid^="membership-row-"]');
  await expect(rows).toHaveCount(17);
  await expect(rows.first()).toContainText('Petra Keller');
  await expect(rows.last()).toContainText('Alex Admin');
  // Filters: men only, then a year of birth
  await people.getByRole('radio', { name: 'Männer' }).check();
  await expect(page.getByTestId('membership-count')).toContainText('1 von 17 Personen');
  await expect(rows).toHaveText([/Marc Huber/]);
  await people.getByRole('button', { name: 'Filter zurücksetzen' }).click();
  await people.getByLabel('Jahrgang ab').selectOption('2000');
  await expect(page.getByTestId('membership-count')).toContainText('3 von 17 Personen');
  await people.getByRole('button', { name: 'Filter zurücksetzen' }).click();
  // A coach, then players with one tap each; the rows stay where they are
  await people.getByRole('radio', { name: 'Coach' }).check();
  await cell(RUTH).click();
  await expect(cell(RUTH)).toHaveAttribute('data-role', 'COACH');
  await people.getByRole('radio', { name: 'Spielerin oder Spieler' }).check();
  await cell(KIM).click();
  await expect(cell(KIM)).toHaveAttribute('data-role', 'PLAYER');
  await expect(cell(KIM)).toHaveAttribute('aria-pressed', 'true');
  await expect(cell(KIM)).toHaveAccessibleName('Kim Baumann in Team 4: Spielerin oder Spieler');
  await expect(page.getByTestId('membership-status')).toHaveText('Kim Baumann ist Spielerin oder Spieler in Team 4.');
  await expect(page.getByTestId('membership-in-team')).toHaveText('Im Team: 1 Coach · 1 Spielerin oder Spieler');
  await expect(rows).toHaveCount(17);
  // Another tap takes her out again, also for a coach (E-101); the row stays where it is
  await cell(KIM).click();
  await expect(cell(KIM)).toHaveAttribute('data-role', 'none');
  await expect(page.getByTestId('membership-status')).toHaveText('Kim Baumann ist nicht mehr in Team 4.');
  await expect(rows).toHaveCount(17);
  await cell(KIM).click();
  await expect(cell(KIM)).toHaveAttribute('data-role', 'PLAYER');
  await checkAccessibility(page);

  // 3 swiss unihockey: explained, can be skipped
  await next(page, 'swissunihockey');
  await expect(step(page, 'swissunihockey')).toContainText('kommen Spiele, Spieltage und Tabelle von selbst');

  // 4 Practices: a series on Wednesday
  await next(page, 'trainings');
  const form = page.getByTestId('series-new');
  await form.getByLabel('Wochentag').selectOption({ label: 'Mittwoch' });
  await form.getByLabel('Ort (Halle)').fill('Turnhalle Dorf');
  await form.getByRole('button', { name: 'Serie erstellen' }).click();
  const series = page.getByTestId('wizard-series');
  await expect(series).toContainText('Training · Mittwoch 18:30–20:00');
  // Without an end the series runs on; its events exist one year ahead (TER-22, TER-23)
  await expect(series).toContainText('ohne Enddatum');
  await expect(series.getByTestId('series-events-until')).toContainText('Termine angelegt bis 16.11.2027');
  // It can be changed right here: an end, after the app said what happens (TER-19)
  await series.getByRole('button', { name: 'Ändern' }).click();
  await series.getByLabel('Endet am (freiwillig)').fill('2027-03-31');
  await series.getByTestId('series-check').click();
  await expect(series.getByTestId('series-preview')).toContainText('fallen weg');
  await series.getByTestId('series-apply').click();
  await expect(series).toContainText('31.03.2027');
  await expect(series.getByTestId('series-events-until')).toHaveCount(0);
  await checkAccessibility(page);

  // 5 Match days
  await next(page, 'spieltage');
  await page.getByLabel('Üblicher Treffpunkt').fill('Schulhaus Dorf');
  await page.getByRole('button', { name: 'Angaben speichern' }).click();
  await expect(page.getByRole('status')).toHaveText('Die Angaben für die Spieltage sind gespeichert.');

  // 6 Lines
  await next(page, 'linien');
  await expect(page.getByTestId('wizard-lines-state')).toHaveText('0 von 1 Person ist in einer Linie.');
  await expect(page.getByTestId('wizard-lines')).toHaveAttribute('href', /\/teams\/.*\/linien$/);

  // 7 Done: what is set up and what is open
  await next(page, 'fertig');
  await expect(page.getByTestId('wizard-check-personen')).toHaveAttribute('data-done', 'true');
  await expect(page.getByTestId('wizard-check-swissunihockey')).toHaveAttribute('data-done', 'false');
  await expect(page.getByTestId('wizard-check-trainings')).toHaveText(/1 Trainings-Serie/);
  await expect(page.getByTestId('wizard-check-linien')).toHaveAttribute('data-done', 'false');
  await checkAccessibility(page);

  // Back to a step through its address
  await page.getByTestId('wizard-check-trainings').getByRole('link', { name: 'Ändern' }).click();
  await expect(page).toHaveURL(/schritt=trainings/);
  await page.getByTestId('wizard-step-fertig').click();
  await page.getByTestId('wizard-to-team').click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Team 4');
});

test('TER-21 TER-19: a coach changes a series after the app said what happens to the upcoming events', async ({
  page,
}) => {
  await login(page, 'petra');
  await page.goto('/termine');
  await page.getByTestId('rework-link').click();
  await expect(page).toHaveURL(/\/termine\/ueberarbeiten/);
  await expect(page.getByLabel('Team', { exact: true })).toHaveValue(TEAM_1);
  await expect(page.getByTestId('rework-intro')).toContainText('Änderungen gelten immer nur für kommende Termine');
  await checkAccessibility(page);

  const card = page.getByTestId(`series-card-${SERIES_2}`);
  await expect(card.getByTestId('series-upcoming')).toContainText('nächster Do 19.11.');
  await card.getByTestId(`series-change-${SERIES_2}`).click();

  // A later start: every upcoming event is adjusted, the responses stay
  await card.getByLabel('Beginn').fill('19:30');
  await card.getByLabel('Ende', { exact: true }).fill('21:00');
  await card.getByTestId('series-check').click();
  const preview = card.getByTestId('series-preview');
  await expect(preview).toContainText('Gilt ab 19.11.2026');
  await expect(preview).toContainText('Termine werden angepasst, die Antworten bleiben.');
  await expect(preview).toHaveAttribute('data-state', 'ok');

  // Friday instead: Anna's response cancels the 19.11. – the app warns
  await card.getByLabel('Wochentag').selectOption({ label: 'Freitag' });
  await expect(card.getByTestId('series-preview')).toHaveCount(0);
  await expect(card).toContainText('Prüfe die Auswirkung nochmals.');
  await card.getByTestId('series-check').click();
  await expect(card.getByTestId('series-preview')).toHaveAttribute('data-state', 'warn');
  await expect(card.getByTestId('series-preview')).toContainText('schon Antworten und');
  await checkAccessibility(page);

  // Back to Thursday, later start, and take it over
  await card.getByLabel('Wochentag').selectOption({ label: 'Donnerstag' });
  await card.getByTestId('series-check').click();
  await card.getByTestId('series-apply').click();
  await expect(card.getByRole('heading', { level: 3 })).toHaveText('Training · Donnerstag 19:30–21:00');
});

test('TER-20 TER-07: holidays and ending a series, each with a clear warning', async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/termine/ueberarbeiten?team=${TEAM_1}`);
  const card = page.getByTestId(`series-card-${SERIES_2}`);

  await card.getByTestId(`series-holidays-${SERIES_2}`).click();
  await expect(card).toContainText('Kommende Termine in dieser Zeit fallen weg.');
  await card.getByLabel('Von').fill('2026-11-26');
  await card.getByLabel('Bis').fill('2026-11-26');
  await card.getByLabel('Grund, z.B. Herbstferien').fill('Hallenputz');
  await card.getByRole('button', { name: 'Ferien eintragen' }).last().click();
  await expect(card).toContainText('Ausnahmen: Hallenputz (26.11.2026 – 26.11.2026)');

  await card.getByTestId(`series-end-${SERIES_2}`).click();
  await card.getByLabel('Keine Termine mehr ab').fill('2026-12-01');
  await card.getByTestId(`series-end-check-${SERIES_2}`).click();
  const dialog = page.getByRole('dialog', { name: 'Serie beenden?' });
  await expect(dialog).toContainText('Ab 01.12.2026 gibt es keine Termine dieser Serie mehr.');
  await expect(dialog).toContainText('Das lässt sich nicht rückgängig machen.');
  await checkAccessibility(page);
  await dialog.getByRole('button', { name: 'Serie beenden' }).click();
  // Only Thu 19.11. is left (26.11. is a holiday now)
  await expect(card.getByTestId('series-upcoming')).toContainText('1 kommender Termin');
  await expect(card).toContainText('– 30.11.2026');
});

test('TER-21: players neither see «Überarbeiten» nor change series there', async ({ page }) => {
  await login(page, 'anna');
  await page.goto('/termine');
  await expect(page.getByRole('heading', { level: 1, name: 'Termine' })).toBeVisible();
  await expect(page.getByTestId('rework-link')).toHaveCount(0);
  await page.goto('/termine/ueberarbeiten');
  await expect(page.getByRole('alert')).toHaveText('Keine Berechtigung');
  await page.goto('/verwaltung/teams/neu');
  await expect(page.getByRole('alert')).toHaveText('Keine Berechtigung');
});
