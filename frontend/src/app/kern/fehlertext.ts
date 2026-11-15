import { HttpErrorResponse } from '@angular/common/http';
import { TEXTE } from './texte';

/** Anzeigbarer Text zu einem Fehler vom Backend (Problem Details, Feld `detail`). */
export function fehlertext(fehler: unknown): string {
  if (fehler instanceof HttpErrorResponse) {
    if (fehler.status === 403) {
      return TEXTE.allgemein.keineBerechtigung;
    }
    const detail = (fehler.error as { detail?: unknown } | null)?.detail;
    if (typeof detail === 'string' && detail.length > 0) {
      return detail;
    }
  }
  return TEXTE.allgemein.fehlerLaden;
}

/** HTTP-Status eines Fehlers, sonst 0. */
export function fehlerstatus(fehler: unknown): number {
  return fehler instanceof HttpErrorResponse ? fehler.status : 0;
}

/** Feldfehler aus Problem Details (`felder`), als Zuordnung Feld → Meldung. */
export function feldfehler(fehler: unknown): Record<string, string> {
  const ergebnis: Record<string, string> = {};
  if (fehler instanceof HttpErrorResponse) {
    const felder = (fehler.error as { felder?: { feld: string; meldung: string }[] } | null)
      ?.felder;
    for (const f of felder ?? []) {
      ergebnis[f.feld] = f.meldung;
    }
  }
  return ergebnis;
}
