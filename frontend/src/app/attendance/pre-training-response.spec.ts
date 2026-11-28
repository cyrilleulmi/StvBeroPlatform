import { preTrainingChoice, preTrainingInput } from './pre-training-response';

describe('response to the pre-training (ANM-14, ANM-15)', () => {
  it('goes into the request only with «dabei» and only once it is answered', () => {
    expect(preTrainingInput('ATTENDING', 'ATTENDING')).toEqual({ preTraining: 'ATTENDING' });
    expect(preTrainingInput('ATTENDING', 'DECLINED')).toEqual({ preTraining: 'DECLINED' });
    // Still open: nothing is sent, the backend leaves it as it is
    expect(preTrainingInput('ATTENDING', null)).toEqual({});
    // Only the pre-training without the practice is not possible: nothing is sent
    expect(preTrainingInput('DECLINED', 'ATTENDING')).toEqual({});
    expect(preTrainingInput('MAYBE', 'ATTENDING')).toEqual({});
  });

  it('knows an answer only as «dabei» or «nicht dabei»', () => {
    expect(preTrainingChoice('ATTENDING')).toBe('ATTENDING');
    expect(preTrainingChoice('DECLINED')).toBe('DECLINED');
    expect(preTrainingChoice('PENDING')).toBeNull();
    expect(preTrainingChoice('MAYBE')).toBeNull();
    expect(preTrainingChoice(undefined)).toBeNull();
  });
});
