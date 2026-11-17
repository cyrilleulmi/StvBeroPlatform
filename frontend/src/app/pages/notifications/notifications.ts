import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { yearOf, eventDate, clockTime } from '../../attendance/time';
import type { Notification } from '../../api/types';
import { AuthService } from '../../core/auth.service';
import { NotificationsService } from '../../core/notifications.service';
import { errorText } from '../../core/error-text';
import { Icon } from '../../core/icon';
import { TEXTS } from '../../core/texts';

/**
 * Eigene Benachrichtigungen, neueste zuerst (BEN). Ungelesene sind mit «Neu» markiert. Ein Klick öffnet den
 * Termin und markiert die Benachrichtigung als gelesen (BEN-12).
 */
@Component({
  selector: 'app-benachrichtigungen',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  template: `
    <h1>{{ texts.title }}</h1>
    @if (error(); as message) {
      <p class="meldung-fehler" role="alert">{{ message }}</p>
    }
    @if (feedback(); as message) {
      <p class="meldung-erfolg" role="status">{{ message }}</p>
    }
    @if (unreadOnes() > 0) {
      <div>
        <button type="button" class="button button-sekundaer" [disabled]="busy()" (click)="markAllRead()">
          {{ texts.markAllRead }}
        </button>
      </div>
    }
    <section class="karte" aria-labelledby="benachrichtigungen-liste" [attr.aria-busy]="loading() ? 'true' : null">
      <h2 id="benachrichtigungen-liste" class="visuell-versteckt">{{ texts.title }}</h2>
      @if (loading()) {
        <p class="leer">{{ common.loading }}</p>
      } @else if (list().length === 0) {
        <p class="leer">{{ texts.empty }}</p>
      } @else {
        <ul class="liste" [attr.aria-label]="texts.title">
          @for (b of list(); track b.id) {
            <li
              [attr.data-testid]="'benachrichtigung-' + b.id"
              [attr.data-gelesen]="b.read"
              [attr.data-art]="b.type"
            >
              @if (b.eventId) {
                <a class="zeile-link eintrag" [href]="'/termine/' + b.eventId" (click)="open($event, b)">
                  <span class="text">
                    @if (!b.read) {
                      <span class="chip" data-art="neu">{{ texts.new }}</span>
                    }
                    <span class="zeile-titel">{{ b.text }}</span>
                    <span class="zeit">{{ instant(b.createdAt) }}</span>
                  </span>
                  <app-icon name="chevron-right" [size]="20" />
                </a>
              } @else {
                <div class="zeile eintrag">
                  <span class="text">
                    @if (!b.read) {
                      <span class="chip" data-art="neu">{{ texts.new }}</span>
                    }
                    <span class="zeile-titel">{{ b.text }}</span>
                    <span class="zeit">{{ instant(b.createdAt) }}</span>
                  </span>
                  @if (!b.read) {
                    <button type="button" class="button button-klein button-sekundaer" (click)="mark(b)">
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
  styleUrls: ['../page.css', './notifications.css'],
})
export class Notifications implements OnInit {
  private readonly service = inject(NotificationsService);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  /** Jahr der Backend-Uhr, nicht des Browsers (testbarkeit.md). */
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

  /** «Sa 15.11. 21:00» (mit Jahr, wenn nicht im laufenden Jahr). */
  protected instant(iso: string): string {
    return `${eventDate(iso, this.year())} ${clockTime(iso)}`;
  }

  /** Klick auf eine Benachrichtigung: als gelesen markieren, dann zum Termin (BEN-12). */
  protected async open(domEvent: MouseEvent, b: Notification): Promise<void> {
    if (domEvent.button !== 0 || domEvent.ctrlKey || domEvent.metaKey || domEvent.shiftKey || domEvent.altKey) {
      return; // neuer Tab o.ä.: Browser machen lassen
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
      this.markRead([b.id]);
      this.feedback.set(this.texts.markedRead);
    });
  }

  protected async markAllRead(): Promise<void> {
    await this.run(async () => {
      const offen = this.list().filter((b) => !b.read);
      for (const b of offen) {
        await this.service.markRead(b.id);
      }
      this.markRead(offen.map((b) => b.id));
      this.feedback.set(this.texts.allMarkedRead);
    });
  }

  private markRead(ids: string[]): void {
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
