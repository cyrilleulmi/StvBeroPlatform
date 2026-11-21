import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { firstValueFrom } from 'rxjs';
import type { Timeline, TimelineItem } from '../api/types';
import { clockTime } from '../attendance/time';
import { errorText } from '../core/error-text';
import { Icon } from '../core/icon';
import { TEXTS } from '../core/texts';

interface Line {
  key: string;
  kind: TimelineItem['kind'];
  time: string;
  title: string;
  detail: string | null;
  suggested: boolean;
}

/**
 * Timeline of a match day in the event detail (TML): departure, arrival, time before the first match, the
 * matches with the time between them, cool down and the approximate return. While no meeting time is set,
 * the departure is a suggestion that coaches and admins can take over (TML-04).
 */
@Component({
  selector: 'app-timeline',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  template: `
    <section class="card timeline" aria-labelledby="timeline-title" data-testid="timeline">
      <h2 id="timeline-title" class="section-title">{{ s.title }}</h2>
      @if (success(); as m) {
        <p class="message-success" role="status">{{ m }}</p>
      }
      @if (error(); as m) {
        <p class="message-error" role="alert">{{ m }}</p>
      }
      <div class="content" [attr.aria-busy]="loading() ? 'true' : null">
        @if (loading()) {
          <p class="empty">{{ texts.common.loading }}</p>
        } @else if (data(); as t) {
          <p class="secondary-text" data-testid="timeline-settings">
            {{ s.settings(t.matchDurationMinutes, t.arrivalLeadMinutes) }}
            @if (t.distanceKm !== undefined && t.driveMinutes !== undefined) {
              {{ s.route(t.distanceKm.toFixed(1), t.driveMinutes) }}.
            }
          </p>
          @for (hint of hints(); track hint.key) {
            <p class="hint" data-testid="timeline-hint" [attr.data-hint]="hint.key">
              <app-icon name="triangle-alert" [size]="16" />
              <span>{{ hint.text }}</span>
            </p>
          }
          @if (lines().length > 0) {
            <ol class="lines" [attr.aria-label]="s.listName" data-testid="timeline-items">
              @for (line of lines(); track line.key) {
                <li
                  class="line"
                  [attr.data-kind]="line.kind"
                  [attr.data-suggested]="line.suggested ? 'true' : null"
                  [attr.aria-label]="line.time + ' ' + line.title"
                >
                  <span class="time">{{ line.time }}</span>
                  <span class="marker" aria-hidden="true"></span>
                  <span class="text">
                    <span class="title">
                      {{ line.title }}
                      @if (line.suggested) {
                        <span class="chip" data-kind="suggested">{{ s.suggested }}</span>
                      }
                    </span>
                    @if (line.detail) {
                      <span class="secondary-text">{{ line.detail }}</span>
                    }
                  </span>
                </li>
              }
            </ol>
          }
          @if (t.departureSuggested) {
            <p class="secondary-text">{{ s.suggestedText }}</p>
          }
          @if (t.canApplySuggestion && t.suggestedDeparture; as suggestion) {
            <div class="apply">
              @if (!t.departureSuggested) {
                <p class="secondary-text" data-testid="timeline-suggestion">
                  {{ s.suggestion(time(suggestion)) }}
                </p>
              }
              <button
                type="button"
                class="button button-secondary button-small"
                [disabled]="running()"
                [attr.aria-busy]="running() ? 'true' : null"
                (click)="apply()"
              >
                {{ s.apply }}
              </button>
            </div>
          }
        }
      </div>
    </section>
  `,
  styles: `
    :host {
      display: contents;
    }
    .timeline,
    .content {
      display: grid;
      gap: 10px;
      min-width: 0;
    }
    .timeline p {
      margin: 0;
    }
    .hint {
      display: flex;
      align-items: flex-start;
      gap: 8px;
      padding: 8px 10px;
      border-radius: var(--radius-icon);
      background: var(--mustard-soft);
      border: 1px solid var(--mustard);
      font-size: 14px;
      font-weight: 600;
    }
    .hint app-icon {
      flex: none;
      color: var(--mustard);
      padding-top: 2px;
    }
    .lines {
      list-style: none;
      margin: 0;
      padding: 0;
      display: grid;
    }
    .line {
      display: grid;
      grid-template-columns: 52px 18px minmax(0, 1fr);
      gap: 0 10px;
      align-items: start;
      min-height: 40px;
    }
    .time {
      font-weight: 800;
      font-variant-numeric: tabular-nums;
      padding-top: 1px;
    }
    /* The line that connects the points; the marker of a match is filled */
    .marker {
      position: relative;
      align-self: stretch;
    }
    .marker::before {
      content: '';
      position: absolute;
      left: 8px;
      top: 0;
      bottom: 0;
      width: 2px;
      background: var(--border);
    }
    .line:first-child .marker::before {
      top: 8px;
    }
    .line:last-child .marker::before {
      bottom: calc(100% - 8px);
    }
    .marker::after {
      content: '';
      position: absolute;
      left: 3px;
      top: 5px;
      width: 12px;
      height: 12px;
      border-radius: 50%;
      background: var(--surface);
      border: 2px solid var(--muted);
      box-sizing: border-box;
    }
    .line[data-kind='MATCH'] .marker::after {
      background: var(--primary);
      border-color: var(--primary);
    }
    .line[data-kind='DEPARTURE'] .marker::after,
    .line[data-kind='RETURN'] .marker::after {
      border-color: var(--primary);
    }
    .text {
      display: grid;
      gap: 2px;
      padding-bottom: 12px;
      min-width: 0;
      overflow-wrap: anywhere;
    }
    .title {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 6px 8px;
      font-weight: 600;
    }
    .line[data-kind='MATCH'] .title,
    .line[data-kind='DEPARTURE'] .title,
    .line[data-kind='RETURN'] .title {
      font-weight: 800;
    }
    .chip[data-kind='suggested'] {
      background: var(--mustard-soft);
      color: var(--text);
    }
    .apply {
      display: grid;
      gap: 8px;
      justify-items: start;
    }
  `,
})
export class TimelineSection {
  private readonly http = inject(HttpClient);
  protected readonly texts = TEXTS;
  protected readonly s = TEXTS.timeline;
  protected readonly time = clockTime;

  readonly eventId = input.required<string>();
  /** Name of the own team, for «Team 1 – UHC Musterdorf». */
  readonly team = input('');
  /** Changes when the event was reloaded (matches, meeting point): the timeline loads again. */
  readonly version = input(0);
  /** The meeting time was taken over: the page shows the event again. */
  readonly changed = output<void>();

  protected readonly data = signal<Timeline | null>(null);
  protected readonly loading = signal(true);
  protected readonly running = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly success = signal<string | null>(null);

  protected readonly hints = computed(() => {
    const t = this.data();
    if (!t) return [];
    return t.hints.map((hint) => ({
      key: hint,
      text: hint === 'ARRIVES_LATE' ? this.s.hint.ARRIVES_LATE(t.arrivalLeadMinutes) : this.s.hint[hint],
    }));
  });

  protected readonly lines = computed<Line[]>(() => {
    const t = this.data();
    if (!t) return [];
    let match = 0;
    return t.items.map((item, index) => {
      const span = item.end ? this.s.span(clockTime(item.start), clockTime(item.end)) : null;
      const minutes = item.minutes != null ? this.s.minutes(item.minutes) : null;
      let title: string = this.s.kind[item.kind];
      let detail: string | null = null;
      switch (item.kind) {
        case 'MATCH': {
          match += 1;
          const opponent = item.opponent ?? '';
          const pairing = item.home
            ? TEXTS.matchDay.pairing(this.team(), opponent)
            : TEXTS.matchDay.pairing(opponent, this.team());
          title = this.s.match(match, pairing);
          detail = [span, minutes].filter(Boolean).join(' · ');
          break;
        }
        case 'DEPARTURE':
        case 'RETURN':
          detail = item.minutes != null ? this.s.drive(item.minutes) : null;
          break;
        case 'ARRIVAL':
          break;
        default:
          detail = [span, minutes].filter(Boolean).join(' · ');
      }
      return {
        key: `${index}-${item.kind}`,
        kind: item.kind,
        time: clockTime(item.start),
        title,
        detail,
        suggested: item.kind === 'DEPARTURE' && t.departureSuggested,
      };
    });
  });

  constructor() {
    effect(() => {
      const id = this.eventId();
      this.version();
      untracked(() => void this.load(id));
    });
  }

  private async load(id: string): Promise<void> {
    try {
      this.data.set(await firstValueFrom(this.http.get<Timeline>(`/api/events/${id}/timeline`)));
      this.error.set(null);
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.loading.set(false);
    }
  }

  /** Takes over the suggested departure as meeting time (TML-04). */
  protected async apply(): Promise<void> {
    if (this.running()) return;
    this.running.set(true);
    this.error.set(null);
    this.success.set(null);
    try {
      const t = await firstValueFrom(
        this.http.post<Timeline>(`/api/events/${this.eventId()}/timeline/apply-departure`, null),
      );
      this.data.set(t);
      this.success.set(t.departure ? this.s.applied(clockTime(t.departure)) : null);
      this.changed.emit();
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.running.set(false);
    }
  }
}
