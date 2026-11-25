import type { PlayerStatistics, StatisticsAverage } from '../api/types';
import {
  apiParams,
  averageValue,
  COLUMNS,
  decimal,
  percent,
  playerValue,
  sortKey,
  sortRows,
} from './statistics-common';

const player = (id: string, over: Partial<PlayerStatistics> = {}): PlayerStatistics => ({
  person: { id, firstName: id, lastName: 'X' },
  joinedOn: '2026-07-01',
  events: 10,
  attending: 8,
  declined: 1,
  maybe: 1,
  pending: 0,
  attendingRate: 80,
  lateCancellations: 0,
  attendanceEvents: 8,
  present: 6,
  absent: 1,
  excused: 1,
  attendanceRate: 75,
  ...over,
});

const ids = (rows: PlayerStatistics[]) => rows.map((r) => r.person.id);

describe('statistics-common', () => {
  describe('sortKey', () => {
    it('accepts the two rates and defaults to name', () => {
      expect(sortKey('zusage')).toBe('zusage');
      expect(sortKey('praesenz')).toBe('praesenz');
      expect(sortKey('name')).toBe('name');
      expect(sortKey(undefined)).toBe('name');
      expect(sortKey('')).toBe('name');
      expect(sortKey('ZUSAGE')).toBe('name');
      expect(sortKey('events')).toBe('name');
    });
  });

  describe('apiParams', () => {
    it('is empty without filters', () => {
      expect(apiParams(undefined, undefined, undefined, undefined)).toEqual({});
    });

    it('passes team, period and the translated type', () => {
      expect(apiParams('t1', '2026-07-01', '2026-12-31', 'training')).toEqual({
        team: 't1',
        from: '2026-07-01',
        to: '2026-12-31',
        type: 'PRACTICE',
      });
      expect(apiParams(undefined, undefined, undefined, 'spieltag')).toEqual({ type: 'MATCH_DAY' });
    });

    it('drops unknown types and empty values', () => {
      expect(apiParams('', '', '', 'turnier')).toEqual({});
    });

    // TYPE_PARAM is a plain object: `?art=toString` or `?art=constructor` must not find a property of
    // Object.prototype and put a function into the API parameters.
    it('ignores type names that exist on Object.prototype', () => {
      expect(apiParams(undefined, undefined, undefined, 'toString')).toEqual({});
      expect(apiParams(undefined, undefined, undefined, 'constructor')).toEqual({});
    });
  });

  describe('sortRows', () => {
    const rows = [
      player('anna', { attendingRate: 50, attendanceRate: 90 }),
      player('berta', { attendingRate: 100, attendanceRate: null }),
      player('carla', { attendingRate: null, attendanceRate: 90 }),
      player('dora', { attendingRate: 50, attendanceRate: 100 }),
    ];

    it('keeps the API order by name, as a copy', () => {
      const sorted = sortRows(rows, 'name');
      expect(ids(sorted)).toEqual(['anna', 'berta', 'carla', 'dora']);
      expect(sorted).not.toBe(rows);
    });

    it('sorts by the highest «Zusagequote» first, players without a rate last, ties keep the name order', () => {
      expect(ids(sortRows(rows, 'zusage'))).toEqual(['berta', 'anna', 'dora', 'carla']);
    });

    it('sorts by «Präsenzquote» with the same rules', () => {
      expect(ids(sortRows(rows, 'praesenz'))).toEqual(['dora', 'anna', 'carla', 'berta']);
    });

    it('ranks a rate of 0 above no rate', () => {
      const r = [player('none', { attendingRate: null }), player('zero', { attendingRate: 0 })];
      expect(ids(sortRows(r, 'zusage'))).toEqual(['zero', 'none']);
    });

    it('does not change the input and handles an empty list', () => {
      const copy = [...rows];
      sortRows(rows, 'zusage');
      expect(rows).toEqual(copy);
      expect(sortRows([], 'zusage')).toEqual([]);
    });
  });

  describe('percent and decimal', () => {
    it('writes a percentage with a non-breaking space', () => {
      expect(percent(8)).toBe('8 %');
      expect(percent(0)).toBe('0 %');
    });

    it('writes «–» without a base', () => {
      expect(percent(null)).toBe('–');
      expect(percent(undefined)).toBe('–');
    });

    it('writes averages with one decimal', () => {
      expect(decimal(22)).toBe('22.0');
      expect(decimal(24.1)).toBe('24.1');
    });
  });

  describe('table values', () => {
    const average: StatisticsAverage = {
      events: 12,
      attending: 9.5,
      declined: 1,
      maybe: 0.5,
      pending: 1,
      attendingRate: 79,
      lateCancellations: 0.25,
      present: 8,
      absent: 1,
      excused: 0,
      attendanceRate: null,
    };

    it('lists the columns in display order', () => {
      expect(COLUMNS[0]).toBe('events');
      expect(COLUMNS[COLUMNS.length - 1]).toBe('attendanceRate');
      expect(new Set(COLUMNS).size).toBe(COLUMNS.length);
    });

    it('shows players’ counts as whole numbers and rates as percentages', () => {
      const p = player('anna', { attendingRate: 80, attendanceRate: null });
      expect(playerValue(p, 'events')).toBe('10');
      expect(playerValue(p, 'lateCancellations')).toBe('0');
      expect(playerValue(p, 'attendingRate')).toBe('80 %');
      expect(playerValue(p, 'attendanceRate')).toBe('–');
    });

    it('shows the average of counts with one decimal and of rates as percentages', () => {
      expect(averageValue(average, 'events')).toBe('12.0');
      expect(averageValue(average, 'attending')).toBe('9.5');
      expect(averageValue(average, 'lateCancellations')).toBe('0.3');
      expect(averageValue(average, 'attendingRate')).toBe('79 %');
      expect(averageValue(average, 'attendanceRate')).toBe('–');
    });

    it('can render every column for a player and an average', () => {
      for (const c of COLUMNS) {
        expect(playerValue(player('a'), c)).not.toBe('');
        expect(averageValue(average, c)).not.toBe('');
      }
    });
  });
});
