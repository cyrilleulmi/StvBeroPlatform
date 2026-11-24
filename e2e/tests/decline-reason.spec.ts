import type { Locator } from '@playwright/test';
import { checkAccessibility, expect, login, reset, test } from './helpers';

/**
 * Reason required with «nicht dabei» (VAR-12, ANM-05, E-95): a setting of the event variant. Event 01 is a practice
 * of Team 1 + Team 2 and uses the variant «Training» of Team 1.
 */

const TEAM_1 = '10000000-0000-4000-8000-000000000001';
const T01 = '40000000-0000-4000-8000-000000000001';
const NINA = '20000000-0000-4000-8000-000000000009';

test.beforeEach(async ({ request }) => {
  await reset(request);
});

function responseButton(area: Locator, choice: 'attending' | 'declined' | 'maybe'): Locator {
  return area.getByTestId('my-response').getByTestId(`response-${choice}`);
}

test('VAR-12: a coach switches the reason on, players must give one', async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/verwaltung/teams/${TEAM_1}`);
  const variants = page.getByTestId('event-variants');
  await variants.getByRole('button', { name: 'Variante «Training» bearbeiten' }).click();
  const dialog = page.getByRole('dialog', { name: 'Terminvariante «Training»' });
  const box = dialog.getByRole('checkbox', { name: 'Grund bei «nicht dabei» verlangen' });
  await expect(box).not.toBeChecked();
  await box.check();
  await dialog.getByRole('button', { name: 'Speichern' }).click();
  await expect(dialog).toBeHidden();
  await expect(variants.getByTestId('variant-PRACTICE')).toContainText('Grund bei Abmeldung');

  await login(page, 'nina');
  await page.goto(`/termine/${T01}`);
  await responseButton(page.getByRole('main'), 'declined').click();
  const reason = page.getByRole('dialog', { name: 'Nicht dabei' });
  await expect(reason.getByLabel('Grund', { exact: true })).toBeVisible();
  await checkAccessibility(page);
  // Nothing is saved before the reason is there
  await reason.getByRole('button', { name: 'Abmelden' }).click();
  await expect(reason.getByText('Bitte gib einen Grund an.')).toBeVisible();
  await reason.getByRole('button', { name: 'Abbrechen' }).click();
  await expect(page.getByTestId(`overview-person-${NINA}`)).toHaveAttribute('data-response', 'PENDING');

  await responseButton(page.getByRole('main'), 'declined').click();
  await reason.getByLabel('Grund', { exact: true }).fill('Prüfung');
  await reason.getByRole('button', { name: 'Abmelden' }).click();
  await expect(reason).toBeHidden();
  await expect(page.getByTestId(`overview-person-${NINA}`)).toHaveAttribute('data-response', 'DECLINED');
  await expect(page.getByTestId('my-response')).toContainText('Dein Kommentar: Prüfung');
});
