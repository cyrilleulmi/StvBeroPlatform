import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { SeriesException, Series as SeriesData } from '../api/types';
import { AuthService } from '../core/auth.service';
import { fieldErrorsOf, errorStatus, errorText } from '../core/error-text';
import { date } from '../core/format';
import { FeedbackService } from '../core/feedback.service';
import { TEXTS } from '../core/texts';

type ExceptionField = 'from' | 'until' | 'reason';

/**
 * Series (`/serien/:seriesId`): rule, teams, period and series exceptions (TER-05, TER-07). Coaches of a
 * team of the series and admins add exceptions and get to «Serie bearbeiten».
 */
@Component({
  selector: 'app-series',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <div class="header">
      <a class="back" routerLink="/termine">{{ texts.events.allEvents }}</a>
      <h1>{{ series()?.title ?? texts.events.series }}</h1>
      @if (series(); as s) {
        <p class="secondary-text">{{ rule() }}</p>
      }
    </div>
    @if (success(); as message) {
      <p class="message-success" role="status">{{ message }}</p>
    }
    @if (error(); as message) {
      <p class="message-error" role="alert">{{ message }}</p>
    }
    <div class="sections" [attr.aria-busy]="loading() ? 'true' : null">
      @if (loading()) {
        <p class="empty">{{ texts.common.loading }}</p>
      } @else if (series(); as s) {
        <section class="card" aria-labelledby="series-details">
          <h2 id="series-details" class="section-title">{{ texts.events.details }}</h2>
          <dl class="facts">
            <dt>{{ texts.eventForm.type }}</dt>
            <dd>{{ texts.events.type[s.type] }}</dd>
            <dt>{{ texts.eventForm.weekday }}</dt>
            <dd>{{ texts.eventForm.weekdays[s.weekday] }}</dd>
            <dt>{{ texts.events.time }}</dt>
            <dd>{{ texts.events.period(s.startTime, s.endTime) }}</dd>
            <dt>{{ texts.series.period }}</dt>
            <dd>{{ texts.series.periodText(date(s.validFrom), date(s.validUntil)) }}</dd>
            <dt>{{ texts.events.teams }}</dt>
            <dd>{{ teams() }}</dd>
            @if (s.location) {
              <dt>{{ texts.events.location }}</dt>
              <dd>{{ s.location.name }}</dd>
            }
            <dt>{{ texts.events.responseDeadline }}</dt>
            <dd>{{ s.responseDeadlineHours ?? 24 }} Stunden vor Beginn</dd>
          </dl>
          @if (canEdit()) {
            <a
              class="button button-secondary button-small"
              [routerLink]="['/serien', s.id, 'bearbeiten']"
              >{{ texts.series.edit }}</a
            >
          }
        </section>

        <section class="card" aria-labelledby="series-exceptions" data-testid="exceptions">
          <h2 id="series-exceptions" class="section-title">
            {{ texts.series.exceptions }} <span class="count">{{ s.exceptions.length }}</span>
          </h2>
          @if (s.exceptions.length === 0) {
            <p class="empty">{{ texts.series.noExceptions }}</p>
          } @else {
            <ul class="list" [attr.aria-label]="texts.series.exceptions">
              @for (a of s.exceptions; track $index) {
                <li class="row">{{ exceptionText(a) }}</li>
              }
            </ul>
          }
          @if (canEdit()) {
            <form
              class="form exception"
              (submit)="createException($event)"
              novalidate
              aria-labelledby="exception-title"
            >
              <h3 id="exception-title">{{ texts.series.addException }}</h3>
              <p class="secondary-text" id="exception-hint">{{ texts.series.exceptionHint }}</p>
              <div class="field-row">
                @for (f of dateFields; track f.field) {
                  <div class="field">
                    <label [for]="'exception-' + f.field">{{ f.label }}</label>
                    <input
                      [name]="'exception-' + f.field"
                      [id]="'exception-' + f.field"
                      type="date"
                      [value]="newException()[f.field]"
                      (input)="set(f.field, $any($event.target).value)"
                      [attr.aria-invalid]="fieldErrors()[f.field] ? 'true' : null"
                      [attr.aria-describedby]="
                        fieldErrors()[f.field] ? 'exception-' + f.field + '-error' : null
                      "
                    />
                    @if (fieldErrors()[f.field]; as m) {
                      <p class="field-error" [id]="'exception-' + f.field + '-error'">{{ m }}</p>
                    }
                  </div>
                }
              </div>
              <div class="field">
                <label for="exception-reason">{{ texts.series.reason }}</label>
                <input
                  name="exception-reason"
                  id="exception-reason"
                  type="text"
                  maxlength="200"
                  autocomplete="off"
                  [value]="newException().reason"
                  (input)="set('reason', $any($event.target).value)"
                  [attr.aria-invalid]="fieldErrors().reason ? 'true' : null"
                  [attr.aria-describedby]="
                    fieldErrors().reason ? 'exception-reason-error' : 'exception-hint'
                  "
                />
                @if (fieldErrors().reason; as m) {
                  <p class="field-error" id="exception-reason-error">{{ m }}</p>
                }
              </div>
              <button
                type="submit"
                class="button button-primary"
                [disabled]="saving()"
                [attr.aria-busy]="saving() ? 'true' : null"
              >
                {{ texts.series.addException }}
              </button>
            </form>
          }
        </section>
      }
    </div>
  `,
  styleUrl: './event.css',
  styles: `
    .exception {
      margin-top: 16px;
      padding-top: 16px;
      border-top: 1px solid var(--border);
    }
    .exception h3,
    .exception p {
      margin: 0;
    }
    .exception .button {
      justify-self: start;
    }
  `,
})
export class SeriesPage {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly feedback = inject(FeedbackService);
  protected readonly texts = TEXTS;
  protected readonly date = date;
  protected readonly dateFields: { field: 'from' | 'until'; label: string }[] = [
    { field: 'from', label: TEXTS.series.from },
    { field: 'until', label: TEXTS.series.until },
  ];

  /** Route `/serien/:seriesId` */
  readonly seriesId = input.required<string>();

  protected readonly series = signal<SeriesData | null>(null);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly success = signal<string | null>(null);
  protected readonly fieldErrors = signal<Partial<Record<ExceptionField, string>>>({});
  protected readonly newException = signal<Record<ExceptionField, string>>({ from: '', until: '', reason: '' });

  protected readonly rule = computed(() => {
    const s = this.series();
    return s
      ? TEXTS.series.rule(TEXTS.eventForm.weekdays[s.weekday], s.startTime, s.endTime)
      : '';
  });

  protected readonly teams = computed(() => {
    const s = this.series();
    if (!s) return '';
    // All teams of the series with names from the API, also those I am not in (TER-11).
    return TEXTS.events.joinTeams(s.teams.map((t) => t.name));
  });

  /** Admins and coaches (of any team, E-69) (TER-11, RR-03). */
  protected readonly canEdit = computed(() => {
    const s = this.series();
    const me = this.auth.me();
    if (!s || !me) return false;
    return (
      this.auth.canManage() ||
      me.memberships.some((z) => z.teamRole === 'COACH' && s.teamIds.includes(z.team.id))
    );
  });

  constructor() {
    this.success.set(this.feedback.take());
    effect(() => {
      const id = this.seriesId();
      untracked(() => void this.load(id));
    });
  }

  protected exceptionText(a: SeriesException): string {
    return TEXTS.series.exceptionText(a.reason, date(a.startDate), date(a.endDate));
  }

  protected set(field: ExceptionField, value: string): void {
    this.newException.update((n) => ({ ...n, [field]: value }));
    if (this.fieldErrors()[field]) {
      this.fieldErrors.update((f) => ({ ...f, [field]: undefined }));
    }
  }

  async createException(domEvent: Event): Promise<void> {
    domEvent.preventDefault();
    if (this.saving()) return;
    const n = this.newException();
    const f: Partial<Record<ExceptionField, string>> = {};
    if (!n.from) f.from = TEXTS.eventForm.required('ein Datum');
    if (!n.until) f.until = TEXTS.eventForm.required('ein Datum');
    else if (n.from && n.until < n.from) f.until = TEXTS.series.untilBeforeFrom;
    if (!n.reason.trim()) f.reason = TEXTS.eventForm.reasonRequired;
    this.fieldErrors.set(f);
    const first = Object.keys(f)[0];
    if (first) {
      document.getElementById(`exception-${first}`)?.focus();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.success.set(null);
    try {
      const s = await firstValueFrom(
        this.http.post<SeriesData>(`/api/series/${this.seriesId()}/exceptions`, {
          startDate: n.from,
          endDate: n.until,
          reason: n.reason.trim(),
        }),
      );
      this.series.set(s);
      this.newException.set({ from: '', until: '', reason: '' });
      this.success.set(TEXTS.series.exceptionCreated(n.reason.trim()));
    } catch (e) {
      const fields = fieldErrorsOf(e, { startDate: 'from', endDate: 'until' });
      if (Object.keys(fields).length > 0) {
        this.fieldErrors.set(fields);
      } else {
        this.error.set(errorText(e));
      }
    } finally {
      this.saving.set(false);
    }
  }

  private async load(id: string): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    this.series.set(null);
    try {
      this.series.set(await firstValueFrom(this.http.get<SeriesData>(`/api/series/${id}`)));
    } catch (e) {
      const status = errorStatus(e);
      this.error.set(status === 404 || status === 400 ? TEXTS.series.notFound : errorText(e));
    } finally {
      this.loading.set(false);
    }
  }
}
