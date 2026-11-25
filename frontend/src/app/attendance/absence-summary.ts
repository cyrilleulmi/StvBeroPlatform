import type { Absence } from '../api/types';
import { date } from '../core/format';
import { TEXTS } from '../core/texts';

/**
 * State line of «Abwesenheiten» in the profile (ABS-01): the absence that runs today, otherwise the next one,
 * otherwise «Keine Abwesenheit eingetragen». `today` is the day of the backend clock («2026-11-25»).
 */
export function absenceSummary(
  absences: readonly Pick<Absence, 'from' | 'to' | 'reason'>[],
  today: string,
): string {
  const s = TEXTS.absences;
  const ahead = absences.filter((a) => a.to >= today).sort((a, b) => a.from.localeCompare(b.from));
  const first = ahead[0];
  if (!first) return s.stateNone;
  const reason = s.reasons[first.reason] ?? first.reason;
  if (first.from <= today) return s.stateNow(reason, date(first.to));
  return s.stateNext(reason, s.period(date(first.from), date(first.to)));
}
