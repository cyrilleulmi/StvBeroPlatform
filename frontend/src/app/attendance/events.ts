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
import type { TerminKurz } from '../api/types';
import { AnmeldungService } from '../core/auth.service';
import { fehlertext } from '../core/error-text';
import { Icon } from '../core/icon';
import { TEXTE } from '../core/texts';
import { MeineAntwort } from './my-response';
import { jahrVon, monat, monatSchluessel, terminDatum, uhrzeit } from './time';

interface Monat {
  schluessel: string;
  name: string;
  termine: TerminKurz[];
}

/**
 * Kachel Anwesenheit: Termine aller eigenen Teams, chronologisch (TER-13), mit der eigenen Antwort direkt
 * in der Karte (ANM-08) und dem Gesamtzähler, der sich nach einer Antwort ohne Neuladen aktualisiert. Filter `?team=<id>` und
 * Ansicht `?ansicht=vergangen` stehen in der URL (docs/testbarkeit.md, Regel 5).
 */
@Component({
  selector: 'app-termine',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon, MeineAntwort],
  template: `
    <div class="titelzeile">
      <h1>{{ texte.termine.titel }}</h1>
      @if (darfErfassen()) {
        <a class="button button-primaer button-klein" routerLink="/termine/neu">{{
          texte.termine.erfassen
        }}</a>
      }
    </div>

    <div class="filter">
      <nav class="umschalter" [attr.aria-label]="texte.termine.ansicht">
        <a
          routerLink="/termine"
          [queryParams]="{ ansicht: null }"
          queryParamsHandling="merge"
          [attr.aria-current]="!vergangen() ? 'page' : null"
          >{{ texte.termine.kommend }}</a
        >
        <a
          routerLink="/termine"
          [queryParams]="{ ansicht: 'vergangen' }"
          queryParamsHandling="merge"
          [attr.aria-current]="vergangen() ? 'page' : null"
          >{{ texte.termine.vergangen }}</a
        >
      </nav>
      @if (meineTeams().length > 1 || team()) {
        <div class="feld">
          <label for="termine-team">{{ texte.termine.team }}</label>
          <select id="termine-team" (change)="teamWaehlen($any($event.target).value)">
            <option value="" [selected]="!team()">{{ texte.termine.alleTeams }}</option>
            @for (t of teamAuswahl(); track t.id) {
              <option [value]="t.id" [selected]="team() === t.id">{{ t.name }}</option>
            }
          </select>
        </div>
      }
    </div>

    @if (fehler(); as meldung) {
      <p class="meldung-fehler" role="alert">{{ meldung }}</p>
    }

    <section
      class="karte"
      aria-labelledby="termine-liste-titel"
      [attr.aria-busy]="laedt() ? 'true' : null"
    >
      <h2 id="termine-liste-titel" class="visuell-versteckt">
        {{ vergangen() ? texte.termine.vergangeneTermine : texte.termine.kommendeTermine }}
      </h2>
      @if (laedt()) {
        <p class="leer">{{ texte.allgemein.laden }}</p>
      } @else if (termine().length === 0) {
        <p class="leer">{{ vergangen() ? texte.termine.leerVergangen : texte.termine.leer }}</p>
      } @else {
        @for (m of monate(); track m.schluessel) {
          <h3 class="monat" [id]="'monat-' + m.schluessel">{{ m.name }}</h3>
          <ul class="liste" [attr.aria-labelledby]="'monat-' + m.schluessel">
            @for (t of m.termine; track t.id) {
              <li
                class="termin-eintrag"
                [attr.data-testid]="'termin-karte-' + t.id"
                [attr.aria-label]="kartenName(t)"
                [attr.data-status]="t.status"
              >
                <a class="zeile-link termin" [routerLink]="['/termine', t.id]">
                  <span class="datum" aria-hidden="true">
                    <span class="wochentag">{{ datum(t.start).slice(0, 2) }}</span>
                    <span class="tag">{{ datum(t.start).slice(3, 9) }}</span>
                  </span>
                  <span class="inhalt">
                    <span class="zeile-titel" [class.durchgestrichen]="t.status === 'CANCELLED'">
                      {{ t.title }}
                    </span>
                    <span class="nebentext">
                      {{ zeitraum(t) }} · {{ teams(t) }}
                      @if (t.location) {
                        · {{ t.location.name }}
                      }
                    </span>
                    @if (t.status === 'CANCELLED') {
                      <span class="status-zeile">
                        <span class="chip" data-art="abgesagt" data-testid="termin-status">{{
                          texte.termine.abgesagt
                        }}</span>
                        @if (t.cancellationReason) {
                          <span class="nebentext">{{ t.cancellationReason }}</span>
                        }
                      </span>
                    } @else {
                      @if (t.rescheduled) {
                        <span class="status-zeile">
                          <span class="chip" data-art="verschoben" data-testid="termin-status">{{
                            texte.termine.verschoben
                          }}</span>
                        </span>
                      }
                      @if (t.counts; as z) {
                        <span class="nebentext zaehler" data-testid="termin-zaehler">
                          <span data-zaehler="dabei">{{ texte.uebersicht.dabei(z.attending) }}</span>
                          ·
                          <span data-zaehler="nicht-dabei">{{
                            texte.uebersicht.nichtDabei(z.declined)
                          }}</span>
                          ·
                          <span data-zaehler="vielleicht">{{
                            texte.uebersicht.vielleicht(z.maybe)
                          }}</span>
                          ·
                          <span data-zaehler="offen">{{ texte.uebersicht.offen(z.pending) }}</span>
                        </span>
                      }
                    }
                    @if (vergangen()) {
                      <span class="nebentext" [attr.data-antwort]="t.myResponse.status">{{
                        texte.antwort.deineAntwort(texte.antwort.status[t.myResponse.status])
                      }}</span>
                    }
                  </span>
                  <app-icon name="chevron-right" [groesse]="20" />
                </a>
                @if (!vergangen()) {
                  <app-meine-antwort
                    class="karten-antwort"
                    [terminId]="t.id"
                    [antwort]="t.myResponse"
                    [name]="texte.antwort.meineAntwortFuer(kartenName(t))"
                    [gesperrt]="t.status === 'CANCELLED' ? texte.antwort.abgesagt : null"
                    (geaendert)="nachladen(t.id)"
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
export class Termine {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly anmeldung = inject(AnmeldungService);
  protected readonly texte = TEXTE;

  /** Query `?team=` */
  readonly team = input<string | undefined>();
  /** Query `?ansicht=vergangen` */
  readonly ansicht = input<string | undefined>();

  protected readonly termine = signal<TerminKurz[]>([]);
  protected readonly laedt = signal(true);
  protected readonly fehler = signal<string | null>(null);

  protected readonly vergangen = computed(() => this.ansicht() === 'vergangen');
  private readonly jahr = computed(() => jahrVon(this.anmeldung.ich()?.now));

  /** Eigene Teams der aktuellen Saison, nach Name. */
  protected readonly meineTeams = computed(() =>
    (this.anmeldung.ich()?.memberships ?? [])
      .map((z) => z.team)
      .sort((a, b) => a.name.localeCompare(b.name, 'de-CH')),
  );

  /**
   * Auswahl «Team»: die eigenen Teams. Zeigt `?team=` auf ein anderes Team (z.B. ein geteilter Link),
   * erscheint es zusätzlich, damit Auswahl und Liste übereinstimmen (Test-Befund F-03).
   */
  protected readonly teamAuswahl = computed(() => {
    const eigene = this.meineTeams();
    const id = this.team();
    if (!id || eigene.some((t) => t.id === id)) return eigene;
    const name =
      this.termine()
        .flatMap((t) => t.teams)
        .find((t) => t.id === id)?.name ?? TEXTE.termine.anderesTeam;
    return [...eigene, { id, name }];
  });

  /** Termine erfassen: Coaches (in irgendeinem Team) und Admins (TER-11, RR-05). */
  protected readonly darfErfassen = computed(() => {
    const ich = this.anmeldung.ich();
    return !!ich && (ich.admin || ich.memberships.some((z) => z.teamRole === 'COACH'));
  });

  /** Termine nach Monat gruppiert, in der Reihenfolge der API. */
  protected readonly monate = computed<Monat[]>(() => {
    const monate: Monat[] = [];
    for (const t of this.termine()) {
      const schluessel = monatSchluessel(t.start);
      let m = monate.at(-1);
      if (!m || m.schluessel !== schluessel) {
        m = { schluessel, name: monat(t.start), termine: [] };
        monate.push(m);
      }
      m.termine.push(t);
    }
    return monate;
  });

  constructor() {
    effect(() => {
      const team = this.team();
      const ansicht = this.ansicht();
      untracked(() => void this.laden(team, ansicht));
    });
  }

  protected datum(iso: string): string {
    return terminDatum(iso, this.jahr());
  }

  protected zeitraum(t: TerminKurz): string {
    return TEXTE.termine.zeitraum(uhrzeit(t.start), uhrzeit(t.end));
  }

  protected teams(t: TerminKurz): string {
    return TEXTE.termine.teamsVerbinden(t.teams.map((x) => x.name));
  }

  /** Name des Listeneintrags, z.B. «Training Di 17.11. 18:30». */
  protected kartenName(t: TerminKurz): string {
    return TEXTE.termine.karte(t.title, this.datum(t.start), uhrzeit(t.start));
  }

  protected teamWaehlen(teamId: string): void {
    void this.router.navigate(['/termine'], {
      queryParams: { team: teamId || null },
      queryParamsHandling: 'merge',
    });
  }

  /** Nach einer Antwort: eigene Antwort und Zähler dieses Termins neu holen (ohne Neuladen der Seite). */
  protected async nachladen(id: string): Promise<void> {
    try {
      const neu = await firstValueFrom(this.http.get<TerminKurz>(`/api/events/${id}`));
      this.termine.update((liste) => liste.map((t) => (t.id === id ? neu : t)));
    } catch (e) {
      this.fehler.set(fehlertext(e));
    }
  }

  private async laden(team: string | undefined, ansicht: string | undefined): Promise<void> {
    this.laedt.set(true);
    this.fehler.set(null);
    try {
      const params: Record<string, string> = {};
      if (team) params['team'] = team;
      if (ansicht === 'vergangen') params['view'] = 'past';
      this.termine.set(
        await firstValueFrom(this.http.get<TerminKurz[]>('/api/events', { params })),
      );
    } catch (e) {
      this.termine.set([]);
      this.fehler.set(fehlertext(e));
    } finally {
      this.laedt.set(false);
    }
  }
}
