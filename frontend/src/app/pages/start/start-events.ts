import type { EventSummary } from '../../api/types';

/** Number of upcoming practices the start screen shows at least (F-13). */
export const PRACTICES_ON_START = 2;

/**
 * Events of the start screen (F-13): the next own event of any type, then practices until at least
 * {@link PRACTICES_ON_START} practices are visible in total. Cancelled events are skipped and do not
 * count. `events` are the upcoming events of the own teams in chronological order (`GET /api/events`).
 */
export function startEvents(events: EventSummary[]): {
  next: EventSummary | null;
  practices: EventSummary[];
  matchDay: EventSummary | null;
} {
  const planned = events.filter((t) => t.status !== 'CANCELLED');
  const next = planned[0] ?? null;
  if (!next) return { next: null, practices: [], matchDay: null };
  const missing = PRACTICES_ON_START - (next.type === 'PRACTICE' ? 1 : 0);
  const practices = planned
    .slice(1)
    .filter((t) => t.type === 'PRACTICE')
    .slice(0, missing);
  // The next match day always shows (AUF-35); if it is the next event itself, only once
  const matchDay = next.type === 'MATCH_DAY' ? null : (planned.find((t) => t.type === 'MATCH_DAY') ?? null);
  return { next, practices, matchDay };
}
