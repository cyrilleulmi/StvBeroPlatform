import { expect, test } from '@playwright/test';
import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

/**
 * Nachbesserung #17 · CSS-Selektoren in E2E-Tests (docs/testbarkeit.md, Konventionen für E2E-Tests).
 * Statische Prüfung der Testdateien, braucht weder Browser noch Datenbank.
 * Ausnahme: hilfen.ts kapselt die Dokument-Elemente ohne Rolle (`html`, `link`).
 */
const VERBOTEN: { muster: RegExp; grund: string }[] = [
  { muster: /\.locator\(/, grund: 'locator() mit CSS-Selektor' },
  { muster: /\.\$\$?\(|\.\$\$?eval\(/, grund: '$()/$$()/$eval() mit CSS-Selektor' },
  { muster: /waitForSelector\(/, grund: 'waitForSelector() mit CSS-Selektor' },
  { muster: /['"`](css|xpath)=/, grund: 'CSS- oder XPath-Selektor' },
];
const DIESE_DATEI = 'nb17-keine-css-selektoren.spec.ts';
const AUSNAHMEN = new Set(['hilfen.ts', DIESE_DATEI]);

function testdateien(): string[] {
  return readdirSync(__dirname).filter((d) => d.endsWith('.ts') && !AUSNAHMEN.has(d));
}

test('Nachbesserung #17 AK1: Die Ausnahme für Dokument-Elemente ist in hilfen.ts gekapselt', () => {
  const hilfen = readFileSync(join(__dirname, 'hilfen.ts'), 'utf8');
  expect(hilfen).toContain('export function dokumentWurzel(');
  expect(hilfen).toContain('export function kopfLink(');
});

test('Nachbesserung #17 AK2: Ausserhalb von hilfen.ts verwenden die E2E-Tests keine CSS-Selektoren', () => {
  const dateien = testdateien();
  expect(dateien.length).toBeGreaterThan(0);
  const funde: string[] = [];
  for (const datei of dateien) {
    readFileSync(join(__dirname, datei), 'utf8')
      .split('\n')
      .forEach((zeile, i) => {
        for (const { muster, grund } of VERBOTEN) {
          if (muster.test(zeile)) funde.push(`${datei}:${i + 1}: ${grund}: ${zeile.trim()}`);
        }
      });
  }
  expect(funde, 'Locators über getByRole/getByLabel/getByText/getByTestId oder die Hilfen aus hilfen.ts').toEqual([]);
});
