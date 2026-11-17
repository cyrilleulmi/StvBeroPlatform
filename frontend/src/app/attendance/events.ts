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
import type { EventSummary } from '../api/types';
import { AuthService } from '../core/auth.service';
import { errorText } from '../core/error-text';
import { Icon } from '../core/icon';
import { TEXTS } from '../core/texts';
import { MyResponse } from './my-response';
import { yearOf, month, monthKey, eventDate, clockTime } from './time';

interface Month {
  key: string;
  name: string;
  termine: EventSummary[];
}

/**
 * Kachel Anwesenheit: Termine aller eigenen Teams, chronologisch (TER-13), mit der eigenen Antwort direkt
 * in der Karte (ANM-08) und dem Gesamtzähler, der sich nach einer Antwort ohne Neuladen aktualisiert. Filter `?team=<id>` und
 * Ansicht `?ansicht=vergangen` stehen in der URL (docs/testbarkeit.md, Regel 5).
 */
@Component({
  selector: 'app-termine',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon, MyResponse],
  template: `
    <div class="titelzeile">
      <h1>{{ texts.termine.title }}</h1>
      @if (canCreate()) {
        <a class="button button-primaer button-klein" routerLink="/termine/neu">{{
          texts.termine.create
        }}</a>
      }
    </div>

    <div class="filter">
      <nav class="umschalter" [attr.aria-label]="texts.termine.view">
        <a
          routerLink="/termine"
          [queryParams]="{ ansicht: null }"
          queryParamsHandling="merge"
          [attr.aria-current]="!past() ? 'page' : null"
          >{{ texts.termine.upcoming }}</a
        >
        <a
          routerLink="/termine"
          [queryParams]="{ ansicht: 'vergangen' }"
          queryParamsHandling="merge"
          [attr.aria-current]="past() ? 'page' : null"
          >{{ texts.termine.past }}</a
        >
      </nav>
      @if (myTeams().length > 1 || team()) {
        <div class="feld">
          <label for="termine-team">{{ texts.termine.team }}</label>
          <select id="termine-team" (change)="selectTeam($any($event.target).value)">
            <option value="" [selected]="!team()">{{ texts.termine.allTeams }}</option>
            @for (t of teamSelection(); track t.id) {
              <option [value]="t.id" [selected]="team() === t.id">{{ t.name }}</option>
            }
          </select>
        </div>
      }
    </div>

    @if (error(); as message) {
      <p class="meldung-fehler" role="alert">{{ message }}</p>
    }

    <section
      class="karte"
      aria-labelledby="termine-liste-titel"
      [attr.aria-busy]="loading() ? 'true' : null"
    >
      <h2 id="termine-liste-titel" class="visuell-versteckt">
        {{ past() ? texts.termine.pastEvents : texts.termine.upcomingEvents }}
      </h2>
      @if (loading()) {
        <p class="leer">{{ texts.common.loading }}</p>
      } @else if (termine().length === 0) {
        <p class="leer">{{ past() ? texts.termine.emptyPast : texts.termine.empty }}</p>
      } @else {
        @for (m of months(); track m.key) {
          <h3 class="monat" [id]="'monat-' + m.key">{{ m.name }}</h3>
          <ul class="liste" [attr.aria-labelledby]="'monat-' + m.key">
            @for (t of m.termine; track t.id) {
              <li
                class="termin-eintrag"
                [attr.data-testid]="'termin-karte-' + t.id"
                [attr.aria-label]="cardName(t)"
                [attr.data-status]="t.status"
              >
                <a class="zeile-link termin" [routerLink]="['/termine', t.id]">
                  <span class="datum" aria-hidden="true">
                    <span class="wochentag">{{ date(t.start).slice(0, 2) }}</span>
                    <span class="tag">{{ date(t.start).slice(3, 9) }}</span>
                  </span>
                  <span class="inhalt">
                    <span class="zeile-titel" [class.durchgestrichen]="t.status === 'CANCELLED'">
                      {{ t.title }}
                    </span>
                    <span class="nebentext">
                      {{ period(t) }} · {{ teams(t) }}
                      @if (t.location) {
                        · {{ t.location.name }}
                      }
                    </span>
                    @if (t.status === 'CANCELLED') {
                      <span class="status-zeile">
                        <span class="chip" data-art="abgesagt" data-testid="termin-status">{{
                          texts.termine.cancelled
                        }}</span>
                        @if (t.cancellationReason) {
                          <span class="nebentext">{{ t.cancellationReason }}</span>
                        }
                      </span>
                    } @else {
                      @if (t.rescheduled) {
                        <span class="status-zeile">
                          <span class="chip" data-art="verschoben" data-testid="termin-status">{{
                            texts.termine.rescheduled
                          }}</span>
                        </span>
                      }
                      @if (t.counts; as z) {
                        <span class="nebentext zaehler" data-testid="termin-zaehler">
                          <span data-zaehler="dabei">{{ texts.overview.attending(z.attending) }}</span>
                          ·
                          <span data-zaehler="nicht-dabei">{{
                            texts.overview.declined(z.declined)
                          }}</span>
                          ·
                          <span data-zaehler="vielleicht">{{
                            texts.overview.vielleicht(z.maybe)
                          }}</span>
                          ·
                          <span data-zaehler="offen">{{ texts.overview.offen(z.pending) }}</span>
                        </span>
                      }
                    }
                    @if (past()) {
                      <span class="nebentext" [attr.data-antwort]="t.myResponse.status">{{
                        texts.response.yourResponse(texts.response.status[t.myResponse.status])
                      }}</span>
                    }
                  </span>
                  <app-icon name="chevron-right" [size]="20" />
                </a>
                @if (!past()) {
                  <app-meine-antwort
                    class="karten-antwort"
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
  readonly view = input<string | undefined>(undefined, { alias: 'ansicht' });

  protected readonly termine = signal<EventSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly past = computed(() => this.view() === 'vergangen');
  private readonly year = computed(() => yearOf(this.auth.me()?.now));

  /** Eigene Teams der aktuellen Saison, nach Name. */
  protected readonly myTeams = computed(() =>
    (this.auth.me()?.memberships ?? [])
      .map((z) => z.team)
      .sort((a, b) => a.name.localeCompare(b.name, 'de-CH')),
  );

  /**
   * Auswahl «Team»: die eigenen Teams. Zeigt `?team=` auf ein anderes Team (z.B. ein geteilter Link),
   * erscheint es zusätzlich, damit Auswahl und Liste übereinstimmen (Test-Befund F-03).
   */
  protected readonly teamSelection = computed(() => {
    const own = this.myTeams();
    const id = this.team();
    if (!id || own.some((t) => t.id === id)) return own;
    const name =
      this.termine()
        .flatMap((t) => t.teams)
        .find((t) => t.id === id)?.name ?? TEXTS.termine.otherTeam;
    return [...own, { id, name }];
  });

  /** Termine erfassen: Coaches (in irgendeinem Team) und Admins (TER-11, RR-05). */
  protected readonly canCreate = computed(() => {
    const me = this.auth.me();
    return !!me && (me.admin || me.memberships.some((z) => z.teamRole === 'COACH'));
  });

  /** Termine nach Monat gruppiert, in der Reihenfolge der API. */
  protected readonly months = computed<Month[]>(() => {
    const months: Month[] = [];
    for (const t of this.termine()) {
      const key = monthKey(t.start);
      let m = months.at(-1);
      if (!m || m.key !== key) {
        m = { key, name: month(t.start), termine: [] };
        months.push(m);
      }
      m.termine.push(t);
    }
    return months;
  });

  constructor() {
    effect(() => {
      const team = this.team();
      const view = this.view();
      untracked(() => void this.load(team, view));
    });
  }

  protected date(iso: string): string {
    return eventDate(iso, this.year());
  }

  protected period(t: EventSummary): string {
    return TEXTS.termine.period(clockTime(t.start), clockTime(t.end));
  }

  protected teams(t: EventSummary): string {
    return TEXTS.termine.joinTeams(t.teams.map((x) => x.name));
  }

  /** Name des Listeneintrags, z.B. «Training Di 17.11. 18:30». */
  protected cardName(t: EventSummary): string {
    return TEXTS.termine.card(t.title, this.date(t.start), clockTime(t.start));
  }

  protected selectTeam(teamId: string): void {
    void this.router.navigate(['/termine'], {
      queryParams: { team: teamId || null },
      queryParamsHandling: 'merge',
    });
  }

  /** Nach einer Antwort: eigene Antwort und Zähler dieses Termins neu holen (ohne Neuladen der Seite). */
  protected async reload(id: string): Promise<void> {
    try {
      const updated = await firstValueFrom(this.http.get<EventSummary>(`/api/events/${id}`));
      this.termine.update((list) => list.map((t) => (t.id === id ? updated : t)));
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
      this.termine.set(
        await firstValueFrom(this.http.get<EventSummary[]>('/api/events', { params })),
      );
    } catch (e) {
      this.termine.set([]);
      this.error.set(errorText(e));
    } finally {
      this.loading.set(false);
    }
  }
}
