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
import type { EventSummary, Team } from '../api/types';
import { AuthService } from '../core/auth.service';
import { errorText } from '../core/error-text';
import { Icon } from '../core/icon';
import { TEXTS } from '../core/texts';
import { MyResponse } from './my-response';
import { yearOf, month, monthKey, eventDate, clockTime } from './time';

interface Month {
  key: string;
  name: string;
  events: EventSummary[];
}

/**
 * Tile Anwesenheit: events of all own teams, chronological (TER-13), with the own response directly
 * in the card (ANM-08) and the total counter, which updates after a response without reloading. Filter
 * `?team=<id>` and view `?ansicht=vergangen` are in the URL (docs/testability.md, rule 5).
 */
@Component({
  selector: 'app-events',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon, MyResponse],
  template: `
    <div class="title-row">
      <h1>{{ texts.events.title }}</h1>
      @if (canCreate()) {
        <div class="title-actions">
          <a
            class="button button-secondary button-small"
            routerLink="/statistik"
            data-testid="statistics-link"
            >{{ texts.statistics.link }}</a
          >
          <a class="button button-primary button-small" routerLink="/termine/neu">{{
            texts.events.create
          }}</a>
        </div>
      }
    </div>

    <div class="filter">
      <nav class="switch" [attr.aria-label]="texts.events.view">
        <a
          routerLink="/termine"
          [queryParams]="{ ansicht: null }"
          queryParamsHandling="merge"
          [attr.aria-current]="!past() ? 'page' : null"
          >{{ texts.events.upcoming }}</a
        >
        <a
          routerLink="/termine"
          [queryParams]="{ ansicht: 'vergangen' }"
          queryParamsHandling="merge"
          [attr.aria-current]="past() ? 'page' : null"
          >{{ texts.events.past }}</a
        >
      </nav>
      @if (myTeams().length > 1 || team()) {
        <div class="field">
          <label for="events-team">{{ texts.events.team }}</label>
          <select id="events-team" (change)="selectTeam($any($event.target).value)">
            <option value="" [selected]="!team()">{{ texts.events.allTeams }}</option>
            @for (t of teamSelection(); track t.id) {
              <option [value]="t.id" [selected]="team() === t.id">{{ t.name }}</option>
            }
          </select>
        </div>
      }
    </div>

    @if (error(); as message) {
      <p class="message-error" role="alert">{{ message }}</p>
    }

    <section
      class="card"
      aria-labelledby="events-list-title"
      [attr.aria-busy]="loading() ? 'true' : null"
    >
      <h2 id="events-list-title" class="visually-hidden">
        {{ past() ? texts.events.pastEvents : texts.events.upcomingEvents }}
      </h2>
      @if (loading()) {
        <p class="empty">{{ texts.common.loading }}</p>
      } @else if (events().length === 0) {
        <p class="empty">{{ past() ? texts.events.emptyPast : texts.events.empty }}</p>
      } @else {
        @for (m of months(); track m.key) {
          <h3 class="month" [id]="'month-' + m.key">{{ m.name }}</h3>
          <ul class="list" [attr.aria-labelledby]="'month-' + m.key">
            @for (t of m.events; track t.id) {
              <li
                class="event-entry"
                [attr.data-testid]="'event-card-' + t.id"
                [attr.aria-label]="cardName(t)"
                [attr.data-status]="t.status"
              >
                <a class="row-link event" [routerLink]="['/termine', t.id]">
                  <span class="date" aria-hidden="true">
                    <span class="weekday">{{ date(t.start).slice(0, 2) }}</span>
                    <span class="day">{{ date(t.start).slice(3, 9) }}</span>
                  </span>
                  <span class="content">
                    <span class="row-title" [class.struck-through]="t.status === 'CANCELLED'">
                      {{ t.title }}
                    </span>
                    <span class="secondary-text">
                      {{ period(t) }} · {{ teams(t) }}
                      @if (t.location) {
                        · {{ t.location.name }}
                      }
                    </span>
                    @if (t.status === 'CANCELLED') {
                      <span class="status-row">
                        <span class="chip" data-kind="cancelled" data-testid="event-status">{{
                          texts.events.cancelled
                        }}</span>
                        @if (t.cancellationReason) {
                          <span class="secondary-text">{{ t.cancellationReason }}</span>
                        }
                      </span>
                    } @else {
                      @if (t.rescheduled) {
                        <span class="status-row">
                          <span class="chip" data-kind="rescheduled" data-testid="event-status">{{
                            texts.events.rescheduled
                          }}</span>
                        </span>
                      }
                      @if (t.counts; as z) {
                        <span class="secondary-text counter" data-testid="event-counter">
                          <span data-counter="attending">{{
                            texts.overview.attending(z.attending)
                          }}</span>
                          ·
                          <span data-counter="declined">{{
                            texts.overview.declined(z.declined)
                          }}</span>
                          ·
                          <span data-counter="maybe">{{ texts.overview.maybe(z.maybe) }}</span>
                          ·
                          <span data-counter="pending">{{
                            texts.overview.pending(z.pending)
                          }}</span>
                        </span>
                      }
                    }
                    @if (past() && isMember(t)) {
                      <span class="secondary-text" [attr.data-response]="t.myResponse.status">{{
                        texts.response.yourResponse(texts.response.status[t.myResponse.status])
                      }}</span>
                    }
                  </span>
                  <app-icon name="chevron-right" [size]="20" />
                </a>
                @if (!past() && isMember(t)) {
                  <app-my-response
                    class="card-response"
                    [eventId]="t.id"
                    [response]="t.myResponse"
                    [name]="texts.response.myResponseFor(cardName(t))"
                    [locked]="t.status === 'CANCELLED' ? texts.response.cancelled : null"
                    (changed)="reload(t.id)"
                  />
                }
              </li>
            }
          </ul>
        }
      }
    </section>
  `,
  styleUrl: './events.css',
})
export class Events {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  protected readonly texts = TEXTS;

  /** Query `?team=` */
  readonly team = input<string | undefined>();
  /** Query `?ansicht=vergangen` */
  // eslint-disable-next-line @angular-eslint/no-input-rename -- query parameter `?ansicht=` is part of the browser URL and stays German (NF-40)
  readonly view = input<string | undefined>(undefined, { alias: 'ansicht' });

  protected readonly events = signal<EventSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly past = computed(() => this.view() === 'vergangen');
  private readonly year = computed(() => yearOf(this.auth.me()?.now));

  /** All teams of the current season, only loaded for coaches and admins (RR-03, E-69). */
  private readonly allTeams = signal<{ id: string; name: string }[]>([]);

  /**
   * Teams to filter by, by name: the own teams of the current season. Coaches and admins choose from
   * all teams, and then see that team's events even without being a member (E-69).
   */
  protected readonly myTeams = computed(() => {
    const own = (this.auth.me()?.memberships ?? []).map((z) => z.team);
    const all = this.allTeams();
    return (all.length > 0 ? [...all] : own).sort((a, b) => a.name.localeCompare(b.name, 'de-CH'));
  });

  /**
   * Select «Team»: the own teams. If `?team=` points to another team (e.g. a shared link), it appears
   * in addition, so that the select and the list match (test finding F-03).
   */
  protected readonly teamSelection = computed(() => {
    const own = this.myTeams();
    const id = this.team();
    if (!id || own.some((t) => t.id === id)) return own;
    const name =
      this.events()
        .flatMap((t) => t.teams)
        .find((t) => t.id === id)?.name ?? TEXTS.events.otherTeam;
    return [...own, { id, name }];
  });

  /** Create events: coaches (in any team) and admins (TER-11, RR-05). */
  protected readonly canCreate = computed(() => this.auth.canManage());

  /** Events grouped by month, in the order of the API. */
  protected readonly months = computed<Month[]>(() => {
    const months: Month[] = [];
    for (const t of this.events()) {
      const key = monthKey(t.start);
      let m = months.at(-1);
      if (!m || m.key !== key) {
        m = { key, name: month(t.start), events: [] };
        months.push(m);
      }
      m.events.push(t);
    }
    return months;
  });

  constructor() {
    effect(() => {
      const team = this.team();
      const view = this.view();
      untracked(() => void this.load(team, view));
    });
    if (this.auth.canManage()) {
      void this.loadAllTeams();
    }
  }

  private async loadAllTeams(): Promise<void> {
    try {
      const teams = await firstValueFrom(this.http.get<Team[]>('/api/teams'));
      this.allTeams.set(teams.map((t) => ({ id: t.id, name: t.name })));
    } catch {
      // Without the list the select shows the own teams; the event list itself is not affected.
    }
  }

  protected date(iso: string): string {
    return eventDate(iso, this.year());
  }

  protected period(t: EventSummary): string {
    return TEXTS.events.period(clockTime(t.start), clockTime(t.end));
  }

  protected teams(t: EventSummary): string {
    return TEXTS.events.joinTeams(t.teams.map((x) => x.name));
  }

  /**
   * Member of an involved team: only then the person can respond for themselves (ANM-01, RR-02). Coaches
   * and admins also see events of other teams (E-69), but without «Meine Antwort» (same rule as the
   * event detail page).
   */
  protected isMember(t: EventSummary): boolean {
    return t.teams.some((x) => !!x.myTeamRole);
  }

  /** Name of the list entry, e.g. «Training Di 17.11. 18:30». */
  protected cardName(t: EventSummary): string {
    return TEXTS.events.card(t.title, this.date(t.start), clockTime(t.start));
  }

  protected selectTeam(teamId: string): void {
    void this.router.navigate(['/termine'], {
      queryParams: { team: teamId || null },
      queryParamsHandling: 'merge',
    });
  }

  /** After a response: fetch the own response and counters of this event again (without reloading the page). */
  protected async reload(id: string): Promise<void> {
    try {
      const updated = await firstValueFrom(this.http.get<EventSummary>(`/api/events/${id}`));
      this.events.update((list) => list.map((t) => (t.id === id ? updated : t)));
    } catch (e) {
      this.error.set(errorText(e));
    }
  }

  private async load(team: string | undefined, view: string | undefined): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      const params: Record<string, string> = {};
      if (team) params['team'] = team;
      if (view === 'vergangen') params['view'] = 'past';
      this.events.set(
        await firstValueFrom(this.http.get<EventSummary[]>('/api/events', { params })),
      );
    } catch (e) {
      this.events.set([]);
      this.error.set(errorText(e));
    } finally {
      this.loading.set(false);
    }
  }
}
