import {
  eventPreTraining,
  preTrainingErrors,
  seriesPreTraining,
  suggestedTime,
  valuesOfEvent,
  valuesOfSeries,
  type PreTrainingForm,
} from './pre-training-form';

const FORM: PreTrainingForm = {
  type: 'PRACTICE',
  date: '2026-12-01',
  start: '20:20',
  preTraining: true,
  preTrainingTitle: ' Joggen ',
  preTrainingTime: '20:00',
};

describe('suggestedTime (TER-32)', () => {
  it('is 15 minutes before the practice', () => {
    expect(suggestedTime('20:20')).toBe('20:05');
    expect(suggestedTime('19:00')).toBe('18:45');
  });

  it('never goes before 00:00', () => {
    expect(suggestedTime('00:10')).toBe('00:00');
    expect(suggestedTime('00:00')).toBe('00:00');
  });

  it('is empty without a valid start', () => {
    expect(suggestedTime('')).toBe('');
    expect(suggestedTime('abc')).toBe('');
  });
});

describe('preTrainingErrors (TER-32)', () => {
  it('accepts a complete pre-training before the practice', () => {
    expect(preTrainingErrors(FORM)).toEqual({});
  });

  it('asks for title and time', () => {
    const e = preTrainingErrors({ ...FORM, preTrainingTitle: '  ', preTrainingTime: '' });
    expect(e.preTrainingTitle).toBe('Bitte angeben, was im Vortraining gemacht wird.');
    expect(e.preTrainingTime).toBe('Bitte eine Zeit angeben.');
  });

  it('refuses a start at or after the practice', () => {
    const message = 'Das Vortraining muss vor dem Training beginnen.';
    expect(preTrainingErrors({ ...FORM, preTrainingTime: '20:20' }).preTrainingTime).toBe(message);
    expect(preTrainingErrors({ ...FORM, preTrainingTime: '21:00' }).preTrainingTime).toBe(message);
  });

  it('checks nothing when it is off or the type is not a practice', () => {
    const empty = { preTrainingTitle: '', preTrainingTime: '' };
    expect(preTrainingErrors({ ...FORM, ...empty, preTraining: false })).toEqual({});
    expect(preTrainingErrors({ ...FORM, ...empty, type: 'OTHER' })).toEqual({});
  });
});

describe('API values (TER-32)', () => {
  it('builds the pre-training of an event on its date', () => {
    expect(eventPreTraining(FORM)).toEqual({ title: 'Joggen', start: '2026-12-01T20:00:00+01:00' });
  });

  it('builds the pre-training of a series with the time of day', () => {
    expect(seriesPreTraining(FORM)).toEqual({ title: 'Joggen', startTime: '20:00' });
  });

  it('is null when off or not a practice', () => {
    expect(eventPreTraining({ ...FORM, preTraining: false })).toBeNull();
    expect(seriesPreTraining({ ...FORM, type: 'OTHER' })).toBeNull();
  });

  it('reads the values back from an event and a series', () => {
    expect(valuesOfEvent({ preTraining: { title: 'Joggen', start: '2026-12-01T20:00:00+01:00' } })).toEqual({
      preTraining: true,
      preTrainingTitle: 'Joggen',
      preTrainingTime: '20:00',
    });
    expect(valuesOfSeries({ preTraining: { title: 'Kraft', startTime: '19:00' } })).toEqual({
      preTraining: true,
      preTrainingTitle: 'Kraft',
      preTrainingTime: '19:00',
    });
    expect(valuesOfEvent({}).preTraining).toBe(false);
    expect(valuesOfSeries({ preTraining: null }).preTraining).toBe(false);
  });
});
