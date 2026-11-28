import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { SuConnection, SuLink, Team } from '../api/types';
import { AuthService } from '../core/auth.service';
import { errorText } from '../core/error-text';
import { Icon } from '../core/icon';
import { TEXTS } from '../core/texts';
import { LeagueOverview } from './league-overview';
import { SuConnectionSection } from './su-connection';
import { chosenSuTeamId, type SuTeamRef } from './su-team-choice';

/** Address of a team at swissunihockey.ch (SUH-16). */
export function suTeamUrl(suTeamId: number): string {
  return `https://www.swissunihockey.ch/de/team-detail/?team_id=${suTeamId}`;
}

/**
 * «swiss unihockey» (`/swissunihockey`, SUH-16, E-116): the overview of the current team right away (league and
 * group, table, upcoming matches, results) with the link to the team page at swissunihockey.ch. The current team is
 * the first own team (player first); coaches and admins choose among every team of the season, players among their
 * own; the choice is in the URL (`?team=<id>`). A team without a link shows how to link it, with the dialog for
 * those who may. Those who may manage find links, unlinking and sync under «Verbindung verwalten».
 */
@Component({
  selector: 'app-swiss-unihockey',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, LeagueOverview, SuConnectionSection],
  template: `
    <h1 class="page-title">{{ s.title }}</h1>
    <p class="secondary-text intro">{{ s.intro }}</p>
    @if (error(); as m) {
      <p class="message-error" role="alert">{{ m }}</p>
    }
    @if (loadingTeams()) {
      <p class="empty" aria-busy="true">{{ texts.common.loading }}</p>
    } @else if (teams().length === 0) {
      <p class="empty" data-testid="su-page-no-team">{{ s.noTeam }}</p>
    } @else if (current(); as chosen) {
      @if (teams().length > 1) {
        <div class="field team-switch">
          <label for="su-team">{{ s.team }}</label>
          <select
            id="su-team"
            name="su-team"
            data-testid="su-team-select"
            (change)="switchTeam($any($event.target).value)"
          >
            @for (x of teams(); track x.id) {
              <option [value]="x.id" [selected]="x.id === chosen.id">{{ x.name }}</option>
            }
          </select>
        </div>
      }
      <div class="overview" [attr.aria-busy]="connection() ? null : 'true'">
        @if (connection(); as c) {
          @if (c.links.length === 0) {
            <section class="card not-linked" aria-labelledby="su-not-linked-title" data-testid="su-not-linked">
              <h2 id="su-not-linked-title" class="section-title">{{ s.notLinkedTitle }}</h2>
              <p>{{ s.notLinkedText(chosen.name) }}</p>
              <ol class="steps">
                @for (step of s.steps; track step) {
                  <li>{{ step }}</li>
                }
              </ol>
              @if (c.canManage) {
                <div>
                  <button type="button" class="button button-primary" data-testid="su-link-now" (click)="linkNow()">
                    {{ s.linkNow }}
                  </button>
                </div>
                <app-su-connection
                  [teamId]="chosen.id"
                  [teamName]="chosen.name"
                  [dialogOnly]="true"
                  (changed)="changed()"
                />
              } @else {
                <p class="ask-coach" data-testid="su-ask-coach">{{ s.askCoach }}</p>
              }
            </section>
          } @else {
            <div class="open-at-su" data-testid="su-open-at-su">
              @for (link of c.links; track link.id) {
                <a
                  class="button button-secondary button-small"
                  [href]="url(link)"
                  target="_blank"
                  rel="noopener noreferrer"
                  [attr.data-testid]="'su-open-at-su-' + link.suTeamId"
                  [attr.aria-label]="s.openAtSuFor(label(link, c.links.length))"
                >
                  {{ s.openAtSu }}{{ c.links.length > 1 ? ' · ' + label(link, c.links.length) : '' }}
                  <app-icon name="external-link" [size]="16" />
                </a>
              }
            </div>
            <app-league-overview
              [teamId]="chosen.id"
              [reloadKey]="reloadKey()"
              [notLinkedCard]="false"
            />
            @if (c.canManage) {
              <details class="manage" data-testid="su-manage">
                <summary>{{ s.manage }}</summary>
                <app-su-connection [teamId]="chosen.id" [teamName]="chosen.name" (changed)="changed()" />
              </details>
            }
          }
        }
      </div>
    }
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
    .team-switch {
      max-width: 360px;
    }
    .overview {
      display: grid;
      gap: var(--section-gap);
      min-width: 0;
    }
    .not-linked {
      display: grid;
      gap: 12px;
      min-width: 0;
    }
    .not-linked p,
    .steps {
      margin: 0;
    }
    .steps {
      padding-left: 22px;
      display: grid;
      gap: 6px;
    }
    .ask-coach {
      font-weight: 700;
    }
    .open-at-su {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }
    .open-at-su .button {
      gap: 6px;
      min-height: var(--touch);
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
  private readonly router = inject(Router);
  protected readonly texts = TEXTS;
  protected readonly s = TEXTS.suPage;

  /** Query parameter `?team=<id>` (component input binding). */
  readonly team = input<string | undefined>(undefined);

  protected readonly loadingTeams = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly teams = signal<SuTeamRef[]>([]);
  protected readonly connection = signal<SuConnection | null>(null);
  /** Counts the changes of the connection; the overview loads again when it changes. */
  protected readonly reloadKey = signal(0);

  private readonly section = viewChild(SuConnectionSection);

  /** The team shown: the requested one if it may be chosen, otherwise the current team (SUH-16). */
  protected readonly current = computed(() => {
    const id = chosenSuTeamId(this.teams(), this.auth.me()?.memberships ?? [], this.team());
    return this.teams().find((t) => t.id === id) ?? null;
  });
  private readonly teamId = computed(() => this.current()?.id ?? null);

  constructor() {
    effect(() => {
      const id = this.teamId();
      untracked(() => void this.loadConnection(id));
    });
  }

  async ngOnInit(): Promise<void> {
    try {
      this.teams.set(await this.choosableTeams());
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.loadingTeams.set(false);
    }
  }

  protected url(link: SuLink): string {
    return suTeamUrl(link.suTeamId);
  }

  protected label(link: SuLink, count: number): string {
    return link.name ?? (count > 1 ? this.s.teamNumber(link.suTeamId) : (this.current()?.name ?? ''));
  }

  protected switchTeam(id: string): void {
    void this.router.navigate([], { queryParams: { team: id }, queryParamsHandling: 'merge' });
  }

  protected linkNow(): void {
    void this.section()?.open();
  }

  /** Links, unlinking or sync changed something: read the connection and the league again. */
  protected changed(): void {
    this.reloadKey.update((n) => n + 1);
    void this.loadConnection(this.teamId(), true);
  }

  private async loadConnection(teamId: string | null, keep = false): Promise<void> {
    if (!keep) this.connection.set(null);
    if (!teamId) return;
    try {
      const connection = await firstValueFrom(this.http.get<SuConnection>(`/api/teams/${teamId}/swiss-unihockey`));
      if (teamId === this.teamId()) this.connection.set(connection);
    } catch (e) {
      this.error.set(errorText(e));
    }
  }

  /** Own teams; coaches and admins may choose every team of the current season. */
  private async choosableTeams(): Promise<SuTeamRef[]> {
    if (this.auth.canManage()) {
      const all = await firstValueFrom(this.http.get<Team[]>('/api/teams'));
      return all.map((t) => ({ id: t.id, name: t.name }));
    }
    const seen = new Map<string, SuTeamRef>();
    for (const m of this.auth.me()?.memberships ?? []) seen.set(m.team.id, m.team);
    return [...seen.values()];
  }
}
