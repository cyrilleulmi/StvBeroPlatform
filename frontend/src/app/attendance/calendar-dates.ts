/**
 * Plain calendar arithmetic on day keys («2026-11-17») for the calendar view (KAL). Days are calendar days in
 * Zurich; the arithmetic runs in UTC so that daylight saving time does not shift a day.
 */

const two = (n: number) => String(n).padStart(2, '0');

function toUtc(key: string): Date {
  const [y, m, d] = key.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d));
}

function toKey(date: Date): string {
  return `${date.getUTCFullYear()}-${two(date.getUTCMonth() + 1)}-${two(date.getUTCDate())}`;
}

export function addDays(key: string, days: number): string {
  const date = toUtc(key);
  date.setUTCDate(date.getUTCDate() + days);
  return toKey(date);
}

/** Monday = 0 … Sunday = 6. */
export function weekdayIndex(key: string): number {
  return (toUtc(key).getUTCDay() + 6) % 7;
}

/** «2026-11» is a valid month key. */
export function isMonthKey(value: string | undefined | null): value is string {
  return !!value && /^\d{4}-(0[1-9]|1[0-2])$/.test(value);
}

export function isDayKey(value: string | undefined | null): value is string {
  return !!value && /^\d{4}-\d{2}-\d{2}$/.test(value) && toKey(toUtc(value)) === value;
}

export function shiftMonth(monthKey: string, months: number): string {
  const [y, m] = monthKey.split('-').map(Number);
  const date = new Date(Date.UTC(y, m - 1 + months, 1));
  return `${date.getUTCFullYear()}-${two(date.getUTCMonth() + 1)}`;
}

/** Weeks of the month grid, Monday to Sunday, with the days of the neighbouring months (at most six weeks). */
export function monthGrid(monthKey: string): string[][] {
  const first = `${monthKey}-01`;
  const start = addDays(first, -weekdayIndex(first));
  const next = `${shiftMonth(monthKey, 1)}-01`;
  const weeks: string[][] = [];
  let day = start;
  while (day < next) {
    const week: string[] = [];
    for (let i = 0; i < 7; i++) {
      week.push(day);
      day = addDays(day, 1);
    }
    weeks.push(week);
  }
  return weeks;
}
