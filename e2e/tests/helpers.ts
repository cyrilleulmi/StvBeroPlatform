import AxeBuilder from '@axe-core/playwright';
import { APIRequestContext, Locator, Page, expect, test as base } from '@playwright/test';

/**
 * Shared helpers for all E2E tests.
 * - `test` checks after every test that the browser console stayed free of errors.
 * - `expectedConsoleErrors`: patterns for messages a test triggers on purpose (e.g. the
 *   browser entry for an expected 409 response). Default: none.
 */
export const test = base.extend<{ consoleErrors: string[]; expectedConsoleErrors: RegExp[] }>({
  expectedConsoleErrors: [[], { option: true }],
  consoleErrors: [
    async ({ page, expectedConsoleErrors }, use) => {
      const errors: string[] = [];
      page.on('console', (m) => {
        if (m.type() === 'error') errors.push(m.text());
      });
      page.on('pageerror', (e) => errors.push(e.message));
      await use(errors);
      const unexpected = errors.filter((f) => !expectedConsoleErrors.some((m) => m.test(f)));
      expect(unexpected, 'Errors in the browser console').toEqual([]);
    },
    { auto: true },
  ],
});

export { expect };

/** Reset the database to the seed (docs/test-data.md). */
export async function reset(request: APIRequestContext): Promise<void> {
  const response = await request.post('/api/test/reset');
  expect(response.status()).toBe(204);
}

/** Sign in as a seed person (key from docs/test-data.md). The cookie applies to the page. */
export async function login(page: Page, key: string): Promise<void> {
  const response = await page.request.post('/api/test/login', { data: { loginKey: key } });
  expect(response.status()).toBe(204);
}

/** Accessibility check (axe) without critical or serious findings. */
export async function checkAccessibility(page: Page): Promise<void> {
  const result = await new AxeBuilder({ page }).analyze();
  const findings = result.violations
    .filter((v) => v.impact === 'critical' || v.impact === 'serious')
    .map((v) => `${v.id}: ${v.help} (${v.nodes.map((n) => n.target.join(' ')).join(', ')})`);
  expect(findings, 'axe findings').toEqual([]);
}

/**
 * Document elements without a role (docs/testability.md, exception to «no CSS selectors»).
 * `html` and `link` do not appear in the accessibility tree, so there is no locator for them
 * via role, label, text or test ID. CSS selectors are allowed only here;
 * nb17-css-selectors.spec.ts checks that the other tests use none.
 */
export function documentRoot(page: Page): Locator {
  return page.locator('html');
}

/** The `<link rel="…">` entry in the document head, e.g. `manifest`. */
export function headLink(page: Page, rel: string): Locator {
  return page.locator(`head link[rel="${rel}"]`);
}
