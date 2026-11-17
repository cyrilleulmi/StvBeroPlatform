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
import type { PersonSummary, Season, TeamDetail } from '../../api/types';
import { AuthService } from '../../core/auth.service';
import { errorStatus, errorText } from '../../core/error-text';
import { teamRoleText, name } from '../../core/format';
import { TEXTS } from '../../core/texts';

/**
 * Team: coaches and players separately (PTF-07), the players by line (LIN-12).
 * `?saison=<id>` shows another season, e.g. the archive (SAI-02).
 */
@Component({
  selector: 'app-team',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <div class="header">
      <a class="back" routerLink="/teams">{{ texts.teams.allTeams }}</a>
      <h1>{{ team()?.name ?? texts.teams.title }}</h1>
      @if (team()?.myTeamRole; as f) {
        <p class="secondary-text">{{ teamRole(f) }}</p>
      }
    </div>
    @if (seasonData(); as s) {
      @if (s.archived) {
        <p class="message-notice" data-testid="archive-notice">
          {{ texts.season.archiveNotice(s.name) }}
        </p>
      }
    }
    @if (error(); as message) {
      <p class="message-error" role="alert">{{ message }}</p>
    }
    <div class="sections" [attr.aria-busy]="loading() ? 'true' : null">
      @if (loading()) {
        <p class="empty">{{ texts.common.loading }}</p>
      } @else if (team(); as t) {
        <section class="card" aria-labelledby="team-coaches-title" data-testid="team-coaches">
          <h2 id="team-coaches-title" class="section-title">
            {{ texts.teams.coaches }} <span class="count">{{ t.coaches.length }}</span>
          </h2>
          @if (t.coaches.length === 0) {
            <p class="empty">{{ texts.teams.noCoaches }}</p>
          } @else {
            <ul class="list" [attr.aria-label]="texts.teams.coaches">
              @for (p of t.coaches; track p.id) {
                <li class="row" [attr.data-testid]="'team-person-' + p.id">{{ name(p) }}</li>
              }
            </ul>
          }
        </section>
        <section
          class="card"
          aria-labelledby="team-players-title"
          data-testid="team-players"
        >
          <div class="players-header">
            <h2 id="team-players-title" class="section-title">
              {{ texts.teams.players }} <span class="count">{{ players().length }}</span>
            </h2>
            @if (canEditLines()) {
              <a
                class="button button-secondary button-small"
                [routerLink]="['/teams', t.id, 'linien']"
                >{{ texts.teams.editLines }}</a
              >
            }
          </div>
          @if (players().length === 0) {
            <p class="empty">{{ texts.teams.noPlayers }}</p>
          } @else {
            <!-- By line, then goalies, then «Ohne Linie» (LIN-06, LIN-09). Empty groups are left out. -->
            @for (g of groups(); track g.key) {
              <h3 class="group-title" [id]="'team-group-' + g.key">{{ g.name }}</h3>
              <ul class="list" [attr.aria-labelledby]="'team-group-' + g.key">
                @for (p of g.players; track p.id) {
                  <li class="row" [attr.data-testid]="'team-person-' + p.id">{{ name(p) }}</li>
                }
              </ul>
            }
          }
        </section>
      }
    </div>
  `,
  styles: `
    :host,
    .sections {
      display: grid;
      gap: var(--section-gap);
    }
    .players-header {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
      margin-bottom: 8px;
    }
    .players-header .section-title {
      margin: 0;
    }
    .group-title {
      margin: 12px 0 0;
      font-size: 13px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      color: var(--muted);
    }
  `,
})
export class Team {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  protected readonly texts = TEXTS;
  protected readonly name = name;
  protected readonly teamRole = teamRoleText;

  /** Route `/teams/:teamId` */
  readonly teamId = input.required<string>();
  /** Query `?saison=` */
  // eslint-disable-next-line @angular-eslint/no-input-rename -- query parameter `?saison=` is part of the browser URL and stays German (NF-40)
  readonly season = input<string | undefined>(undefined, { alias: 'saison' });

  protected readonly team = signal<TeamDetail | null>(null);
  protected readonly seasonData = signal<Season | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  /** Non-empty groups in display order, «Ohne Linie» last. */
  protected readonly groups = computed(() => {
    const t = this.team();
    if (!t) return [];
    return [
      ...t.lineup.groups.map((g) => ({
        key: g.id,
        name: g.name,
        players: g.players,
      })),
      {
        key: 'no-line',
        name: TEXTS.lines.noLine,
        players: t.lineup.noLine,
      },
    ].filter((g) => g.players.length > 0);
  });

  /** Coaches of the team and admins assign the lines (LIN-07), only in the current season. */
  protected readonly canEditLines = computed(
    () => !this.season() && (this.auth.isAdmin() || this.team()?.myTeamRole === 'COACH'),
  );

  /** All players: by groups, then without a line. Coaches are never included (PTF-07). */
  protected readonly players = computed<PersonSummary[]>(() => {
    const t = this.team();
    if (!t) return [];
    return [...t.lineup.groups.flatMap((g) => g.players), ...t.lineup.noLine];
  });

  constructor() {
    effect(() => {
      const teamId = this.teamId();
      const season = this.season();
      untracked(() => void this.load(teamId, season));
    });
  }

  private async load(teamId: string, seasonId: string | undefined): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    this.team.set(null);
    const me = this.auth.me();
    const member = me?.memberships.some((m) => m.team.id === teamId) ?? false;
    if (!seasonId && me && !me.admin && !member) {
      // Without membership in the current season do not even ask; the backend refuses (403).
      this.error.set(TEXTS.common.noPermission);
      this.loading.set(false);
      return;
    }
    try {
      const params: Record<string, string> = seasonId ? { season: seasonId } : {};
      this.team.set(
        await firstValueFrom(this.http.get<TeamDetail>(`/api/teams/${teamId}`, { params })),
      );
      if (seasonId) {
        const seasons = await firstValueFrom(this.http.get<Season[]>('/api/seasons'));
        this.seasonData.set(seasons.find((s) => s.id === seasonId) ?? null);
      } else {
        this.seasonData.set(null);
      }
    } catch (e) {
      this.error.set(errorStatus(e) === 404 ? TEXTS.teams.notFound : errorText(e));
    } finally {
      this.loading.set(false);
    }
  }
}
