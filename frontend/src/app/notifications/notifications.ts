import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { yearOf, eventDate, clockTime } from '../core/time';
import type { Notification } from '../api/types';
import { AuthService } from '../core/auth.service';
import { NotificationsService } from '../core/notifications.service';
import { errorText } from '../core/error-text';
import { Icon } from '../core/icon';
import { TEXTS } from '../core/texts';

/**
 * Own notifications, newest first (BEN). Unread ones are marked with «Neu». A click opens the
 * event and marks the notification as read (BEN-12).
 */
@Component({
  selector: 'app-notifications',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  template: `
    <h1>{{ texts.title }}</h1>
    @if (error(); as message) {
      <p class="message-error" role="alert">{{ message }}</p>
    }
    @if (feedback(); as message) {
      <p class="message-success" role="status">{{ message }}</p>
    }
    @if (unreadOnes() > 0) {
      <div>
        <button type="button" class="button button-secondary" [disabled]="busy()" (click)="markAllRead()">
          {{ texts.markAllRead }}
        </button>
      </div>
    }
    <section class="card" aria-labelledby="notifications-list" [attr.aria-busy]="loading() ? 'true' : null">
      <h2 id="notifications-list" class="visually-hidden">{{ texts.title }}</h2>
      @if (loading()) {
        <p class="empty">{{ common.loading }}</p>
      } @else if (list().length === 0) {
        <p class="empty">{{ texts.empty }}</p>
      } @else {
        <ul class="list" [attr.aria-label]="texts.title">
          @for (b of list(); track b.id) {
            <li
              [attr.data-testid]="'notification-' + b.id"
              [attr.data-read]="b.read"
              [attr.data-kind]="b.type"
            >
              @if (b.eventId) {
                <a class="row-link entry" [href]="'/termine/' + b.eventId" (click)="open($event, b)">
                  <span class="text">
                    @if (!b.read) {
                      <span class="chip" data-kind="new">{{ texts.new }}</span>
                    }
                    <span class="row-title">{{ b.text }}</span>
                    <span class="time">{{ instant(b.createdAt) }}</span>
                  </span>
                  <app-icon name="chevron-right" [size]="20" />
                </a>
              } @else {
                <div class="row entry">
                  <span class="text">
                    @if (!b.read) {
                      <span class="chip" data-kind="new">{{ texts.new }}</span>
                    }
                    <span class="row-title">{{ b.text }}</span>
                    <span class="time">{{ instant(b.createdAt) }}</span>
                  </span>
                  @if (!b.read) {
                    <button type="button" class="button button-small button-secondary" (click)="mark(b)">
                      {{ texts.markRead }}
                    </button>
                  }
                </div>
              }
            </li>
          }
        </ul>
      }
    </section>
  `,
  styleUrl: './notifications.css',
})
export class Notifications implements OnInit {
  private readonly service = inject(NotificationsService);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  /** Year of the backend clock, not of the browser (testability.md). */
  private readonly year = computed(() => yearOf(this.auth.me()?.now));
  protected readonly texts = TEXTS.notifications;
  protected readonly common = TEXTS.common;
  protected readonly list = signal<Notification[]>([]);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly feedback = signal('');
  protected readonly unreadOnes = computed(() => this.list().filter((b) => !b.read).length);

  async ngOnInit(): Promise<void> {
    try {
      this.list.set(await this.service.all());
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.loading.set(false);
    }
  }

  /** «Sa 15.11. 21:00» (with year if not in the current year). */
  protected instant(iso: string): string {
    return `${eventDate(iso, this.year())} ${clockTime(iso)}`;
  }

  /** Click on a notification: mark it as read, then go to the event (BEN-12). */
  protected async open(domEvent: MouseEvent, b: Notification): Promise<void> {
    if (domEvent.button !== 0 || domEvent.ctrlKey || domEvent.metaKey || domEvent.shiftKey || domEvent.altKey) {
      return; // new tab or similar: let the browser handle it
    }
    domEvent.preventDefault();
    if (this.busy()) return;
    this.busy.set(true);
    try {
      if (!b.read) {
        await this.service.markRead(b.id);
      }
      await this.router.navigate(['/termine', b.eventId]);
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.busy.set(false);
    }
  }

  protected async mark(b: Notification): Promise<void> {
    await this.run(async () => {
      await this.service.markRead(b.id);
      this.setRead([b.id]);
      this.feedback.set(this.texts.markedRead);
    });
  }

  protected async markAllRead(): Promise<void> {
    await this.run(async () => {
      const unread = this.list().filter((b) => !b.read);
      for (const b of unread) {
        await this.service.markRead(b.id);
      }
      this.setRead(unread.map((b) => b.id));
      this.feedback.set(this.texts.allMarkedRead);
    });
  }

  private setRead(ids: string[]): void {
    this.list.update((l) => l.map((b) => (ids.includes(b.id) ? { ...b, read: true } : b)));
  }

  private async run(action: () => Promise<void>): Promise<void> {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set(null);
    this.feedback.set('');
    try {
      await action();
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.busy.set(false);
    }
  }
}
