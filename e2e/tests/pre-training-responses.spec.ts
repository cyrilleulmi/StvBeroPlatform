import type { Page } from '@playwright/test';
import { checkAccessibility, expect, login, reset, test } from './helpers';

/**
 * Responses to the pre-training of a practice (ANM-14 to ANM-17, E-119): the second question below the response
 * buttons, on the event page and on the card; coaches for themselves and on behalf; the overview.
 * Plan: ../plans/pre-training.md. IDs from docs/test-data.md: Anna and Lea play in Team 1, Petra coaches it.
 * Default "now" after reset: Mon 16.11.2026, 06:00.
 */

const TEAM_1 = '10000000-0000-4000-8000-000000000001';
const ANNA = '20000000-0000-4000-8000-000000000005';
const LEA = '20000000-0000-4000-8000-000000000006';
const PETRA = '20000000-0000-4000-8000-000000000002';

test.beforeEach(async ({ request }) => {
  await reset(request);
});

/** Petra creates a practice of Team 1 on Fri 20.11. 18:00–19:30 with «Joggen» at 17:30. */
async function practiceWithPreTraining(page: Page): Promise<string> {
  await login(page, 'petra');
  const response = await page.request.post('/api/events', {
    data: {
      type: 'PRACTICE',
      title: 'Training mit Vortraining',
      start: '2026-11-20T18:00:00+01:00',
      end: '2026-11-20T19:30:00+01:00',
      teamIds: [TEAM_1],
      preTraining: { title: 'Joggen', start: '2026-11-20T17:30:00+01:00' },
    },
  });
  expect(response.status()).toBe(201);
  return (await response.json()).id as string;
}

test('ANM-14 ANM-15: A player who is «dabei» says separately whether she is at the pre-training', async ({ page }) => {
  const id = await practiceWithPreTraining(page);
  await login(page, 'anna');
  await page.goto(`/termine/${id}`);

  // Nothing is asked before she is «dabei» at the practice
  const mine = page.getByTestId('my-response');
  const block = page.getByTestId('pre-training-response');
  await expect(mine.getByTestId('response-attending')).toHaveAttribute('aria-pressed', 'false');
  await expect(block).toHaveCount(0);

  // «dabei» signs nobody up for the pre-training: the question opens
  await mine.getByTestId('response-attending').click();
  await expect(block).toBeVisible();
  await expect(block).toHaveAttribute('data-response', 'PENDING');
  await expect(block.getByTestId('pre-training-question')).toHaveText('Auch beim Vortraining?');
  await expect(block).toContainText('17:30 · Joggen');
  await expect(block.getByRole('button', { name: 'Dabei', exact: true })).toHaveAttribute('aria-pressed', 'false');
  await expect(block.getByRole('button', { name: 'Nicht dabei' })).toHaveAttribute('aria-pressed', 'false');
  await checkAccessibility(page);

  // One tap answers, another one changes the answer
  await block.getByTestId('pre-training-response-attending').click();
  await expect(block).toHaveAttribute('data-response', 'ATTENDING');
  await expect(block.getByTestId('pre-training-response-attending')).toHaveAttribute('aria-pressed', 'true');
  await expect(block.getByTestId('pre-training-question')).toHaveText('Vortraining');
  await expect(mine.getByRole('status')).toHaveText('Gespeichert: beim Vortraining dabei.');
  await expect(mine.getByTestId('response-attending')).toHaveAttribute('aria-pressed', 'true');

  await block.getByTestId('pre-training-response-declined').click();
  await expect(block).toHaveAttribute('data-response', 'DECLINED');
  await expect(mine.getByRole('status')).toHaveText('Gespeichert: ohne Vortraining.');
  // The practice stays «dabei», and the overview marks her
  await expect(mine.getByTestId('response-attending')).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByTestId(`overview-person-${ANNA}`).getByTestId('overview-pre-training')).toHaveText(
    'ohne Vortraining',
  );
  await page.reload();
  await expect(block).toHaveAttribute('data-response', 'DECLINED');

  // Only the pre-training without the practice is not possible: with «nicht dabei» the question is gone …
  await mine.getByTestId('response-declined').click();
  await page.getByRole('dialog', { name: 'Nicht dabei' }).getByRole('button', { name: 'Überspringen' }).click();
  await expect(block).toHaveCount(0);
  await expect(page.getByTestId(`overview-person-${ANNA}`).getByTestId('overview-pre-training')).toHaveCount(0);
  // … and it is asked again with the next «dabei»
  await mine.getByTestId('response-attending').click();
  await expect(block).toHaveAttribute('data-response', 'PENDING');
});

test('ANM-14 ANM-08: The card of the event list asks the second question right below the buttons', async ({ page }) => {
  const id = await practiceWithPreTraining(page);
  await login(page, 'anna');
  await page.goto('/termine');
  const card = page.getByTestId(`event-card-${id}`);
  await expect(card.getByTestId('pre-training-response')).toHaveCount(0);

  await card.getByTestId('response-attending').click();
  const block = card.getByTestId('pre-training-response');
  await expect(block.getByTestId('pre-training-question')).toHaveText('Auch beim Vortraining?');
  await block.getByTestId('pre-training-response-attending').click();
  await expect(block).toHaveAttribute('data-response', 'ATTENDING');

  // Events without a pre-training never ask
  await expect(page.getByTestId('pre-training-response')).toHaveCount(1);
  await page.reload();
  await expect(card.getByTestId('pre-training-response')).toHaveAttribute('data-response', 'ATTENDING');
  await checkAccessibility(page);
});

test('ANM-16 ANM-17: A coach answers for herself and on behalf, the overview counts the pre-training', async ({ page }) => {
  const id = await practiceWithPreTraining(page);
  await page.goto(`/termine/${id}`);

  // Petra is coach of Team 1: the same two questions
  const mine = page.getByTestId('my-response');
  await mine.getByTestId('response-attending').click();
  await mine.getByTestId('pre-training-response-attending').click();
  const coach = page.getByTestId('coach-block').getByTestId(`overview-person-${PETRA}`);
  await expect(coach.getByTestId('overview-pre-training')).toHaveText('mit Vortraining');

  // Coaches do not count; nobody of the players has answered yet
  const total = page.getByTestId('overview-pre-training-total');
  await expect(total).toContainText('Vortraining');
  await expect(total).toContainText('0 dabei');

  // On behalf of Anna: «dabei» at both
  await page.getByRole('button', { name: 'Antwort für Anna Muster eintragen' }).click();
  let dialog = page.getByRole('dialog', { name: 'Antwort für Anna Muster eintragen' });
  const preTraining = dialog.getByRole('group', { name: 'Vortraining' });
  await dialog.getByRole('group', { name: 'Antwort' }).getByRole('radio', { name: 'nicht dabei', exact: true }).check();
  // Not without the practice
  await expect(preTraining.getByRole('radio', { name: 'dabei', exact: true })).toBeDisabled();
  await expect(preTraining.getByRole('radio', { name: 'nicht dabei', exact: true })).toBeDisabled();
  await expect(preTraining).toContainText('Nur mit «dabei» beim Training.');
  await dialog.getByRole('group', { name: 'Antwort' }).getByRole('radio', { name: 'dabei', exact: true }).check();
  await expect(preTraining.getByRole('radio', { name: 'dabei', exact: true })).toBeEnabled();
  await expect(preTraining).toContainText('Noch offen');
  await preTraining.getByRole('radio', { name: 'dabei', exact: true }).check();
  await dialog.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.getByTestId(`overview-person-${ANNA}`).getByTestId('overview-pre-training')).toHaveText(
    'mit Vortraining',
  );

  // On behalf of Lea: «dabei» at the practice, the pre-training stays open
  await page.getByRole('button', { name: 'Antwort für Lea Graf eintragen' }).click();
  dialog = page.getByRole('dialog', { name: 'Antwort für Lea Graf eintragen' });
  await dialog.getByRole('group', { name: 'Antwort' }).getByRole('radio', { name: 'dabei', exact: true }).check();
  await dialog.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.getByTestId(`overview-person-${LEA}`).getByTestId('overview-pre-training')).toHaveText(
    'Vortraining offen',
  );

  await expect(total).toContainText('1 dabei');
  await expect(total).toContainText('0 nicht dabei');
  await checkAccessibility(page);

  // Opening the dialog again shows what is stored
  await page.getByRole('button', { name: 'Antwort für Anna Muster eintragen' }).click();
  dialog = page.getByRole('dialog', { name: 'Antwort für Anna Muster eintragen' });
  await expect(
    dialog.getByRole('group', { name: 'Vortraining' }).getByRole('radio', { name: 'dabei', exact: true }),
  ).toBeChecked();
});
