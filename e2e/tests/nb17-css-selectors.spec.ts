import { expect, test } from '@playwright/test';
import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

/**
 * Follow-up fix #17 · CSS selectors in E2E tests (docs/testability.md, conventions for E2E tests).
 * Static check of the test files, needs neither browser nor database.
 * Exception: helpers.ts encapsulates the document elements without role (`html`, `link`).
 */
const FORBIDDEN: { pattern: RegExp; reason: string }[] = [
  { pattern: /\.locator\(/, reason: 'locator() with CSS selector' },
  { pattern: /\.\$\$?\(|\.\$\$?eval\(/, reason: '$()/$$()/$eval() with CSS selector' },
  { pattern: /waitForSelector\(/, reason: 'waitForSelector() with CSS selector' },
  { pattern: /['"`](css|xpath)=/, reason: 'CSS or XPath selector' },
];
const THIS_FILE = 'nb17-css-selectors.spec.ts';
const EXCEPTIONS = new Set(['helpers.ts', THIS_FILE]);

function testFiles(): string[] {
  return readdirSync(__dirname).filter((d) => d.endsWith('.ts') && !EXCEPTIONS.has(d));
}

test('Follow-up fix #17 AK1: The exception for document elements is encapsulated in helpers.ts', () => {
  const helpers = readFileSync(join(__dirname, 'helpers.ts'), 'utf8');
  expect(helpers).toContain('export function documentRoot(');
  expect(helpers).toContain('export function headLink(');
});

test('Follow-up fix #17 AK2: Outside helpers.ts the E2E tests use no CSS selectors', () => {
  const files = testFiles();
  expect(files.length).toBeGreaterThan(0);
  const findings: string[] = [];
  for (const file of files) {
    readFileSync(join(__dirname, file), 'utf8')
      .split('\n')
      .forEach((line, i) => {
        for (const { pattern, reason } of FORBIDDEN) {
          if (pattern.test(line)) findings.push(`${file}:${i + 1}: ${reason}: ${line.trim()}`);
        }
      });
  }
  expect(findings, 'Locators via getByRole/getByLabel/getByText/getByTestId or the helpers from helpers.ts').toEqual([]);
});
