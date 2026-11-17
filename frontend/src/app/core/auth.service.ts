import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { umgebung } from '../../environments/environment';
import type { Ich, Sitzung } from '../api/types';

/**
 * `GET /api/me` antwortet mit 409, wenn keine Saison als aktuell festgelegt ist (SAI-03).
 * Der Anmelde-Guard leitet dann auf `/keine-saison` um.
 */
export class KeineAktuelleSaison extends Error {
  constructor() {
    super('Keine aktuelle Saison');
    this.name = 'KeineAktuelleSaison';
  }
}

/** Wer angemeldet ist. Quelle ist immer das Backend (`GET /api/me`). */
@Injectable({ providedIn: 'root' })
export class AnmeldungService {
  private readonly http = inject(HttpClient);

  readonly ich = signal<Ich | null>(null);
  readonly istAdmin = computed(() => this.ich()?.admin === true);

  /**
   * Lädt die angemeldete Person. Liefert `null`, wenn niemand angemeldet ist, und wirft
   * `KeineAktuelleSaison`, wenn keine Saison als aktuell festgelegt ist (409).
   * Zuerst `GET /api/session` (immer 200), damit ohne Anmeldung kein 401 in der Konsole landet.
   */
  async laden(): Promise<Ich | null> {
    const sitzung = await firstValueFrom(this.http.get<Sitzung>('/api/session'));
    if (!sitzung.signedIn) {
      this.ich.set(null);
      return null;
    }
    try {
      const ich = await firstValueFrom(this.http.get<Ich>('/api/me'));
      this.ich.set(ich);
      return ich;
    } catch (fehler) {
      if (fehler instanceof HttpErrorResponse && fehler.status === 401) {
        this.ich.set(null);
        return null;
      }
      if (fehler instanceof HttpErrorResponse && fehler.status === 409) {
        this.ich.set(null);
        throw new KeineAktuelleSaison();
      }
      throw fehler;
    }
  }

  async alsTestpersonAnmelden(schluessel: string): Promise<void> {
    await firstValueFrom(this.http.post<void>('/api/test/login', { loginKey: schluessel }));
    try {
      await this.laden();
    } catch (fehler) {
      // Ohne aktuelle Saison ist die Anmeldung trotzdem gültig; der Guard zeigt den Hinweis.
      if (!(fehler instanceof KeineAktuelleSaison)) {
        throw fehler;
      }
    }
  }

  async abmelden(): Promise<void> {
    await firstValueFrom(this.http.post<void>('/api/logout', null));
    this.ich.set(null);
  }

  /** Route der Anmeldung: im Testmodus /dev-login, sonst /anmelden. */
  anmeldeRoute(): string {
    return umgebung.testmodus ? '/dev-login' : '/anmelden';
  }
}

/** Nur Ziele innerhalb der App zulassen (kein offener Redirect). */
export function sicheresZiel(ziel: string | null | undefined): string {
  if (!ziel || !ziel.startsWith('/') || ziel.startsWith('//') || ziel.startsWith('/\\')) {
    return '/';
  }
  return ziel;
}
