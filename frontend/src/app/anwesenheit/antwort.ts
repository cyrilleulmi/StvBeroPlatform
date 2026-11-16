import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import type { Antwort, AntwortSetzen, AntwortStatus } from '../api/typen';

/** Antworten setzen (ANM): eigene Antwort und stellvertretend (Coach, ANM-11). */
@Injectable({ providedIn: 'root' })
export class AntwortService {
  private readonly http = inject(HttpClient);

  /** `personId` leer: eigene Antwort (`/antworten/ich`). */
  setzen(terminId: string, eingabe: AntwortSetzen, personId?: string): Promise<Antwort> {
    return firstValueFrom(
      this.http.put<Antwort>(`/api/events/${terminId}/responses/${personId ?? 'me'}`, eingabe),
    );
  }
}

/** Icon pro Antwortstatus (O-16, docs/design-grundlagen.md): Farbe allein trägt keine Information. */
export const ANTWORT_ICON: Record<AntwortStatus, string> = {
  ATTENDING: 'check',
  DECLINED: 'x',
  MAYBE: 'circle-help',
  PENDING: 'circle-dashed',
};

/**
 * Wert für `data-art`/Test-IDs in kebab-case, z.B. `nicht-dabei`. Die Test-IDs bleiben deutsch
 * (docs/ui/navigation.md), auch wenn die API die Antworten englisch nennt (F-10).
 */
const ANTWORT_SCHLUESSEL: Record<AntwortStatus, string> = {
  ATTENDING: 'dabei',
  DECLINED: 'nicht-dabei',
  MAYBE: 'vielleicht',
  PENDING: 'offen',
};

export function antwortSchluessel(status: AntwortStatus): string {
  return ANTWORT_SCHLUESSEL[status];
}
