import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { SuConnection, SuLink, Team } from '../api/types';
import { AuthService } from '../core/auth.service';
import { errorText } from '../core/error-text';
import { Icon } from '../core/icon';
import { TEXTS } from '../core/texts';
import { SuConnectionSection } from './su-connection';

/** Address of a team at swissunihockey.ch (SUH-16). */
export function suTeamUrl(suTeamId: number): string {
  return `https://www.swissunihockey.ch/de/team-detail/?team_id=${suTeamId}`;
}

/**
 * «swiss unihockey» (`/swissunihockey`, SUH-16): for every own team (coaches and admins: every team of the current
 * season) its links to swiss unihockey, with a link to the team page there and to the league in the app. Coaches
 * and admins link right here; players read «Nicht verbunden. Frag den Coach zum Einrichten.»
 */
@Component({
  selector: 'app-swiss-unihockey',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon, SuConnectionSection],
  template: `
    <h1 class="page-title">{{ s.title }}</h1>
    <p class="secondary-text intro">{{ s.intro }}</p>
    @if (error(); as m) {
      <p class="message-error" role="alert">{{ m }}</p>
    }
    <div class="teams" [attr.aria-busy]="loading() ? 'true' : null">
      @if (loading()) {
        <p class="empty">{{ texts.common.loading }}</p>
      } @else if (teams().length === 0) {
        <p class="empty" data-testid="su-page-no-team">{{ s.noTeam }}</p>
      } @else {
        @for (x of teams(); track x.team.id) {
          <section
            class="card team"
            [attr.aria-labelledby]="'su-team-' + x.team.id"
            [attr.data-testid]="'su-team-' + x.team.id"
          >
            <h2 class="section-title" [id]="'su-team-' + x.team.id">{{ x.team.name }}</h2>
            @if (x.connection.links.length === 0) {
              <p class="not-linked" data-testid="su-not-linked">
                {{ x.connection.canManage ? s.notLinkedCoach : s.notLinked }}
              </p>
            } @else {
              <ul class="list links" [attr.aria-label]="s.linksOf(x.team.name)">
                @for (link of x.connection.links; track link.id) {
                  <li class="link" [attr.data-testid]="'su-page-link-' + link.suTeamId">
                    <span class="row-title">{{ link.name ?? s.teamNumber(link.suTeamId) }}</span>
                    <a
                      class="button button-secondary button-small"
                      [href]="url(link)"
                      target="_blank"
                      rel="noopener noreferrer"
                      [attr.aria-label]="s.openAtSuFor(link.name ?? s.teamNumber(link.suTeamId))"
                    >
                      {{ s.openAtSu }}
                      <app-icon name="external-link" [size]="16" />
                    </a>
                  </li>
                }
              </ul>
              <a class="league-link" [routerLink]="['/teams', x.team.id, 'liga']" data-testid="su-league-link">
                {{ s.league }}
                <app-icon name="chevron-right" [size]="16" />
              </a>
            }
            @if (x.connection.canManage) {
              <details class="manage" [attr.data-testid]="'su-manage-' + x.team.id" [open]="x.connection.links.length === 0">
                <summary>{{ x.connection.links.length === 0 ? s.link : s.edit }}</summary>
                <app-su-connection [teamId]="x.team.id" [teamName]="x.team.name" (changed)="reload(x.team.id)" />
              </details>
            }
          </section>
        }
      }
    </div>
  `,
  styles: `
    :host {
      display: grid;
      gap: 16px;
      min-width: 0;
    }
    .intro {
      margin: 0;
    }
    .teams {
      display: grid;
      gap: 16px;
      min-width: 0;
    }
    .team {
      display: grid;
      gap: 10px;
      min-width: 0;
    }
    .team p {
      margin: 0;
    }
    .not-linked {
      padding: 8px 12px;
      border-radius: var(--radius-icon);
      background: var(--mustard-soft);
      font-weight: 600;
    }
    .links {
      list-style: none;
      margin: 0;
      padding: 0;
      display: grid;
      gap: 8px;
    }
    .link {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 8px 12px;
      padding: 8px 12px;
      border-radius: var(--radius-icon);
      background: var(--board);
      overflow-wrap: anywhere;
    }
    .link .button {
      gap: 6px;
    }
    .league-link {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      min-height: var(--touch);
      font-weight: 700;
      color: var(--primary);
      justify-self: start;
    }
    .manage summary {
      min-height: var(--touch);
      display: flex;
      align-items: center;
      font-weight: 700;
      color: var(--primary);
      cursor: pointer;
    }
    .manage[open] summary {
      margin-bottom: 8px;
    }
  `,
})
export class SwissUnihockeyPage implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  protected readonly texts = TEXTS;
  protected readonly s = TEXTS.suPage;

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly teams = signal<{ team: { id: string; name: string }; connection: SuConnection }[]>([]);
  private readonly canManage = computed(() => this.auth.canManage());

  async ngOnInit(): Promise<void> {
    try {
      const teams = await this.ownTeams();
      const connections = await Promise.all(
        teams.map((team) => firstValueFrom(this.http.get<SuConnection>(`/api/teams/${team.id}/swiss-unihockey`))),
      );
      this.teams.set(teams.map((team, i) => ({ team, connection: connections[i] })));
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.loading.set(false);
    }
  }

  protected url(link: SuLink): string {
    return suTeamUrl(link.suTeamId);
  }

  protected async reload(teamId: string): Promise<void> {
    try {
      const connection = await firstValueFrom(this.http.get<SuConnection>(`/api/teams/${teamId}/swiss-unihockey`));
      this.teams.update((list) => list.map((x) => (x.team.id === teamId ? { ...x, connection } : x)));
    } catch (e) {
      this.error.set(errorText(e));
    }
  }

  /** Own teams; coaches and admins see every team of the current season. */
  private async ownTeams(): Promise<{ id: string; name: string }[]> {
    if (this.canManage()) {
      const all = await firstValueFrom(this.http.get<Team[]>('/api/teams'));
      return all.map((t) => ({ id: t.id, name: t.name }));
    }
    const seen = new Map<string, { id: string; name: string }>();
    for (const m of this.auth.me()?.memberships ?? []) seen.set(m.team.id, m.team);
    return [...seen.values()];
  }
}
