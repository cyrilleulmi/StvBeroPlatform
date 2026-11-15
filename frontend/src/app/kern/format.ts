import type { Funktion, PersonKurz, Saison } from '../api/typen';
import { TEXTE } from './texte';

/** «Petra Keller» */
export function name(p: Pick<PersonKurz, 'vorname' | 'nachname'>): string {
  return `${p.vorname} ${p.nachname}`;
}

/** Bezeichnung einer Funktion. Unbekannte Funktionen (PTF-06) als «Mitglied». */
export function funktionText(f: Funktion | string | null | undefined): string {
  if (f === 'SPIELERIN' || f === 'COACH') {
    return TEXTE.funktion[f];
  }
  return TEXTE.funktion.unbekannt;
}

/** ISO-Datum «2026-07-01» als «01.07.2026». */
export function datum(iso: string): string {
  const [j, m, t] = iso.split('-');
  return `${t}.${m}.${j}`;
}

/** Status einer Saison: «aktuell», «Archiv» oder «geplant». */
export function saisonStatus(s: Saison): string {
  if (s.aktuell) return TEXTE.saison.aktuell;
  if (s.archiv) return TEXTE.saison.archiv;
  return TEXTE.saison.geplant;
}
