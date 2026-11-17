import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import type { EventResponse, ResponseInput, ResponseStatus } from '../api/types';

/** Set responses (ANM): own response and on behalf of others (coach, ANM-11). */
@Injectable({ providedIn: 'root' })
export class ResponseService {
  private readonly http = inject(HttpClient);

  /** Empty `personId`: own response (`/responses/me`). */
  set(eventId: string, input: ResponseInput, personId?: string): Promise<EventResponse> {
    return firstValueFrom(
      this.http.put<EventResponse>(`/api/events/${eventId}/responses/${personId ?? 'me'}`, input),
    );
  }
}

/** Icon per response status (O-16, docs/design-principles.md): color alone carries no information. */
export const RESPONSE_ICON: Record<ResponseStatus, string> = {
  ATTENDING: 'check',
  DECLINED: 'x',
  MAYBE: 'circle-help',
  PENDING: 'circle-dashed',
};

/**
 * Value for `data-kind` and test IDs: the response status in lowercase, e.g. `declined` (F-11).
 */
const RESPONSE_KEY: Record<ResponseStatus, string> = {
  ATTENDING: 'attending',
  DECLINED: 'declined',
  MAYBE: 'maybe',
  PENDING: 'pending',
};

export function responseKey(status: ResponseStatus): string {
  return RESPONSE_KEY[status];
}
