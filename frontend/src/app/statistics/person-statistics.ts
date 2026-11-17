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
import type { PersonStatistics } from '../api/types';
import { responseKey } from '../attendance/response';
import { clockTime, eventDate, yearOf } from '../attendance/time';
import { AuthService } from '../core/auth.service';
import { errorStatus, errorText } from '../core/error-text';
import { date, name } from '../core/format';
import { TEXTS } from '../core/texts';
import { COLUMNS, apiParams, playerValue } from './statistics-common';
import { StatisticsFilter } from './statistics-filter';

/**
 * Statistics of one player with her events in the period (STA-06). Coaches and admins open it from the
 * team statistics (`/statistik/:personId`), players see their own under the profile
 * (`/profil/statistik`, STA-08). Filters are in the URL.
 */
@Component({
  selector: 'app-person-statistics',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, StatisticsFilter],
  template: `
    <div class="header">
      @if (own()) {
        <a class="back" routerLink="/profil">{{ t.toProfile }}</a>
        <h1>{{ t.myTitle }}</h1>
      } @else {
        <a class="back" routerLink="/statistik" [queryParams]="backParams()">{{
          t.toStatistics
        }}</a>
        <h1>{{ data() ? name(data()!.person) : t.title }}</h1>
      }
      @if (data()?.team; as team) {
        <p class="secondary-text">{{ t.season(team.name, data()!.season.name) }}</p>
      }
    </div>
    @if (error(); as message) {
      <p class="message-error" role="alert">{{ message }}</p>
    }
    @if (data(); as d) {
      @if (d.teams.length > 0) {
        <app-statistics-filter
          [teams]="d.teams"
          [teamId]="d.team?.id"
          [from]="d.from"
          [to]="d.to"
          [type]="type()"
          [seasonStart]="d.season.startDate"
          [seasonEnd]="d.season.endDate"
          [customPeriod]="!!from() || !!to()"
        />
      }
    }
    <div class="sections" [attr.aria-busy]="loading() ? 'true' : null">
      @if (loading()) {
        <p class="empty">{{ texts.common.loading }}</p>
      } @else if (data(); as d) {
        @if (d.statistics; as s) {
          <section
            class="card"
            data-testid="statistics-numbers"
            aria-labelledby="statistics-numbers-title"
          >
            <h2 id="statistics-numbers-title" class="section-title">
              {{ t.period(date(d.from), date(d.to)) }}
            </h2>
            <dl class="numbers">
              @for (c of columns; track c) {
                <div class="number" [attr.data-column]="c">
                  <dt>{{ t.column[c] }}</dt>
                  <dd>{{ value(s, c) }}</dd>
                </div>
              }
            </dl>
            <p class="secondary-text" data-testid="statistics-base">
              {{ t.attendanceBase(s.attendanceEvents) }}
              @if (s.joinedOn > d.season.startDate) {
                {{ t.joinedOn(date(s.joinedOn)) }}
              }
            </p>
            <p class="secondary-text">{{ t.countsHint }}</p>
          </section>
          <section class="card" aria-labelledby="statistics-events-title">
            <h2 id="statistics-events-title" class="section-title">
              {{ t.events }} <span class="count">{{ d.events.length }}</span>
            </h2>
            @if (d.events.length === 0) {
              <p class="empty">{{ t.noEvents }}</p>
            } @else {
              <ul class="list" data-testid="statistics-events" [attr.aria-label]="t.events">
                @for (e of d.events; track e.id) {
                  <li
                    class="event"
                    [attr.data-testid]="'statistics-event-' + e.id"
                    [attr.data-response]="e.response"
                    [attr.data-attendance]="e.attendance ?? null"
                  >
                    <a [routerLink]="['/termine', e.id]">{{ eventName(e) }}</a>
                    <span class="details">
                      <span class="chip" [attr.data-kind]="responseKey(e.response)">{{
                        t.response(texts.response.status[e.response])
                      }}</span>
                      @if (e.lateCancellation) {
                        <span class="chip" data-kind="late">{{
                          texts.response.lateCancellation
                        }}</span>
                      }
                      @if (e.attendance; as a) {
                        <span class="chip" [attr.data-kind]="attendanceKey(a)">{{
                          t.attendance(texts.attendanceCheck.status[a])
                        }}</span>
                      }
                    </span>
                  </li>
                }
              </ul>
            }
          </section>
        } @else {
          <p class="empty">{{ own() ? t.notPlaying : t.personNotPlaying }}</p>
        }
      }
    </div>
  `,
  styles: `
    :host,
    .sections {
      display: grid;
      gap: var(--section-gap);
      min-width: 0;
    }
    .numbers {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 8px 16px;
      margin: 0 0 12px;
    }
    @media (min-width: 640px) {
      .numbers {
        grid-template-columns: repeat(4, minmax(0, 1fr));
      }
    }
    .number {
      display: flex;
      justify-content: space-between;
      gap: 8px;
      padding: 6px 0;
      border-bottom: 1px solid var(--border);
    }
    .number dt {
      color: var(--muted);
    }
    .number dd {
      margin: 0;
      font-weight: 700;
      font-variant-numeric: tabular-nums;
    }
    .event {
      display: grid;
      gap: 6px;
      padding: 10px 0;
      border-bottom: 1px solid var(--border);
    }
    .event:last-child {
      border-bottom: 0;
    }
    .event a {
      font-weight: 600;
      color: var(--text);
    }
    .chip[data-kind='attending'],
    .chip[data-kind='present'] {
      background: var(--primary-soft);
    }
    .chip[data-kind='declined'],
    .chip[data-kind='absent'],
    .chip[data-kind='late'] {
      background: var(--coral-soft);
    }
    .chip[data-kind='maybe'] {
      background: var(--mustard-soft);
    }
    .chip[data-kind='excused'] {
      background: var(--blue-soft);
    }
    .details {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
    }
  `,
})
export class PersonStatisticsPage {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  protected readonly texts = TEXTS;
  protected readonly t = TEXTS.statistics;
  protected readonly date = date;
  protected readonly name = name;
  protected readonly columns = COLUMNS;
  protected readonly value = playerValue;
  protected readonly responseKey = responseKey;
  protected readonly attendanceKey = (status: string) => status.toLowerCase();

  /** Route `/statistik/:personId`; missing under `/profil/statistik` (own numbers). */
  readonly personId = input<string | undefined>(undefined);
  /** Query `?team=` */
  readonly team = input<string | undefined>(undefined);
  // eslint-disable-next-line @angular-eslint/no-input-rename -- query parameter is part of the browser URL and stays German (NF-40)
  readonly from = input<string | undefined>(undefined, { alias: 'von' });
  // eslint-disable-next-line @angular-eslint/no-input-rename -- query parameter is part of the browser URL and stays German (NF-40)
  readonly to = input<string | undefined>(undefined, { alias: 'bis' });
  // eslint-disable-next-line @angular-eslint/no-input-rename -- query parameter is part of the browser URL and stays German (NF-40)
  readonly type = input<string | undefined>(undefined, { alias: 'art' });

  protected readonly data = signal<PersonStatistics | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly own = computed(() => !this.personId());
  private readonly year = computed(() => yearOf(this.auth.me()?.now));

  /** Back to the team statistics with the same filters. */
  protected readonly backParams = computed(() => ({
    team: this.data()?.team?.id ?? this.team() ?? null,
    von: this.from() ?? null,
    bis: this.to() ?? null,
    art: this.type() ?? null,
  }));

  constructor() {
    effect(() => {
      const person = this.personId() ?? this.auth.me()?.id;
      const params = apiParams(this.team(), this.from(), this.to(), this.type());
      untracked(() => void this.load(person, params));
    });
  }

  protected eventName(e: PersonStatistics['events'][number]): string {
    return TEXTS.events.card(e.title, eventDate(e.start, this.year()), clockTime(e.start));
  }

  private async load(person: string | undefined, params: Record<string, string>): Promise<void> {
    if (!person) return;
    this.loading.set(true);
    this.error.set(null);
    try {
      this.data.set(
        await firstValueFrom(
          this.http.get<PersonStatistics>(`/api/statistics/persons/${person}`, { params }),
        ),
      );
    } catch (e) {
      this.data.set(null);
      this.error.set(errorStatus(e) === 404 ? TEXTS.statistics.personNotPlaying : errorText(e));
    } finally {
      this.loading.set(false);
    }
  }
}
