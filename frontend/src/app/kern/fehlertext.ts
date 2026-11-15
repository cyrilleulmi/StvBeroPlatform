import { HttpErrorResponse } from '@angular/common/http';
import { TEXTE } from './texte';

/** Anzeigbarer Text zu einem Fehler vom Backend (Problem Details, Feld `detail`). */
export function fehlertext(fehler: unknown): string {
  if (fehler instanceof HttpErrorResponse) {
    const detail = (fehler.error as { detail?: unknown } | null)?.detail;
    if (typeof detail === 'string' && detail.length > 0) {
      return detail;
    }
  }
  return TEXTE.allgemein.fehlerLaden;
}
