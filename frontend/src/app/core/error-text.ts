import { HttpErrorResponse } from '@angular/common/http';
import { TEXTE } from './texts';

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

/** Regel-ID eines Fehlers vom Backend (Problem Details, Feld `rule`), sonst null. */
export function fehlerregel(fehler: unknown): string | null {
  if (fehler instanceof HttpErrorResponse) {
    const regel = (fehler.error as { rule?: unknown } | null)?.rule;
    return typeof regel === 'string' ? regel : null;
  }
  return null;
}

/**
 * Feldfehler aus Problem Details (`fields`), als Zuordnung Feld → Meldung. Die API nennt die Felder
 * englisch; `zuordnung` übersetzt sie bei Bedarf in die Feldnamen des Formulars.
 */
export function feldfehler(
  fehler: unknown,
  zuordnung: Record<string, string> = {},
): Record<string, string> {
  const ergebnis: Record<string, string> = {};
  if (fehler instanceof HttpErrorResponse) {
    const felder = (fehler.error as { fields?: { field: string; message: string }[] } | null)
      ?.fields;
    for (const f of felder ?? []) {
      ergebnis[zuordnung[f.field] ?? f.field] = f.message;
    }
  }
  return ergebnis;
}
