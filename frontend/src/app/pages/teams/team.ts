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
 * Team: Coaches und Spielerinnen getrennt (PTF-07), die Spielerinnen nach Linie (LIN-12).
 * `?saison=<id>` zeigt eine andere Saison, z.B. das Archiv (SAI-02).
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
        <p class="message-notice" data-testid="archiv-hinweis">
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
        <section class="card" aria-labelledby="team-coaches-titel" data-testid="team-coaches">
          <h2 id="team-coaches-titel" class="section-title">
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
          aria-labelledby="team-spielerinnen-titel"
          data-testid="team-spielerinnen"
        >
          <div class="spielerinnen-kopf">
            <h2 id="team-spielerinnen-titel" class="section-title">
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
            <!-- Nach Linie, dann Goalies, dann «Ohne Linie» (LIN-06, LIN-09). Leere Gruppen fehlen. -->
            @for (g of groups(); track g.key) {
              <h3 class="gruppe-titel" [id]="'team-gruppe-' + g.key">{{ g.name }}</h3>
              <ul class="list" [attr.aria-labelledby]="'team-gruppe-' + g.key">
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
    .spielerinnen-kopf {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
      margin-bottom: 8px;
    }
    .spielerinnen-kopf .section-title {
      margin: 0;
    }
    .gruppe-titel {
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
  readonly season = input<string | undefined>(undefined, { alias: 'saison' });

  protected readonly team = signal<TeamDetail | null>(null);
  protected readonly seasonData = signal<Season | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  /** Nicht leere Gruppen in Anzeigereihenfolge, zuletzt «Ohne Linie». */
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
        key: 'ohne-linie',
        name: TEXTS.lines.noLine,
        players: t.lineup.noLine,
      },
    ].filter((g) => g.players.length > 0);
  });

  /** Coaches des Teams und Admins teilen die Linien ein (LIN-07), nur in der aktuellen Saison. */
  protected readonly canEditLines = computed(
    () => !this.season() && (this.auth.isAdmin() || this.team()?.myTeamRole === 'COACH'),
  );

  /** Alle Spielerinnen: nach Gruppen, dann ohne Linie. Coaches sind nie dabei (PTF-07). */
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
    const member = me?.memberships.some((z) => z.team.id === teamId) ?? false;
    if (!seasonId && me && !me.admin && !member) {
      // Ohne Zugehörigkeit in der aktuellen Saison gar nicht erst anfragen; das Backend lehnt ab (403).
      this.error.set(TEXTS.common.noPermission);
      this.loading.set(false);
      return;
    }
    try {
      const params: Record<string, string> = seasonId ? { saison: seasonId } : {};
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
