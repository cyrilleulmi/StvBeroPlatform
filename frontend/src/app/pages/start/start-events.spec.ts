import type { EventSummary } from '../../api/types';
import { mondayOf, startEvents } from './start-events';

/** An event in Zurich winter time (+01:00), lasting 90 minutes. */
const event = (
  id: string,
  type: EventSummary['type'],
  start: string,
  extra: Partial<EventSummary> = {},
): EventSummary =>
  ({
    id,
    type,
    status: 'PLANNED',
    pinned: false,
    start: `${start}:00+01:00`,
    end: new Date(Date.parse(`${start}:00+01:00`) + 90 * 60_000).toISOString(),
    ...extra,
  }) as EventSummary;
const ids = (events: EventSummary[]) => events.map((e) => e.id);

// Test data set: Mon 16.11.2026 06:00
const NOW = '2026-11-16T06:00:00+01:00';
const TUE = event('tue', 'PRACTICE', '2026-11-17T18:30');
const THU = event('thu', 'PRACTICE', '2026-11-19T19:00');
const SUN_MATCH = event('sun-match', 'MATCH_DAY', '2026-11-22T09:30');
const NEXT_TUE = event('next-tue', 'PRACTICE', '2026-11-24T18:30');
const NEXT_THU = event('next-thu', 'PRACTICE', '2026-11-26T19:00');
const NEXT_SAT_MATCH = event('next-sat-match', 'MATCH_DAY', '2026-11-28T14:00');
const DINNER = event('dinner', 'OTHER', '2026-12-11T19:00', { pinned: true });

describe('mondayOf', () => {
  it('finds the Monday of the week in Zurich, Monday to Sunday', () => {
    expect(mondayOf('2026-11-16T00:00:00+01:00')).toBe('2026-11-16');
    expect(mondayOf('2026-11-22T23:59:00+01:00')).toBe('2026-11-16');
    expect(mondayOf('2026-11-23T00:00:00+01:00')).toBe('2026-11-23');
  });

  it('uses the time in Zurich, not UTC: Sunday 23:30 in Zurich is still that week', () => {
    // 22:30 UTC on Sunday is 23:30 in Zurich; 23:30 UTC on Sunday is already Monday 00:30 in Zurich
    expect(mondayOf('2026-11-22T22:30:00Z')).toBe('2026-11-16');
    expect(mondayOf('2026-11-22T23:30:00Z')).toBe('2026-11-23');
    // Summer time (+02:00) works the same, without a fixed offset
    expect(mondayOf('2027-04-04T21:30:00Z')).toBe('2027-03-29');
    expect(mondayOf('2027-04-04T22:30:00Z')).toBe('2027-04-05');
  });

  it('crosses months and years', () => {
    expect(mondayOf('2027-01-01T12:00:00+01:00')).toBe('2026-12-28');
  });
});

describe('startEvents', () => {
  const all = [TUE, THU, SUN_MATCH, NEXT_TUE, NEXT_THU, NEXT_SAT_MATCH, DINNER];

  it('shows nothing without events', () => {
    expect(startEvents([], NOW)).toEqual({ events: [], week: null, firstPracticeId: null, matchDayId: null });
  });

  it('shows the practices left this week, the next match day and the pinned event, by start', () => {
    const r = startEvents(all, NOW);
    expect(ids(r.events)).toEqual(['tue', 'thu', 'sun-match', 'dinner']);
    expect(r.week).toEqual({ kind: 'current', monday: '2026-11-16' });
    expect(r.firstPracticeId).toBe('tue');
    expect(r.matchDayId).toBe('sun-match');
  });

  it('leaves out the practices that are over today, keeps one that is still running', () => {
    // Tue 19:00: the practice of Tue 18:30 runs until 20:00
    expect(ids(startEvents(all, '2026-11-17T19:00:00+01:00').events)).toEqual(['tue', 'thu', 'sun-match', 'dinner']);
    // Tue 20:00: it is over
    const r = startEvents(all, '2026-11-17T20:00:00+01:00');
    expect(ids(r.events)).toEqual(['thu', 'sun-match', 'dinner']);
    expect(r.week?.kind).toBe('current');
    expect(r.firstPracticeId).toBe('thu');
  });

  it('shows the practices of next week once all of this week are over', () => {
    const r = startEvents(all, '2026-11-20T08:00:00+01:00');
    expect(ids(r.events)).toEqual(['sun-match', 'next-tue', 'next-thu', 'dinner']);
    expect(r.week).toEqual({ kind: 'next', monday: '2026-11-23' });
  });

  it('takes the week after when next week has no practices (holidays)', () => {
    const later = event('later', 'PRACTICE', '2026-12-01T18:30');
    const laterThu = event('later-thu', 'PRACTICE', '2026-12-03T19:00');
    const r = startEvents([THU, SUN_MATCH, later, laterThu, event('after', 'PRACTICE', '2026-12-08T18:30')],
      '2026-11-20T08:00:00+01:00');
    expect(ids(r.events)).toEqual(['sun-match', 'later', 'later-thu']);
    expect(r.week).toEqual({ kind: 'later', monday: '2026-11-30' });
  });

  it('treats Sunday evening as this week and Monday morning as the next one', () => {
    const sunday = event('sunday', 'PRACTICE', '2026-11-22T20:00');
    const monday = event('monday', 'PRACTICE', '2026-11-23T07:00');
    // Sunday 19:00: the Sunday practice is left this week; Monday's belongs to the next week and stays out
    let r = startEvents([sunday, monday], '2026-11-22T19:00:00+01:00');
    expect(ids(r.events)).toEqual(['sunday']);
    expect(r.week?.kind).toBe('current');
    // Sunday 23:00: Sunday is over, so next week
    r = startEvents([sunday, monday], '2026-11-22T23:00:00+01:00');
    expect(ids(r.events)).toEqual(['monday']);
    expect(r.week).toEqual({ kind: 'next', monday: '2026-11-23' });
    // Monday 00:30: that week has become this week
    r = startEvents([monday], '2026-11-23T00:30:00+01:00');
    expect(r.week).toEqual({ kind: 'current', monday: '2026-11-23' });
  });

  it('shows the next match day also when it is weeks ahead', () => {
    const far = event('far-match', 'MATCH_DAY', '2027-01-17T10:00');
    const r = startEvents([TUE, THU, far, event('farther', 'MATCH_DAY', '2027-01-24T10:00')], NOW);
    expect(ids(r.events)).toEqual(['tue', 'thu', 'far-match']);
    expect(r.matchDayId).toBe('far-match');
  });

  it('shows the next match day before the practices when it comes first', () => {
    const r = startEvents([SUN_MATCH, NEXT_TUE, NEXT_THU], '2026-11-22T06:00:00+01:00');
    expect(ids(r.events)).toEqual(['sun-match', 'next-tue', 'next-thu']);
    expect(r.week?.kind).toBe('next');
  });

  it('works without a match day', () => {
    const r = startEvents([TUE, THU], NOW);
    expect(ids(r.events)).toEqual(['tue', 'thu']);
    expect(r.matchDayId).toBeNull();
  });

  it('shows a pinned event far ahead and drops it once it is over', () => {
    const r = startEvents([TUE, event('cup', 'OTHER', '2027-03-20T09:00', { pinned: true })], NOW);
    expect(ids(r.events)).toEqual(['tue', 'cup']);
    expect(ids(startEvents([DINNER], '2026-12-11T20:31:00+01:00').events)).toEqual([]);
  });

  it('shows a pinned event that is also the next match day or a practice of the week once', () => {
    const pinnedMatch = { ...SUN_MATCH, pinned: true };
    const pinnedThu = { ...THU, pinned: true };
    const r = startEvents([TUE, pinnedThu, pinnedMatch, NEXT_TUE], NOW);
    expect(ids(r.events)).toEqual(['tue', 'thu', 'sun-match']);
    expect(r.matchDayId).toBe('sun-match');
  });

  it('shows a pinned practice of a later week without changing the week', () => {
    const r = startEvents([TUE, { ...NEXT_THU, pinned: true }], NOW);
    expect(ids(r.events)).toEqual(['tue', 'next-thu']);
    expect(r.week?.kind).toBe('current');
    expect(r.firstPracticeId).toBe('tue');
  });

  it('leaves out cancelled events everywhere, also when pinned, and does not count them', () => {
    const cancelled = { status: 'CANCELLED' as const };
    const r = startEvents(
      [
        { ...TUE, ...cancelled },
        { ...THU, ...cancelled },
        { ...SUN_MATCH, ...cancelled },
        NEXT_TUE,
        NEXT_SAT_MATCH,
        { ...DINNER, ...cancelled },
      ],
      NOW,
    );
    // No practice is left this week, so next week; the cancelled match day makes way for the next one
    expect(ids(r.events)).toEqual(['next-tue', 'next-sat-match']);
    expect(r.week?.kind).toBe('next');
    expect(r.matchDayId).toBe('next-sat-match');
  });

  it('shows only the pinned event when there is neither practice nor match day', () => {
    const r = startEvents([DINNER], NOW);
    expect(ids(r.events)).toEqual(['dinner']);
    expect(r.week).toBeNull();
    expect(r.firstPracticeId).toBeNull();
  });

  it('keeps other events that are neither practices of the week nor pinned out', () => {
    const r = startEvents([TUE, event('party', 'OTHER', '2026-11-18T19:00')], NOW);
    expect(ids(r.events)).toEqual(['tue']);
  });
});
