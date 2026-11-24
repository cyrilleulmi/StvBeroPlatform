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
import { Scoreboard } from '../core/scoreboard';
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
  imports: [RouterLink, Icon, MyResponse, Scoreboard],
  template: `
    <div class="title-row">
      <h1>{{ texts.events.title }}</h1>
      <div class="title-actions">
        <a
          class="button button-secondary button-small"
          routerLink="/termine/kalender"
          data-testid="calendar-link"
        >
          <app-icon name="calendar" [size]="18" />
          {{ texts.calendarView.link }}
        </a>
        @if (canCreate()) {
          <a
            class="button button-secondary button-small"
            routerLink="/statistik"
            data-testid="statistics-link"
            >{{ texts.statistics.link }}</a
          >
          <!-- Series, single events, match days, swiss unihockey of a team in one place (TER-21) -->
          <a
            class="button button-secondary button-small"
            routerLink="/termine/ueberarbeiten"
            [queryParams]="{ team: team() || null }"
            data-testid="rework-link"
          >
            <app-icon name="pencil" [size]="16" />
            {{ texts.rework.button }}
          </a>
          <a class="button button-primary button-small" routerLink="/termine/neu">{{
            texts.events.create
          }}</a>
        }
      </div>
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
      @if (optionalCount() > 0 || !showOptional()) {
        <label class="check optional-filter">
          <input
            name="optional-filter"
            type="checkbox"
            data-testid="optional-filter"
            [checked]="showOptional()"
            (change)="toggleOptional($any($event.target).checked)"
          />
          {{ texts.participation.filter }}
        </label>
      }
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

    <section aria-labelledby="events-list-title" [attr.aria-busy]="loading() ? 'true' : null">
      <h2 id="events-list-title" class="visually-hidden">
        {{ past() ? texts.events.pastEvents : texts.events.upcomingEvents }}
      </h2>
      @if (loading()) {
        <p class="card empty">{{ texts.common.loading }}</p>
      } @else if (shown().length === 0) {
        <div class="card">
          <p class="empty">{{ past() ? texts.events.emptyPast : texts.events.empty }}</p>
          @if (hiddenCount(); as n) {
            <p class="secondary-text" data-testid="optional-hidden">
              {{ texts.participation.hidden(n) }}
            </p>
          }
        </div>
      } @else {
        @if (hiddenCount(); as n) {
          <p class="secondary-text" data-testid="optional-hidden">
            {{ texts.participation.hidden(n) }}
          </p>
        }
        @for (m of months(); track m.key) {
          <h3 class="month" [id]="'month-' + m.key">{{ m.name }}</h3>
          <!-- Every event as a card like on the start screen, in the colour of its kind (VAR-05, TER-14) -->
          <ul class="cards" [attr.aria-labelledby]="'month-' + m.key">
            @for (t of m.events; track t.id) {
              <li
                class="card event-card event-tint"
                [attr.data-filled]="filled().has(t.id) ? 'true' : null"
                [attr.data-testid]="'event-card-' + t.id"
                [attr.aria-label]="cardName(t)"
                [attr.data-status]="t.status"
                [attr.data-variant]="t.variant?.kind"
                [attr.data-tag]="t.status === 'CANCELLED' ? null : (t.variant?.color ?? null)"
                [attr.data-participation]="t.myParticipation ?? null"
              >
                <a class="event-link" [routerLink]="['/termine', t.id]">
                  <span class="event-title" [class.struck-through]="t.status === 'CANCELLED'">{{ t.title }}</span>
                  <!-- When and where lead (they decide whether one can come), the teams follow quietly -->
                  <span class="when-where">{{ date(t.start) }} · {{ period(t) }}</span>
                  @if (t.location) {
                    <span class="place">{{ t.location.name }}</span>
                  }
                  <span class="secondary-text">{{ teams(t) }}</span>
                  <app-icon class="chevron" name="chevron-right" [size]="22" />
                </a>
                <!-- One row of markers: kind of event (VAR-05, TER-14), voluntary, status, warnings -->
                <span class="status-row">
                  @if (t.variant; as v) {
                    <span class="tag-chip" data-testid="event-variant" [attr.data-tag]="v.color">{{ v.name }}</span>
                  }
                  @if (t.myParticipation === 'OPTIONAL') {
                    <span class="chip" data-kind="optional" data-testid="event-optional">{{
                      texts.participation.optional
                    }}</span>
                  }
                  @if (t.status === 'CANCELLED') {
                    <span class="chip" data-kind="cancelled" data-testid="event-status">{{
                      texts.events.cancelled
                    }}</span>
                  } @else {
                    @if (t.rescheduled) {
                      <span class="chip" data-kind="rescheduled" data-testid="event-status">{{
                        texts.events.rescheduled
                      }}</span>
                    }
                    <!-- Only coaches and admins get a warning count from the API (F-16) -->
                    @if (t.warningCount; as n) {
                      <span
                        class="chip warning-chip"
                        data-kind="warning"
                        data-testid="event-warnings"
                        [attr.data-warning-count]="n"
                      >
                        <app-icon name="triangle-alert" [size]="14" />
                        {{ texts.warnings.count(n) }}
                      </span>
                    }
                  }
                </span>
                @if (t.status === 'CANCELLED') {
                  @if (t.cancellationReason) {
                    <p class="secondary-text reason">{{ t.cancellationReason }}</p>
                  }
                } @else if (t.counts; as z) {
                  <!-- The counts as on the start screen: big digits, «8 dabei · 3 nicht dabei …» for screen readers -->
                  <p class="scoreboard counter" data-testid="event-counter">
                    <app-scoreboard [counts]="z" />
                  </p>
                }
                @if (past() && isMember(t)) {
                  <p class="secondary-text" [attr.data-response]="t.myResponse.status">{{
                    texts.response.yourResponse(texts.response.status[t.myResponse.status])
                  }}</p>
                }
                @if (!past() && isMember(t)) {
                  <app-my-response
                    class="card-response"
                    [eventId]="t.id"
                    [response]="t.myResponse"
                    [reasonRequired]="!!t.variant?.declineReasonRequired"
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

  /** Query `?freiwillig=aus`: hide the events that are voluntary for the person (TER-17). */
  // eslint-disable-next-line @angular-eslint/no-input-rename -- query parameter `?freiwillig=` is part of the browser URL and stays German (NF-40)
  readonly optional = input<string | undefined>(undefined, { alias: 'freiwillig' });

  protected readonly past = computed(() => this.view() === 'vergangen');
  protected readonly showOptional = computed(() => this.optional() !== 'aus');

  /** Events that are voluntary for the logged-in person (TER-15). */
  protected readonly optionalCount = computed(
    () => this.events().filter((t) => t.myParticipation === 'OPTIONAL').length,
  );
  protected readonly hiddenCount = computed(() => (this.showOptional() ? 0 : this.optionalCount()));
  protected readonly shown = computed(() =>
    this.showOptional()
      ? this.events()
      : this.events().filter((t) => t.myParticipation !== 'OPTIONAL'),
  );
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
    for (const t of this.shown()) {
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

  /** Teams of the event; the ones invited without obligation are named as such (TER-17). */
  protected teams(t: EventSummary): string {
    const optional = t.optionalTeamIds ?? [];
    return TEXTS.events.joinTeams(
      t.teams.map((x) =>
        optional.includes(x.id) ? TEXTS.participation.optionalTeam(x.name) : x.name,
      ),
    );
  }

  protected toggleOptional(show: boolean): void {
    void this.router.navigate(['/termine'], {
      queryParams: { freiwillig: show ? null : 'aus' },
      queryParamsHandling: 'merge',
    });
  }

  /**
   * Member of an involved team: only then the person can respond for themselves (ANM-01, RR-02). Coaches
   * and admins also see events of other teams (E-69), but without «Meine Antwort» (same rule as the
   * event detail page).
   */
  protected isMember(t: EventSummary): boolean {
    return t.teams.some((x) => !!x.myTeamRole);
  }

  /**
   * The next practice and the next match day of the upcoming events: their cards are filled with the colour
   * of their kind, the others only have the line on top (E-105). Cancelled events do not count.
   */
  protected readonly filled = computed(() => {
    const ids = new Set<string>();
    if (this.past()) return ids;
    const planned = this.shown().filter((t) => t.status !== 'CANCELLED');
    for (const type of ['PRACTICE', 'MATCH_DAY'] as const) {
      const first = planned.find((t) => t.type === type);
      if (first) ids.add(first.id);
    }
    return ids;
  });

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
