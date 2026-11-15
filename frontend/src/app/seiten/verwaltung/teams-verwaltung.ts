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
import type { Saison, Team } from '../../api/typen';
import { AnmeldungService } from '../../kern/anmeldung.service';
import { feldfehler, fehlertext } from '../../kern/fehlertext';
import { saisonStatus } from '../../kern/format';
import { Icon } from '../../kern/icon';
import { TEXTE } from '../../kern/texte';

/** Teams verwalten (Admin): Teams einer Saison (`?saison=`), Team anlegen. Archiv nur lesend (SAI-02). */
@Component({
  selector: 'app-teams-verwaltung',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon],
  template: `
    <div class="kopf">
      <a class="zurueck" routerLink="/verwaltung">{{ texte.verwaltung.zurVerwaltung }}</a>
      <h1>{{ texte.teamVerwaltung.titel }}</h1>
    </div>
    @if (!anmeldung.istAdmin()) {
      <p class="meldung-fehler" role="alert">{{ texte.allgemein.keineBerechtigung }}</p>
    } @else {
      <div class="feld">
        <label for="teams-saison">{{ texte.teamVerwaltung.saison }}</label>
        <select id="teams-saison" (change)="saisonWaehlen($any($event.target).value)">
          @for (s of saisons(); track s.id) {
            <option [value]="s.id" [selected]="s.id === saisonDaten()?.id">
              {{ s.name }} ({{ status(s) }})
            </option>
          }
        </select>
      </div>
      @if (saisonDaten()?.archiv) {
        <p class="meldung-hinweis" data-testid="archiv-hinweis">
          {{ texte.saison.archivHinweis(saisonDaten()!.name) }}
        </p>
      }
      @if (erfolg(); as meldung) {
        <p class="meldung-erfolg" role="status">{{ meldung }}</p>
      }
      @if (fehler(); as meldung) {
        <p class="meldung-fehler" role="alert">{{ meldung }}</p>
      }
      <section
        class="karte"
        aria-labelledby="teams-verwaltung-liste"
        [attr.aria-busy]="laedt() ? 'true' : null"
      >
        <h2 id="teams-verwaltung-liste" class="abschnitt-titel">
          {{ texte.teamVerwaltung.teamsListe }}
        </h2>
        @if (laedt() && teams().length === 0) {
          <p class="leer">{{ texte.allgemein.laden }}</p>
        } @else if (teams().length === 0) {
          <p class="leer">{{ texte.teamVerwaltung.leer }}</p>
        } @else {
          <ul class="liste" [attr.aria-label]="texte.teamVerwaltung.teamsListe">
            @for (t of teams(); track t.id) {
              <li [attr.data-testid]="'team-' + t.id">
                <a
                  class="zeile-link"
                  [routerLink]="['/verwaltung/teams', t.id]"
                  [queryParams]="{ saison: saisonDaten()?.id }"
                >
                  <span class="zeile-titel">{{ t.name }}</span>
                  <app-icon name="chevron-right" [groesse]="20" />
                </a>
              </li>
            }
          </ul>
        }
      </section>
      @if (saisonDaten() && !saisonDaten()!.archiv) {
        <form
          class="karte formular"
          (submit)="anlegen($event)"
          novalidate
          aria-labelledby="team-neu-titel"
        >
          <h2 id="team-neu-titel">{{ texte.teamVerwaltung.neuesTeam }}</h2>
          <div class="feld">
            <label for="team-neu-name">{{ texte.teamVerwaltung.teamName }}</label>
            <input
              id="team-neu-name"
              type="text"
              maxlength="60"
              autocomplete="off"
              [value]="neuerName()"
              (input)="neuerName.set($any($event.target).value)"
              [attr.aria-invalid]="nameFehler() ? 'true' : null"
              [attr.aria-describedby]="nameFehler() ? 'team-neu-name-fehler' : null"
            />
            @if (nameFehler(); as m) {
              <p class="feldfehler" id="team-neu-name-fehler">{{ m }}</p>
            }
          </div>
          <button
            type="submit"
            class="button button-primaer"
            [disabled]="speichert()"
            [attr.aria-busy]="speichert() ? 'true' : null"
          >
            {{ texte.teamVerwaltung.anlegen }}
          </button>
        </form>
      }
    }
  `,
  styleUrl: '../seite.css',
  styles: `
    form .button {
      justify-self: start;
    }
  `,
})
export class TeamsVerwaltung {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  protected readonly anmeldung = inject(AnmeldungService);
  protected readonly texte = TEXTE;
  protected readonly status = saisonStatus;

  /** Query `?saison=`; ohne Angabe die aktuelle Saison. */
  readonly saison = input<string | undefined>();

  protected readonly saisons = signal<Saison[]>([]);
  protected readonly saisonDaten = computed(() => {
    const id = this.saison();
    return this.saisons().find((s) => (id ? s.id === id : s.aktuell)) ?? null;
  });
  protected readonly teams = signal<Team[]>([]);
  protected readonly laedt = signal(true);
  protected readonly speichert = signal(false);
  protected readonly fehler = signal<string | null>(null);
  protected readonly erfolg = signal<string | null>(null);
  protected readonly neuerName = signal('');
  protected readonly nameFehler = signal<string | null>(null);

  constructor() {
    effect(() => {
      const id = this.saison();
      if (this.anmeldung.istAdmin()) {
        untracked(() => void this.laden(id));
      }
    });
  }

  protected saisonWaehlen(id: string): void {
    void this.router.navigate([], { queryParams: { saison: id } });
  }

  private async laden(saisonId: string | undefined): Promise<void> {
    this.laedt.set(true);
    this.fehler.set(null);
    this.erfolg.set(null);
    try {
      const params: Record<string, string> = saisonId ? { saison: saisonId } : {};
      const [saisons, teams] = await Promise.all([
        firstValueFrom(this.http.get<Saison[]>('/api/saisons')),
        firstValueFrom(this.http.get<Team[]>('/api/teams', { params })),
      ]);
      this.saisons.set(saisons);
      this.teams.set(teams);
    } catch (e) {
      this.teams.set([]);
      this.fehler.set(fehlertext(e));
    } finally {
      this.laedt.set(false);
    }
  }

  protected async anlegen(ereignis: Event): Promise<void> {
    ereignis.preventDefault();
    if (this.speichert()) return;
    this.erfolg.set(null);
    this.fehler.set(null);
    const name = this.neuerName().trim();
    if (!name) {
      this.nameFehler.set(TEXTE.teamVerwaltung.namePflicht);
      return;
    }
    this.nameFehler.set(null);
    this.speichert.set(true);
    try {
      const params: Record<string, string> = this.saisonDaten()
        ? { saison: this.saisonDaten()!.id }
        : {};
      const team = await firstValueFrom(this.http.post<Team>('/api/teams', { name }, { params }));
      this.neuerName.set('');
      await this.laden(this.saison());
      this.erfolg.set(TEXTE.teamVerwaltung.angelegt(team.name));
    } catch (e) {
      this.nameFehler.set(feldfehler(e)['name'] ?? null);
      this.fehler.set(fehlertext(e));
    } finally {
      this.speichert.set(false);
    }
  }
}
