import type { TeamRole, PersonSummary, Season } from '../api/types';
import { TEXTS } from './texts';

/** «Petra Keller» */
export function name(p: Pick<PersonSummary, 'firstName' | 'lastName'>): string {
  return `${p.firstName} ${p.lastName}`;
}

/** Label of a team role. Unknown team roles (PTF-06) as «Mitglied». */
export function teamRoleText(f: TeamRole | string | null | undefined): string {
  if (f === 'PLAYER' || f === 'COACH') {
    return TEXTS.teamRole[f];
  }
  return TEXTS.teamRole.unknown;
}

/** ISO date «2026-07-01» as «01.07.2026». */
export function date(iso: string): string {
  const [y, m, d] = iso.split('-');
  return `${d}.${m}.${y}`;
}

/** Status of a season: «aktuell», «Archiv» or «geplant». */
export function seasonStatus(s: Season): string {
  if (s.current) return TEXTS.season.current;
  if (s.archived) return TEXTS.season.archive;
  return TEXTS.season.planned;
}
