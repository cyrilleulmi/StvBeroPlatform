import type { TeamRole, PersonSummary, Season } from '../api/types';
import { TEXTS } from './texts';

/** «Petra Keller» */
export function name(p: Pick<PersonSummary, 'firstName' | 'lastName'>): string {
  return `${p.firstName} ${p.lastName}`;
}

/** Bezeichnung einer Funktion. Unbekannte Funktionen (PTF-06) als «Mitglied». */
export function teamRoleText(f: TeamRole | string | null | undefined): string {
  if (f === 'PLAYER' || f === 'COACH') {
    return TEXTS.teamRole[f];
  }
  return TEXTS.teamRole.unknown;
}

/** ISO-Datum «2026-07-01» als «01.07.2026». */
export function date(iso: string): string {
  const [j, m, t] = iso.split('-');
  return `${t}.${m}.${j}`;
}

/** Status einer Saison: «aktuell», «Archiv» oder «geplant». */
export function seasonStatus(s: Season): string {
  if (s.current) return TEXTS.season.current;
  if (s.archived) return TEXTS.season.archive;
  return TEXTS.season.planned;
}
