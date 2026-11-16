import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import type { Benachrichtigung } from '../api/typen';

/** Eigene Benachrichtigungen (BEN) und die Anzahl ungelesener für die Navigation. */
@Injectable({ providedIn: 'root' })
export class BenachrichtigungenService {
  private readonly http = inject(HttpClient);

  /** Anzahl ungelesener Benachrichtigungen (`benachrichtigungen-zaehler`). */
  readonly ungelesen = signal(0);

  /** Zähler neu laden. Klappt es nicht, bleibt der bisherige Wert stehen. */
  async zaehlerAktualisieren(): Promise<void> {
    try {
      const liste = await firstValueFrom(
        this.http.get<Benachrichtigung[]>('/api/benachrichtigungen', { params: { ungelesen: true } }),
      );
      this.ungelesen.set(liste.length);
    } catch {
      // Der Zähler ist eine Hilfe; die Seite selbst zeigt Fehler an.
    }
  }

  /** Alle eigenen, neueste zuerst (Reihenfolge der API). */
  async alle(): Promise<Benachrichtigung[]> {
    const liste = await firstValueFrom(this.http.get<Benachrichtigung[]>('/api/benachrichtigungen'));
    this.ungelesen.set(liste.filter((b) => !b.gelesen).length);
    return liste;
  }

  async gelesen(id: string): Promise<void> {
    await firstValueFrom(this.http.post<void>(`/api/benachrichtigungen/${id}/gelesen`, null));
    this.ungelesen.update((n) => Math.max(0, n - 1));
  }
}
