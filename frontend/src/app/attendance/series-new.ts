import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import type { Season, Series, SeriesInput } from '../api/types';
import { AuthService } from '../core/auth.service';
import { errorText, fieldErrorsOf } from '../core/error-text';
import { TEXTS } from '../core/texts';
import { PreTrainingFields } from './pre-training-fields';
import { NO_PRE_TRAINING, preTrainingErrors, seriesPreTraining, type PreTrainingValues } from './pre-training-form';

type Weekday = SeriesInput['weekday'];
const WEEKDAYS: Weekday[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
type Field = 'title' | 'startTime' | 'endTime' | 'validFrom' | 'validUntil' | 'preTrainingTitle' | 'preTrainingTime';
/** Field names of the API for the pre-training mapped to the fields of this form. */
const API_FIELDS: Record<string, Field> = { 'preTraining.title': 'preTrainingTitle', 'preTraining.startTime': 'preTrainingTime' };

/**
 * A new weekly series of a team (TER-05): practice by default, from today (or the start of the season), without an
 * end unless one is given (TER-22, TER-23). Used in the assistant «Neues Team» (PTF-13) and on «Termine überarbeiten» (TER-21).
 */
@Component({
  selector: 'app-series-new',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [PreTrainingFields],
  template: `
    <form class="form" (submit)="save($event)" novalidate [attr.aria-label]="label()" data-testid="series-new">
      @if (message(); as m) {
        <p class="message-success" role="status">{{ m }}</p>
      }
      @if (error(); as m) {
        <p class="message-error" role="alert">{{ m }}</p>
      }
      <div class="field-row">
        <div class="field">
          <label for="series-new-title">{{ t.title }}</label>
          <input
            id="series-new-title"
            name="series-new-title"
            type="text"
            maxlength="80"
            autocomplete="off"
            [value]="title()"
            (input)="title.set($any($event.target).value)"
            [attr.aria-invalid]="errors().title ? 'true' : null"
            [attr.aria-describedby]="errors().title ? 'series-new-title-error' : null"
          />
          @if (errors().title; as m) {
            <p class="field-error" id="series-new-title-error">{{ m }}</p>
          }
        </div>
        <div class="field">
          <label for="series-new-weekday">{{ t.weekday }}</label>
          <select id="series-new-weekday" name="series-new-weekday" (change)="weekday.set($any($event.target).value)">
            @for (d of weekdays; track d) {
              <option [value]="d" [selected]="d === weekday()">{{ dayName(d) }}</option>
            }
          </select>
        </div>
      </div>
      <div class="field-row">
        @for (f of times; track f.field) {
          <div class="field">
            <label [for]="'series-new-' + f.field">{{ f.label }}</label>
            <input
              [id]="'series-new-' + f.field"
              [name]="'series-new-' + f.field"
              type="time"
              [value]="value(f.field)"
              (input)="setValue(f.field, $any($event.target).value)"
              [attr.aria-invalid]="errors()[f.field] ? 'true' : null"
              [attr.aria-describedby]="errors()[f.field] ? 'series-new-' + f.field + '-error' : null"
            />
            @if (errors()[f.field]; as m) {
              <p class="field-error" [id]="'series-new-' + f.field + '-error'">{{ m }}</p>
            }
          </div>
        }
      </div>
      <div class="field">
        <label for="series-new-location">{{ t.location }}</label>
        <input
          id="series-new-location"
          name="series-new-location"
          type="text"
          maxlength="120"
          autocomplete="off"
          [value]="location()"
          (input)="location.set($any($event.target).value)"
        />
      </div>
      <app-pre-training-fields
        idPrefix="series-new"
        [enabled]="pre().preTraining"
        [title]="pre().preTrainingTitle"
        [time]="pre().preTrainingTime"
        [practiceStart]="values().startTime"
        [titleError]="errors().preTrainingTitle"
        [timeError]="errors().preTrainingTime"
        [disabled]="saving()"
        (enabledChange)="setPre({ preTraining: $event })"
        (titleChange)="setPre({ preTrainingTitle: $event })"
        (timeChange)="setPre({ preTrainingTime: $event })"
      />
      <div class="field-row">
        @for (f of dates; track f.field) {
          <div class="field">
            <label [for]="'series-new-' + f.field">{{ f.label }}</label>
            <input
              [id]="'series-new-' + f.field"
              [name]="'series-new-' + f.field"
              type="date"
              [value]="value(f.field)"
              (input)="setValue(f.field, $any($event.target).value)"
              [attr.aria-invalid]="errors()[f.field] ? 'true' : null"
              [attr.aria-describedby]="'series-new-hint' + (errors()[f.field] ? ' series-new-' + f.field + '-error' : '')"
            />
            @if (errors()[f.field]; as m) {
              <p class="field-error" [id]="'series-new-' + f.field + '-error'">{{ m }}</p>
            }
          </div>
        }
      </div>
      <p class="secondary-text" id="series-new-hint">{{ t.hint(dayName(weekday())) }}</p>
      <button
        type="submit"
        class="button button-primary button-small"
        [disabled]="saving()"
        [attr.aria-busy]="saving() ? 'true' : null"
      >
        {{ t.save }}
      </button>
    </form>
  `,
  styles: `
    .form {
      display: grid;
      gap: 12px;
    }
    .form > .button {
      justify-self: start;
    }
  `,
})
export class SeriesNew {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  protected readonly t = TEXTS.seriesNew;
  protected readonly weekdays = WEEKDAYS;
  protected readonly times = [
    { field: 'startTime' as const, label: TEXTS.seriesNew.start },
    { field: 'endTime' as const, label: TEXTS.seriesNew.end },
  ];
  protected readonly dates = [
    { field: 'validFrom' as const, label: TEXTS.seriesNew.from },
    { field: 'validUntil' as const, label: TEXTS.seriesNew.until },
  ];

  readonly teamId = input.required<string>();
  /** The season of the team: the series runs at most to its end. */
  readonly season = input<Season | null>(null);
  readonly label = input(TEXTS.rework.newSeries);
  readonly created = output<Series>();

  protected readonly title = signal(TEXTS.seriesNew.defaultTitle);
  protected readonly weekday = signal<Weekday>('TUESDAY');
  protected readonly location = signal('');
  protected readonly pre = signal<PreTrainingValues>({ ...NO_PRE_TRAINING });
  protected readonly values = signal<Record<Exclude<Field, 'preTrainingTitle' | 'preTrainingTime'>, string>>({
    title: '',
    startTime: '18:30',
    endTime: '20:00',
    validFrom: '',
    validUntil: '',
  });
  protected readonly errors = signal<Partial<Record<Field, string>>>({});
  protected readonly saving = signal(false);
  protected readonly message = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  /** «Today» of the backend clock. */
  private readonly today = computed(() => (this.auth.me()?.now ?? new Date().toISOString()).slice(0, 10));

  constructor() {
    effect(() => {
      const season = this.season();
      const today = this.today();
      untracked(() => {
        const from = season && season.startDate > today ? season.startDate : today;
        // Without an end by default: the series runs on, one year ahead (TER-22, TER-23)
        this.values.update((v) => ({ ...v, validFrom: from }));
      });
    });
  }

  protected dayName(d: Weekday): string {
    return TEXTS.eventForm.weekdays[d];
  }

  protected setPre(patch: Partial<PreTrainingValues>): void {
    this.pre.update((p) => ({ ...p, ...patch }));
    this.errors.update((e) => ({ ...e, preTrainingTitle: undefined, preTrainingTime: undefined }));
  }

  protected value(field: 'startTime' | 'endTime' | 'validFrom' | 'validUntil'): string {
    return this.values()[field];
  }

  protected setValue(field: 'startTime' | 'endTime' | 'validFrom' | 'validUntil', value: string): void {
    this.values.update((v) => ({ ...v, [field]: value }));
  }

  protected async save(domEvent: Event): Promise<void> {
    domEvent.preventDefault();
    if (this.saving()) return;
    const v = this.values();
    const errors: Partial<Record<Field, string>> = {};
    if (!this.title().trim()) errors.title = this.t.required;
    for (const f of ['startTime', 'endTime', 'validFrom'] as const) {
      if (!v[f]) errors[f] = this.t.required;
    }
    if (!errors.endTime && !errors.startTime && v.endTime <= v.startTime) errors.endTime = TEXTS.series.untilBeforeFrom;
    if (v.validUntil && !errors.validFrom && v.validUntil < v.validFrom) errors.validUntil = TEXTS.series.untilBeforeFrom;
    const preForm = { ...this.pre(), type: 'PRACTICE', date: '', start: v.startTime };
    Object.assign(errors, preTrainingErrors(preForm));
    this.errors.set(errors);
    const first = Object.keys(errors)[0];
    if (first) {
      document.getElementById('series-new-' + first)?.focus();
      return;
    }
    const input: SeriesInput = {
      type: 'PRACTICE',
      title: this.title().trim(),
      teamIds: [this.teamId()],
      weekday: this.weekday(),
      startTime: v.startTime,
      endTime: v.endTime,
      validFrom: v.validFrom,
      validUntil: v.validUntil || null,
      location: this.location().trim() ? { name: this.location().trim() } : null,
      // Like a new series in the event form: answers until 24 hours before (TER-09)
      responseDeadlineHours: 24,
      preTraining: seriesPreTraining(preForm),
    };
    this.saving.set(true);
    this.message.set(null);
    this.error.set(null);
    try {
      const series = await firstValueFrom(this.http.post<Series>('/api/series', input));
      this.message.set(this.t.created(series.title, this.dayName(series.weekday)));
      this.created.emit(series);
    } catch (e) {
      const fields = fieldErrorsOf(e);
      if (Object.keys(fields).length > 0) {
        const mapped = Object.entries(fields).map(([k, m]) => [API_FIELDS[k] ?? k, m]);
        this.errors.set(Object.fromEntries(mapped) as Partial<Record<Field, string>>);
      } else {
        this.error.set(errorText(e));
      }
    } finally {
      this.saving.set(false);
    }
  }
}
