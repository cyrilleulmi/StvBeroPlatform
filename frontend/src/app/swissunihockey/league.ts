import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { League as LeagueData, LeagueMatch } from '../api/types';
import { clockTime, parts, weekday } from '../core/time';
import { AuthService } from '../core/auth.service';
import { errorStatus, errorText } from '../core/error-text';
import { TEXTS } from '../core/texts';

type Outcome = 'WIN' | 'DRAW' | 'LOSS';

const two = (n: number) => String(n).padStart(2, '0');

/** «Mo 16.11.2026, 04:30» */
function dateTime(iso: string): string {
  const t = parts(iso);
  return `${weekday(t)} ${two(t.day)}.${two(t.month)}.${t.year}, ${clockTime(iso)}`;
}

/** «Sa 15.11.2026» */
function day(iso: string): string {
  const t = parts(iso);
  return `${weekday(t)} ${two(t.day)}.${two(t.month)}.${t.year}`;
}

/** Win, draw or loss from the view of the own team, from «home:away». */
function outcome(m: LeagueMatch): Outcome | null {
  const match = /^(\d+):(\d+)$/.exec(m.result ?? '');
  if (!match) return null;
  const [home, away] = [Number(match[1]), Number(match[2])];
  const own = m.home ? home - away : away - home;
  return own > 0 ? 'WIN' : own < 0 ? 'LOSS' : 'DRAW';
}

/**
 * League of a team (`/teams/:teamId/liga`, F-17): league and group, table, upcoming matches and the latest
 * results from swiss unihockey. The data comes from the cache of the daily sync, never live; admins can reload
 * it. If swiss unihockey was not reachable, the last loaded data stays with a hint (SUH-09).
 */
@Component({
  selector: 'app-league',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <div class="header">
      <a class="back" [routerLink]="['/teams', teamId()]">{{
        data() ? t.backTo(data()!.teamName) : texts.common.back
      }}</a>
      <h1>{{ t.title }}</h1>
      @if (data(); as d) {
        <p class="secondary-text">{{ d.teamName }}</p>
      }
    </div>
    @if (error(); as message) {
      <p class="message-error" role="alert">{{ message }}</p>
    }
    <div class="sections" [attr.aria-busy]="loading() ? 'true' : null">
      @if (loading()) {
        <p class="empty">{{ texts.common.loading }}</p>
      } @else if (data(); as d) {
        @if (!d.linked) {
          <section class="card" aria-labelledby="league-info-title" data-testid="league-not-linked">
            <h2 id="league-info-title" class="section-title">{{ t.title }}</h2>
            <p>{{ t.notLinked }}</p>
            @if (auth.isAdmin()) {
              <a [routerLink]="['/verwaltung/teams', d.teamId]" [queryParams]="{ bereich: 'einstellungen' }">{{
                t.linkInAdministration
              }}</a>
            }
          </section>
        } @else {
          <section class="card" aria-labelledby="league-info-title" data-testid="league-info">
            <h2 id="league-info-title" class="section-title">
              {{ t.leagueGroup(d.league ?? null, d.group ?? null) || t.title }}
            </h2>
            @if (d.loadedAt) {
              <p data-testid="league-loaded-at">{{ t.loadedAt(dateTime(d.loadedAt)) }}</p>
              @if (d.failedAt) {
                <p class="message-notice" data-testid="league-stale">
                  {{ t.stale(dateTime(d.failedAt), dateTime(d.loadedAt)) }}
                </p>
              }
            } @else {
              <p class="empty" data-testid="league-never-loaded">{{ t.neverLoaded }}</p>
              @if (d.failedAt) {
                <p class="message-notice" data-testid="league-stale">
                  {{ t.failedNever(dateTime(d.failedAt)) }}
                </p>
              }
            }
            <p class="secondary-text">{{ t.dailyHint }}</p>
            @if (refreshMessage(); as message) {
              <p class="message-success" role="status">{{ message }}</p>
            }
            @if (refreshError(); as message) {
              <p class="message-error" role="alert">{{ message }}</p>
            }
            @if (auth.isAdmin()) {
              <div>
                <button
                  type="button"
                  class="button button-secondary button-small"
                  data-testid="league-refresh"
                  [disabled]="refreshing()"
                  [attr.aria-busy]="refreshing() ? 'true' : null"
                  (click)="refresh()"
                >
                  {{ t.refresh }}
                </button>
              </div>
            }
          </section>

          @if (d.loadedAt) {
            <section class="card" aria-labelledby="league-table-title" data-testid="league-table">
              <h2 id="league-table-title" class="section-title">{{ t.table }}</h2>
              @if (d.table.length === 0) {
                <p class="empty">{{ t.noTable }}</p>
              } @else {
                <!-- Rank, team and points first: readable at 375px, the rest scrolls inside the card -->
                <div class="scroll" tabindex="0" role="region" [attr.aria-label]="t.table">
                  <table [attr.aria-label]="t.table">
                    <thead>
                      <tr>
                        <th scope="col" class="rank">{{ t.column.rank }}</th>
                        <th scope="col" class="team">{{ t.column.team }}</th>
                        <th scope="col">{{ t.column.points }}</th>
                        <th scope="col">{{ t.column.matches }}</th>
                        <th scope="col">{{ t.column.wins }}</th>
                        <th scope="col">{{ t.column.draws }}</th>
                        <th scope="col">{{ t.column.losses }}</th>
                        <th scope="col">{{ t.column.goals }}</th>
                      </tr>
                    </thead>
                    <tbody>
                      @for (r of d.table; track $index) {
                        <tr
                          [attr.data-testid]="'league-row-' + r.rank"
                          [attr.data-own]="r.own ? 'true' : null"
                          [class.own]="r.own"
                        >
                          <td class="rank">{{ r.rank }}</td>
                          <th scope="row" class="team">
                            <span class="team-name">{{ r.team }}</span>
                            @if (r.own) {
                              <span class="chip" data-testid="league-own-team">{{ t.ownTeam }}</span>
                            }
                          </th>
                          <td data-column="points" class="points">{{ r.points }}</td>
                          <td data-column="matches">{{ r.matches }}</td>
                          <td data-column="wins">{{ r.wins }}</td>
                          <td data-column="draws">{{ r.draws }}</td>
                          <td data-column="losses">{{ r.losses }}</td>
                          <td data-column="goals">{{ r.goalsFor }}:{{ r.goalsAgainst }}</td>
                        </tr>
                      }
                    </tbody>
                  </table>
                </div>
              }
            </section>

            <section class="card" aria-labelledby="league-upcoming-title" data-testid="league-upcoming">
              <h2 id="league-upcoming-title" class="section-title">{{ t.upcoming }}</h2>
              @if (d.upcoming.length === 0) {
                <p class="empty">{{ t.noUpcoming }}</p>
              } @else {
                <ul class="list" [attr.aria-label]="t.upcoming">
                  @for (m of d.upcoming; track m.suMatchId) {
                    <li [attr.data-testid]="'league-upcoming-' + m.suMatchId">
                      @if (m.eventId) {
                        <a class="match match-link" [routerLink]="['/termine', m.eventId]">
                          <span class="when">{{ day(m.startTime) }} · {{ time(m.startTime) }}</span>
                          <span class="what">{{ m.home ? t.home(m.opponent) : t.away(m.opponent) }}</span>
                          <span class="secondary-text">{{ t.venue(m.hall, m.location ?? null) }}</span>
                          <span class="to-match-day">{{ t.toMatchDay }}</span>
                        </a>
                      } @else {
                        <div class="match">
                          <span class="when">{{ day(m.startTime) }} · {{ time(m.startTime) }}</span>
                          <span class="what">{{ m.home ? t.home(m.opponent) : t.away(m.opponent) }}</span>
                          <span class="secondary-text">{{ t.venue(m.hall, m.location ?? null) }}</span>
                        </div>
                      }
                    </li>
                  }
                </ul>
              }
            </section>

            <section class="card" aria-labelledby="league-results-title" data-testid="league-results">
              <h2 id="league-results-title" class="section-title">{{ t.results }}</h2>
              @if (d.results.length === 0) {
                <p class="empty">{{ t.noResults }}</p>
              } @else {
                <ul class="list" [attr.aria-label]="t.results">
                  @for (m of d.results; track m.suMatchId) {
                    @let o = outcome(m);
                    <li
                      class="match"
                      [attr.data-testid]="'league-result-' + m.suMatchId"
                      [attr.data-outcome]="o"
                    >
                      <span class="when">{{ day(m.startTime) }}</span>
                      <span class="what">
                        {{ m.homeTeam }} – {{ m.awayTeam }} <strong class="result">{{ m.result }}</strong>
                      </span>
                      @if (o) {
                        <span class="chip outcome" [attr.data-kind]="o">{{ t.outcome[o] }}</span>
                      }
                    </li>
                  }
                </ul>
              }
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
    .card > p {
      margin: 0 0 8px;
    }
    .scroll {
      overflow-x: auto;
      margin: 0 -4px;
      border-radius: 12px;
    }
    .scroll:focus-visible {
      outline: 2px solid var(--primary);
      outline-offset: 2px;
    }
    table {
      border-collapse: separate;
      border-spacing: 0;
      width: 100%;
      font-variant-numeric: tabular-nums;
    }
    th,
    td {
      padding: 10px 6px;
      text-align: right;
      white-space: nowrap;
      border-bottom: 1px solid var(--border);
    }
    thead th {
      font-size: 12px;
      font-weight: 700;
      color: var(--muted);
      vertical-align: bottom;
      white-space: normal;
      min-width: 44px;
    }
    .rank {
      width: 1%;
      min-width: 36px;
    }
    .team {
      text-align: left;
      white-space: normal;
      min-width: 120px;
      font-weight: 500;
    }
    tbody .team {
      overflow-wrap: anywhere;
    }
    .team-name {
      margin-right: 6px;
    }
    .points {
      font-weight: 700;
    }
    /* Own team: bold, a bar at the start and the chip «Unser Team», not only a colour (criterion 2) */
    tr.own td,
    tr.own th {
      background: var(--primary-soft);
      font-weight: 700;
    }
    tr.own td.rank {
      box-shadow: inset 4px 0 0 var(--primary);
    }
    tbody tr:last-child th,
    tbody tr:last-child td {
      border-bottom: 0;
    }
    .list {
      list-style: none;
      margin: 0;
      padding: 0;
    }
    .list > li + li {
      border-top: 1px solid var(--border);
    }
    .match {
      display: grid;
      gap: 2px;
      padding: 10px 0;
    }
    .match-link {
      color: var(--text);
      text-decoration: none;
      min-height: var(--touch);
    }
    .match-link:focus-visible .what {
      text-decoration: underline;
    }
    @media (hover: hover) {
      .match-link:hover .what {
        text-decoration: underline;
      }
    }
    .when {
      font-size: 13px;
      font-weight: 700;
      color: var(--muted);
    }
    .what {
      font-weight: 600;
      overflow-wrap: anywhere;
    }
    .to-match-day {
      color: var(--text);
      font-size: 13px;
      font-weight: 700;
      text-decoration: underline;
    }
    .result {
      margin-left: 4px;
      font-variant-numeric: tabular-nums;
    }
    .outcome {
      justify-self: start;
    }
    .outcome[data-kind='LOSS'] {
      background: var(--mustard-soft);
    }
    .outcome[data-kind='DRAW'] {
      background: var(--blue-soft);
    }
  `,
})
export class League {
  private readonly http = inject(HttpClient);
  protected readonly auth = inject(AuthService);
  protected readonly texts = TEXTS;
  protected readonly t = TEXTS.league;
  protected readonly dateTime = dateTime;
  protected readonly day = day;
  protected readonly time = clockTime;
  protected readonly outcome = outcome;

  /** Route `/teams/:teamId/liga` */
  readonly teamId = input.required<string>();

  protected readonly data = signal<LeagueData | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly refreshing = signal(false);
  protected readonly refreshMessage = signal<string | null>(null);
  protected readonly refreshError = signal<string | null>(null);

  constructor() {
    effect(() => {
      const teamId = this.teamId();
      untracked(() => void this.load(teamId));
    });
  }

  /** Reload from swiss unihockey (admins, criterion 6). On failure the last data stays with a hint. */
  protected async refresh(): Promise<void> {
    const d = this.data();
    if (!d || this.refreshing()) return;
    this.refreshing.set(true);
    this.refreshMessage.set(null);
    this.refreshError.set(null);
    try {
      this.data.set(
        await firstValueFrom(this.http.post<LeagueData>(`/api/teams/${d.teamId}/league/refresh`, null)),
      );
      this.refreshMessage.set(this.t.refreshed);
    } catch (e) {
      this.refreshError.set(errorText(e));
      // The failed attempt is stored: show the hint with its date.
      try {
        this.data.set(await firstValueFrom(this.http.get<LeagueData>(`/api/teams/${d.teamId}/league`)));
      } catch {
        // the error message is already shown
      }
    } finally {
      this.refreshing.set(false);
    }
  }

  private async load(teamId: string): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    this.data.set(null);
    const me = this.auth.me();
    const member = me?.memberships.some((m) => m.team.id === teamId) ?? false;
    if (me && !this.auth.canManage() && !member) {
      // Without membership do not even ask; the backend refuses (403, criterion 10).
      this.error.set(TEXTS.common.noPermission);
      this.loading.set(false);
      return;
    }
    try {
      this.data.set(await firstValueFrom(this.http.get<LeagueData>(`/api/teams/${teamId}/league`)));
    } catch (e) {
      this.error.set(errorStatus(e) === 404 ? TEXTS.teams.notFound : errorText(e));
    } finally {
      this.loading.set(false);
    }
  }
}
