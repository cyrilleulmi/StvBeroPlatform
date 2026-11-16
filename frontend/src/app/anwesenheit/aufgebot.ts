import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { Aufgebot as AufgebotDaten, PersonKurz, Termin } from '../api/typen';
import { AnmeldungService } from '../kern/anmeldung.service';
import { fehlerstatus, fehlertext } from '../kern/fehlertext';
import { Icon } from '../kern/icon';
import { TEXTE } from '../kern/texte';
import { ANTWORT_ICON, antwortSchluessel } from './antwort';
import { jahrVon, terminDatum, uhrzeit } from './zeit';

/**
 * Aufgebot eines Spieltags (`/termine/:terminId/aufgebot`, nur Coaches und Admins): alle Spielerinnen des
 * Teams nach Linien mit Antwortstatus, Auswahl speichert sofort, Veröffentlichen mit Bestätigung
 * (AUF-21 bis AUF-25, AUF-28).
 */
@Component({
  selector: 'app-aufgebot',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon],
  template: `
    <div class="kopf">
      <a class="zurueck" [routerLink]="['/termine', terminId()]">{{ t.zumTermin }}</a>
      <h1>{{ t.titel }}</h1>
      @if (termin(); as tm) {
        <p class="nebentext">
          {{ tm.titel }} {{ datum(tm.beginn) }} · {{ zeit(tm.beginn) }} · {{ teamName() }}
        </p>
      }
    </div>
    @if (meldung(); as m) {
      <p class="meldung-erfolg" role="status">{{ m }}</p>
    }
    @if (fehler(); as m) {
      <p class="meldung-fehler" role="alert">{{ m }}</p>
    }
    <div class="abschnitte" [attr.aria-busy]="laedt() ? 'true' : null">
      @if (laedt()) {
        <p class="leer">{{ texte.allgemein.laden }}</p>
      } @else if (aufgebot(); as a) {
        <section class="karte stand" aria-labelledby="aufgebot-stand-titel">
          <h2 id="aufgebot-stand-titel" class="abschnitt-titel">{{ texte.spieltag.aufgebot }}</h2>
          <p class="status-zeile">
            <span class="chip" [attr.data-art]="a.status.toLowerCase()" data-testid="aufgebot-status">{{
              statusText(a)
            }}</span>
            <span class="anzahl-text" data-testid="aufgebot-anzahl">{{ t.anzahl(a.anzahlAufgeboten ?? 0) }}</span>
          </p>
          @if (!aenderbar()) {
            <p class="meldung-hinweis">{{ t.nichtAenderbar }}</p>
          } @else if (a.status === 'VEROEFFENTLICHT') {
            <p class="nebentext">{{ t.veroeffentlichtHinweis }}</p>
          } @else {
            <p class="nebentext">{{ t.entwurfHinweis }}</p>
            <div>
              <button
                type="button"
                class="button button-primaer"
                data-testid="aufgebot-veroeffentlichen"
                [disabled]="laeuft() || (a.anzahlAufgeboten ?? 0) === 0"
                [attr.aria-describedby]="(a.anzahlAufgeboten ?? 0) === 0 ? 'aufgebot-leer' : null"
                (click)="dialogOeffnen()"
              >
                {{ t.veroeffentlichen }}
              </button>
              @if ((a.anzahlAufgeboten ?? 0) === 0) {
                <p class="nebentext" id="aufgebot-leer">{{ t.leer }}</p>
              }
            </div>
          }
        </section>

        <section class="karte auswahl" data-testid="aufgebot-auswahl" aria-labelledby="aufgebot-auswahl-titel">
          <h2 id="aufgebot-auswahl-titel" class="abschnitt-titel">{{ t.auswahl }}</h2>
          <p class="nebentext">{{ t.auswahlHinweis }}</p>
          @for (g of a.gruppen; track g.gruppeId ?? 'ohne-linie') {
            <fieldset class="gruppe">
              <legend>{{ g.name }}</legend>
              <ul class="personen">
                @for (e of g.eintraege; track e.person.id) {
                  <li
                    class="person"
                    [attr.data-testid]="'aufgebot-person-' + e.person.id"
                    [attr.data-antwort]="e.antwort.status"
                    [attr.data-aufgeboten]="e.aufgeboten"
                  >
                    <label class="auswahl-zeile">
                      <input
                        type="checkbox"
                        [checked]="e.aufgeboten"
                        [disabled]="laeuft() || !aenderbar()"
                        [attr.aria-busy]="laufendePerson() === e.person.id ? 'true' : null"
                        [attr.aria-describedby]="'aufgebot-antwort-' + e.person.id"
                        (change)="umschalten(e.person, $any($event.target))"
                      />
                      <span class="person-name">{{ name(e.person) }}</span>
                    </label>
                    <span class="antwort" [id]="'aufgebot-antwort-' + e.person.id">
                      <span class="symbol" [attr.data-art]="schluessel(e.antwort.status)" aria-hidden="true">
                        <app-icon [name]="icon[e.antwort.status]" [groesse]="14" />
                      </span>
                      <span class="status-text">{{ texte.antwort.status[e.antwort.status] }}</span>
                      @if (e.antwort.kommentar; as k) {
                        <span class="nebentext kommentar">«{{ k }}»</span>
                      }
                    </span>
                  </li>
                }
              </ul>
            </fieldset>
          } @empty {
            <p class="leer">{{ t.keineSpielerinnen }}</p>
          }
        </section>
      }
    </div>

    <dialog
      #dialog
      class="karte dialog"
      aria-modal="true"
      aria-labelledby="aufgebot-dialog-titel"
      aria-describedby="aufgebot-dialog-text"
    >
      <div class="formular">
        <h2 id="aufgebot-dialog-titel">{{ t.dialogTitel }}</h2>
        <p id="aufgebot-dialog-text">{{ t.dialogText(aufgebot()?.anzahlAufgeboten ?? 0, teamName()) }}</p>
        <div class="dialog-knoepfe">
          <button
            type="button"
            class="button button-primaer button-klein"
            [disabled]="laeuft()"
            [attr.aria-busy]="laeuft() ? 'true' : null"
            (click)="veroeffentlichen()"
          >
            {{ t.veroeffentlichen }}
          </button>
          <button
            type="button"
            class="button button-sekundaer button-klein"
            [disabled]="laeuft()"
            (click)="dialogSchliessen()"
          >
            {{ t.abbrechen }}
          </button>
        </div>
      </div>
    </dialog>
  `,
  styleUrls: ['./uebersicht.css', './aufgebot.css'],
})
export class Aufgebot {
  private readonly http = inject(HttpClient);
  private readonly anmeldung = inject(AnmeldungService);
  protected readonly texte = TEXTE;
  protected readonly t = TEXTE.aufgebot;
  protected readonly icon = ANTWORT_ICON;
  protected readonly schluessel = antwortSchluessel;
  protected readonly zeit = uhrzeit;

  /** Route `/termine/:terminId/aufgebot` */
  readonly terminId = input.required<string>();

  protected readonly termin = signal<Termin | null>(null);
  protected readonly aufgebot = signal<AufgebotDaten | null>(null);
  protected readonly laedt = signal(true);
  protected readonly laeuft = signal(false);
  protected readonly laufendePerson = signal<string | null>(null);
  protected readonly meldung = signal<string | null>(null);
  protected readonly fehler = signal<string | null>(null);

  private readonly dialog = viewChild<ElementRef<HTMLDialogElement>>('dialog');
  private readonly jahr = computed(() => jahrVon(this.anmeldung.ich()?.jetzt));

  protected readonly teamName = computed(() => this.termin()?.teams[0]?.name ?? '');

  /** Geplant und nicht vorbei (TER-06); `darfBearbeiten` gilt für Coaches und Admins genau dann. */
  protected readonly aenderbar = computed(() => this.termin()?.darfBearbeiten === true);

  constructor() {
    effect(() => {
      const id = this.terminId();
      untracked(() => void this.laden(id));
    });
  }

  protected datum(iso: string): string {
    return terminDatum(iso, this.jahr());
  }

  protected name(p: PersonKurz): string {
    return `${p.vorname} ${p.nachname}`;
  }

  protected statusText(a: AufgebotDaten): string {
    if (a.status === 'VEROEFFENTLICHT' && a.veroeffentlichtAm) {
      return this.t.veroeffentlichtAm(this.datum(a.veroeffentlichtAm), uhrzeit(a.veroeffentlichtAm));
    }
    return this.t.status[a.status];
  }

  /** Auswahl sofort speichern (AUF-21, AUF-22). Schlägt es fehl, bleibt die alte Auswahl. */
  protected async umschalten(person: PersonKurz, feld: HTMLInputElement): Promise<void> {
    const a = this.aufgebot();
    if (!a || this.laeuft()) {
      feld.checked = !feld.checked;
      return;
    }
    const ja = feld.checked;
    const ids = a.gruppen
      .flatMap((g) => g.eintraege)
      .filter((e) => (e.person.id === person.id ? ja : e.aufgeboten))
      .map((e) => e.person.id);
    this.laeuft.set(true);
    this.laufendePerson.set(person.id);
    this.meldung.set(null);
    this.fehler.set(null);
    try {
      const neu = await firstValueFrom(
        this.http.put<AufgebotDaten>(`/api/termine/${this.terminId()}/aufgebot`, {
          personIds: [...new Set(ids)],
        }),
      );
      this.aufgebot.set(neu);
      this.meldung.set(this.t.gespeichert(this.name(person), ja));
    } catch (e) {
      feld.checked = !ja;
      this.fehler.set(fehlertext(e));
    } finally {
      this.laeuft.set(false);
      this.laufendePerson.set(null);
    }
  }

  protected dialogOeffnen(): void {
    const d = this.dialog()?.nativeElement;
    if (d && !d.open) d.showModal();
  }

  protected dialogSchliessen(): void {
    this.dialog()?.nativeElement.close();
  }

  /** Veröffentlichen nach Bestätigung (AUF-23, AUF-24). */
  protected async veroeffentlichen(): Promise<void> {
    if (this.laeuft()) return;
    this.laeuft.set(true);
    this.meldung.set(null);
    this.fehler.set(null);
    try {
      const neu = await firstValueFrom(
        this.http.post<AufgebotDaten>(`/api/termine/${this.terminId()}/aufgebot/veroeffentlichen`, null),
      );
      this.aufgebot.set(neu);
      this.dialogSchliessen();
      this.meldung.set(this.t.veroeffentlicht);
    } catch (e) {
      this.dialogSchliessen();
      this.fehler.set(fehlertext(e));
    } finally {
      this.laeuft.set(false);
    }
  }

  private async laden(id: string): Promise<void> {
    this.laedt.set(true);
    this.fehler.set(null);
    this.termin.set(null);
    this.aufgebot.set(null);
    try {
      const termin = await firstValueFrom(this.http.get<Termin>(`/api/termine/${id}`));
      this.termin.set(termin);
      // Rechte und Art zuerst aus dem Termin: so entsteht ohne Berechtigung keine 403-Anfrage.
      if (termin.typ !== 'SPIELTAG') {
        this.fehler.set(this.t.nurSpieltag);
        return;
      }
      const coach = termin.teams.some((t) => t.meineFunktion === 'COACH');
      if (!coach && !this.anmeldung.istAdmin()) {
        this.fehler.set(TEXTE.allgemein.keineBerechtigung);
        return;
      }
      this.aufgebot.set(
        await firstValueFrom(this.http.get<AufgebotDaten>(`/api/termine/${id}/aufgebot`)),
      );
    } catch (e) {
      const status = fehlerstatus(e);
      if (status === 404 || status === 400) {
        this.fehler.set(TEXTE.termine.nichtGefunden);
      } else if (status === 409) {
        this.fehler.set(this.t.nurSpieltag);
      } else {
        this.fehler.set(fehlertext(e));
      }
    } finally {
      this.laedt.set(false);
    }
  }
}
