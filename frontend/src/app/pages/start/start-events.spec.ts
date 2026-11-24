import type { EventSummary } from '../../api/types';
import { PRACTICES_ON_START, startEvents } from './start-events';

const event = (
  id: string,
  type: EventSummary['type'],
  status: EventSummary['status'] = 'PLANNED',
): EventSummary => ({ id, type, status }) as EventSummary;
const ids = (events: EventSummary[]) => events.map((e) => e.id);

describe('startEvents', () => {
  it('shows nothing without events', () => {
    expect(startEvents([])).toEqual({ next: null, practices: [], matchDay: null });
  });

  it('shows nothing if every event is cancelled', () => {
    expect(startEvents([event('a', 'PRACTICE', 'CANCELLED')])).toEqual({
      next: null,
      practices: [],
      matchDay: null,
    });
  });

  it('shows the next practice and one more practice (two practices in total)', () => {
    const r = startEvents([
      event('p1', 'PRACTICE'),
      event('p2', 'PRACTICE'),
      event('p3', 'PRACTICE'),
    ]);
    expect(r.next?.id).toBe('p1');
    expect(ids(r.practices)).toEqual(['p2']);
    expect(PRACTICES_ON_START).toBe(2);
  });

  it('shows two further practices if the next event is a match day', () => {
    const r = startEvents([
      event('m1', 'MATCH_DAY'),
      event('p1', 'PRACTICE'),
      event('p2', 'PRACTICE'),
      event('p3', 'PRACTICE'),
    ]);
    expect(r.next?.id).toBe('m1');
    expect(ids(r.practices)).toEqual(['p1', 'p2']);
    expect(r.matchDay).toBeNull();
  });

  it('adds the next match day if it is not the next event', () => {
    const r = startEvents([
      event('p1', 'PRACTICE'),
      event('p2', 'PRACTICE'),
      event('m1', 'MATCH_DAY'),
      event('m2', 'MATCH_DAY'),
    ]);
    expect(r.matchDay?.id).toBe('m1');
  });

  it('skips cancelled events everywhere', () => {
    const r = startEvents([
      event('c1', 'PRACTICE', 'CANCELLED'),
      event('p1', 'PRACTICE'),
      event('c2', 'PRACTICE', 'CANCELLED'),
      event('p2', 'PRACTICE'),
      event('cm', 'MATCH_DAY', 'CANCELLED'),
      event('m1', 'MATCH_DAY'),
    ]);
    expect(r.next?.id).toBe('p1');
    expect(ids(r.practices)).toEqual(['p2']);
    expect(r.matchDay?.id).toBe('m1');
  });

  it('works with a single event', () => {
    expect(startEvents([event('p1', 'PRACTICE')])).toEqual({
      next: event('p1', 'PRACTICE'),
      practices: [],
      matchDay: null,
    });
  });
});
