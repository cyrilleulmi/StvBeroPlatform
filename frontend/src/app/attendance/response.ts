import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import type { EventResponse, ResponseInput, ResponseStatus } from '../api/types';

/** Antworten setzen (ANM): eigene Antwort und stellvertretend (Coach, ANM-11). */
@Injectable({ providedIn: 'root' })
export class ResponseService {
  private readonly http = inject(HttpClient);

  /** `personId` leer: eigene Antwort (`/antworten/ich`). */
  set(eventId: string, input: ResponseInput, personId?: string): Promise<EventResponse> {
    return firstValueFrom(
      this.http.put<EventResponse>(`/api/events/${eventId}/responses/${personId ?? 'me'}`, input),
    );
  }
}

/** Icon pro Antwortstatus (O-16, docs/design-grundlagen.md): Farbe allein trägt keine Information. */
export const RESPONSE_ICON: Record<ResponseStatus, string> = {
  ATTENDING: 'check',
  DECLINED: 'x',
  MAYBE: 'circle-help',
  PENDING: 'circle-dashed',
};

/**
 * Wert für `data-art`/Test-IDs in kebab-case, z.B. `nicht-dabei`. Die Test-IDs bleiben deutsch
 * (docs/ui/navigation.md), auch wenn die API die Antworten englisch nennt (F-10).
 */
const RESPONSE_KEY: Record<ResponseStatus, string> = {
  ATTENDING: 'attending',
  DECLINED: 'nicht-dabei',
  MAYBE: 'vielleicht',
  PENDING: 'offen',
};

export function responseKey(status: ResponseStatus): string {
  return RESPONSE_KEY[status];
}
