import { formatDecimal as decimal, formatWhole as whole } from './number';

// The de-CH grouping separator is ’ (U+2019) in current CLDR data, but ' (U+0027) in the ICU data of some
// Node versions. Compare with one spelling so that the test does not depend on the ICU version of the runner.
const apostrophe = (text: string) => text.replace(/'/g, '’');
const formatDecimal = (value: number) => apostrophe(decimal(value));
const formatWhole = (value: number) => apostrophe(whole(value));

describe('number (de-CH)', () => {
  describe('formatDecimal', () => {
    it('always shows one decimal', () => {
      expect(formatDecimal(22)).toBe('22.0');
      expect(formatDecimal(24.1)).toBe('24.1');
      expect(formatDecimal(0)).toBe('0.0');
    });

    it('rounds to one decimal', () => {
      expect(formatDecimal(190.64)).toBe('190.6');
      expect(formatDecimal(190.66)).toBe('190.7');
    });

    it('groups thousands with the Swiss apostrophe', () => {
      expect(formatDecimal(1204.5)).toBe('1’204.5');
      expect(formatDecimal(1234567.25)).toBe('1’234’567.3');
    });
  });

  describe('formatWhole', () => {
    it('shows no decimals', () => {
      expect(formatWhole(381)).toBe('381');
      expect(formatWhole(0)).toBe('0');
    });

    it('rounds to whole numbers', () => {
      expect(formatWhole(380.4)).toBe('380');
      expect(formatWhole(380.6)).toBe('381');
    });

    it('groups thousands with the Swiss apostrophe', () => {
      expect(formatWhole(1204)).toBe('1’204');
    });
  });
});
