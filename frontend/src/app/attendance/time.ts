/**
 * Datum und Zeit in der Zeitzone der Fachlogik (Europe/Zurich), unabhängig von der Zeitzone des Browsers.
 * Formate nach docs/oberflaeche/navigation.md: «Di 17.11.», «18:30», mit Jahr nur, wenn es nicht das
 * aktuelle Jahr der Backend-Uhr ist.
 */

const ZONE = 'Europe/Zurich';
const WOCHENTAGE = ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa'];
const MONATE = [
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

export interface Teile {
  jahr: number;
  monat: number;
  tag: number;
  stunde: number;
  minute: number;
}

function teileVonMs(ms: number): Teile {
  const t: Record<string, number> = {};
  for (const p of FORMAT.formatToParts(new Date(ms))) {
    if (p.type !== 'literal') t[p.type] = Number(p.value);
  }
  return {
    jahr: t['year'],
    monat: t['month'],
    tag: t['day'],
    stunde: t['hour'],
    minute: t['minute'],
  };
}

/** Teile eines Zeitpunkts in Zürich. */
export function teile(iso: string): Teile {
  return teileVonMs(Date.parse(iso));
}

const zwei = (n: number) => String(n).padStart(2, '0');

/** «Di» */
export function wochentag(t: Teile): string {
  return WOCHENTAGE[new Date(Date.UTC(t.jahr, t.monat - 1, t.tag)).getUTCDay()];
}

/** «Di 17.11.» bzw. «Di 12.05.2026», wenn nicht im Jahr `aktuellesJahr`. */
export function terminDatum(iso: string, aktuellesJahr: number | null): string {
  const t = teile(iso);
  const jahr = aktuellesJahr !== null && t.jahr !== aktuellesJahr ? String(t.jahr) : '';
  return `${wochentag(t)} ${zwei(t.tag)}.${zwei(t.monat)}.${jahr}`;
}

/** «18:30» */
export function uhrzeit(iso: string): string {
  const t = teile(iso);
  return `${zwei(t.stunde)}:${zwei(t.minute)}`;
}

/** «November 2026» */
export function monat(iso: string): string {
  const t = teile(iso);
  return `${MONATE[t.monat - 1]} ${t.jahr}`;
}

/** Schlüssel eines Monats, z.B. «2026-11». */
export function monatSchluessel(iso: string): string {
  const t = teile(iso);
  return `${t.jahr}-${zwei(t.monat)}`;
}

/** Werte für `<input type="date">` und `<input type="time">`: «2026-11-17», «18:30». */
export function eingabe(iso: string): { datum: string; zeit: string } {
  const t = teile(iso);
  return {
    datum: `${t.jahr}-${zwei(t.monat)}-${zwei(t.tag)}`,
    zeit: `${zwei(t.stunde)}:${zwei(t.minute)}`,
  };
}

/** Datum und Zeit in Zürich als ISO 8601 mit Offset, z.B. «2026-11-17T18:30:00+01:00». */
export function zurich(datum: string, zeit: string): string {
  const [j, mo, d] = datum.split('-').map(Number);
  const [h, mi] = zeit.split(':').map(Number);
  const lokal = Date.UTC(j, mo - 1, d, h, mi);
  const versatz = (utc: number) => {
    const t = teileVonMs(utc);
    return (Date.UTC(t.jahr, t.monat - 1, t.tag, t.stunde, t.minute) - utc) / 60000;
  };
  let minuten = versatz(lokal);
  minuten = versatz(lokal - minuten * 60000);
  const vorzeichen = minuten >= 0 ? '+' : '-';
  const betrag = Math.abs(minuten);
  return `${datum}T${zeit}:00${vorzeichen}${zwei(Math.floor(betrag / 60))}:${zwei(betrag % 60)}`;
}

/** Jahr der Backend-Uhr («jetzt» aus `GET /api/me`). */
export function jahrVon(jetzt: string | undefined): number | null {
  return jetzt ? teile(jetzt).jahr : null;
}
