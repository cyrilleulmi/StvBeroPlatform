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
import type { PersonSummary, Season, TeamDetail, WarningMinimums } from '../../api/types';
import { AuthService } from '../../core/auth.service';
import { errorStatus, errorText, fieldErrorsOf } from '../../core/error-text';
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
        <section class="card" aria-labelledby="team-players-title" data-testid="team-players">
          <div class="players-header">
            <h2 id="team-players-title" class="section-title">
              {{ texts.teams.players }} <span class="count">{{ players().length }}</span>
            </h2>
            @if (canEditLines() || canSeeStatistics()) {
              <div class="team-actions">
                @if (canSeeStatistics()) {
                  <a
                    class="button button-secondary button-small"
                    routerLink="/statistik"
                    [queryParams]="{ team: t.id }"
                    data-testid="statistics-link"
                    >{{ texts.statistics.link }}</a
                  >
                }
                @if (canEditLines()) {
                  <a
                    class="button button-secondary button-small"
                    [routerLink]="['/teams', t.id, 'linien']"
                    >{{ texts.teams.editLines }}</a
                  >
                }
              </div>
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
        @if (!season()) {
          <!-- League from swiss unihockey (F-17, criterion 1), current season only -->
          <section class="card" aria-labelledby="team-league-title" data-testid="team-league">
            <h2 id="team-league-title" class="section-title">{{ texts.league.title }}</h2>
            @if (t.suTeamId) {
              <p class="secondary-text">{{ texts.league.sectionText }}</p>
              <a
                class="button button-secondary button-small"
                [routerLink]="['/teams', t.id, 'liga']"
                data-testid="team-league-link"
                >{{ texts.league.open }}</a
              >
            } @else {
              <p class="secondary-text">{{ texts.league.notLinked }}</p>
              @if (auth.canManage()) {
                <a [routerLink]="['/verwaltung/teams', t.id]">{{
                  texts.league.linkInAdministration
                }}</a>
              }
            }
          </section>
        }
        <!-- Driver plan of the season: who drives to which match day (DRV-14) -->
        <section class="card" aria-labelledby="team-driver-plan-title" data-testid="team-driver-plan">
          <h2 id="team-driver-plan-title" class="section-title">{{ texts.driverPlan.title }}</h2>
          <p class="secondary-text">{{ texts.driverPlan.explanation }}</p>
          <a
            class="button button-secondary button-small"
            [routerLink]="['/teams', t.id, 'fahrerliste']"
            [queryParams]="season() ? { saison: season() } : {}"
            data-testid="team-driver-plan-link"
            >{{ texts.driverPlan.openPlan }}</a
          >
        </section>
        @if (minimums(); as m) {
          <!-- Minimums for the warnings, coaches and admins in the current season (F-16, criterion 4) -->
          <section
            class="card"
            aria-labelledby="team-warning-minimums-title"
            data-testid="team-warning-minimums"
          >
            <h2 id="team-warning-minimums-title" class="section-title">{{ w.minimums }}</h2>
            <p class="secondary-text" id="team-warning-minimums-text">{{ w.minimumsText }}</p>
            @if (minimumsMessage(); as message) {
              <p class="message-success" role="status">{{ message }}</p>
            }
            @if (minimumsError(); as message) {
              <p class="message-error" role="alert">{{ message }}</p>
            }
            <form class="form minimums" (submit)="saveMinimums($event)" novalidate>
              <div class="minimum-fields">
                @for (f of minimumFields; track f.key) {
                  <div class="field">
                    <label [for]="'minimum-' + f.key">{{ f.label }}</label>
                    <input
                      [id]="'minimum-' + f.key"
                      type="number"
                      inputmode="numeric"
                      min="0"
                      max="99"
                      step="1"
                      required
                      [value]="m[f.key]"
                      (input)="setMinimum(f.key, $any($event.target).value)"
                      [attr.aria-invalid]="minimumErrors()[f.key] ? 'true' : null"
                      [attr.aria-describedby]="minimumErrors()[f.key] ? 'minimum-' + f.key + '-error' : null"
                    />
                    @if (minimumErrors()[f.key]; as message) {
                      <p class="field-error" [id]="'minimum-' + f.key + '-error'">{{ message }}</p>
                    }
                  </div>
                }
              </div>
              <div>
                <button
                  type="submit"
                  class="button button-primary button-small"
                  [disabled]="savingMinimums()"
                  [attr.aria-busy]="savingMinimums() ? 'true' : null"
                >
                  {{ w.saveMinimums }}
                </button>
              </div>
            </form>
          </section>
        }
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
    .team-actions {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }
    .players-header .section-title {
      margin: 0;
    }
    .minimum-fields {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: 14px;
    }
    .group-title {
      margin: 12px 0 0;
      font-size: 13px;
      font-weight: 600;
      color: var(--muted);
    }
  `,
})
export class Team {
  private readonly http = inject(HttpClient);
  protected readonly auth = inject(AuthService);
  protected readonly texts = TEXTS;
  protected readonly name = name;
  protected readonly teamRole = teamRoleText;
  protected readonly w = TEXTS.warnings;
  protected readonly minimumFields: { key: keyof WarningMinimums; label: string }[] = [
    { key: 'goalies', label: TEXTS.warnings.goaliesMinimum },
    { key: 'fieldPlayersMatchDay', label: TEXTS.warnings.fieldPlayersMatchDayMinimum },
    { key: 'fieldPlayersPractice', label: TEXTS.warnings.fieldPlayersPracticeMinimum },
    { key: 'perLine', label: TEXTS.warnings.perLineMinimum },
  ];

  /** Route `/teams/:teamId` */
  readonly teamId = input.required<string>();
  /** Query `?saison=` */
  // eslint-disable-next-line @angular-eslint/no-input-rename -- query parameter `?saison=` is part of the browser URL and stays German (NF-40)
  readonly season = input<string | undefined>(undefined, { alias: 'saison' });

  protected readonly team = signal<TeamDetail | null>(null);
  protected readonly seasonData = signal<Season | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  /** Entered values of the minimums (as text, checked on saving). */
  private readonly minimumInput = signal<Record<string, string>>({});
  protected readonly minimumErrors = signal<Record<string, string>>({});
  protected readonly savingMinimums = signal(false);
  protected readonly minimumsMessage = signal<string | null>(null);
  protected readonly minimumsError = signal<string | null>(null);

  /** Only coaches and admins get the minimums from the API; changed in the current season only (F-16). */
  protected readonly minimums = computed(() =>
    this.season() ? null : (this.team()?.warningMinimums ?? null),
  );

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

  /** Coaches (of any team, E-69) and admins assign the lines (LIN-07), only in the current season. */
  protected readonly canEditLines = computed(
    () => !this.season() && (this.auth.canManage() || this.team()?.myTeamRole === 'COACH'),
  );

  /** Team statistics: coaches (of any team, E-69) and admins, current season (STA-01). */
  protected readonly canSeeStatistics = computed(() => !this.season() && this.auth.canManage());

  /** All players: by groups, then without a line. Coaches are never included (PTF-07). */
  protected readonly players = computed<PersonSummary[]>(() => {
    const t = this.team();
    if (!t) return [];
    return [...t.lineup.groups.flatMap((g) => g.players), ...t.lineup.noLine];
  });

  protected setMinimum(key: keyof WarningMinimums, value: string): void {
    this.minimumInput.update((v) => ({ ...v, [key]: value }));
  }

  /** Save the minimums (F-16, criterion 4). Every value is a whole number from 0 to 99. */
  protected async saveMinimums(domEvent: Event): Promise<void> {
    domEvent.preventDefault();
    const t = this.team();
    const current = t?.warningMinimums;
    if (!t || !current || this.savingMinimums()) return;
    const entered = this.minimumInput();
    const body = { ...current };
    const errors: Record<string, string> = {};
    for (const f of this.minimumFields) {
      const raw = (entered[f.key] ?? String(current[f.key])).trim();
      const n = Number(raw);
      if (raw === '' || !Number.isInteger(n) || n < 0 || n > 99) {
        errors[f.key] = TEXTS.warnings.invalidNumber;
      } else {
        body[f.key] = n;
      }
    }
    this.minimumErrors.set(errors);
    this.minimumsMessage.set(null);
    this.minimumsError.set(null);
    const first = this.minimumFields.find((f) => errors[f.key]);
    if (first) {
      document.getElementById('minimum-' + first.key)?.focus();
      return;
    }
    this.savingMinimums.set(true);
    try {
      const saved = await firstValueFrom(
        this.http.put<WarningMinimums>(`/api/teams/${t.id}/warning-minimums`, body),
      );
      this.team.update((x) => (x ? { ...x, warningMinimums: saved } : x));
      this.minimumInput.set({});
      this.minimumsMessage.set(TEXTS.warnings.minimumsSaved);
    } catch (e) {
      const fields = fieldErrorsOf(e);
      if (Object.keys(fields).length > 0) {
        this.minimumErrors.set(fields);
      } else {
        this.minimumsError.set(errorText(e));
      }
    } finally {
      this.savingMinimums.set(false);
    }
  }

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
    if (!seasonId && me && !this.auth.canManage() && !member) {
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
