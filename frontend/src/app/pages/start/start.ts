import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { Tile, EventSummary } from '../../api/types';
import { MyResponse } from '../../attendance/my-response';
import { yearOf, eventDate, clockTime } from '../../attendance/time';
import { AuthService } from '../../core/auth.service';
import { errorText } from '../../core/error-text';
import { Icon } from '../../core/icon';
import { TEXTS } from '../../core/texts';

/**
 * Start: Begrüssung, nächster eigener Termin mit Antwort-Buttons (F-04, ANM-08) und Kacheln aus
 * `GET /api/tiles` (NF-11, NF-12, RR-05).
 */
@Component({
  selector: 'app-start',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon, MyResponse],
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

  /** Nächster geplanter Termin der eigenen Teams; abgesagte überspringen. */
  protected readonly next = signal<EventSummary | null>(null);
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

  /** Nach einer Antwort: Zähler nachladen, ohne die Seite neu zu laden. */
  protected async reload(id: string): Promise<void> {
    try {
      this.next.set(await firstValueFrom(this.http.get<EventSummary>(`/api/events/${id}`)));
    } catch (e) {
      this.eventError.set(errorText(e));
    }
  }

  private async loadEvent(): Promise<void> {
    try {
      const termine = await firstValueFrom(this.http.get<EventSummary[]>('/api/events'));
      this.next.set(termine.find((t) => t.status !== 'CANCELLED') ?? null);
    } catch (e) {
      this.eventError.set(errorText(e));
    } finally {
      this.eventLoading.set(false);
    }
  }
}
