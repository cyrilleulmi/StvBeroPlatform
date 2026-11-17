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
import type { Season, Team } from '../../api/types';
import { AuthService } from '../../core/auth.service';
import { fieldErrorsOf, errorText } from '../../core/error-text';
import { seasonStatus } from '../../core/format';
import { Icon } from '../../core/icon';
import { TEXTS } from '../../core/texts';

/** Teams verwalten (Admin): Teams einer Saison (`?saison=`), Team anlegen. Archiv nur lesend (SAI-02). */
@Component({
  selector: 'app-teams-verwaltung',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon],
  template: `
    <div class="header">
      <a class="back" routerLink="/verwaltung">{{ texts.administration.toAdministration }}</a>
      <h1>{{ texts.teamAdmin.title }}</h1>
    </div>
    @if (!auth.isAdmin()) {
      <p class="message-error" role="alert">{{ texts.common.noPermission }}</p>
    } @else {
      <div class="field">
        <label for="teams-saison">{{ texts.teamAdmin.season }}</label>
        <select id="teams-saison" (change)="selectSeason($any($event.target).value)">
          @for (s of seasons(); track s.id) {
            <option [value]="s.id" [selected]="s.id === seasonData()?.id">
              {{ s.name }} ({{ status(s) }})
            </option>
          }
        </select>
      </div>
      @if (seasonData()?.archived) {
        <p class="message-notice" data-testid="archiv-hinweis">
          {{ texts.season.archiveNotice(seasonData()!.name) }}
        </p>
      }
      @if (success(); as message) {
        <p class="message-success" role="status">{{ message }}</p>
      }
      @if (error(); as message) {
        <p class="message-error" role="alert">{{ message }}</p>
      }
      <section
        class="card"
        aria-labelledby="teams-verwaltung-liste"
        [attr.aria-busy]="loading() ? 'true' : null"
      >
        <h2 id="teams-verwaltung-liste" class="section-title">
          {{ texts.teamAdmin.teamsList }}
        </h2>
        @if (loading() && teams().length === 0) {
          <p class="empty">{{ texts.common.loading }}</p>
        } @else if (teams().length === 0) {
          <p class="empty">{{ texts.teamAdmin.empty }}</p>
        } @else {
          <ul class="list" [attr.aria-label]="texts.teamAdmin.teamsList">
            @for (t of teams(); track t.id) {
              <li class="team-zeile" [attr.data-testid]="'team-' + t.id">
                <a
                  class="row-link"
                  [routerLink]="['/verwaltung/teams', t.id]"
                  [queryParams]="{ saison: seasonData()?.id }"
                >
                  <span class="row-title">{{ t.name }}</span>
                  <app-icon name="chevron-right" [size]="20" />
                </a>
                @if (t.suTeamId) {
                  <span class="chip" [attr.data-testid]="'su-verknuepft-' + t.id">{{
                    texts.swissUnihockey.chip
                  }}</span>
                }
              </li>
            }
          </ul>
        }
      </section>
      @if (seasonData() && !seasonData()!.archived) {
        <form
          class="card form"
          (submit)="create($event)"
          novalidate
          aria-labelledby="team-neu-titel"
        >
          <h2 id="team-neu-titel">{{ texts.teamAdmin.newTeam }}</h2>
          <div class="field">
            <label for="team-neu-name">{{ texts.teamAdmin.teamName }}</label>
            <input
              id="team-neu-name"
              type="text"
              maxlength="60"
              autocomplete="off"
              [value]="newName()"
              (input)="newName.set($any($event.target).value)"
              [attr.aria-invalid]="nameError() ? 'true' : null"
              [attr.aria-describedby]="nameError() ? 'team-neu-name-fehler' : null"
            />
            @if (nameError(); as m) {
              <p class="field-error" id="team-neu-name-fehler">{{ m }}</p>
            }
          </div>
          <button
            type="submit"
            class="button button-primary"
            [disabled]="saving()"
            [attr.aria-busy]="saving() ? 'true' : null"
          >
            {{ texts.teamAdmin.create }}
          </button>
        </form>
      }
    }
  `,
  styleUrl: '../page.css',
  styles: `
    form .button {
      justify-self: start;
    }
    .team-zeile {
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .team-zeile .row-link {
      flex: 1 1 auto;
      min-width: 0;
    }
  `,
})
export class TeamsAdmin {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);
  protected readonly texts = TEXTS;
  protected readonly status = seasonStatus;

  /** Query `?saison=`; ohne Angabe die aktuelle Saison. */
  readonly season = input<string | undefined>(undefined, { alias: 'saison' });

  protected readonly seasons = signal<Season[]>([]);
  protected readonly seasonData = computed(() => {
    const id = this.season();
    return this.seasons().find((s) => (id ? s.id === id : s.current)) ?? null;
  });
  protected readonly teams = signal<Team[]>([]);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly success = signal<string | null>(null);
  protected readonly newName = signal('');
  protected readonly nameError = signal<string | null>(null);

  constructor() {
    effect(() => {
      const id = this.season();
      if (this.auth.isAdmin()) {
        untracked(() => void this.load(id));
      }
    });
  }

  protected selectSeason(id: string): void {
    void this.router.navigate([], { queryParams: { saison: id } });
  }

  private async load(seasonId: string | undefined): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    this.success.set(null);
    try {
      const params: Record<string, string> = seasonId ? { saison: seasonId } : {};
      const [seasons, teams] = await Promise.all([
        firstValueFrom(this.http.get<Season[]>('/api/seasons')),
        firstValueFrom(this.http.get<Team[]>('/api/teams', { params })),
      ]);
      this.seasons.set(seasons);
      this.teams.set(teams);
    } catch (e) {
      this.teams.set([]);
      this.error.set(errorText(e));
    } finally {
      this.loading.set(false);
    }
  }

  protected async create(domEvent: Event): Promise<void> {
    domEvent.preventDefault();
    if (this.saving()) return;
    this.success.set(null);
    this.error.set(null);
    const name = this.newName().trim();
    if (!name) {
      this.nameError.set(TEXTS.teamAdmin.nameRequired);
      return;
    }
    this.nameError.set(null);
    this.saving.set(true);
    try {
      const params: Record<string, string> = this.seasonData()
        ? { saison: this.seasonData()!.id }
        : {};
      const team = await firstValueFrom(this.http.post<Team>('/api/teams', { name }, { params }));
      this.newName.set('');
      await this.load(this.season());
      this.success.set(TEXTS.teamAdmin.created(team.name));
    } catch (e) {
      this.nameError.set(fieldErrorsOf(e)['name'] ?? null);
      this.error.set(errorText(e));
    } finally {
      this.saving.set(false);
    }
  }
}
