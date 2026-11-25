import { safeTarget } from './auth.service';

describe('safeTarget', () => {
  it('keeps targets inside the app, with query and fragment', () => {
    expect(safeTarget('/termine/e1')).toBe('/termine/e1');
    expect(safeTarget('/termine?monat=2026-11#top')).toBe('/termine?monat=2026-11#top');
    expect(safeTarget('/')).toBe('/');
  });

  it('falls back to the start page without a target', () => {
    expect(safeTarget(null)).toBe('/');
    expect(safeTarget(undefined)).toBe('/');
    expect(safeTarget('')).toBe('/');
  });

  it('rejects other sites (open redirect)', () => {
    expect(safeTarget('https://evil.example/')).toBe('/');
    expect(safeTarget('//evil.example/')).toBe('/');
    expect(safeTarget('/\\evil.example')).toBe('/');
    expect(safeTarget('javascript:alert(1)')).toBe('/');
    expect(safeTarget('termine')).toBe('/');
  });
});
