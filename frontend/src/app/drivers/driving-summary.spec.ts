import { drivingSummary } from './driving-summary';

describe('drivingSummary (DRV-18, E-109)', () => {
  it('names the places when the person can drive', () => {
    expect(drivingSummary({ canDrive: true, carSeats: 5 })).toBe('Kann fahren · 5 Plätze');
  });

  it('says so when she does not drive', () => {
    expect(drivingSummary({ canDrive: false, carSeats: 5 })).toBe('Fährt nicht');
  });
});
