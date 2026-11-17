/**
 * Datum und Zeit in der Zeitzone der Fachlogik (Europe/Zurich), unabhängig von der Zeitzone des Browsers.
 * Formate nach docs/oberflaeche/navigation.md: «Di 17.11.», «18:30», mit Jahr nur, wenn es nicht das
 * aktuelle Jahr der Backend-Uhr ist.
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

/** Teile eines Zeitpunkts in Zürich. */
export function parts(iso: string): Parts {
  return partsFromMs(Date.parse(iso));
}

const two = (n: number) => String(n).padStart(2, '0');

/** «Di» */
export function weekday(t: Parts): string {
  return WEEKDAYS[new Date(Date.UTC(t.year, t.month - 1, t.day)).getUTCDay()];
}

/** «Di 17.11.» bzw. «Di 12.05.2026», wenn nicht im Jahr `aktuellesJahr`. */
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

/** Schlüssel eines Monats, z.B. «2026-11». */
export function monthKey(iso: string): string {
  const t = parts(iso);
  return `${t.year}-${two(t.month)}`;
}

/** Werte für `<input type="date">` und `<input type="time">`: «2026-11-17», «18:30». */
export function inputParts(iso: string): { date: string; time: string } {
  const t = parts(iso);
  return {
    date: `${t.year}-${two(t.month)}-${two(t.day)}`,
    time: `${two(t.hour)}:${two(t.minute)}`,
  };
}

/** Datum und Zeit in Zürich als ISO 8601 mit Offset, z.B. «2026-11-17T18:30:00+01:00». */
export function zurich(date: string, time: string): string {
  const [j, mo, d] = date.split('-').map(Number);
  const [h, mi] = time.split(':').map(Number);
  const local = Date.UTC(j, mo - 1, d, h, mi);
  const offset = (utc: number) => {
    const t = partsFromMs(utc);
    return (Date.UTC(t.year, t.month - 1, t.day, t.hour, t.minute) - utc) / 60000;
  };
  let minuten = offset(local);
  minuten = offset(local - minuten * 60000);
  const sign = minuten >= 0 ? '+' : '-';
  const amount = Math.abs(minuten);
  return `${date}T${time}:00${sign}${two(Math.floor(amount / 60))}:${two(amount % 60)}`;
}

/** Jahr der Backend-Uhr («jetzt» aus `GET /api/me`). */
export function yearOf(now: string | undefined): number | null {
  return now ? parts(now).year : null;
}
