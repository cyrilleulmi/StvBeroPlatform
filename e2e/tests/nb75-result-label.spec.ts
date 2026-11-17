import AxeBuilder from '@axe-core/playwright';
import { checkAccessibility, expect, login, reset, test } from './helpers';

/**
 * Follow-up fix #75 · The button «Resultat erfassen» has an accessible name that contains the
 * visible text (WCAG 2.5.3 «Label in Name», AUF-15). Found in #74.
 */

const T04 = '40000000-0000-4000-8000-000000000004';

test.beforeEach(async ({ request }) => {
  await reset(request);
});

test('NB-75 K1 AUF-15: Every «Resultat erfassen» button contains the visible text and is unique per match', async ({
  page,
}) => {
  await login(page, 'petra');
  await page.goto(`/termine/${T04}`);
  const buttons = page.getByTestId('matches').getByRole('button', { name: 'Resultat erfassen' });
  await expect(buttons).toHaveCount(2);
  await expect(buttons.nth(0)).toHaveText('Resultat erfassen');
  await expect(buttons.nth(0)).toHaveAccessibleName('Resultat erfassen: Team 1 – UHC Musterdorf');
  await expect(buttons.nth(1)).toHaveAccessibleName('Resultat erfassen: Unihockey Beispielstadt – Team 1');
});

test('NB-75 K2: Match day 04 as coach has no axe finding label-content-name-mismatch', async ({ page }) => {
  await login(page, 'petra');
  await page.goto(`/termine/${T04}`);
  await expect(page.getByTestId('matches').getByRole('button', { name: 'Resultat erfassen' })).toHaveCount(2);
  const result = await new AxeBuilder({ page }).withRules(['label-content-name-mismatch']).analyze();
  expect(result.violations.map((v) => v.id)).toEqual([]);
  await checkAccessibility(page);
});
