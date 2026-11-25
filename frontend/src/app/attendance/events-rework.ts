import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { components } from '../api/schema';
import type { EventSummary, Season, Team, TeamDetail } from '../api/types';
import { AuthService } from '../core/auth.service';
import { errorText } from '../core/error-text';
import { Icon } from '../core/icon';
import { TEXTS } from '../core/texts';
import { EventVariantsSection } from '../pages/administration/event-variants';
import { MatchDaySettingsForm } from '../pages/administration/match-day-settings-form';
import { SuConnectionSection } from '../pages/administration/su-connection';
import { SeriesCard } from './series-card';
import { SeriesNew } from './series-new';
import { clockTime, eventDate, yearOf } from './time';

type Overview = components['schemas']['SeriesOverview'];

/**
 * «Termine überarbeiten» (`/termine/ueberarbeiten?team=<id>`, TER-21): the events of a team in one place for coaches
 * and admins. The series with a preview before every change (TER-18 to TER-20), the next single events, the match
 * day settings, swiss unihockey and the event variants.
 */
@Component({
  selector: 'app-events-rework',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon, SeriesCard, SeriesNew, MatchDaySettingsForm, SuConnectionSection, EventVariantsSection],
  template: `
    <div class="header">
      <a class="back" routerLink="/termine">{{ texts.events.allEvents }}</a>
      <h1>{{ r.title }}</h1>
    </div>
    @if (!auth.canManage()) {
      <p class="message-error" role="alert">{{ texts.common.noPermission }}</p>
    } @else if (loadingTeams()) {
      <p class="empty" aria-busy="true">{{ texts.common.loading }}</p>
    } @else if (teams().length === 0) {
      <p class="empty">{{ r.noTeam }}</p>
    } @else {
      <div class="field team-switch">
        <label for="rework-team">{{ r.team }}</label>
        <select id="rework-team" name="rework-team" (change)="switchTeam($any($event.target).value)">
          @for (t of teams(); track t.id) {
            <option [value]="t.id" [selected]="t.id === teamId()">{{ t.name }}</option>
          }
        </select>
      </div>
      <p class="message-notice intro" data-testid="rework-intro">
        <app-icon name="info" [size]="18" />
        <span>{{ r.intro }}</span>
      </p>
      @if (error(); as m) {
        <p class="message-error" role="alert">{{ m }}</p>
      }
      <div class="sections" [attr.aria-busy]="loading() ? 'true' : null">
        <section class="card" aria-labelledby="rework-series-title" data-testid="rework-series">
          <div class="section-head">
            <h2 id="rework-series-title" class="section-title">{{ r.seriesTitle }}</h2>
            <button
              type="button"
              class="button button-secondary button-small"
              data-testid="rework-new-series"
              [attr.aria-expanded]="newSeries()"
              (click)="newSeries.set(!newSeries())"
            >
              <app-icon [name]="newSeries() ? 'x' : 'calendar'" [size]="16" />
              {{ newSeries() ? texts.seriesCard.cancel : r.newSeries }}
            </button>
          </div>
          @if (newSeries() && detail(); as t) {
            <app-series-new [teamId]="t.id" [season]="season()" (created)="seriesCreated()" />
          }
          @if (series().length === 0 && !loading()) {
            <p class="empty">{{ r.seriesNone }}</p>
          }
          @for (o of series(); track o.series.id) {
            <app-series-card [overview]="o" (changed)="reloadSeries()" />
          }
        </section>

        <section class="card" aria-labelledby="rework-single-title" data-testid="rework-single">
          <div class="section-head">
            <h2 id="rework-single-title" class="section-title">{{ r.singleTitle }}</h2>
            <a class="button button-secondary button-small" routerLink="/termine/neu">{{ r.newEvent }}</a>
          </div>
          <p class="secondary-text">{{ r.singleText }}</p>
          @if (single().length === 0) {
            <p class="empty">{{ r.singleNone }}</p>
          } @else {
            <ul class="list" [attr.aria-label]="r.singleTitle">
              @for (e of single(); track e.id) {
                <li class="row single" [attr.aria-label]="e.title + ' ' + day(e)">
                  <a class="row-title" [routerLink]="['/termine', e.id]">
                    {{ day(e) }} {{ time(e) }} · {{ e.title }}
                    @if (e.status === 'CANCELLED') {
                      <span class="chip" data-kind="cancelled">{{ texts.events.cancelled }}</span>
                    }
                  </a>
                  <a
                    class="button button-secondary button-small"
                    [routerLink]="['/termine', e.id, 'bearbeiten']"
                    [attr.aria-label]="r.editEventOf(e.title, day(e))"
                    >{{ r.editEvent }}</a
                  >
                </li>
              }
            </ul>
          }
        </section>

        @if (detail(); as t) {
          <section class="card" aria-labelledby="rework-match-days-title">
            <h2 id="rework-match-days-title" class="section-title">{{ texts.matchDaySettings.title }}</h2>
            <p class="secondary-text">{{ texts.matchDaySettings.explanation }}</p>
            <app-match-day-settings [teamId]="t.id" [settings]="t.matchDaySettings" />
          </section>

          <app-su-connection [teamId]="t.id" [teamName]="t.name" (changed)="reload()" />

          <details class="card variants" data-testid="rework-variants">
            <summary>
              <h2 class="section-title">{{ r.variantsTitle }}</h2>
            </summary>
            <app-event-variants [teamId]="t.id" />
          </details>
        }
      </div>
    }
  `,
  styleUrl: '../pages/page.css',
  styles: `
    .sections {
      display: grid;
      gap: var(--section-gap);
      min-width: 0;
    }
    .card {
      display: grid;
      gap: 12px;
      min-width: 0;
    }
    .team-switch {
      max-width: 360px;
    }
    .intro {
      display: flex;
      gap: 10px;
      align-items: flex-start;
    }
    .section-head {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 8px 12px;
    }
    .section-head .section-title {
      margin: 0;
    }
    .section-head .button {
      display: inline-flex;
      align-items: center;
      gap: 6px;
    }
    .single {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 6px 12px;
    }
    .single .row-title {
      min-width: 0;
      overflow-wrap: anywhere;
    }
    .variants summary {
      cursor: pointer;
      list-style: revert;
    }
    .variants summary .section-title {
      display: inline;
    }
  `,
})
export class EventsRework {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);
  protected readonly texts = TEXTS;
  protected readonly r = TEXTS.rework;

  /** Query `?team=<id>`; without it the first team the person coaches. */
  readonly team = input<string | undefined>(undefined);

  protected readonly teams = signal<Team[]>([]);
  protected readonly seasons = signal<Season[]>([]);
  protected readonly loadingTeams = signal(true);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly detail = signal<TeamDetail | null>(null);
  protected readonly series = signal<Overview[]>([]);
  protected readonly events = signal<EventSummary[]>([]);
  protected readonly newSeries = signal(false);

  protected readonly teamId = computed(() => {
    const wanted = this.team();
    const all = this.teams();
    if (wanted && all.some((t) => t.id === wanted)) return wanted;
    return (all.find((t) => t.myTeamRole === 'COACH') ?? all[0])?.id ?? null;
  });
  protected readonly season = computed(() => this.seasons().find((s) => s.current) ?? null);
  /** Single events: the next ones that belong to no series. */
  protected readonly single = computed(() => this.events().filter((e) => !e.seriesId).slice(0, 8));
  private readonly year = computed(() => yearOf(this.auth.me()?.now));

  constructor() {
    effect(() => {
      if (this.auth.canManage()) untracked(() => void this.loadTeams());
    });
    effect(() => {
      const id = this.teamId();
      if (id) {
        untracked(() => {
          this.newSeries.set(false);
          void this.load(id);
        });
      }
    });
  }

  protected day(e: EventSummary): string {
    return eventDate(e.start, this.year());
  }

  protected time(e: EventSummary): string {
    return clockTime(e.start);
  }

  protected switchTeam(id: string): void {
    void this.router.navigate([], { queryParams: { team: id } });
  }

  private async loadTeams(): Promise<void> {
    this.loadingTeams.set(true);
    try {
      const [teams, seasons] = await Promise.all([
        firstValueFrom(this.http.get<Team[]>('/api/teams')),
        firstValueFrom(this.http.get<Season[]>('/api/seasons')),
      ]);
      this.teams.set(teams);
      this.seasons.set(seasons);
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.loadingTeams.set(false);
    }
  }

  private async load(id: string): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      const [team, series, events] = await Promise.all([
        firstValueFrom(this.http.get<TeamDetail>(`/api/teams/${id}`)),
        firstValueFrom(this.http.get<Overview[]>('/api/series', { params: { team: id } })),
        firstValueFrom(this.http.get<EventSummary[]>('/api/events', { params: { team: id, view: 'upcoming' } })),
      ]);
      this.detail.set(team);
      this.series.set(series);
      this.events.set(events);
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.loading.set(false);
    }
  }

  protected reload(): void {
    const id = this.teamId();
    if (id) void this.load(id);
  }

  protected async reloadSeries(): Promise<void> {
    const id = this.teamId();
    if (!id) return;
    try {
      const [series, events] = await Promise.all([
        firstValueFrom(this.http.get<Overview[]>('/api/series', { params: { team: id } })),
        firstValueFrom(this.http.get<EventSummary[]>('/api/events', { params: { team: id, view: 'upcoming' } })),
      ]);
      this.series.set(series);
      this.events.set(events);
    } catch (e) {
      this.error.set(errorText(e));
    }
  }

  protected seriesCreated(): void {
    void this.reloadSeries();
  }
}
