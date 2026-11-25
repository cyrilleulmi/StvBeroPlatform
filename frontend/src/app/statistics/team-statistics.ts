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
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { TeamStatistics } from '../api/types';
import { AuthService } from '../core/auth.service';
import { errorStatus, errorText } from '../core/error-text';
import { date } from '../core/format';
import { TEXTS } from '../core/texts';
import { apiParams, sortKey, sortRows, type SortKey } from './statistics-common';
import { StatisticsFilter } from './statistics-filter';
import { StatisticsTable } from './statistics-table';

const SORT_COLUMN: Record<SortKey, string> = {
  name: 'name',
  zusage: 'attendingRate',
  praesenz: 'attendanceRate',
};

/**
 * Statistics of a team (`/statistik`, coaches and admins, STA-01 to STA-05): per player the responses
 * and, where recorded, the attendance of the past events in the period, grouped by line, sortable, with
 * the team average. Filters and sorting are in the URL.
 */
@Component({
  selector: 'app-team-statistics',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, StatisticsFilter, StatisticsTable],
  template: `
    <div class="header">
      <a class="back" routerLink="/termine">{{ texts.events.allEvents }}</a>
      <h1>{{ t.title }}</h1>
      @if (data()?.team; as team) {
        <p class="secondary-text">{{ t.season(team.name, data()!.season.name) }}</p>
      }
    </div>
    @if (error(); as message) {
      <p class="message-error" role="alert">{{ message }}</p>
      @if (!data() && auth.canManage()) {
        <a routerLink="/statistik" [queryParams]="{ team: team() ?? null }">{{ t.wholeSeason }}</a>
      }
    }
    @if (data(); as d) {
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
    <div class="sections" [attr.aria-busy]="loading() ? 'true' : null">
      @if (loading()) {
        <p class="empty">{{ texts.common.loading }}</p>
      } @else if (data(); as d) {
        @if (!d.team) {
          <p class="empty">{{ t.noTeams }}</p>
        } @else {
          <section class="card" aria-labelledby="statistics-base-title">
            <h2 id="statistics-base-title" class="section-title">
              {{ t.period(date(d.from), date(d.to)) }}
            </h2>
            <p data-testid="statistics-base">{{ t.base(d.events, d.attendanceEvents) }}</p>
            <p class="secondary-text">{{ t.countsHint }}</p>
            @if (d.groups.length > 0) {
              <div class="field sort">
                <label for="statistics-sort">{{ t.sort }}</label>
                <select id="statistics-sort" (change)="setSort($any($event.target).value)">
                  <option value="name" [selected]="sort() === 'name'">{{ t.sortName }}</option>
                  <option value="zusage" [selected]="sort() === 'zusage'">
                    {{ t.sortAttending }}
                  </option>
                  <option value="praesenz" [selected]="sort() === 'praesenz'">
                    {{ t.sortAttendance }}
                  </option>
                </select>
              </div>
            }
          </section>
          @if (d.groups.length === 0) {
            <p class="empty">{{ t.noPlayers }}</p>
          }
          @for (g of d.groups; track g.groupId ?? 'no-line') {
            @let key = g.groupId ? 'statistics-group-' + g.groupId : 'statistics-no-line';
            <section class="card" [attr.data-testid]="key" [attr.aria-labelledby]="key + '-title'">
              <h2 class="section-title" [id]="key + '-title'">{{ g.name }}</h2>
              <app-statistics-table
                [label]="t.table(g.name)"
                [rows]="sorted(g.players)"
                [sortColumn]="sortColumn()"
              />
            </section>
          }
          @if (d.average; as a) {
            <section
              class="card"
              data-testid="statistics-average"
              aria-labelledby="statistics-average-title"
            >
              <h2 id="statistics-average-title" class="section-title">{{ t.average }}</h2>
              <p class="secondary-text">{{ t.averageHint }}</p>
              <app-statistics-table [label]="t.average" [average]="a" />
            </section>
          }
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
    .sections > .card {
      min-width: 0;
    }
    .sort {
      max-width: 320px;
      margin-top: 12px;
    }
  `,
})
export class TeamStatisticsPage {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);
  protected readonly texts = TEXTS;
  protected readonly t = TEXTS.statistics;
  protected readonly date = date;

  /** Query `?team=` */
  readonly team = input<string | undefined>(undefined);
  /** Query `?von=` (German browser URL, NF-40) */
  // eslint-disable-next-line @angular-eslint/no-input-rename -- query parameter is part of the browser URL and stays German (NF-40)
  readonly from = input<string | undefined>(undefined, { alias: 'von' });
  /** Query `?bis=` */
  // eslint-disable-next-line @angular-eslint/no-input-rename -- query parameter is part of the browser URL and stays German (NF-40)
  readonly to = input<string | undefined>(undefined, { alias: 'bis' });
  /** Query `?art=training|spieltag` */
  // eslint-disable-next-line @angular-eslint/no-input-rename -- query parameter is part of the browser URL and stays German (NF-40)
  readonly type = input<string | undefined>(undefined, { alias: 'art' });
  /** Query `?sortierung=name|zusage|praesenz` */
  // eslint-disable-next-line @angular-eslint/no-input-rename -- query parameter is part of the browser URL and stays German (NF-40)
  readonly sortParam = input<string | undefined>(undefined, { alias: 'sortierung' });

  protected readonly data = signal<TeamStatistics | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly sort = computed(() => sortKey(this.sortParam()));
  protected readonly sortColumn = computed(() => SORT_COLUMN[this.sort()]);

  constructor() {
    effect(() => {
      const params = apiParams(this.team(), this.from(), this.to(), this.type());
      untracked(() => void this.load(params));
    });
  }

  protected sorted(rows: TeamStatistics['groups'][number]['players']) {
    return sortRows(rows, this.sort());
  }

  protected setSort(value: string): void {
    void this.router.navigate([], {
      queryParams: { sortierung: value === 'name' ? null : value },
      queryParamsHandling: 'merge',
    });
  }

  /** Counts the loads: a slow answer to an earlier filter must not replace the answer to the latest one. */
  private request = 0;

  private async load(params: Record<string, string>): Promise<void> {
    if (!this.auth.canManage()) {
      // Players see only their own numbers (STA-08); the backend refuses anyway (403).
      this.error.set(TEXTS.common.noPermission);
      this.loading.set(false);
      return;
    }
    const nr = ++this.request;
    this.loading.set(true);
    this.error.set(null);
    try {
      const data = await firstValueFrom(
        this.http.get<TeamStatistics>('/api/statistics', { params }),
      );
      if (nr === this.request) this.data.set(data);
    } catch (e) {
      if (nr === this.request) {
        this.error.set(errorStatus(e) === 404 ? TEXTS.teams.notFound : errorText(e));
      }
    } finally {
      if (nr === this.request) this.loading.set(false);
    }
  }
}
