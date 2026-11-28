import { HttpClient, HttpParams } from '@angular/common/http';
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
import { PinnedChip } from './pin';
import { PreTrainingDisplay } from './pre-training';
import { yearOf, month, monthKey, eventDate, clockTime } from '../core/time';
import {
  apiTeams as teamsForApi,
  isDefault,
  lockedTeam,
  selectedTeams,
  teamOptions,
  toggleTeam as toggled,
} from './team-chips';

interface Month {
  key: string;
  name: string;
  events: EventSummary[];
}

/**
 * Tile Anwesenheit: events of all own teams, chronological (TER-13), with the own response directly
 * in the card (ANM-08) and the total counter, which updates after a response without reloading. Team chips
 * `?teams=<id>,<id>` (E-111), voluntary events `?freiwillig=aus` and view `?ansicht=vergangen` are in the URL
 * (docs/testability.md, rule 5).
 */
@Component({
  selector: 'app-events',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon, MyResponse, Scoreboard, PinnedChip, PreTrainingDisplay],
  template: `
    <div class="title-row">
      <h1>{{ texts.events.title }}</h1>
      <!-- Actions as icons, so that title and actions fit into one row on a phone (E-111) -->
      <div class="title-actions">
        <a
          class="icon-action"
          routerLink="/termine/kalender"
          data-testid="calendar-link"
          [attr.aria-label]="texts.calendarView.link"
          [title]="texts.calendarView.link"
        >
          <app-icon name="calendar" [size]="22" />
        </a>
        @if (canCreate()) {
          <!-- Series, single events, match days, swiss unihockey of a team in one place (TER-21) -->
          <a
            class="icon-action"
            routerLink="/termine/ueberarbeiten"
            [queryParams]="{ team: singleTeam() }"
            data-testid="rework-link"
            [attr.aria-label]="texts.rework.button"
            [title]="texts.rework.button"
          >
            <app-icon name="wrench" [size]="22" />
          </a>
          <a
            class="icon-action primary"
            routerLink="/termine/neu"
            [attr.aria-label]="texts.events.create"
            [title]="texts.events.create"
          >
            <app-icon name="plus" [size]="24" />
          </a>
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
      @if (optionalChip() || teamChips()) {
        <!-- One row of chips that scrolls sideways on a phone: voluntary events, then the teams (TER-17, E-111) -->
        <div class="chips" role="group" [attr.aria-label]="texts.events.filters">
          @if (optionalChip()) {
            <label class="toggle-chip">
              <input
                name="optional-filter"
                type="checkbox"
                data-testid="optional-filter"
                [attr.aria-label]="texts.participation.filter"
                [checked]="showOptional()"
                (change)="toggleOptional($any($event.target).checked)"
              />
              <span class="bubble"
                ><app-icon class="tick" name="check" [size]="14" />{{ texts.events.optionalChip }}</span
              >
            </label>
          }
          @if (teamChips()) {
            <span class="team-chips" role="group" [attr.aria-label]="texts.events.teams">
              @for (t of options(); track t.id) {
                <label class="toggle-chip">
                  <input
                    type="checkbox"
                    name="events-team"
                    [value]="t.id"
                    [attr.data-testid]="'team-chip-' + t.id"
                    [checked]="selected().includes(t.id)"
                    [disabled]="locked() === t.id"
                    [attr.aria-describedby]="locked() === t.id ? 'events-last-team' : null"
                    (change)="toggleTeam(t.id, $any($event.target).checked)"
                  />
                  <span class="bubble"><app-icon class="tick" name="check" [size]="14" />{{ t.name }}</span>
                </label>
              }
            </span>
            <span id="events-last-team" class="visually-hidden">{{ texts.events.lastTeam }}</span>
          }
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
                  <app-pre-training class="when-where" [event]="t" [day]="date(t.start)" [period]="period(t)" />
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
                  @if (t.pinned) {
                    <app-pinned-chip />
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
                  <!-- Coaches, admins and people with a special permission edit right from the list (E-111, RR-09) -->
                  @if (t.canEdit) {
                    <a
                      class="edit-link"
                      [routerLink]="['/termine', t.id, 'bearbeiten']"
                      [attr.data-testid]="'event-edit-' + t.id"
                      [attr.aria-label]="texts.events.editFor(cardName(t))"
                      [title]="texts.events.edit"
                    >
                      <app-icon name="pencil" [size]="18" />
                    </a>
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

  /** Query `?teams=<id>,<id>`: the teams that are on (E-111). */
  // eslint-disable-next-line @angular-eslint/no-input-rename -- `teams()` is the teams line of a card
  readonly chosenTeams = input<string | undefined>(undefined, { alias: 'teams' });
  /** Query `?team=<id>` of earlier links: still read as a choice of one team (E-111). */
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

  /** The own teams of the current season: on by default (TER-13). */
  private readonly defaults = computed(() => (this.auth.me()?.memberships ?? []).map((z) => z.team.id));

  /** Teams that are on (E-111): from the URL, otherwise the own teams. */
  protected readonly selected = computed(() => selectedTeams(this.chosenTeams(), this.team(), this.defaults()));
  protected readonly locked = computed(() => lockedTeam(this.selected(), this.defaults()));
  /** «Überarbeiten» hands over the team if exactly one is on (TER-21). */
  protected readonly singleTeam = computed(() => (this.selected().length === 1 ? this.selected()[0] : null));
  private readonly apiTeams = computed(() => teamsForApi(this.selected(), this.defaults()).join(','));

  /**
   * The chips: the own teams; coaches and admins choose from all teams and then see that team's events even
   * without being a member (E-69). A chosen team that is not among them (a shared link) is added.
   */
  protected readonly options = computed(() => {
    const own = (this.auth.me()?.memberships ?? []).map((z) => z.team);
    const all = this.allTeams();
    const known = this.events().flatMap((t) => t.teams);
    return teamOptions(all.length > 0 ? all : own, this.selected(), known, TEXTS.events.otherTeam);
  });

  /** No chips for a person with one team only, unless the URL chose another one (as before E-111). */
  protected readonly teamChips = computed(
    () => this.options().length > 1 || !isDefault(this.selected(), this.defaults()),
  );
  protected readonly optionalChip = computed(() => this.optionalCount() > 0 || !this.showOptional());

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
      const teams = this.apiTeams();
      const view = this.view();
      untracked(() => void this.load(teams, view));
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

  protected toggleTeam(id: string, on: boolean): void {
    const params = toggled(this.selected(), id, on, this.defaults(), this.options().map((t) => t.id));
    if (!params) return;
    void this.router.navigate(['/termine'], { queryParams: params, queryParamsHandling: 'merge' });
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

  /** Counts the loads: a slow answer to an earlier filter must not replace the answer to the latest one. */
  private request = 0;

  private async load(teams: string, view: string | undefined): Promise<void> {
    const nr = ++this.request;
    this.loading.set(true);
    this.error.set(null);
    try {
      let params = new HttpParams();
      for (const id of teams.split(',').filter((x) => x)) params = params.append('team', id);
      if (view === 'vergangen') params = params.set('view', 'past');
      const events = await firstValueFrom(
        this.http.get<EventSummary[]>('/api/events', { params }),
      );
      if (nr === this.request) this.events.set(events);
    } catch (e) {
      if (nr === this.request) {
        this.events.set([]);
        this.error.set(errorText(e));
      }
    } finally {
      if (nr === this.request) this.loading.set(false);
    }
  }
}
