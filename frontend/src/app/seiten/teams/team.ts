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
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { PersonKurz, Saison, TeamDetail } from '../../api/typen';
import { AnmeldungService } from '../../kern/anmeldung.service';
import { fehlerstatus, fehlertext } from '../../kern/fehlertext';
import { funktionText, name } from '../../kern/format';
import { TEXTE } from '../../kern/texte';

/**
 * Team: Coaches und Spielerinnen getrennt (PTF-07), die Spielerinnen nach Linie (LIN-12).
 * `?saison=<id>` zeigt eine andere Saison, z.B. das Archiv (SAI-02).
 */
@Component({
  selector: 'app-team',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <div class="kopf">
      <a class="zurueck" routerLink="/teams">{{ texte.teams.alleTeams }}</a>
      <h1>{{ team()?.name ?? texte.teams.titel }}</h1>
      @if (team()?.meineFunktion; as f) {
        <p class="nebentext">{{ funktion(f) }}</p>
      }
    </div>
    @if (saisonDaten(); as s) {
      @if (s.archiv) {
        <p class="meldung-hinweis" data-testid="archiv-hinweis">
          {{ texte.saison.archivHinweis(s.name) }}
        </p>
      }
    }
    @if (fehler(); as meldung) {
      <p class="meldung-fehler" role="alert">{{ meldung }}</p>
    }
    <div class="abschnitte" [attr.aria-busy]="laedt() ? 'true' : null">
      @if (laedt()) {
        <p class="leer">{{ texte.allgemein.laden }}</p>
      } @else if (team(); as t) {
        <section class="karte" aria-labelledby="team-coaches-titel" data-testid="team-coaches">
          <h2 id="team-coaches-titel" class="abschnitt-titel">
            {{ texte.teams.coaches }} <span class="anzahl">{{ t.coaches.length }}</span>
          </h2>
          @if (t.coaches.length === 0) {
            <p class="leer">{{ texte.teams.keineCoaches }}</p>
          } @else {
            <ul class="liste" [attr.aria-label]="texte.teams.coaches">
              @for (p of t.coaches; track p.id) {
                <li class="zeile" [attr.data-testid]="'team-person-' + p.id">{{ name(p) }}</li>
              }
            </ul>
          }
        </section>
        <section
          class="karte"
          aria-labelledby="team-spielerinnen-titel"
          data-testid="team-spielerinnen"
        >
          <div class="spielerinnen-kopf">
            <h2 id="team-spielerinnen-titel" class="abschnitt-titel">
              {{ texte.teams.spielerinnen }} <span class="anzahl">{{ spielerinnen().length }}</span>
            </h2>
            @if (darfLinienBearbeiten()) {
              <a
                class="button button-sekundaer button-klein"
                [routerLink]="['/teams', t.id, 'linien']"
                >{{ texte.teams.linienBearbeiten }}</a
              >
            }
          </div>
          @if (spielerinnen().length === 0) {
            <p class="leer">{{ texte.teams.keineSpielerinnen }}</p>
          } @else {
            <!-- Nach Linie, dann Goalies, dann «Ohne Linie» (LIN-06, LIN-09). Leere Gruppen fehlen. -->
            @for (g of gruppen(); track g.schluessel) {
              <h3 class="gruppe-titel" [id]="'team-gruppe-' + g.schluessel">{{ g.name }}</h3>
              <ul class="liste" [attr.aria-labelledby]="'team-gruppe-' + g.schluessel">
                @for (p of g.spielerinnen; track p.id) {
                  <li class="zeile" [attr.data-testid]="'team-person-' + p.id">{{ name(p) }}</li>
                }
              </ul>
            }
          }
        </section>
      }
    </div>
  `,
  styles: `
    :host,
    .abschnitte {
      display: grid;
      gap: var(--abschnitt);
    }
    .spielerinnen-kopf {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
      margin-bottom: 8px;
    }
    .spielerinnen-kopf .abschnitt-titel {
      margin: 0;
    }
    .gruppe-titel {
      margin: 12px 0 0;
      font-size: 13px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      color: var(--muted);
    }
  `,
})
export class Team {
  private readonly http = inject(HttpClient);
  private readonly anmeldung = inject(AnmeldungService);
  protected readonly texte = TEXTE;
  protected readonly name = name;
  protected readonly funktion = funktionText;

  /** Route `/teams/:teamId` */
  readonly teamId = input.required<string>();
  /** Query `?saison=` */
  readonly saison = input<string | undefined>();

  protected readonly team = signal<TeamDetail | null>(null);
  protected readonly saisonDaten = signal<Saison | null>(null);
  protected readonly laedt = signal(true);
  protected readonly fehler = signal<string | null>(null);

  /** Nicht leere Gruppen in Anzeigereihenfolge, zuletzt «Ohne Linie». */
  protected readonly gruppen = computed(() => {
    const t = this.team();
    if (!t) return [];
    return [
      ...t.einteilung.gruppen.map((g) => ({
        schluessel: g.id,
        name: g.name,
        spielerinnen: g.spielerinnen,
      })),
      {
        schluessel: 'ohne-linie',
        name: TEXTE.linien.ohneLinie,
        spielerinnen: t.einteilung.ohneLinie,
      },
    ].filter((g) => g.spielerinnen.length > 0);
  });

  /** Coaches des Teams und Admins teilen die Linien ein (LIN-07), nur in der aktuellen Saison. */
  protected readonly darfLinienBearbeiten = computed(
    () => !this.saison() && (this.anmeldung.istAdmin() || this.team()?.meineFunktion === 'COACH'),
  );

  /** Alle Spielerinnen: nach Gruppen, dann ohne Linie. Coaches sind nie dabei (PTF-07). */
  protected readonly spielerinnen = computed<PersonKurz[]>(() => {
    const t = this.team();
    if (!t) return [];
    return [...t.einteilung.gruppen.flatMap((g) => g.spielerinnen), ...t.einteilung.ohneLinie];
  });

  constructor() {
    effect(() => {
      const teamId = this.teamId();
      const saison = this.saison();
      untracked(() => void this.laden(teamId, saison));
    });
  }

  private async laden(teamId: string, saisonId: string | undefined): Promise<void> {
    this.laedt.set(true);
    this.fehler.set(null);
    this.team.set(null);
    const ich = this.anmeldung.ich();
    const mitglied = ich?.zugehoerigkeiten.some((z) => z.team.id === teamId) ?? false;
    if (!saisonId && ich && !ich.admin && !mitglied) {
      // Ohne Zugehörigkeit in der aktuellen Saison gar nicht erst anfragen; das Backend lehnt ab (403).
      this.fehler.set(TEXTE.allgemein.keineBerechtigung);
      this.laedt.set(false);
      return;
    }
    try {
      const params: Record<string, string> = saisonId ? { saison: saisonId } : {};
      this.team.set(
        await firstValueFrom(this.http.get<TeamDetail>(`/api/teams/${teamId}`, { params })),
      );
      if (saisonId) {
        const saisons = await firstValueFrom(this.http.get<Saison[]>('/api/saisons'));
        this.saisonDaten.set(saisons.find((s) => s.id === saisonId) ?? null);
      } else {
        this.saisonDaten.set(null);
      }
    } catch (e) {
      this.fehler.set(fehlerstatus(e) === 404 ? TEXTE.teams.nichtGefunden : fehlertext(e));
    } finally {
      this.laedt.set(false);
    }
  }
}
