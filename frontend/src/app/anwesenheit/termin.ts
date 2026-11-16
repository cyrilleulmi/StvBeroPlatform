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
import type { Termin as TerminDaten } from '../api/typen';
import { AnmeldungService } from '../kern/anmeldung.service';
import { fehlerstatus, fehlertext } from '../kern/fehlertext';
import { RueckmeldungService } from '../kern/rueckmeldung.service';
import { TEXTE } from '../kern/texte';
import { jahrVon, terminDatum, uhrzeit } from './zeit';

/**
 * Termin (`/termine/:terminId`): Angaben, Status (abgesagt mit Grund, verschoben) und für Coaches der Link
 * zum Bearbeiten (TER-04, TER-06). Antworten und Übersicht kommen mit F-04.
 */
@Component({
  selector: 'app-termin',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <div class="kopf">
      <a class="zurueck" routerLink="/termine">{{ texte.termine.alleTermine }}</a>
      <h1>{{ termin()?.titel ?? texte.termine.titel }}</h1>
      @if (termin(); as t) {
        <p class="nebentext">{{ datum(t.beginn) }} · {{ zeitraum() }} · {{ teams() }}</p>
      }
    </div>
    @if (erfolg(); as meldung) {
      <p class="meldung-erfolg" role="status">{{ meldung }}</p>
    }
    @if (fehler(); as meldung) {
      <p class="meldung-fehler" role="alert">{{ meldung }}</p>
    }
    <div class="abschnitte" [attr.aria-busy]="laedt() ? 'true' : null">
      @if (laedt()) {
        <p class="leer">{{ texte.allgemein.laden }}</p>
      } @else if (termin(); as t) {
        @if (t.status === 'ABGESAGT') {
          <div class="meldung-fehler status" data-testid="termin-status">
            <strong>{{ texte.termine.abgesagtMitGrund(t.absagegrund ?? '') }}</strong>
            <span>{{ texte.termine.abgesagtHinweis }}</span>
          </div>
        } @else if (t.verschoben) {
          <p class="meldung-hinweis" data-testid="termin-status">
            {{ texte.termine.verschobenHinweis }}
          </p>
        }
        <section class="karte" aria-labelledby="termin-angaben">
          <h2 id="termin-angaben" class="abschnitt-titel">{{ texte.termine.angaben }}</h2>
          <dl class="angaben">
            <dt>{{ texte.termine.datum }}</dt>
            <dd>{{ datum(t.beginn) }}</dd>
            <dt>{{ texte.termine.zeit }}</dt>
            <dd>{{ zeitraum() }}</dd>
            <dt>{{ texte.termine.teams }}</dt>
            <dd>{{ teams() }}</dd>
            @if (t.ort) {
              <dt>{{ texte.termine.ort }}</dt>
              <dd>
                {{ t.ort.name }}
                @if (t.ort.adresse) {
                  , {{ t.ort.adresse }}
                }
                @if (t.ort.mapsUrl) {
                  <br />
                  <a
                    [href]="t.ort.mapsUrl"
                    target="_blank"
                    rel="noopener"
                    data-testid="spielort-karte"
                    >{{ texte.termine.karteOeffnen }}</a
                  >
                }
              </dd>
            }
            @if (t.treffpunkt; as tp) {
              <dt>{{ texte.termine.treffpunkt }}</dt>
              <dd data-testid="treffpunkt">{{ uhrzeit(tp.zeit) }} · {{ tp.ort }}</dd>
            }
            <dt>{{ texte.termine.antwortfrist }}</dt>
            <dd data-testid="antwortfrist">
              {{
                texte.termine.antwortfristText(
                  datum(t.antwortfrist),
                  uhrzeit(t.antwortfrist),
                  t.antwortfristStunden ?? 24
                )
              }}
            </dd>
            @if (t.hinweise) {
              <dt>{{ texte.termine.hinweise }}</dt>
              <dd class="hinweise">{{ t.hinweise }}</dd>
            }
            @if (t.serieId) {
              <dt>{{ texte.termine.serie }}</dt>
              <dd>
                @if (t.darfBearbeiten) {
                  <a [routerLink]="['/serien', t.serieId]">{{ texte.termine.serieAnsehen }}</a>
                } @else {
                  {{ texte.termine.teilEinerSerie }}
                }
              </dd>
            }
          </dl>
          @if (t.darfBearbeiten) {
            <a
              class="button button-sekundaer button-klein bearbeiten"
              [routerLink]="['/termine', t.id, 'bearbeiten']"
              >{{ texte.termine.bearbeiten }}</a
            >
          }
        </section>
      }
    </div>
  `,
  styleUrl: './termin.css',
})
export class Termin {
  private readonly http = inject(HttpClient);
  private readonly anmeldung = inject(AnmeldungService);
  private readonly rueckmeldung = inject(RueckmeldungService);
  protected readonly texte = TEXTE;
  protected readonly uhrzeit = uhrzeit;

  /** Route `/termine/:terminId` */
  readonly terminId = input.required<string>();

  protected readonly termin = signal<TerminDaten | null>(null);
  protected readonly laedt = signal(true);
  protected readonly fehler = signal<string | null>(null);
  protected readonly erfolg = signal<string | null>(null);

  private readonly jahr = computed(() => jahrVon(this.anmeldung.ich()?.jetzt));

  protected readonly zeitraum = computed(() => {
    const t = this.termin();
    return t ? TEXTE.termine.zeitraum(uhrzeit(t.beginn), uhrzeit(t.ende)) : '';
  });

  protected readonly teams = computed(() =>
    TEXTE.termine.teamsVerbinden((this.termin()?.teams ?? []).map((t) => t.name)),
  );

  constructor() {
    this.erfolg.set(this.rueckmeldung.abholen());
    effect(() => {
      const id = this.terminId();
      untracked(() => void this.laden(id));
    });
  }

  protected datum(iso: string): string {
    return terminDatum(iso, this.jahr());
  }

  private async laden(id: string): Promise<void> {
    this.laedt.set(true);
    this.fehler.set(null);
    this.termin.set(null);
    try {
      this.termin.set(await firstValueFrom(this.http.get<TerminDaten>(`/api/termine/${id}`)));
    } catch (e) {
      const status = fehlerstatus(e);
      this.fehler.set(
        status === 404 || status === 400 ? TEXTE.termine.nichtGefunden : fehlertext(e),
      );
    } finally {
      this.laedt.set(false);
    }
  }
}
