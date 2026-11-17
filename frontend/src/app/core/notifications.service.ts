import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import type { Notification } from '../api/types';

/** Eigene Benachrichtigungen (BEN) und die Anzahl ungelesener für die Navigation. */
@Injectable({ providedIn: 'root' })
export class NotificationsService {
  private readonly http = inject(HttpClient);

  /** Anzahl ungelesener Benachrichtigungen (`benachrichtigungen-zaehler`). */
  readonly unread = signal(0);

  /** Zähler neu laden. Klappt es nicht, bleibt der bisherige Wert stehen. */
  async refreshCounter(): Promise<void> {
    try {
      const list = await firstValueFrom(
        this.http.get<Notification[]>('/api/notifications', { params: { unread: true } }),
      );
      this.unread.set(list.length);
    } catch {
      // Der Zähler ist eine Hilfe; die Seite selbst zeigt Fehler an.
    }
  }

  /** Alle eigenen, neueste zuerst (Reihenfolge der API). */
  async all(): Promise<Notification[]> {
    const list = await firstValueFrom(this.http.get<Notification[]>('/api/notifications'));
    this.unread.set(list.filter((b) => !b.read).length);
    return list;
  }

  async markRead(id: string): Promise<void> {
    await firstValueFrom(this.http.post<void>(`/api/notifications/${id}/read`, null));
    this.unread.update((n) => Math.max(0, n - 1));
  }
}
