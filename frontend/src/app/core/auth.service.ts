import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';
import type { Me, Session } from '../api/types';

/**
 * `GET /api/me` answers with 409 when no season is set as current (SAI-03).
 * The auth guard then redirects to `/keine-saison`.
 */
export class NoCurrentSeasonError extends Error {
  constructor() {
    super('No current season');
    this.name = 'NoCurrentSeasonError';
  }
}

/** Who is logged in. The source is always the backend (`GET /api/me`). */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  readonly me = signal<Me | null>(null);
  readonly isAdmin = computed(() => this.me()?.admin === true);

  /**
   * Loads the logged-in person. Returns `null` when nobody is logged in, and throws
   * `NoCurrentSeasonError` when no season is set as current (409).
   * First `GET /api/session` (always 200), so that without login no 401 ends up in the console.
   */
  async load(): Promise<Me | null> {
    const session = await firstValueFrom(this.http.get<Session>('/api/session'));
    if (!session.signedIn) {
      this.me.set(null);
      return null;
    }
    try {
      const me = await firstValueFrom(this.http.get<Me>('/api/me'));
      this.me.set(me);
      return me;
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status === 401) {
        this.me.set(null);
        return null;
      }
      if (error instanceof HttpErrorResponse && error.status === 409) {
        this.me.set(null);
        throw new NoCurrentSeasonError();
      }
      throw error;
    }
  }

  async loginAsTestPerson(key: string): Promise<void> {
    await firstValueFrom(this.http.post<void>('/api/test/login', { loginKey: key }));
    try {
      await this.load();
    } catch (error) {
      // Without a current season the login is still valid; the guard shows the notice.
      if (!(error instanceof NoCurrentSeasonError)) {
        throw error;
      }
    }
  }

  async logout(): Promise<void> {
    await firstValueFrom(this.http.post<void>('/api/logout', null));
    this.me.set(null);
  }

  /** Route of the login: /dev-login in test mode, otherwise /anmelden. */
  loginRoute(): string {
    return environment.testMode ? '/dev-login' : '/anmelden';
  }
}

/** Only allow targets inside the app (no open redirect). */
export function safeTarget(target: string | null | undefined): string {
  if (!target || !target.startsWith('/') || target.startsWith('//') || target.startsWith('/\\')) {
    return '/';
  }
  return target;
}
