import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { Tile, EventSummary } from '../../api/types';
import { MyResponse } from '../../attendance/my-response';
import { yearOf, eventDate, clockTime } from '../../attendance/time';
import { AuthService } from '../../core/auth.service';
import { errorText } from '../../core/error-text';
import { Icon } from '../../core/icon';
import { SuStatusNotice } from '../../core/su-status-notice';
import { TEXTS } from '../../core/texts';
import { startEvents } from './start-events';

/**
 * Start: greeting, next own event with response buttons (F-04, ANM-08), then the next practices so
 * that at least two upcoming practices are visible (F-13), and tiles from `GET /api/tiles`
 * (NF-11, NF-12, RR-05).
 */
@Component({
  selector: 'app-start',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, NgTemplateOutlet, Icon, MyResponse, SuStatusNotice],
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

  /** Next planned event of the own teams; skip cancelled ones. */
  protected readonly next = signal<EventSummary | null>(null);
  /** Further practices after the next event (F-13), cancelled ones skipped. */
  protected readonly practices = signal<EventSummary[]>([]);
  protected readonly eventLoading = signal(true);
  protected readonly eventError = signal<string | null>(null);

  private readonly year = computed(() => yearOf(this.auth.me()?.now));

  async ngOnInit(): Promise<void> {
    void this.loadEvent();
    try {
      this.tiles.set(await firstValueFrom(this.http.get<Tile[]>('/api/tiles')));
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.loading.set(false);
    }
  }

  /** «Training Di 17.11. 18:30–20:00 · Team 1 + Team 2» */
  protected description(t: EventSummary): string {
    return `${eventDate(t.start, this.year())} · ${TEXTS.events.period(clockTime(t.start), clockTime(t.end))} · ${TEXTS.events.joinTeams(t.teams.map((x) => x.name))}`;
  }

  protected cardName(t: EventSummary): string {
    return TEXTS.events.card(t.title, eventDate(t.start, this.year()), clockTime(t.start));
  }

  /** After a response: reload the counters of this event without reloading the page (F-13 K3). */
  protected async reload(id: string): Promise<void> {
    try {
      const updated = await firstValueFrom(this.http.get<EventSummary>(`/api/events/${id}`));
      if (this.next()?.id === id) this.next.set(updated);
      this.practices.update((list) => list.map((t) => (t.id === id ? updated : t)));
    } catch (e) {
      this.eventError.set(errorText(e));
    }
  }

  private async loadEvent(): Promise<void> {
    try {
      const events = await firstValueFrom(this.http.get<EventSummary[]>('/api/events'));
      const shown = startEvents(events);
      this.next.set(shown.next);
      this.practices.set(shown.practices);
    } catch (e) {
      this.eventError.set(errorText(e));
    } finally {
      this.eventLoading.set(false);
    }
  }
}
