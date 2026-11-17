import { HttpErrorResponse } from '@angular/common/http';
import { TEXTS } from './texts';

/** Displayable text for an error from the backend (Problem Details, field `detail`). */
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

/** HTTP status of an error, otherwise 0. */
export function errorStatus(error: unknown): number {
  return error instanceof HttpErrorResponse ? error.status : 0;
}

/** Rule ID of an error from the backend (Problem Details, field `rule`), otherwise null. */
export function errorRule(error: unknown): string | null {
  if (error instanceof HttpErrorResponse) {
    const rule = (error.error as { rule?: unknown } | null)?.rule;
    return typeof rule === 'string' ? rule : null;
  }
  return null;
}

/**
 * Field errors from Problem Details (`fields`), as a map field → message. `fieldMap` translates the
 * API field names into the field names of the form where they differ.
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
