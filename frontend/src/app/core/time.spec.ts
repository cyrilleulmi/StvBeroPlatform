import {
  clockTime,
  dayKey,
  dayLabel,
  eventDate,
  inputParts,
  month,
  monthKey,
  monthName,
  parts,
  weekday,
  yearOf,
  zurich,
} from './time';

describe('time (Europe/Zurich)', () => {
  describe('parts', () => {
    it('converts a UTC instant to winter time (UTC+1)', () => {
      expect(parts('2026-11-17T17:30:00Z')).toEqual({
        year: 2026,
        month: 11,
        day: 17,
        hour: 18,
        minute: 30,
      });
    });

    it('converts a UTC instant to summer time (UTC+2)', () => {
      expect(parts('2026-07-15T16:30:00Z')).toEqual({
        year: 2026,
        month: 7,
        day: 15,
        hour: 18,
        minute: 30,
      });
    });

    it('uses the offset in the instant, not the browser time zone', () => {
      expect(parts('2026-11-17T18:30:00+01:00')).toEqual(parts('2026-11-17T17:30:00Z'));
      expect(parts('2026-11-17T12:30:00-05:00')).toEqual(parts('2026-11-17T17:30:00Z'));
    });

    it('rolls over to the next day and year after midnight in Zurich', () => {
      expect(parts('2026-12-31T23:30:00Z')).toEqual({
        year: 2027,
        month: 1,
        day: 1,
        hour: 0,
        minute: 30,
      });
    });

    it('writes midnight as hour 0, never 24', () => {
      expect(parts('2026-11-16T23:00:00Z').hour).toBe(0);
    });

    it('follows the switch to summer time on 2026-03-29 (02:00 -> 03:00)', () => {
      expect(clockTime('2026-03-29T00:59:00Z')).toBe('01:59');
      expect(clockTime('2026-03-29T01:00:00Z')).toBe('03:00');
    });

    it('follows the switch back to winter time on 2026-10-25 (03:00 -> 02:00)', () => {
      expect(clockTime('2026-10-24T23:59:00Z')).toBe('01:59');
      expect(clockTime('2026-10-25T00:30:00Z')).toBe('02:30');
      expect(clockTime('2026-10-25T01:30:00Z')).toBe('02:30');
      expect(clockTime('2026-10-25T02:00:00Z')).toBe('03:00');
    });
  });

  describe('weekday', () => {
    it('gives the German abbreviation', () => {
      const names = ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'];
      names.forEach((expected, i) => {
        const day = 16 + i; // 2026-11-16 is a Monday
        expect(weekday({ year: 2026, month: 11, day, hour: 0, minute: 0 })).toBe(expected);
      });
    });
  });

  describe('eventDate', () => {
    it('omits the year in the current year', () => {
      expect(eventDate('2026-11-17T17:30:00Z', 2026)).toBe('Di 17.11.');
    });

    it('shows the year when it differs from the current year', () => {
      expect(eventDate('2027-05-12T10:00:00Z', 2026)).toBe('Mi 12.05.2027');
    });

    it('shows no year when the current year is unknown', () => {
      expect(eventDate('2027-05-12T10:00:00Z', null)).toBe('Mi 12.05.');
    });

    it('pads day and month to two digits', () => {
      expect(eventDate('2026-01-05T10:00:00Z', 2026)).toBe('Mo 05.01.');
    });

    it('uses the date in Zurich, not the UTC date', () => {
      // 23:30 UTC on Monday is already Tuesday 00:30 in Zurich
      expect(eventDate('2026-11-16T23:30:00Z', 2026)).toBe('Di 17.11.');
    });

    it('compares the year in Zurich: New Year after midnight belongs to the next year', () => {
      expect(eventDate('2026-12-31T23:30:00Z', 2026)).toBe('Fr 01.01.2027');
    });
  });

  describe('clockTime, month, monthKey, dayKey, inputParts', () => {
    it('formats the clock time with leading zeros', () => {
      expect(clockTime('2026-11-17T08:05:00Z')).toBe('09:05');
    });

    it('names the month in German', () => {
      expect(month('2026-03-10T12:00:00Z')).toBe('März 2026');
      expect(month('2026-12-31T23:30:00Z')).toBe('Januar 2027');
    });

    it('builds month and day keys in Zurich', () => {
      expect(monthKey('2026-02-28T23:30:00Z')).toBe('2026-03');
      expect(dayKey('2026-02-28T23:30:00Z')).toBe('2026-03-01');
      expect(dayKey('2026-02-28T22:30:00Z')).toBe('2026-02-28');
    });

    it('splits an instant into the values of date and time inputs', () => {
      expect(inputParts('2026-07-15T16:30:00Z')).toEqual({ date: '2026-07-15', time: '18:30' });
    });
  });

  describe('zurich', () => {
    it('adds +01:00 in winter', () => {
      expect(zurich('2026-11-17', '18:30')).toBe('2026-11-17T18:30:00+01:00');
    });

    it('adds +02:00 in summer', () => {
      expect(zurich('2026-07-15', '18:30')).toBe('2026-07-15T18:30:00+02:00');
    });

    it('is the inverse of inputParts for ordinary times', () => {
      for (const [date, time] of [
        ['2026-01-01', '00:00'],
        ['2026-03-28', '23:59'],
        ['2026-03-29', '03:00'],
        ['2026-10-25', '03:00'],
        ['2026-12-31', '23:59'],
      ]) {
        expect(inputParts(zurich(date, time))).toEqual({ date, time });
      }
    });

    it('uses the right offset on the days of the switch', () => {
      expect(zurich('2026-03-29', '01:59')).toBe('2026-03-29T01:59:00+01:00');
      expect(zurich('2026-03-29', '03:00')).toBe('2026-03-29T03:00:00+02:00');
      expect(zurich('2026-10-25', '01:59')).toBe('2026-10-25T01:59:00+02:00');
      expect(zurich('2026-10-25', '03:00')).toBe('2026-10-25T03:00:00+01:00');
    });

    it('moves a time that does not exist (gap 02:00-03:00) to a valid instant after the gap', () => {
      const iso = zurich('2026-03-29', '02:30');
      expect(clockTime(iso)).toBe('03:30');
    });

    it('resolves an ambiguous time (02:00-03:00 on 2026-10-25) to a valid instant of that night', () => {
      const iso = zurich('2026-10-25', '02:30');
      expect(inputParts(iso)).toEqual({ date: '2026-10-25', time: '02:30' });
    });
  });

  describe('yearOf', () => {
    it('is null without a clock value', () => {
      expect(yearOf(undefined)).toBeNull();
      expect(yearOf('')).toBeNull();
    });

    it('takes the year in Zurich', () => {
      expect(yearOf('2026-11-21T14:03:00+01:00')).toBe(2026);
      expect(yearOf('2026-12-31T23:30:00Z')).toBe(2027);
    });
  });

  describe('monthName, dayLabel', () => {
    it('names a month key', () => {
      expect(monthName('2026-11')).toBe('November 2026');
      expect(monthName('2027-01')).toBe('Januar 2027');
    });

    it('labels a day key, with the year only outside the current year', () => {
      expect(dayLabel('2026-11-17', 2026)).toBe('Di 17.11.');
      expect(dayLabel('2027-02-01', 2026)).toBe('Mo 01.02.2027');
      expect(dayLabel('2027-02-01', null)).toBe('Mo 01.02.');
    });

    it('has the correct weekday for a leap day', () => {
      expect(dayLabel('2028-02-29', 2028)).toBe('Di 29.02.');
    });
  });
});
