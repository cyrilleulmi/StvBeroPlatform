import { absenceSummary } from './absence-summary';

describe('absenceSummary (ABS-01, E-109)', () => {
  const today = '2026-11-25';

  it('says when nothing is entered', () => {
    expect(absenceSummary([], today)).toBe('Keine Abwesenheit eingetragen');
  });

  it('ignores absences that are over', () => {
    expect(
      absenceSummary([{ from: '2026-11-01', to: '2026-11-24', reason: 'VACATION' }], today),
    ).toBe('Keine Abwesenheit eingetragen');
  });

  it('shows the absence that runs today, also on its last day', () => {
    expect(
      absenceSummary([{ from: '2026-11-20', to: '2026-11-25', reason: 'INJURY' }], today),
    ).toBe('Abwesend bis 25.11.2026 · Verletzung');
  });

  it('otherwise the next one, the earliest first', () => {
    expect(
      absenceSummary(
        [
          { from: '2027-02-01', to: '2027-02-07', reason: 'OTHER' },
          { from: '2026-12-14', to: '2026-12-20', reason: 'VACATION' },
        ],
        today,
      ),
    ).toBe('Nächste: Ferien 14.12.2026 – 20.12.2026');
  });

  it('shows a single day once', () => {
    expect(absenceSummary([{ from: '2026-12-01', to: '2026-12-01', reason: 'OTHER' }], today)).toBe(
      'Nächste: Anderes 01.12.2026',
    );
  });
});
