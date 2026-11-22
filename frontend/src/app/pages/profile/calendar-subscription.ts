import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { CalendarSubscription as Subscription } from '../../api/types';
import { errorText } from '../../core/error-text';
import { Icon } from '../../core/icon';
import { TEXTS } from '../../core/texts';

/**
 * Section «Kalender abonnieren» in the profile (F-18, CAL-01, CAL-07): personal address of the iCalendar feed,
 * copy button, link for calendar apps (webcal), short instructions and «Adresse erneuern».
 */
@Component({
  selector: 'app-calendar-subscription',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  template: `
    <section
      class="card calendar"
      aria-labelledby="profile-calendar"
      data-testid="calendar-subscription"
      [attr.aria-busy]="loading() ? 'true' : null"
    >
      <h2 id="profile-calendar" class="section-title">{{ texts.title }}</h2>
      <p class="secondary-text">{{ texts.intro }}</p>

      @if (url(); as address) {
        <div class="field">
          <label for="calendar-url">{{ texts.address }}</label>
          <input
            name="calendar-url"
            #urlField
            id="calendar-url"
            type="text"
            readonly
            spellcheck="false"
            data-testid="calendar-url"
            [value]="address"
            (focus)="select()"
          />
        </div>
        <div class="actions">
          <button type="button" class="button button-primary button-small" (click)="copy()">
            <app-icon name="copy" [size]="18" />
            {{ texts.copy }}
          </button>
          <a class="button button-secondary button-small" [href]="webcal()">
            <app-icon name="calendar" [size]="18" />
            {{ texts.open }}
          </a>
        </div>
      } @else if (loading()) {
        <p class="secondary-text">{{ common.loading }}</p>
      }

      @if (message(); as m) {
        <p class="message-success" role="status">{{ m }}</p>
      }
      @if (error(); as m) {
        <p class="message-error" role="alert">{{ m }}</p>
      }

      <h3 class="subtitle">{{ texts.howTo }}</h3>
      <div class="help">
        <details data-testid="calendar-help-iphone">
          <summary>{{ texts.iphone }}</summary>
          <ol>
            @for (step of texts.iphoneSteps; track $index) {
              <li>{{ step }}</li>
            }
          </ol>
        </details>
        <details data-testid="calendar-help-android">
          <summary>{{ texts.android }}</summary>
          <ol>
            @for (step of texts.androidSteps; track $index) {
              <li>{{ step }}</li>
            }
          </ol>
        </details>
        <details data-testid="calendar-help-outlook">
          <summary>{{ texts.outlook }}</summary>
          <ol>
            @for (step of texts.outlookSteps; track $index) {
              <li>{{ step }}</li>
            }
          </ol>
        </details>
      </div>
      <p class="secondary-text">{{ texts.refresh }}</p>
      <p class="secondary-text">{{ texts.private }}</p>

      @if (url()) {
        <button
          type="button"
          class="button button-secondary button-small renew"
          [disabled]="renewing()"
          (click)="openRenew()"
        >
          {{ texts.renew }}
        </button>
      }
    </section>

    <dialog
      #renewDialog
      class="card dialog"
      aria-modal="true"
      aria-labelledby="calendar-renew-title"
      aria-describedby="calendar-renew-text"
    >
      <h2 id="calendar-renew-title">{{ texts.renewTitle }}</h2>
      <p id="calendar-renew-text" class="secondary-text">{{ texts.renewText }}</p>
      <div class="dialog-buttons">
        <button
          type="button"
          class="button button-primary button-small"
          [disabled]="renewing()"
          [attr.aria-busy]="renewing() ? 'true' : null"
          (click)="renew()"
        >
          {{ texts.renewConfirm }}
        </button>
        <button
          type="button"
          class="button button-secondary button-small"
          [disabled]="renewing()"
          (click)="renewDialog.close()"
        >
          {{ texts.cancel }}
        </button>
      </div>
    </dialog>
  `,
  styles: `
    .calendar {
      display: grid;
      gap: 12px;
    }
    .calendar p,
    .calendar h2,
    .calendar h3 {
      margin: 0;
    }
    .field input {
      font-size: 14px;
      text-overflow: ellipsis;
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }
    .subtitle {
      font-size: 15px;
      font-weight: 700;
    }
    .help {
      display: grid;
      gap: 6px;
    }
    details {
      border: 1px solid var(--border);
      border-radius: var(--radius-icon);
      padding: 0 14px;
    }
    summary {
      min-height: var(--touch);
      display: flex;
      align-items: center;
      font-weight: 700;
      cursor: pointer;
    }
    summary:focus-visible {
      outline: 3px solid var(--primary);
      outline-offset: 2px;
    }
    ol {
      margin: 0 0 12px;
      padding-left: 20px;
      display: grid;
      gap: 6px;
    }
    .renew {
      justify-self: start;
    }
    .dialog {
      max-width: min(420px, calc(100vw - 32px));
      color: var(--text);
    }
    .dialog:not([open]) {
      display: none;
    }
    .dialog[open] {
      display: grid;
      gap: 12px;
    }
    .dialog h2,
    .dialog p {
      margin: 0;
    }
    .dialog::backdrop {
      background: color-mix(in srgb, var(--bg) 80%, transparent);
    }
    .dialog-buttons {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }
  `,
})
export class CalendarSubscriptionSection {
  private readonly http = inject(HttpClient);
  protected readonly texts = TEXTS.calendar;
  protected readonly common = TEXTS.common;

  protected readonly subscription = signal<Subscription | null>(null);
  protected readonly loading = signal(true);
  protected readonly renewing = signal(false);
  protected readonly message = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  private readonly urlField = viewChild<ElementRef<HTMLInputElement>>('urlField');
  private readonly renewDialog = viewChild<ElementRef<HTMLDialogElement>>('renewDialog');

  /** Absolute address: the app and the API share the origin (in development through the proxy). */
  protected readonly url = computed(() => {
    const s = this.subscription();
    return s ? `${location.origin}${s.feedPath}` : null;
  });

  /** Same address with webcal:, so that phones offer to subscribe right away. */
  protected readonly webcal = computed(() => this.url()?.replace(/^https?:/, 'webcal:') ?? null);

  constructor() {
    void this.load();
  }

  private async load(): Promise<void> {
    try {
      this.subscription.set(await firstValueFrom(this.http.get<Subscription>('/api/me/calendar')));
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.loading.set(false);
    }
  }

  protected select(): void {
    this.urlField()?.nativeElement.select();
  }

  protected async copy(): Promise<void> {
    const address = this.url();
    if (!address) return;
    this.error.set(null);
    try {
      await navigator.clipboard.writeText(address);
      this.message.set(this.texts.copied);
    } catch {
      this.message.set(null);
      this.error.set(this.texts.copyFailed);
      this.select();
    }
  }

  protected openRenew(): void {
    const d = this.renewDialog()?.nativeElement;
    if (d && !d.open) d.showModal();
  }

  protected async renew(): Promise<void> {
    if (this.renewing()) return;
    this.renewing.set(true);
    this.message.set(null);
    this.error.set(null);
    try {
      this.subscription.set(
        await firstValueFrom(this.http.post<Subscription>('/api/me/calendar/renew', null)),
      );
      this.message.set(this.texts.renewed);
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.renewing.set(false);
      this.renewDialog()?.nativeElement.close();
    }
  }
}
