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
      this.http.put<Antwort>(`/api/termine/${terminId}/antworten/${personId ?? 'ich'}`, eingabe),
    );
  }
}

/** Icon pro Antwortstatus (O-16, docs/design-grundlagen.md): Farbe allein trägt keine Information. */
export const ANTWORT_ICON: Record<AntwortStatus, string> = {
  DABEI: 'check',
  NICHT_DABEI: 'x',
  VIELLEICHT: 'circle-help',
  OFFEN: 'circle-dashed',
};

/** Wert für `data-antwort`/Test-IDs in kebab-case, z.B. `nicht-dabei`. */
export function antwortSchluessel(status: AntwortStatus): string {
  return status.toLowerCase().replace('_', '-');
}
