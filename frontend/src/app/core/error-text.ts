import { HttpErrorResponse } from '@angular/common/http';
import { TEXTS } from './texts';

/** Anzeigbarer Text zu einem Fehler vom Backend (Problem Details, Feld `detail`). */
export function errorText(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 403) {
      return TEXTS.common.noPermission;
    }
    const detail = (error.error as { detail?: unknown } | null)?.detail;
    if (typeof detail === 'string' && detail.length > 0) {
      return detail;
    }
  }
  return TEXTS.common.loadError;
}

/** HTTP-Status eines Fehlers, sonst 0. */
export function errorStatus(error: unknown): number {
  return error instanceof HttpErrorResponse ? error.status : 0;
}

/** Regel-ID eines Fehlers vom Backend (Problem Details, Feld `rule`), sonst null. */
export function errorRule(error: unknown): string | null {
  if (error instanceof HttpErrorResponse) {
    const rule = (error.error as { rule?: unknown } | null)?.rule;
    return typeof rule === 'string' ? rule : null;
  }
  return null;
}

/**
 * Feldfehler aus Problem Details (`fields`), als Zuordnung Feld → Meldung. Die API nennt die Felder
 * englisch; `zuordnung` übersetzt sie bei Bedarf in die Feldnamen des Formulars.
 */
export function fieldErrorsOf(
  error: unknown,
  fieldMap: Record<string, string> = {},
): Record<string, string> {
  const outcome: Record<string, string> = {};
  if (error instanceof HttpErrorResponse) {
    const fields = (error.error as { fields?: { field: string; message: string }[] } | null)
      ?.fields;
    for (const f of fields ?? []) {
      outcome[fieldMap[f.field] ?? f.field] = f.message;
    }
  }
  return outcome;
}
