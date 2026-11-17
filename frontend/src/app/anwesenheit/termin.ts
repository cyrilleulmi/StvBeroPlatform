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
import type { Termin as TerminDaten, Uebersicht as UebersichtDaten } from '../api/typen';
import { AnmeldungService } from '../kern/anmeldung.service';
import { fehlerstatus, fehlertext } from '../kern/fehlertext';
import { RueckmeldungService } from '../kern/rueckmeldung.service';
import { TEXTE } from '../kern/texte';
import { MeineAntwort } from './meine-antwort';
import { Spieltag } from './spieltag';
import { Uebersicht } from './uebersicht';
import { jahrVon, terminDatum, uhrzeit } from './zeit';

/**
 * Termin (`/termine/:terminId`): Angaben, Status (abgesagt mit Grund, verschoben), für Coaches der Link
 * zum Bearbeiten (TER-04, TER-06), die eigene Antwort und die Übersicht nach Team und Linie (F-04, ANM).
 * Bei Spieltagen zusätzlich Aufgebot und Spiele (F-05, AUF).
 */
@Component({
  selector: 'app-termin',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, MeineAntwort, Spieltag, Uebersicht],
  template: `
    <div class="kopf">
      <a class="zurueck" routerLink="/termine">{{ texte.termine.alleTermine }}</a>
      <h1>{{ termin()?.title ?? texte.termine.titel }}</h1>
      @if (termin(); as t) {
        <p class="nebentext">{{ datum(t.start) }} · {{ zeitraum() }} · {{ teams() }}</p>
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
        @if (t.status === 'CANCELLED') {
          <div class="meldung-fehler status" data-testid="termin-status">
            <strong>{{ texte.termine.abgesagtMitGrund(t.cancellationReason ?? '') }}</strong>
            <span>{{ texte.termine.abgesagtHinweis }}</span>
          </div>
        } @else if (t.rescheduled) {
          <p class="meldung-hinweis" data-testid="termin-status">
            {{ texte.termine.verschobenHinweis }}
          </p>
        }
        @if (binMitglied()) {
          <section class="karte" aria-labelledby="termin-meine-antwort">
            <h2 id="termin-meine-antwort" class="abschnitt-titel">
              {{ texte.antwort.meineAntwort }}
            </h2>
            <app-meine-antwort
              [terminId]="t.id"
              [antwort]="t.myResponse"
              [name]="texte.antwort.meineAntwort"
              [gesperrt]="sperre()"
              [details]="true"
              (geaendert)="nachladen()"
            />
          </section>
        }
        @if (t.type === 'MATCH_DAY') {
          <app-spieltag
            [termin]="t"
            [darfAufgebot]="coachOderAdmin()"
            [darfResultat]="coachOderAdmin() && t.status !== 'CANCELLED'"
            (geaendert)="nachladen()"
          />
        }
        <section class="karte" aria-labelledby="termin-angaben">
          <h2 id="termin-angaben" class="abschnitt-titel">{{ texte.termine.angaben }}</h2>
          <dl class="angaben">
            <dt>{{ texte.termine.datum }}</dt>
            <dd>{{ datum(t.start) }}</dd>
            <dt>{{ texte.termine.zeit }}</dt>
            <dd>{{ zeitraum() }}</dd>
            <dt>{{ texte.termine.teams }}</dt>
            <dd>{{ teams() }}</dd>
            @if (t.location) {
              <dt>{{ texte.termine.ort }}</dt>
              <dd>
                {{ t.location.name }}
                @if (t.location.address) {
                  , {{ t.location.address }}
                }
                @if (t.location.mapsUrl) {
                  <br />
                  <a
                    [href]="t.location.mapsUrl"
                    target="_blank"
                    rel="noopener"
                    data-testid="spielort-karte"
                    >{{ texte.termine.karteOeffnen }}</a
                  >
                }
              </dd>
            }
            @if (t.meetingPoint; as tp) {
              <dt>{{ texte.termine.treffpunkt }}</dt>
              <dd data-testid="treffpunkt">{{ uhrzeit(tp.time) }} · {{ tp.location }}</dd>
            }
            <dt>{{ texte.termine.antwortfrist }}</dt>
            <dd data-testid="antwortfrist">
              {{
                texte.termine.antwortfristText(
                  datum(t.responseDeadline),
                  uhrzeit(t.responseDeadline),
                  t.responseDeadlineHours ?? 24
                )
              }}
            </dd>
            @if (t.notes) {
              <dt>{{ texte.termine.hinweise }}</dt>
              <dd class="hinweise">{{ t.notes }}</dd>
            }
            @if (t.seriesId) {
              <dt>{{ texte.termine.serie }}</dt>
              <dd>
                @if (t.canEdit) {
                  <a [routerLink]="['/serien', t.seriesId]">{{ texte.termine.serieAnsehen }}</a>
                } @else {
                  {{ texte.termine.teilEinerSerie }}
                }
              </dd>
            }
          </dl>
          @if (t.canEdit) {
            <a
              class="button button-sekundaer button-klein bearbeiten"
              [routerLink]="['/termine', t.id, 'bearbeiten']"
              >{{ texte.termine.bearbeiten }}</a
            >
          }
        </section>
        @if (uebersicht(); as u) {
          <app-uebersicht
            [terminId]="t.id"
            [daten]="u"
            [coachIn]="coachIn()"
            [admin]="anmeldung.ich()?.admin ?? false"
            [ichId]="anmeldung.ich()?.id ?? null"
            [offen]="!sperre()"
            (geaendert)="nachladen()"
          />
        }
      }
    </div>
  `,
  styleUrl: './termin.css',
})
export class Termin {
  private readonly http = inject(HttpClient);
  protected readonly anmeldung = inject(AnmeldungService);
  private readonly rueckmeldung = inject(RueckmeldungService);
  protected readonly texte = TEXTE;
  protected readonly uhrzeit = uhrzeit;

  /** Route `/termine/:terminId` */
  readonly terminId = input.required<string>();

  protected readonly termin = signal<TerminDaten | null>(null);
  protected readonly uebersicht = signal<UebersichtDaten | null>(null);
  protected readonly laedt = signal(true);
  protected readonly fehler = signal<string | null>(null);
  protected readonly erfolg = signal<string | null>(null);

  private readonly jahr = computed(() => jahrVon(this.anmeldung.ich()?.now));

  protected readonly zeitraum = computed(() => {
    const t = this.termin();
    return t ? TEXTE.termine.zeitraum(uhrzeit(t.start), uhrzeit(t.end)) : '';
  });

  /** Mitglied eines beteiligten Teams: kann für sich antworten (ANM-01). */
  protected readonly binMitglied = computed(() =>
    (this.termin()?.teams ?? []).some((t) => !!t.myTeamRole),
  );

  /** Teams des Termins, in denen die angemeldete Person Coach ist (RR-06). */
  protected readonly coachIn = computed(() =>
    (this.termin()?.teams ?? []).filter((t) => t.myTeamRole === 'COACH').map((t) => t.id),
  );

  /** Coach eines beteiligten Teams oder Admin: Aufgebot und Resultate (RR-03). */
  protected readonly coachOderAdmin = computed(
    () => this.coachIn().length > 0 || (this.anmeldung.ich()?.admin ?? false),
  );

  /** Erklärung, warum nicht mehr geantwortet werden kann (TER-06), sonst `null`. */
  protected readonly sperre = computed(() => {
    const t = this.termin();
    if (!t) return null;
    if (t.status === 'CANCELLED') return TEXTE.antwort.abgesagt;
    const jetzt = this.anmeldung.ich()?.now;
    if (jetzt && Date.parse(t.end) <= Date.parse(jetzt)) return TEXTE.antwort.vorbei;
    return null;
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

  /** Nach einer Antwort: Termin (eigene Antwort, Zähler) und Übersicht ohne Neuladen der Seite. */
  protected async nachladen(): Promise<void> {
    const id = this.terminId();
    try {
      const [termin, uebersicht] = await Promise.all([
        firstValueFrom(this.http.get<TerminDaten>(`/api/events/${id}`)),
        firstValueFrom(this.http.get<UebersichtDaten>(`/api/events/${id}/overview`)),
      ]);
      this.termin.set(termin);
      this.uebersicht.set(uebersicht);
    } catch (e) {
      this.fehler.set(fehlertext(e));
    }
  }

  private async laden(id: string): Promise<void> {
    this.laedt.set(true);
    this.fehler.set(null);
    this.termin.set(null);
    this.uebersicht.set(null);
    try {
      const [termin, uebersicht] = await Promise.all([
        firstValueFrom(this.http.get<TerminDaten>(`/api/events/${id}`)),
        firstValueFrom(this.http.get<UebersichtDaten>(`/api/events/${id}/overview`)),
      ]);
      this.termin.set(termin);
      this.uebersicht.set(uebersicht);
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
