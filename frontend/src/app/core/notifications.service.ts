import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import type { Notification } from '../api/types';

/** Own notifications (BEN) and the number of unread ones for the navigation. */
@Injectable({ providedIn: 'root' })
export class NotificationsService {
  private readonly http = inject(HttpClient);

  /** Number of unread notifications (`notifications-counter`). */
  readonly unread = signal(0);

  /** Reload the counter. If that fails, the previous value stays. */
  async refreshCounter(): Promise<void> {
    try {
      const list = await firstValueFrom(
        this.http.get<Notification[]>('/api/notifications', { params: { unread: true } }),
      );
      this.unread.set(list.length);
    } catch {
      // The counter is a help; the page itself shows errors.
    }
  }

  /** All own notifications, newest first (order of the API). */
  async all(): Promise<Notification[]> {
    const list = await firstValueFrom(this.http.get<Notification[]>('/api/notifications'));
    this.unread.set(list.filter((n) => !n.read).length);
    return list;
  }

  async markRead(id: string): Promise<void> {
    await firstValueFrom(this.http.post<void>(`/api/notifications/${id}/read`, null));
    this.unread.update((n) => Math.max(0, n - 1));
  }
}
