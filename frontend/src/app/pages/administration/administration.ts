import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { Person, Season, Team } from '../../api/types';
import { AuthService } from '../../core/auth.service';
import { coachedTeams } from '../../core/format';
import { Icon } from '../../core/icon';
import { TEXTS } from '../../core/texts';

/** A row of the group «Verein»: the state line shows what is there now, or what the page is for. */
interface ClubRow {
  key: string;
  route: string;
  title: string;
  icon: string;
  state: string | null;
}

/**
 * Administration (admins and coaches, RR-03/RR-05, E-69) as a hub (E-110): «Meine Teams» with direct actions for each
 * team the person coaches in the current season, and «Verein» with people, all teams, the team assignment, the
 * statistics (STA-01, E-111) and, for admins only, the seasons (RR-04). Same rows as the profile (E-109).
 */
@Component({
  selector: 'app-administration',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon],
  templateUrl: './administration.html',
  styleUrls: ['../rows.css', './administration.css'],
})
export class Administration {
  private readonly http = inject(HttpClient);
  protected readonly auth = inject(AuthService);
  protected readonly texts = TEXTS;
  protected readonly t = TEXTS.administration;

  protected readonly season = signal<Season | null>(null);
  private readonly peopleCount = signal<number | null>(null);
  private readonly teamCount = signal<number | null>(null);
  /** The counts could not be loaded: the rows say what they are for instead. */
  private readonly failed = signal(false);

  /** Teams the person coaches in the current season (`/api/me`), same as «<Team> verwalten» in the navigation. */
  protected readonly myTeams = computed(() => coachedTeams(this.auth.me()?.memberships ?? []));

  protected readonly clubRows = computed<ClubRow[]>(() => {
    const t = this.t;
    const season = this.season();
    const failed = this.failed();
    const people = this.peopleCount();
    const teams = this.teamCount();
    const rows: ClubRow[] = [
      {
        key: 'people',
        route: '/verwaltung/personen',
        title: t.people,
        icon: 'contact',
        state: people !== null ? t.peopleState(people) : failed ? t.peopleText : null,
      },
      {
        key: 'teams',
        route: '/verwaltung/teams',
        title: t.teams,
        icon: 'users',
        state: teams !== null && season ? t.teamsState(teams, season.name) : failed ? t.teamsText : null,
      },
      {
        key: 'assignment',
        route: '/verwaltung/zuteilung',
        title: t.assignment,
        icon: 'sheet',
        state: t.assignmentText,
      },
    ];
    // Statistics of all teams (STA-01): reached from here since E-111, no longer from the event list
    rows.push({
      key: 'statistics',
      route: '/statistik',
      title: t.statistics,
      icon: 'chart-column',
      state: t.statisticsText,
    });
    // Seasons only for admins (RR-04)
    if (this.auth.isAdmin()) {
      rows.push({
        key: 'seasons',
        route: '/verwaltung/saisons',
        title: t.seasons,
        icon: 'calendar-range',
        state: season ? t.seasonsState(season.name) : failed ? t.seasonsText : null,
      });
    }
    return rows;
  });

  constructor() {
    effect(() => {
      if (this.auth.canManage()) untracked(() => void this.load());
    });
  }

  /** The counts are a help, not the page: if a request fails, the rows stay usable without them. */
  private async load(): Promise<void> {
    const [seasons, people, teams] = await Promise.allSettled([
      firstValueFrom(this.http.get<Season[]>('/api/seasons')),
      firstValueFrom(this.http.get<Person[]>('/api/persons')),
      firstValueFrom(this.http.get<Team[]>('/api/teams')),
    ]);
    if (seasons.status === 'fulfilled') this.season.set(seasons.value.find((s) => s.current) ?? null);
    if (people.status === 'fulfilled') this.peopleCount.set(people.value.length);
    if (teams.status === 'fulfilled') this.teamCount.set(teams.value.length);
    this.failed.set([seasons, people, teams].some((r) => r.status === 'rejected'));
  }
}
