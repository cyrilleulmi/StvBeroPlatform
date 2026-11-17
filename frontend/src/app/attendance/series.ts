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
 * Serie (`/serien/:serieId`): Regel, Teams, Zeitraum und Ausnahmen (TER-05, TER-07). Coaches eines Teams der
 * Serie und Admins fügen Ausnahmen hinzu und kommen zu «Serie bearbeiten».
 */
@Component({
  selector: 'app-serie',
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
        <section class="card" aria-labelledby="serie-angaben">
          <h2 id="serie-angaben" class="section-title">{{ texts.events.details }}</h2>
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

        <section class="card" aria-labelledby="serie-ausnahmen" data-testid="ausnahmen">
          <h2 id="serie-ausnahmen" class="section-title">
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
              class="form ausnahme"
              (submit)="createException($event)"
              novalidate
              aria-labelledby="ausnahme-titel"
            >
              <h3 id="ausnahme-titel">{{ texts.series.addException }}</h3>
              <p class="secondary-text" id="ausnahme-hinweis">{{ texts.series.exceptionHint }}</p>
              <div class="field-row">
                @for (f of dateFields; track f.field) {
                  <div class="field">
                    <label [for]="'ausnahme-' + f.field">{{ f.label }}</label>
                    <input
                      [id]="'ausnahme-' + f.field"
                      type="date"
                      [value]="newException()[f.field]"
                      (input)="set(f.field, $any($event.target).value)"
                      [attr.aria-invalid]="fieldErrors()[f.field] ? 'true' : null"
                      [attr.aria-describedby]="
                        fieldErrors()[f.field] ? 'ausnahme-' + f.field + '-fehler' : null
                      "
                    />
                    @if (fieldErrors()[f.field]; as m) {
                      <p class="field-error" [id]="'ausnahme-' + f.field + '-fehler'">{{ m }}</p>
                    }
                  </div>
                }
              </div>
              <div class="field">
                <label for="ausnahme-grund">{{ texts.series.reason }}</label>
                <input
                  id="ausnahme-grund"
                  type="text"
                  maxlength="200"
                  autocomplete="off"
                  [value]="newException().reason"
                  (input)="set('reason', $any($event.target).value)"
                  [attr.aria-invalid]="fieldErrors().reason ? 'true' : null"
                  [attr.aria-describedby]="
                    fieldErrors().reason ? 'ausnahme-grund-fehler' : 'ausnahme-hinweis'
                  "
                />
                @if (fieldErrors().reason; as m) {
                  <p class="field-error" id="ausnahme-grund-fehler">{{ m }}</p>
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
    .ausnahme {
      margin-top: 16px;
      padding-top: 16px;
      border-top: 1px solid var(--border);
    }
    .ausnahme h3,
    .ausnahme p {
      margin: 0;
    }
    .ausnahme .button {
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

  /** Route `/serien/:serieId` */
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
    // Alle Teams der Serie mit Namen aus der API, auch die, in denen ich nicht bin (TER-11).
    return TEXTS.events.joinTeams(s.teams.map((t) => t.name));
  });

  /** Admins und Coaches eines Teams der Serie (TER-11, RR-06). */
  protected readonly canEdit = computed(() => {
    const s = this.series();
    const me = this.auth.me();
    if (!s || !me) return false;
    return (
      me.admin ||
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
      document.getElementById(`ausnahme-${first}`)?.focus();
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
      const fields = fieldErrorsOf(e, { startDate: 'from', endDate: 'until', reason: 'reason' });
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
