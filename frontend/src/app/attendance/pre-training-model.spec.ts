import { describe, expect, it } from 'vitest';
import { durationText, isDesign, neighbour, preTrainingView } from './pre-training-model';

const practice = (pre: string | null, start = '2026-12-03T20:20:00+01:00', end = '2026-12-03T21:50:00+01:00') => ({
  start,
  end,
  preTraining: pre ? { title: 'Joggen', start: pre } : null,
});

describe('preTrainingView (TER-29, TER-31)', () => {
  it('has nothing to show without a pre-training', () => {
    expect(preTrainingView(practice(null))).toBeNull();
    expect(preTrainingView({ start: '2026-12-03T20:20:00+01:00', end: '2026-12-03T21:50:00+01:00' })).toBeNull();
  });

  it('gives the three times and the two durations', () => {
    expect(preTrainingView(practice('2026-12-03T20:00:00+01:00'))).toEqual({
      title: 'Joggen',
      start: '20:00',
      practiceStart: '20:20',
      end: '21:50',
      leadMinutes: 20,
      practiceMinutes: 90,
      share: 18,
    });
  });

  it('keeps a short pre-training wide enough to read in the time bar', () => {
    expect(preTrainingView(practice('2026-12-03T20:15:00+01:00'))?.share).toBe(16);
  });

  it('never gives a long pre-training more than half of the time bar', () => {
    expect(preTrainingView(practice('2026-12-03T17:00:00+01:00'))?.share).toBe(50);
  });

  it('shows nothing if the pre-training does not start before the practice', () => {
    expect(preTrainingView(practice('2026-12-03T20:20:00+01:00'))).toBeNull();
    expect(preTrainingView(practice('2026-12-03T20:40:00+01:00'))).toBeNull();
  });
});

describe('durationText', () => {
  it('says minutes and hours the plain way', () => {
    expect(durationText(20)).toBe('20 Min.');
    expect(durationText(60)).toBe('1 Std.');
    expect(durationText(90)).toBe('1 Std. 30 Min.');
  });
});

describe('design proposals (E-118)', () => {
  it('knows its keys', () => {
    expect(isDesign('c')).toBe(true);
    expect(isDesign('z')).toBe(false);
    expect(isDesign(null)).toBe(false);
  });

  it('cycles through the proposals and wraps around', () => {
    expect(neighbour('a', 1)).toBe('b');
    expect(neighbour('e', 1)).toBe('a');
    expect(neighbour('a', -1)).toBe('e');
  });
});
