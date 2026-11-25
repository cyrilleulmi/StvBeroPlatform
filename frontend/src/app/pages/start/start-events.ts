import type { EventSummary } from '../../api/types';
import { parts } from '../../core/time';

/** Which week the practices on the start page are from (TER-25). */
export type StartWeekKind = 'current' | 'next' | 'later';

export interface StartWeek {
  kind: StartWeekKind;
  /** Monday of that week in Zurich, «2026-11-30». */
  monday: string;
}

/** The events of the start page in one list sorted by start, and what the cards need to know about them. */
export interface StartEvents {
  events: EventSummary[];
  /** The week of the practices; `null` without upcoming practices. */
  week: StartWeek | null;
  /** The first practice of the list: it carries the week as its eyebrow. */
  firstPracticeId: string | null;
  /** The next match day, shown whenever there is one (AUF-35). */
  matchDayId: string | null;
}

const DAY = 86_400_000;

/** Monday of the week (Monday to Sunday, Europe/Zurich) of an instant, as «2026-11-16». */
export function mondayOf(iso: string): string {
  const t = parts(iso);
  // Calendar arithmetic on the local date in UTC: no offset, no daylight saving time involved
  const date = Date.UTC(t.year, t.month - 1, t.day);
  const weekday = (new Date(date).getUTCDay() + 6) % 7; // Monday 0 … Sunday 6
  return new Date(date - weekday * DAY).toISOString().slice(0, 10);
}

function weekKind(monday: string, nowMonday: string): StartWeekKind {
  if (monday === nowMonday) return 'current';
  const next = new Date(Date.parse(nowMonday) + 7 * DAY).toISOString().slice(0, 10);
  return monday === next ? 'next' : 'later';
}

/**
 * Events of the start page (TER-25, E-113). `events` are the upcoming events of the own teams in chronological order
 * (`GET /api/events`), `now` is the backend clock. Cancelled events and events that are over stay out.
 *
 * 1. The practices of the week (Monday to Sunday, Europe/Zurich) of the next practice that is not over: this week's
 *    remaining practices, or, when they are all over, those of the next week that has practices.
 * 2. The next match day, whenever it is (AUF-35).
 * 3. Pinned events (TER-26).
 *
 * All in one list sorted by start, each event once.
 */
export function startEvents(events: EventSummary[], now: string): StartEvents {
  const at = Date.parse(now);
  const open = events.filter((e) => e.status !== 'CANCELLED' && Date.parse(e.end) > at);
  const byStart = (a: EventSummary, b: EventSummary) => Date.parse(a.start) - Date.parse(b.start);
  const practices = open.filter((e) => e.type === 'PRACTICE').sort(byStart);
  const firstPractice = practices[0] ?? null;
  const monday = firstPractice ? mondayOf(firstPractice.start) : null;
  const ofWeek = monday ? practices.filter((e) => mondayOf(e.start) === monday) : [];
  const matchDay = open.filter((e) => e.type === 'MATCH_DAY').sort(byStart)[0] ?? null;
  const pinned = open.filter((e) => e.pinned);

  const chosen = new Map<string, EventSummary>();
  for (const e of [...ofWeek, ...(matchDay ? [matchDay] : []), ...pinned]) chosen.set(e.id, e);
  // A stable sort keeps the order of the API for events with the same start
  const list = open.filter((e) => chosen.has(e.id)).sort(byStart);
  return {
    events: list,
    week: monday ? { kind: weekKind(monday, mondayOf(now)), monday } : null,
    firstPracticeId: firstPractice?.id ?? null,
    matchDayId: matchDay?.id ?? null,
  };
}
