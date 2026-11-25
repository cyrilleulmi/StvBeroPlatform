import {
  addDays,
  isDayKey,
  isMonthKey,
  monthGrid,
  shiftMonth,
  weekdayIndex,
} from './calendar-dates';

describe('calendar-dates', () => {
  describe('addDays', () => {
    it('adds and subtracts days across month and year boundaries', () => {
      expect(addDays('2026-11-30', 1)).toBe('2026-12-01');
      expect(addDays('2026-12-31', 1)).toBe('2027-01-01');
      expect(addDays('2027-01-01', -1)).toBe('2026-12-31');
      expect(addDays('2026-03-01', -1)).toBe('2026-02-28');
    });

    it('knows leap years', () => {
      expect(addDays('2028-02-28', 1)).toBe('2028-02-29');
      expect(addDays('2027-02-28', 1)).toBe('2027-03-01');
      expect(addDays('2100-02-28', 1)).toBe('2100-03-01');
    });

    it('is not shifted by daylight saving time', () => {
      expect(addDays('2026-03-28', 1)).toBe('2026-03-29');
      expect(addDays('2026-03-29', 1)).toBe('2026-03-30');
      expect(addDays('2026-10-24', 1)).toBe('2026-10-25');
      expect(addDays('2026-10-25', 1)).toBe('2026-10-26');
    });

    it('handles zero and large offsets', () => {
      expect(addDays('2026-11-17', 0)).toBe('2026-11-17');
      expect(addDays('2026-01-01', 365)).toBe('2027-01-01');
      expect(addDays('2026-01-01', -365)).toBe('2025-01-01');
    });
  });

  describe('weekdayIndex', () => {
    it('counts Monday as 0 and Sunday as 6', () => {
      expect(weekdayIndex('2026-11-16')).toBe(0);
      expect(weekdayIndex('2026-11-17')).toBe(1);
      expect(weekdayIndex('2026-11-22')).toBe(6);
    });
  });

  describe('isMonthKey', () => {
    it('accepts valid month keys', () => {
      expect(isMonthKey('2026-11')).toBe(true);
      expect(isMonthKey('2026-01')).toBe(true);
      expect(isMonthKey('2026-12')).toBe(true);
    });

    it('rejects everything else', () => {
      for (const v of [
        '2026-00',
        '2026-13',
        '2026-1',
        '26-11',
        '2026-11-01',
        '',
        ' 2026-11',
        null,
        undefined,
      ]) {
        expect(isMonthKey(v)).toBe(false);
      }
    });
  });

  describe('isDayKey', () => {
    it('accepts real calendar days', () => {
      expect(isDayKey('2026-11-17')).toBe(true);
      expect(isDayKey('2028-02-29')).toBe(true);
    });

    it('rejects days that do not exist', () => {
      expect(isDayKey('2027-02-29')).toBe(false);
      expect(isDayKey('2026-02-30')).toBe(false);
      expect(isDayKey('2026-04-31')).toBe(false);
      expect(isDayKey('2026-13-01')).toBe(false);
      expect(isDayKey('2026-00-10')).toBe(false);
      expect(isDayKey('2026-11-00')).toBe(false);
    });

    it('rejects malformed values', () => {
      for (const v of ['2026-1-1', '2026-11', 'abcd-ef-gh', '', null, undefined]) {
        expect(isDayKey(v)).toBe(false);
      }
    });
  });

  describe('shiftMonth', () => {
    it('moves forward and backward across years', () => {
      expect(shiftMonth('2026-11', 1)).toBe('2026-12');
      expect(shiftMonth('2026-12', 1)).toBe('2027-01');
      expect(shiftMonth('2026-01', -1)).toBe('2025-12');
    });

    it('handles zero, a full year and more than a year', () => {
      expect(shiftMonth('2026-05', 0)).toBe('2026-05');
      expect(shiftMonth('2026-05', 12)).toBe('2027-05');
      expect(shiftMonth('2026-05', -13)).toBe('2025-04');
    });
  });

  describe('monthGrid', () => {
    it('starts on a Monday and ends on a Sunday with 7 days per week', () => {
      for (const month of [
        '2026-01',
        '2026-02',
        '2026-03',
        '2026-10',
        '2026-11',
        '2027-02',
        '2028-02',
      ]) {
        const grid = monthGrid(month);
        expect(grid.every((week) => week.length === 7)).toBe(true);
        expect(weekdayIndex(grid[0][0])).toBe(0);
        expect(weekdayIndex(grid[grid.length - 1][6])).toBe(6);
      }
    });

    it('contains every day of the month exactly once, in order and without gaps', () => {
      const days = monthGrid('2026-10').flat();
      for (let i = 1; i < days.length; i++) expect(days[i]).toBe(addDays(days[i - 1], 1));
      expect(days.filter((d) => d.startsWith('2026-10-'))).toHaveLength(31);
    });

    it('fills the first week with days of the previous month (November 2026 starts on a Sunday)', () => {
      const grid = monthGrid('2026-11');
      expect(grid[0]).toEqual([
        '2026-10-26',
        '2026-10-27',
        '2026-10-28',
        '2026-10-29',
        '2026-10-30',
        '2026-10-31',
        '2026-11-01',
      ]);
      expect(grid).toHaveLength(6);
      expect(grid[5][0]).toBe('2026-11-30');
    });

    it('has exactly four weeks if February starts on a Monday and has 28 days (2027)', () => {
      const grid = monthGrid('2027-02');
      expect(grid).toHaveLength(4);
      expect(grid[0][0]).toBe('2027-02-01');
      expect(grid[3][6]).toBe('2027-02-28');
    });

    it('has no more than six weeks', () => {
      for (let i = 0; i < 48; i++)
        expect(monthGrid(shiftMonth('2026-01', i)).length).toBeLessThanOrEqual(6);
    });

    it('does not add an empty trailing week when the month ends on a Sunday', () => {
      // 2026-05-31 is a Sunday
      const grid = monthGrid('2026-05');
      expect(grid[grid.length - 1][6]).toBe('2026-05-31');
    });
  });
});
