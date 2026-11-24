import type { EventType, PlayerStatistics, StatisticsAverage } from '../api/types';
import { formatDecimal } from '../core/number';
import { TEXTS } from '../core/texts';

/** Event type in the browser URL (`?art=`), German (NF-40): «training», «spieltag». */
export const TYPE_PARAM: Record<string, EventType> = {
  training: 'PRACTICE',
  spieltag: 'MATCH_DAY',
};

/** Sort order in the browser URL (`?sortierung=`): by name (default), by «Zusagequote», by «Präsenzquote». */
export type SortKey = 'name' | 'zusage' | 'praesenz';

export function sortKey(value: string | undefined): SortKey {
  return value === 'zusage' || value === 'praesenz' ? value : 'name';
}

/** API query parameters from the URL parameters of the statistics screens. */
export function apiParams(
  team: string | undefined,
  from: string | undefined,
  to: string | undefined,
  type: string | undefined,
): Record<string, string> {
  const params: Record<string, string> = {};
  if (team) params['team'] = team;
  if (from) params['from'] = from;
  if (to) params['to'] = to;
  // Only own keys: «?art=constructor» must not find a property of Object.prototype
  const apiType = type && Object.hasOwn(TYPE_PARAM, type) ? TYPE_PARAM[type] : undefined;
  if (apiType) params['type'] = apiType;
  return params;
}

/**
 * Rows of a group sorted (STA-05): by name keeps the API order (last name, first name); by a rate the
 * highest first, players without a rate last, ties by name.
 */
export function sortRows(rows: readonly PlayerStatistics[], key: SortKey): PlayerStatistics[] {
  if (key === 'name') return [...rows];
  const rate = (p: PlayerStatistics) => (key === 'zusage' ? p.attendingRate : p.attendanceRate);
  return rows
    .map((p, index) => ({ p, index }))
    .sort((a, b) => {
      const ra = rate(a.p) ?? -1;
      const rb = rate(b.p) ?? -1;
      return rb - ra || a.index - b.index;
    })
    .map((x) => x.p);
}

/** «8 %», without a base «–». */
export function percent(value: number | null | undefined): string {
  return value === null || value === undefined
    ? TEXTS.statistics.noRate
    : TEXTS.statistics.percent(value);
}

/** Average always with one decimal as in Switzerland, e.g. «24.1», «22.0» (STA-05). */
export function decimal(value: number): string {
  return formatDecimal(value);
}

/** Columns of the table in display order (STA-03, STA-04). */
export const COLUMNS = [
  'events',
  'attending',
  'declined',
  'maybe',
  'pending',
  'attendingRate',
  'lateCancellations',
  'present',
  'absent',
  'excused',
  'attendanceRate',
] as const;

export type Column = (typeof COLUMNS)[number];

/** Value of a column for a player. */
export function playerValue(p: PlayerStatistics, c: Column): string {
  if (c === 'attendingRate' || c === 'attendanceRate') return percent(p[c]);
  return String(p[c]);
}

/** Value of a column for the team average. */
export function averageValue(a: StatisticsAverage, c: Column): string {
  if (c === 'attendingRate' || c === 'attendanceRate') return percent(a[c]);
  return decimal(a[c]);
}
