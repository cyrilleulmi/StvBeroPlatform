import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { components } from '../api/schema';
import type { SeriesInput } from '../api/types';
import { AuthService } from '../core/auth.service';
import { ConfirmDialog } from '../core/confirm-dialog';
import { errorText, fieldErrorsOf } from '../core/error-text';
import { date } from '../core/format';
import { Icon } from '../core/icon';
import { TEXTS } from '../core/texts';
import { eventDate, yearOf } from '../core/time';

type Overview = components['schemas']['SeriesOverview'];
type Preview = components['schemas']['SeriesPreview'];
type Weekday = SeriesInput['weekday'];
type Panel = 'change' | 'holidays' | 'end' | null;
const WEEKDAYS: Weekday[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];

/**
 * One series on «Termine überarbeiten» (TER-18 to TER-21): its rule and upcoming events. Coaches change it after a
 * preview of what happens to the upcoming events (TER-19), enter holidays (TER-07) or end it after a question
 * (TER-20).
 */
@Component({
  selector: 'app-series-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon, ConfirmDialog],
  template: `
    @let s = overview().series;
    <article class="series" [attr.aria-labelledby]="'series-' + s.id + '-title'" [attr.data-testid]="'series-card-' + s.id">
      <div class="head">
        <div>
          <h3 [id]="'series-' + s.id + '-title'">{{ s.title }} · {{ day(s.weekday) }} {{ s.startTime }}–{{ s.endTime }}</h3>
          <p class="secondary-text">
            {{ s.location?.name ?? '' }}{{ s.location ? ' · ' : '' }}{{ texts.series.periodText(date(s.validFrom), s.validUntil ? date(s.validUntil) : null) }}
          </p>
          @if (s.eventsUntil && (!s.validUntil || s.eventsUntil < s.validUntil)) {
            <p class="secondary-text" data-testid="series-events-until">{{ texts.series.eventsUntil(date(s.eventsUntil)) }}</p>
          }
        </div>
        <span class="chip" [attr.data-kind]="overview().upcoming === 0 ? 'ended' : 'running'" data-testid="series-upcoming">
          {{ c.upcoming(overview().upcoming) }}{{ next() ? ' · ' + c.next(next()!) : '' }}
        </span>
      </div>
      @if (s.exceptions.length > 0) {
        <p class="secondary-text">{{ c.exceptions(exceptions()) }}</p>
      }
      @if (message(); as m) {
        <p class="message-success" role="status">{{ m }}</p>
      }
      @if (error(); as m) {
        <p class="message-error" role="alert">{{ m }}</p>
      }
      <div class="actions">
        @if (showView()) {
          <a class="button button-secondary button-small" [routerLink]="['/serien', s.id]">{{ c.view }}</a>
        }
        @if (overview().canEdit) {
          <button
            type="button"
            class="button button-secondary button-small"
            [attr.aria-expanded]="panel() === 'change'"
            [attr.data-testid]="'series-change-' + s.id"
            (click)="open('change')"
          >
            <app-icon name="pencil" [size]="16" /> {{ c.change }}
          </button>
          @if (overview().upcoming > 0) {
            <button
              type="button"
              class="button button-secondary button-small"
              [attr.aria-expanded]="panel() === 'holidays'"
              [attr.data-testid]="'series-holidays-' + s.id"
              (click)="open('holidays')"
            >
              {{ c.holidays }}
            </button>
            <button
              type="button"
              class="button button-secondary button-small button-destructive"
              [attr.aria-expanded]="panel() === 'end'"
              [attr.data-testid]="'series-end-' + s.id"
              (click)="open('end')"
            >
              {{ c.end }}
            </button>
          }
        }
      </div>

      @switch (panel()) {
        @case ('change') {
          <form class="panel form" (submit)="check($event)" novalidate [attr.aria-label]="c.change">
            <p class="secondary-text">{{ c.changeHint }}</p>
            <div class="field-row">
              <div class="field">
                <label [for]="id('weekday')">{{ c.weekday }}</label>
                <select [id]="id('weekday')" [name]="id('weekday')" (change)="edit('weekday', $any($event.target).value)">
                  @for (d of weekdays; track d) {
                    <option [value]="d" [selected]="d === draft().weekday">{{ day(d) }}</option>
                  }
                </select>
              </div>
              <div class="field">
                <label [for]="id('start')">{{ c.start }}</label>
                <input [id]="id('start')" [name]="id('start')" type="time" [value]="draft().startTime"
                       (input)="edit('startTime', $any($event.target).value)" />
              </div>
              <div class="field">
                <label [for]="id('end')">{{ c.endTime }}</label>
                <input [id]="id('end')" [name]="id('end')" type="time" [value]="draft().endTime"
                       [attr.aria-invalid]="fieldError() ? 'true' : null"
                       (input)="edit('endTime', $any($event.target).value)" />
              </div>
            </div>
            <div class="field-row">
              <div class="field">
                <label [for]="id('location')">{{ c.location }}</label>
                <input [id]="id('location')" [name]="id('location')" type="text" maxlength="120" autocomplete="off"
                       [value]="draft().location" (input)="edit('location', $any($event.target).value)" />
              </div>
              <div class="field">
                <label [for]="id('until')">{{ c.validUntil }}</label>
                <input [id]="id('until')" [name]="id('until')" type="date" [value]="draft().validUntil"
                       (input)="edit('validUntil', $any($event.target).value)" />
              </div>
            </div>
            @if (fieldError(); as m) {
              <p class="field-error">{{ m }}</p>
            }
            @if (preview(); as p) {
              <div class="preview" [attr.data-state]="p.cancelled > 0 ? 'warn' : 'ok'" data-testid="series-preview" aria-live="polite">
                @for (line of previewLines(p); track $index) {
                  <p [class.warn]="line.warn">
                    @if (line.warn) {
                      <app-icon name="triangle-alert" [size]="16" />
                    }
                    {{ line.text }}
                  </p>
                }
              </div>
            } @else if (stale()) {
              <p class="secondary-text">{{ c.recheck }}</p>
            }
            <div class="buttons">
              @if (preview() && changesSomething()) {
                <button type="button" class="button button-primary button-small" data-testid="series-apply"
                        [disabled]="busy()" [attr.aria-busy]="busy() === 'apply' ? 'true' : null" (click)="apply()">
                  {{ c.apply }}
                </button>
              } @else {
                <button type="submit" class="button button-primary button-small" data-testid="series-check"
                        [disabled]="busy()" [attr.aria-busy]="busy() === 'check' ? 'true' : null">
                  {{ c.check }}
                </button>
              }
              <button type="button" class="button button-secondary button-small" (click)="open(null)">{{ c.cancel }}</button>
            </div>
          </form>
        }
        @case ('holidays') {
          <form class="panel form" (submit)="holidays($event)" novalidate [attr.aria-label]="c.holidays">
            <p class="secondary-text">{{ c.holidaysHint }}</p>
            <div class="field-row">
              <div class="field">
                <label [for]="id('h-from')">{{ c.holidaysFrom }}</label>
                <input [id]="id('h-from')" [name]="id('h-from')" type="date" [value]="hFrom()" (input)="hFrom.set($any($event.target).value)" />
              </div>
              <div class="field">
                <label [for]="id('h-until')">{{ c.holidaysUntil }}</label>
                <input [id]="id('h-until')" [name]="id('h-until')" type="date" [value]="hUntil()" (input)="hUntil.set($any($event.target).value)" />
              </div>
              <div class="field">
                <label [for]="id('h-reason')">{{ c.holidaysReason }}</label>
                <input [id]="id('h-reason')" [name]="id('h-reason')" type="text" maxlength="200" autocomplete="off"
                       [value]="hReason()" (input)="hReason.set($any($event.target).value)" />
              </div>
            </div>
            @if (fieldError(); as m) {
              <p class="field-error">{{ m }}</p>
            }
            <div class="buttons">
              <button type="submit" class="button button-primary button-small" [disabled]="busy()"
                      [attr.aria-busy]="busy() === 'holidays' ? 'true' : null">{{ c.holidaysSave }}</button>
              <button type="button" class="button button-secondary button-small" (click)="open(null)">{{ c.cancel }}</button>
            </div>
          </form>
        }
        @case ('end') {
          <form class="panel form" (submit)="askEnd($event)" novalidate [attr.aria-label]="c.end">
            <p class="secondary-text">{{ c.endHint }}</p>
            <div class="field">
              <label [for]="id('end-from')">{{ c.endFrom }}</label>
              <input [id]="id('end-from')" [name]="id('end-from')" type="date" [value]="endFrom()" (input)="endFrom.set($any($event.target).value)" />
            </div>
            <div class="buttons">
              <button type="submit" class="button button-primary button-small" [attr.data-testid]="'series-end-check-' + s.id"
                      [disabled]="busy()" [attr.aria-busy]="busy() === 'end' ? 'true' : null">{{ c.endCheck }}</button>
              <button type="button" class="button button-secondary button-small" (click)="open(null)">{{ c.cancel }}</button>
            </div>
          </form>
        }
      }
    </article>
    <app-confirm-dialog testId="series-end-dialog" />
  `,
  styles: `
    .series {
      display: grid;
      gap: 10px;
      padding: 14px;
      border: 1.5px solid var(--border);
      border-radius: var(--radius-control);
      background: var(--surface);
      min-width: 0;
    }
    .head {
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      align-items: flex-start;
      gap: 6px 12px;
    }
    h3,
    p {
      margin: 0;
    }
    h3 {
      font: 800 16px/1.3 var(--font);
    }
    .chip[data-kind='running'] {
      background: var(--primary-soft);
    }
    .actions,
    .buttons {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }
    .actions .button {
      display: inline-flex;
      align-items: center;
      gap: 6px;
    }
    .panel {
      display: grid;
      gap: 12px;
      padding-top: 12px;
      border-top: 1px solid var(--border);
    }
    .preview {
      display: grid;
      gap: 4px;
      padding: 10px 12px;
      border-radius: var(--radius-icon);
      border: 1.5px solid var(--primary);
      background: var(--primary-soft);
    }
    .preview[data-state='warn'] {
      border-color: var(--coral);
      background: var(--coral-soft);
    }
    .preview .warn {
      display: flex;
      gap: 6px;
      align-items: flex-start;
      font-weight: 700;
    }
  `,
})
export class SeriesCard {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  protected readonly texts = TEXTS;
  protected readonly c = TEXTS.seriesCard;
  protected readonly date = date;
  protected readonly weekdays = WEEKDAYS;
  private readonly confirm = viewChild(ConfirmDialog);

  readonly overview = input.required<Overview>();
  /** «Serie ansehen» leads to another page: not inside the assistant «Neues Team». */
  readonly showView = input(true);
  /** After a change: the page loads the series again. */
  readonly changed = output<void>();

  protected readonly panel = signal<Panel>(null);
  protected readonly busy = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly fieldError = signal<string | null>(null);
  protected readonly draft = signal({ weekday: 'MONDAY' as Weekday, startTime: '', endTime: '', location: '', validUntil: '' });
  protected readonly preview = signal<Preview | null>(null);
  /** The draft changed after the last preview. */
  protected readonly stale = signal(false);
  protected readonly hFrom = signal('');
  protected readonly hUntil = signal('');
  protected readonly hReason = signal('');
  protected readonly endFrom = signal('');

  private readonly today = computed(() => (this.auth.me()?.now ?? new Date().toISOString()).slice(0, 10));
  protected readonly next = computed(() => {
    const n = this.overview().next;
    return n ? eventDate(n, yearOf(this.auth.me()?.now)) : null;
  });
  protected readonly exceptions = computed(() =>
    this.overview()
      .series.exceptions.map((e) => `${e.reason} (${date(e.startDate)} – ${date(e.endDate)})`)
      .join(', '),
  );
  protected readonly changesSomething = computed(() => {
    const p = this.preview();
    return !!p && p.adjusted + p.created + p.removed + p.cancelled > 0;
  });

  protected id(part: string): string {
    return `series-${this.overview().series.id}-${part}`;
  }

  protected day(d: Weekday): string {
    return TEXTS.eventForm.weekdays[d];
  }

  protected open(panel: Panel): void {
    const s = this.overview().series;
    this.panel.set(this.panel() === panel ? null : panel);
    this.preview.set(null);
    this.stale.set(false);
    this.fieldError.set(null);
    this.error.set(null);
    this.draft.set({
      weekday: s.weekday,
      startTime: s.startTime,
      endTime: s.endTime,
      location: s.location?.name ?? '',
      validUntil: s.validUntil ?? '',
    });
    this.hFrom.set(this.today());
    this.hUntil.set(this.today());
    this.hReason.set('');
    this.endFrom.set(this.today());
  }

  protected edit(field: 'weekday' | 'startTime' | 'endTime' | 'location' | 'validUntil', value: string): void {
    this.draft.update((d) => ({ ...d, [field]: value }));
    if (this.preview()) this.stale.set(true);
    this.preview.set(null);
  }

  /** The changed series as input: everything else stays as it is. */
  private input(): SeriesInput {
    const s = this.overview().series;
    const d = this.draft();
    const name = d.location.trim();
    return {
      type: s.type,
      title: s.title,
      teamIds: s.teamIds,
      weekday: d.weekday,
      startTime: d.startTime,
      endTime: d.endTime,
      validFrom: s.validFrom,
      validUntil: d.validUntil || null,
      location: name ? (s.location && s.location.name === name ? s.location : { name }) : null,
      responseDeadlineHours: s.responseDeadlineHours,
      attendanceCheck: s.attendanceCheck,
      optionalTeamIds: s.optionalTeamIds,
    };
  }

  protected previewLines(p: Preview): { text: string; warn: boolean }[] {
    if (p.adjusted + p.created + p.removed + p.cancelled === 0) {
      return [{ text: this.c.preview.nothing, warn: false }];
    }
    const lines = [{ text: this.c.preview.from(p.from ? date(p.from) : ''), warn: false }];
    if (p.adjusted) lines.push({ text: this.c.preview.adjusted(p.adjusted), warn: false });
    if (p.created) lines.push({ text: this.c.preview.created(p.created), warn: false });
    if (p.removed) lines.push({ text: this.c.preview.removed(p.removed), warn: false });
    if (p.cancelled) lines.push({ text: this.c.preview.cancelled(p.cancelled), warn: true });
    if (p.keptAsTheyAre) lines.push({ text: this.c.preview.kept(p.keptAsTheyAre), warn: false });
    return lines;
  }

  /** «Auswirkung prüfen» (TER-19). */
  protected async check(domEvent: Event): Promise<void> {
    domEvent.preventDefault();
    await this.run('check', async () => {
      this.preview.set(
        await firstValueFrom(
          this.http.post<Preview>(`/api/series/${this.overview().series.id}/preview`, this.input()),
        ),
      );
      this.stale.set(false);
      return null;
    });
  }

  protected async apply(): Promise<void> {
    const done = await this.run('apply', async () => {
      await firstValueFrom(this.http.put(`/api/series/${this.overview().series.id}`, this.input()));
      return this.c.applied;
    });
    if (done) {
      this.panel.set(null);
      this.changed.emit();
    }
  }

  protected async holidays(domEvent: Event): Promise<void> {
    domEvent.preventDefault();
    const reason = this.hReason().trim();
    if (!this.hFrom() || !this.hUntil() || !reason) {
      this.fieldError.set(TEXTS.seriesNew.required);
      return;
    }
    const done = await this.run('holidays', async () => {
      await firstValueFrom(
        this.http.post(`/api/series/${this.overview().series.id}/exceptions`, {
          startDate: this.hFrom(),
          endDate: this.hUntil(),
          reason,
        }),
      );
      return this.c.holidaysSaved(reason);
    });
    if (done) {
      this.panel.set(null);
      this.changed.emit();
    }
  }

  /** «Serie beenden»: first what goes, then the question (TER-20). */
  protected async askEnd(domEvent: Event): Promise<void> {
    domEvent.preventDefault();
    const from = this.endFrom();
    if (!from) {
      this.fieldError.set(TEXTS.seriesNew.required);
      return;
    }
    let p: Preview | null = null;
    await this.run('end', async () => {
      p = await firstValueFrom(this.http.post<Preview>(`/api/series/${this.overview().series.id}/end/preview`, { from }));
      return null;
    });
    if (!p) return;
    const preview = p as Preview;
    this.confirm()?.ask({
      title: this.c.endTitle,
      text: this.c.endText(date(from), preview.removed, preview.cancelled),
      confirm: this.c.endConfirm,
      action: async () => {
        const done = await this.run('end', async () => {
          await firstValueFrom(this.http.post(`/api/series/${this.overview().series.id}/end`, { from }));
          return this.c.endDone;
        });
        if (done) {
          this.panel.set(null);
          this.changed.emit();
        }
      },
    });
  }

  private async run(key: string, action: () => Promise<string | null>): Promise<boolean> {
    if (this.busy()) return false;
    this.busy.set(key);
    this.error.set(null);
    this.fieldError.set(null);
    this.message.set(null);
    try {
      const text = await action();
      if (text) this.message.set(text);
      return true;
    } catch (e) {
      const fields = Object.values(fieldErrorsOf(e));
      if (fields.length > 0) this.fieldError.set(fields.join(' '));
      else this.error.set(errorText(e));
      return false;
    } finally {
      this.busy.set(null);
    }
  }
}
