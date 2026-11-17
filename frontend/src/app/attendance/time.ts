/**
 * Date and time in the time zone of the domain logic (Europe/Zurich), independent of the browser's time zone.
 * Formats according to docs/ui/navigation.md: «Di 17.11.», «18:30», with the year only if it is not the
 * current year of the backend clock.
 */

const ZONE = 'Europe/Zurich';
const WEEKDAYS = ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa'];
const MONTHS = [
  'Januar',
  'Februar',
  'März',
  'April',
  'Mai',
  'Juni',
  'Juli',
  'August',
  'September',
  'Oktober',
  'November',
  'Dezember',
];

const FORMAT = new Intl.DateTimeFormat('en-GB', {
  timeZone: ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

export interface Parts {
  year: number;
  month: number;
  day: number;
  hour: number;
  minute: number;
}

function partsFromMs(ms: number): Parts {
  const t: Record<string, number> = {};
  for (const p of FORMAT.formatToParts(new Date(ms))) {
    if (p.type !== 'literal') t[p.type] = Number(p.value);
  }
  return {
    year: t['year'],
    month: t['month'],
    day: t['day'],
    hour: t['hour'],
    minute: t['minute'],
  };
}

/** Parts of an instant in Zurich. */
export function parts(iso: string): Parts {
  return partsFromMs(Date.parse(iso));
}

const two = (n: number) => String(n).padStart(2, '0');

/** «Di» */
export function weekday(t: Parts): string {
  return WEEKDAYS[new Date(Date.UTC(t.year, t.month - 1, t.day)).getUTCDay()];
}

/** «Di 17.11.», or «Di 12.05.2026» if not in the year `currentYear`. */
export function eventDate(iso: string, currentYear: number | null): string {
  const t = parts(iso);
  const year = currentYear !== null && t.year !== currentYear ? String(t.year) : '';
  return `${weekday(t)} ${two(t.day)}.${two(t.month)}.${year}`;
}

/** «18:30» */
export function clockTime(iso: string): string {
  const t = parts(iso);
  return `${two(t.hour)}:${two(t.minute)}`;
}

/** «November 2026» */
export function month(iso: string): string {
  const t = parts(iso);
  return `${MONTHS[t.month - 1]} ${t.year}`;
}

/** Key of a month, e.g. «2026-11». */
export function monthKey(iso: string): string {
  const t = parts(iso);
  return `${t.year}-${two(t.month)}`;
}

/** Values for `<input type="date">` and `<input type="time">`: «2026-11-17», «18:30». */
export function inputParts(iso: string): { date: string; time: string } {
  const t = parts(iso);
  return {
    date: `${t.year}-${two(t.month)}-${two(t.day)}`,
    time: `${two(t.hour)}:${two(t.minute)}`,
  };
}

/** Date and time in Zurich as ISO 8601 with offset, e.g. «2026-11-17T18:30:00+01:00». */
export function zurich(date: string, time: string): string {
  const [y, mon, d] = date.split('-').map(Number);
  const [h, min] = time.split(':').map(Number);
  const local = Date.UTC(y, mon - 1, d, h, min);
  const offset = (utc: number) => {
    const t = partsFromMs(utc);
    return (Date.UTC(t.year, t.month - 1, t.day, t.hour, t.minute) - utc) / 60000;
  };
  let minutes = offset(local);
  minutes = offset(local - minutes * 60000);
  const sign = minutes >= 0 ? '+' : '-';
  const amount = Math.abs(minutes);
  return `${date}T${time}:00${sign}${two(Math.floor(amount / 60))}:${two(amount % 60)}`;
}

/** Year of the backend clock (`now` from `GET /api/me`). */
export function yearOf(now: string | undefined): number | null {
  return now ? parts(now).year : null;
}
