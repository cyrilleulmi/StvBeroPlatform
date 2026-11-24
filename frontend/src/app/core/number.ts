/** Numbers as written in Switzerland (de-CH): «24.1», «1’204.5». One formatter, built once. */
const ONE_DECIMAL = new Intl.NumberFormat('de-CH', { minimumFractionDigits: 1, maximumFractionDigits: 1 });

/** Always one decimal, e.g. kilometres «190.6» or an average «22.0» (STA-05). */
export function formatDecimal(value: number): string {
  return ONE_DECIMAL.format(value);
}

const WHOLE = new Intl.NumberFormat('de-CH', { maximumFractionDigits: 0 });

/** Whole numbers, e.g. kilometres in a narrow column «381», «1’204». */
export function formatWhole(value: number): string {
  return WHOLE.format(value);
}
