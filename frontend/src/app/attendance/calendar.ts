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
import type { Absence, CalendarView, EventSummary } from '../api/types';
import { AuthService } from '../core/auth.service';
import { errorText } from '../core/error-text';
import { Icon } from '../core/icon';
import { TEXTS } from '../core/texts';
import { isDayKey, isMonthKey, monthGrid, shiftMonth } from './calendar-dates';
import { clockTime, dayKey, dayLabel, monthName, yearOf } from '../core/time';

interface Day {
  key: string;
  number: number;
  inMonth: boolean;
  today: boolean;
  events: EventSummary[];
  absence: Absence | null;
}

/** Events shown in a day cell before «+n». */
const VISIBLE_PER_DAY = 3;

/**
 * Calendar view (KAL): a month grid with the events of the chosen teams and the own absences, and below it the
 * events of the chosen day. A player in several teams sees them together; coaches start with the teams they
 * coach and, like admins, may show all teams. Month, day and teams are in the URL (`?monat=2026-11&tag=2026-11-17
 * &teams=<id>,<id>&alle=ja`, docs/testability.md rule 5).
 */
@Component({
  selector: 'app-calendar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon],
  template: `
    <div class="title-row">
      <h1>{{ s.title }}</h1>
      <div class="title-actions">
        <a
          class="button button-secondary button-small"
          routerLink="/profil/abwesenheiten"
          data-testid="absences-link"
          >{{ s.absencesLink }}</a
        >
        <a
          class="button button-secondary button-small"
          routerLink="/termine"
          data-testid="event-list-link"
          >{{ s.listLink }}</a
        >
      </div>
    </div>

    @if (data(); as d) {
      @if (d.teams.length > 1 || d.canShowAllTeams) {
        <fieldset class="teams pill-group" data-testid="calendar-teams">
          <legend>{{ s.teams }}</legend>
          <div class="pill-options">
            @for (t of d.teams; track t.id) {
              <label class="pill-option" [attr.data-testid]="'calendar-team-' + t.id">
                <input
                  type="checkbox"
                  name="calendar-team"
                  [value]="t.id"
                  [checked]="selected().has(t.id)"
                  [disabled]="selected().size === 1 && selected().has(t.id)"
                  [attr.aria-describedby]="
                    selected().size === 1 && selected().has(t.id) ? 'calendar-last-team' : null
                  "
                  (change)="toggleTeam(t.id, $any($event.target).checked)"
                />
                <span>{{ t.name }}</span>
              </label>
            }
          </div>
          <p id="calendar-last-team" class="visually-hidden">{{ s.lastTeam }}</p>
          <div class="team-actions">
            @if (!isDefault()) {
              <a
                class="button button-secondary button-small"
                routerLink="/termine/kalender"
                [queryParams]="{ teams: null, alle: null }"
                queryParamsHandling="merge"
                data-testid="calendar-my-teams"
                >{{ s.myTeams }}</a
              >
            }
            @if (d.canShowAllTeams && !d.allTeams) {
              <a
                class="button button-secondary button-small"
                routerLink="/termine/kalender"
                [queryParams]="{ teams: null, alle: 'ja' }"
                queryParamsHandling="merge"
                data-testid="calendar-all-teams"
                >{{ s.allTeams }}</a
              >
            }
          </div>
        </fieldset>
      }
    }

    @if (error(); as message) {
      <p class="message-error" role="alert">{{ message }}</p>
    }

    <div class="layout">
      <section
        class="card month-card"
        aria-labelledby="calendar-month"
        [attr.aria-busy]="loading() ? 'true' : null"
        data-testid="calendar-month"
        [attr.data-month]="monthKey()"
      >
        <div class="month-row">
          <a
            class="icon-link"
            routerLink="/termine/kalender"
            [queryParams]="{ monat: previousMonth(), tag: null }"
            queryParamsHandling="merge"
            [attr.aria-label]="s.previous"
            data-testid="calendar-previous"
          >
            <app-icon name="chevron-left" [size]="22" />
          </a>
          <h2 id="calendar-month" class="month-title" aria-live="polite">
            {{ monthTitle() }}
          </h2>
          <a
            class="icon-link"
            routerLink="/termine/kalender"
            [queryParams]="{ monat: nextMonth(), tag: null }"
            queryParamsHandling="merge"
            [attr.aria-label]="s.next"
            data-testid="calendar-next"
          >
            <app-icon name="chevron-right" [size]="22" />
          </a>
          <a
            class="button button-secondary button-small today-link"
            routerLink="/termine/kalender"
            [queryParams]="{ monat: null, tag: null }"
            queryParamsHandling="merge"
            data-testid="calendar-today"
            >{{ s.today }}</a
          >
        </div>

        <table class="grid" [attr.aria-label]="s.grid(monthTitle())">
          <thead>
            <tr>
              @for (w of s.weekdays; track w; let i = $index) {
                <th scope="col">
                  <abbr [title]="s.weekdayNames[i]">{{ w }}</abbr>
                </th>
              }
            </tr>
          </thead>
          <tbody>
            @for (week of weeks(); track week[0].key) {
              <tr>
                @for (day of week; track day.key) {
                  <td
                    [attr.data-in-month]="day.inMonth ? 'true' : 'false'"
                    [attr.data-absent]="day.absence ? 'true' : null"
                  >
                    <a
                      class="day"
                      routerLink="/termine/kalender"
                      [queryParams]="{
                        tag: day.key,
                        monat: day.inMonth ? monthKey() : day.key.slice(0, 7),
                      }"
                      queryParamsHandling="merge"
                      [attr.data-testid]="'calendar-day-' + day.key"
                      [attr.data-count]="day.events.length"
                      [attr.aria-current]="day.key === selectedDay() ? 'date' : null"
                      [class.today]="day.today"
                    >
                      <!-- The name says date, number of events and absence; the visible parts are hidden from it -->
                      <span class="visually-hidden">{{ dayName(day) }}</span>
                      <span class="number" aria-hidden="true">{{ day.number }}</span>
                      <span class="entries" aria-hidden="true">
                        @for (t of day.events.slice(0, visible); track t.id) {
                          <span
                            class="entry"
                            [attr.data-tag]="t.variant?.color"
                            [class.cancelled]="t.status === 'CANCELLED'"
                            [attr.data-response]="isMember(t) ? t.myResponse.status : null"
                          >
                            <span class="entry-dot"></span>
                            <span class="entry-text">{{ time(t.start) }} {{ t.title }}</span>
                          </span>
                        }
                        @if (day.events.length > visible) {
                          <span class="more">{{ s.more(day.events.length - visible) }}</span>
                        }
                      </span>
                    </a>
                  </td>
                }
              </tr>
            }
          </tbody>
        </table>
      </section>

      <section
        class="card agenda"
        aria-labelledby="calendar-day-title"
        data-testid="calendar-agenda"
      >
        <h2 id="calendar-day-title" class="section-title">{{ selectedLabel() }}</h2>
        @if (selectedAbsence(); as a) {
          <p class="absence-note" data-testid="calendar-absence">
            <app-icon name="plane" [size]="18" />
            <span>{{ s.absent(absenceText(a)) }}</span>
          </p>
        }
        @if (loading()) {
          <p class="empty">{{ texts.common.loading }}</p>
        } @else if (selectedEvents().length === 0) {
          <p class="empty">{{ s.noEvents }}</p>
        } @else {
          <ul class="list" [attr.aria-label]="s.dayEvents(selectedLabel())">
            @for (t of selectedEvents(); track t.id) {
              <li
                [attr.data-testid]="'calendar-event-' + t.id"
                [attr.data-status]="t.status"
                [attr.aria-label]="t.title + ' ' + time(t.start)"
              >
                <a class="row-link agenda-entry" [routerLink]="['/termine', t.id]">
                  <span class="agenda-time" [attr.data-tag]="t.variant?.color">{{
                    time(t.start)
                  }}</span>
                  <span class="agenda-content">
                    <span class="row-title" [class.struck-through]="t.status === 'CANCELLED'">{{
                      t.title
                    }}</span>
                    <span class="secondary-text">
                      {{ texts.events.period(time(t.start), time(t.end)) }}
                      @if (t.location) {
                        · {{ t.location.name }}
                      }
                    </span>
                    <span class="secondary-text">{{ teamNames(t) }}</span>
                    <span class="status-row">
                      @if (t.variant; as v) {
                        <span class="tag-chip" [attr.data-tag]="v.color">{{ v.name }}</span>
                      }
                      @if (t.status === 'CANCELLED') {
                        <span class="chip" data-kind="cancelled">{{ texts.events.cancelled }}</span>
                      }
                      @if (isMember(t)) {
                        <span
                          class="chip response-chip"
                          [attr.data-response]="t.myResponse.status"
                          data-testid="calendar-my-response"
                          >{{
                            texts.response.yourResponse(texts.response.status[t.myResponse.status])
                          }}</span
                        >
                      }
                    </span>
                  </span>
                  <app-icon name="chevron-right" [size]="20" />
                </a>
              </li>
            }
          </ul>
        }
      </section>
    </div>
  `,
  styleUrl: './calendar.css',
})
export class CalendarPage {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  protected readonly texts = TEXTS;
  protected readonly s = TEXTS.calendarView;
  protected readonly time = clockTime;
  protected readonly visible = VISIBLE_PER_DAY;

  // Query parameters are part of the browser URL and stay German (NF-40)
  // eslint-disable-next-line @angular-eslint/no-input-rename
  readonly monthParam = input<string | undefined>(undefined, { alias: 'monat' });
  // eslint-disable-next-line @angular-eslint/no-input-rename
  readonly dayParam = input<string | undefined>(undefined, { alias: 'tag' });
  readonly teams = input<string | undefined>();
  // eslint-disable-next-line @angular-eslint/no-input-rename
  readonly allParam = input<string | undefined>(undefined, { alias: 'alle' });

  protected readonly data = signal<CalendarView | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  /** Today in Zurich, from the backend clock (docs/testability.md rule 8). */
  private readonly today = computed(() => {
    const now = this.auth.me()?.now;
    return now ? dayKey(now) : dayKey(new Date().toISOString());
  });
  private readonly year = computed(() => yearOf(this.auth.me()?.now));

  protected readonly monthKey = computed(() => {
    const m = this.monthParam();
    if (isMonthKey(m)) return m;
    const d = this.dayParam();
    return isDayKey(d) ? d.slice(0, 7) : this.today().slice(0, 7);
  });
  protected readonly previousMonth = computed(() => shiftMonth(this.monthKey(), -1));
  protected readonly nextMonth = computed(() => shiftMonth(this.monthKey(), 1));
  protected readonly monthTitle = computed(() => monthName(this.monthKey()));

  private readonly grid = computed(() => monthGrid(this.monthKey()));

  /** Chosen day: `?tag=`, otherwise today if it is in the month, otherwise the first of the month. */
  protected readonly selectedDay = computed(() => {
    const d = this.dayParam();
    if (isDayKey(d)) return d;
    const today = this.today();
    return today.startsWith(this.monthKey()) ? today : `${this.monthKey()}-01`;
  });
  protected readonly selectedLabel = computed(() => dayLabel(this.selectedDay(), this.year()));

  private readonly chosenTeams = computed(() =>
    (this.teams() ?? '')
      .split(',')
      .map((id) => id.trim())
      .filter((id) => id.length > 0),
  );

  /** Teams shown now, as the API reports them. */
  protected readonly selected = computed(() => new Set(this.data()?.teamIds ?? []));

  protected readonly isDefault = computed(() => {
    const d = this.data();
    if (!d) return true;
    const defaults = new Set(d.defaultTeamIds);
    return (
      !d.allTeams && d.teamIds.length === defaults.size && d.teamIds.every((id) => defaults.has(id))
    );
  });

  private readonly eventsByDay = computed(() => {
    const map = new Map<string, EventSummary[]>();
    for (const t of this.data()?.events ?? []) {
      const key = dayKey(t.start);
      const list = map.get(key) ?? [];
      list.push(t);
      map.set(key, list);
    }
    return map;
  });

  protected readonly weeks = computed<Day[][]>(() => {
    const month = this.monthKey();
    const today = this.today();
    const byDay = this.eventsByDay();
    const absences = this.data()?.absences ?? [];
    return this.grid().map((week) =>
      week.map((key) => ({
        key,
        number: Number(key.slice(8)),
        inMonth: key.startsWith(month),
        today: key === today,
        events: byDay.get(key) ?? [],
        absence: absences.find((a) => a.from <= key && key <= a.to) ?? null,
      })),
    );
  });

  protected readonly selectedEvents = computed(
    () => this.eventsByDay().get(this.selectedDay()) ?? [],
  );
  protected readonly selectedAbsence = computed(() => {
    const day = this.selectedDay();
    return (this.data()?.absences ?? []).find((a) => a.from <= day && day <= a.to) ?? null;
  });

  constructor() {
    effect(() => {
      const month = this.monthKey();
      const teams = this.chosenTeams();
      const all = this.allParam() === 'ja';
      untracked(() => void this.load(month, teams, all));
    });
  }

  protected dayName(day: Day): string {
    return this.s.day(dayLabel(day.key, this.year()), day.events.length, !!day.absence);
  }

  protected absenceText(a: Absence): string {
    const reason = TEXTS.absences.reasons[a.reason];
    return a.note ? `${reason} · ${a.note}` : reason;
  }

  protected teamNames(t: EventSummary): string {
    return TEXTS.events.joinTeams(t.teams.map((x) => x.name));
  }

  /** Member of an involved team: only then the event has an own response (same rule as the event list). */
  protected isMember(t: EventSummary): boolean {
    return t.teams.some((x) => !!x.myTeamRole);
  }

  protected toggleTeam(id: string, checked: boolean): void {
    const d = this.data();
    if (!d) return;
    const next = new Set(d.teamIds);
    if (checked) next.add(id);
    else next.delete(id);
    if (next.size === 0) return;
    const allIds = d.teams.map((t) => t.id);
    const everything = d.canShowAllTeams && allIds.every((x) => next.has(x));
    const defaults = new Set(d.defaultTeamIds);
    const isDefault = next.size === defaults.size && [...next].every((x) => defaults.has(x));
    void this.router.navigate(['/termine/kalender'], {
      queryParams: {
        alle: everything ? 'ja' : null,
        teams: everything || isDefault ? null : allIds.filter((x) => next.has(x)).join(','),
      },
      queryParamsHandling: 'merge',
    });
  }

  /** Counts the loads: a slow answer to an earlier filter must not replace the answer to the latest one. */
  private request = 0;

  private async load(month: string, teams: string[], all: boolean): Promise<void> {
    const nr = ++this.request;
    this.loading.set(true);
    this.error.set(null);
    const grid = monthGrid(month);
    let params = new HttpParams().set('from', grid[0][0]).set('to', grid[grid.length - 1][6]);
    if (all) {
      params = params.set('all', 'true');
    } else {
      for (const id of teams) params = params.append('team', id);
    }
    try {
      const data = await firstValueFrom(
        this.http.get<CalendarView>('/api/events/calendar', { params }),
      );
      if (nr === this.request) this.data.set(data);
    } catch (e) {
      if (nr === this.request) this.error.set(errorText(e));
    } finally {
      if (nr === this.request) this.loading.set(false);
    }
  }
}
