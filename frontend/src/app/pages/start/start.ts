import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { Tile, EventSummary } from '../../api/types';
import { MyResponse } from '../../attendance/my-response';
import { PinnedChip } from '../../attendance/pin';
import { yearOf, eventDate, clockTime } from '../../core/time';
import { AuthService } from '../../core/auth.service';
import { errorText } from '../../core/error-text';
import { Icon } from '../../core/icon';
import { Scoreboard } from '../../core/scoreboard';
import { SuStatusNotice } from '../../core/su-status-notice';
import { TEXTS } from '../../core/texts';
import { startEvents } from './start-events';

/**
 * Start: greeting, then one list of cards sorted by date (TER-25, E-113): the practices of the week of the next
 * practice, the next match day (AUF-35) and pinned events (TER-26), each with response buttons (ANM-08) and counts;
 * then the tiles from `GET /api/tiles` (NF-11, NF-12, RR-05).
 */
@Component({
  selector: 'app-start',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon, MyResponse, SuStatusNotice, Scoreboard, PinnedChip],
  templateUrl: './start.html',
  styleUrl: './start.css',
})
export class Start implements OnInit {
  private readonly http = inject(HttpClient);
  protected readonly auth = inject(AuthService);
  protected readonly texts = TEXTS;

  protected readonly tiles = signal<Tile[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  /** Upcoming events of the own teams (`GET /api/events`); what shows is chosen by {@link startEvents}. */
  private readonly events = signal<EventSummary[]>([]);
  protected readonly eventLoading = signal(true);
  protected readonly eventError = signal<string | null>(null);

  private readonly now = computed(() => this.auth.me()?.now ?? new Date().toISOString());
  private readonly year = computed(() => yearOf(this.auth.me()?.now));

  protected readonly shown = computed(() => startEvents(this.events(), this.now()));

  /** The next practice and the next match day: filled with the colour of their kind (E-105). */
  protected readonly filled = computed(() => {
    const s = this.shown();
    return new Set([s.firstPracticeId, s.matchDayId].filter((id): id is string => !!id));
  });

  /** «Diese Woche», «Nächste Woche» or «Woche vom 30.11.» above the first practice (TER-25). */
  protected readonly weekLabel = computed(() => {
    const week = this.shown().week;
    if (!week) return '';
    if (week.kind !== 'later') return TEXTS.start.week[week.kind];
    return TEXTS.start.week.later(eventDate(`${week.monday}T12:00:00Z`, this.year()).slice(3));
  });

  async ngOnInit(): Promise<void> {
    void this.loadEvents();
    try {
      this.tiles.set(await firstValueFrom(this.http.get<Tile[]>('/api/tiles')));
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.loading.set(false);
    }
  }

  /** Practices keep `start-practice-<id>`, the next match day `next-match-day`; pinned events `start-pinned-<id>`. */
  protected testId(t: EventSummary): string {
    if (t.id === this.shown().matchDayId) return 'next-match-day';
    return t.type === 'PRACTICE' ? `start-practice-${t.id}` : `start-pinned-${t.id}`;
  }

  /** «Di 17.11. · 18:30–20:00»: day and time lead, the teams follow on their own quiet line. */
  protected when(t: EventSummary): string {
    return `${eventDate(t.start, this.year())} · ${TEXTS.events.period(clockTime(t.start), clockTime(t.end))}`;
  }

  /** «Team 1 + Team 2» */
  protected teamNames(t: EventSummary): string {
    return TEXTS.events.joinTeams(t.teams.map((x) => x.name));
  }

  protected cardName(t: EventSummary): string {
    return TEXTS.events.card(t.title, eventDate(t.start, this.year()), clockTime(t.start));
  }

  /** After a response: reload the counters of this event without reloading the page (F-13 K3). */
  protected async reload(id: string): Promise<void> {
    try {
      const updated = await firstValueFrom(this.http.get<EventSummary>(`/api/events/${id}`));
      this.events.update((list) => list.map((t) => (t.id === id ? updated : t)));
    } catch (e) {
      this.eventError.set(errorText(e));
    }
  }

  private async loadEvents(): Promise<void> {
    try {
      this.events.set(await firstValueFrom(this.http.get<EventSummary[]>('/api/events')));
    } catch (e) {
      this.eventError.set(errorText(e));
    } finally {
      this.eventLoading.set(false);
    }
  }
}
