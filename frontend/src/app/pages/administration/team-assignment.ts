import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { Season } from '../../api/types';
import { AuthService } from '../../core/auth.service';
import { errorText } from '../../core/error-text';
import { seasonStatus } from '../../core/format';
import { TEXTS } from '../../core/texts';
import { MembershipTable } from './membership-table';

/**
 * «Teamzuteilung» (PTF-16, PTF-17): who is in which team, for the whole club in one season (`?saison=`), as one
 * table of people and teams. Made for the end of a season: filter by the team of the season before and put everyone
 * shown into the new team at once. Admins and coaches.
 */
@Component({
  selector: 'app-team-assignment',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, MembershipTable],
  template: `
    <div class="header">
      <a class="back" routerLink="/verwaltung">{{ texts.administration.toAdministration }}</a>
      <h1>{{ t.pageTitle }}</h1>
    </div>
    @if (!auth.canManage()) {
      <p class="message-error" role="alert">{{ texts.common.noPermission }}</p>
    } @else {
      <p class="secondary-text intro">{{ t.pageText }} {{ t.seasonEnd }}</p>
      @if (error(); as m) {
        <p class="message-error" role="alert">{{ m }}</p>
      }
      <div class="top">
        <div class="field">
          <label for="assignment-season">{{ t.season }}</label>
          <select id="assignment-season" name="assignment-season" (change)="selectSeason($any($event.target).value)">
            @for (s of seasons(); track s.id) {
              <option [value]="s.id" [selected]="s.id === chosen()?.id">{{ s.name }} ({{ status(s) }})</option>
            }
          </select>
        </div>
        <div class="links">
          <a
            class="button button-secondary button-small"
            routerLink="/verwaltung/teams/neu"
            [queryParams]="{ saison: seasonParam() }"
            >{{ t.newTeam }}</a
          >
          @if (auth.isAdmin()) {
            <a class="button button-secondary button-small" routerLink="/verwaltung/saisons">{{ t.seasons }}</a>
          }
        </div>
      </div>
      @if (chosen(); as s) {
        <section class="card wide" [attr.aria-label]="t.pageTitle">
          <app-membership-table [season]="s.id" />
        </section>
      }
    }
  `,
  styleUrl: '../page.css',
  styles: `
    .intro {
      margin: 0;
    }
    .top {
      display: flex;
      flex-wrap: wrap;
      align-items: end;
      justify-content: space-between;
      gap: 12px 16px;
    }
    .top .field {
      flex: 0 1 280px;
    }
    .links {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }
    /* Beside the navigation there is room: the table of all teams uses it instead of scrolling sideways */
    @media (min-width: 1024px) {
      .wide {
        width: min(1360px, calc(100vw - 240px - 2 * var(--page-margin) - 20px));
        min-width: 100%;
      }
    }
  `,
})
export class TeamAssignment {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);
  protected readonly texts = TEXTS;
  protected readonly t = TEXTS.membership;
  protected readonly status = seasonStatus;

  /** Query `?saison=<id>`; without it the current season. */
  readonly saison = input<string | undefined>(undefined);

  protected readonly seasons = signal<Season[]>([]);
  protected readonly error = signal<string | null>(null);

  protected readonly chosen = computed(() => {
    const id = this.saison();
    return this.seasons().find((s) => (id ? s.id === id : s.current)) ?? null;
  });
  /** `?saison=` only for another season than the current one. */
  protected readonly seasonParam = computed(() => {
    const s = this.chosen();
    return s && !s.current ? s.id : null;
  });

  constructor() {
    effect(() => {
      if (this.auth.canManage()) untracked(() => void this.load());
    });
  }

  protected selectSeason(id: string): void {
    const season = this.seasons().find((s) => s.id === id);
    void this.router.navigate([], { queryParams: { saison: season && !season.current ? id : null } });
  }

  private async load(): Promise<void> {
    try {
      this.seasons.set(await firstValueFrom(this.http.get<Season[]>('/api/seasons')));
    } catch (e) {
      this.error.set(errorText(e));
    }
  }
}
