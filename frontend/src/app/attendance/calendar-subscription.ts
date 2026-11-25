import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  ElementRef,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  CalendarFilter,
  CalendarFilterInput,
  CalendarResponse,
  EventType,
  CalendarSubscription as Subscription,
} from '../api/types';
import { errorText } from '../core/error-text';
import { Icon } from '../core/icon';
import { TEXTS } from '../core/texts';
import { showModal } from '../core/dialog';

/**
 * Section «Kalender abonnieren» of the page `/profil/kalender` (F-18, CAL-01, CAL-07): personal address of the
 * iCalendar feed, copy button, link for calendar apps (webcal), short instructions and «Adresse erneuern». The
 * page title says what it is, so the section has no heading of its own (E-109).
 */
@Component({
  selector: 'app-calendar-subscription',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  template: `
    <section
      class="card calendar"
      [attr.aria-label]="texts.title"
      data-testid="calendar-subscription"
      [attr.aria-busy]="loading() ? 'true' : null"
    >
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

      @if (filter(); as f) {
        <form class="filter" data-testid="calendar-filter" (submit)="$event.preventDefault()">
          <h2 class="subtitle">{{ texts.filterTitle }}</h2>
          <p class="secondary-text">{{ texts.filterIntro }}</p>
          <fieldset>
            <legend>{{ texts.filterTypes }}</legend>
            @for (type of types; track type) {
              <label class="checkbox">
                <input
                  type="checkbox"
                  [name]="'calendar-type-' + type"
                  [attr.data-testid]="'calendar-type-' + type"
                  [checked]="f.types.includes(type)"
                  [disabled]="saving()"
                  (change)="toggleType(type, $any($event.target))"
                />
                {{ texts.filterTypeLabels[type] }}
              </label>
            }
          </fieldset>
          <fieldset>
            <legend>{{ texts.filterResponses }}</legend>
            <label class="checkbox">
              <input
                type="checkbox"
                name="calendar-response-ATTENDING"
                checked
                disabled
                aria-describedby="calendar-attending-hint"
              />
              {{ texts.filterAttending }}
            </label>
            <p class="secondary-text hint" id="calendar-attending-hint">
              {{ texts.filterAttendingHint }}
            </p>
            @for (response of responses; track response) {
              <label class="checkbox">
                <input
                  type="checkbox"
                  [name]="'calendar-response-' + response"
                  [attr.data-testid]="'calendar-response-' + response"
                  [checked]="f.responses.includes(response)"
                  [disabled]="saving()"
                  (change)="toggleResponse(response, $any($event.target))"
                />
                {{ texts.filterResponseLabels[response] }}
              </label>
            }
          </fieldset>
          @if (f.teams.length > 1) {
            <fieldset>
              <legend>{{ texts.filterTeams }}</legend>
              @for (team of f.teams; track team.teamId) {
                <label class="checkbox">
                  <input
                    type="checkbox"
                    [name]="'calendar-team-' + team.teamId"
                    [attr.data-testid]="'calendar-team-' + team.teamId"
                    [checked]="team.included"
                    [disabled]="saving()"
                    (change)="toggleTeam(team.teamId, $any($event.target))"
                  />
                  {{ team.name }}
                </label>
              }
            </fieldset>
          }
          @if (filterMessage(); as m) {
            <p class="message-success" role="status">{{ m }}</p>
          }
          @if (filterError(); as m) {
            <p class="message-error" role="alert">{{ m }}</p>
          }
        </form>
      }

      <h2 class="subtitle">{{ texts.howTo }}</h2>
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
    .calendar h2 {
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
      font-family: var(--font);
      font-stretch: normal;
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
    .filter {
      display: grid;
      gap: 10px;
    }
    .filter p,
    .filter h2 {
      margin: 0;
    }
    fieldset {
      border: 1px solid var(--border);
      border-radius: var(--radius-icon);
      padding: 4px 14px 8px;
      margin: 0;
      display: grid;
    }
    legend {
      font-weight: 700;
      padding: 0 4px;
    }
    .hint {
      margin: -8px 0 4px 34px;
      font-size: 13px;
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
  private readonly changes = inject(ChangeDetectorRef);
  private readonly http = inject(HttpClient);
  protected readonly texts = TEXTS.calendar;
  protected readonly common = TEXTS.common;

  protected readonly subscription = signal<Subscription | null>(null);
  protected readonly loading = signal(true);
  protected readonly renewing = signal(false);
  protected readonly message = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly filterMessage = signal<string | null>(null);
  protected readonly filterError = signal<string | null>(null);

  /** Order of the checkboxes (CAL-10, CAL-11). */
  protected readonly types: readonly EventType[] = ['PRACTICE', 'MATCH_DAY', 'OTHER'];
  protected readonly responses: readonly CalendarResponse[] = [
    'PENDING',
    'OPTIONAL_PENDING',
    'MAYBE',
    'DECLINED',
  ];

  protected readonly filter = computed<CalendarFilter | null>(
    () => this.subscription()?.filter ?? null,
  );

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

  protected toggleType(type: EventType, box: HTMLInputElement): void {
    const f = this.filter();
    if (!f) return;
    const types = box.checked ? [...f.types, type] : f.types.filter((t) => t !== type);
    if (types.length === 0) {
      // The last type stays checked: the same rule as in the API (CAL-10)
      box.checked = true;
      this.filterMessage.set(null);
      this.filterError.set(this.texts.filterTypeRequired);
      return;
    }
    void this.save({ ...this.input(f), types }, box);
  }

  protected toggleResponse(response: CalendarResponse, box: HTMLInputElement): void {
    const f = this.filter();
    if (!f) return;
    const responses = box.checked
      ? [...f.responses, response]
      : f.responses.filter((r) => r !== response);
    void this.save({ ...this.input(f), responses }, box);
  }

  protected toggleTeam(teamId: string, box: HTMLInputElement): void {
    const f = this.filter();
    if (!f) return;
    const excluded = f.teams
      .filter((t) => (t.teamId === teamId ? !box.checked : !t.included))
      .map((t) => t.teamId);
    void this.save({ ...this.input(f), excludedTeamIds: excluded }, box);
  }

  private input(f: CalendarFilter): CalendarFilterInput {
    return {
      types: f.types,
      responses: f.responses,
      excludedTeamIds: f.teams.filter((t) => !t.included).map((t) => t.teamId),
    };
  }

  private async save(input: CalendarFilterInput, box: HTMLInputElement): Promise<void> {
    this.saving.set(true);
    this.filterMessage.set(null);
    this.filterError.set(null);
    try {
      this.subscription.set(
        await firstValueFrom(this.http.put<Subscription>('/api/me/calendar/filter', input)),
      );
      this.filterMessage.set(this.texts.filterSaved);
    } catch (e) {
      this.filterError.set(errorText(e));
      // Not saved: the box shows the stored state again
      box.checked = !box.checked;
    } finally {
      this.saving.set(false);
    }
  }

  protected openRenew(): void {
    showModal(this.renewDialog()?.nativeElement, this.changes);
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
