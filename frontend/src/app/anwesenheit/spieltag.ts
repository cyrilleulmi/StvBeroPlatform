import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { Spiel, SpielSchreiben, Termin } from '../api/typen';
import { feldfehler, fehlertext } from '../kern/fehlertext';
import { TEXTE } from '../kern/texte';
import { uhrzeit } from './zeit';

const RESULTAT = /^\d{1,2}:\d{1,2}$/;

/**
 * Teil eines Spieltags im Termin-Detail: eigener Aufgebotsstatus bzw. für Coaches der Stand mit Link zum
 * Aufgebot (AUF-23, AUF-24), Spiele nach Anspielzeit (AUF-14) und Resultat erfassen (AUF-15).
 */
@Component({
  selector: 'app-spieltag',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    @if (termin().myCallUp || darfAufgebot()) {
      <section class="karte aufgebot" aria-labelledby="spieltag-aufgebot-titel">
        <h2 id="spieltag-aufgebot-titel" class="abschnitt-titel">{{ s.aufgebot }}</h2>
        @if (termin().myCallUp; as mein) {
          <p class="mein">
            <span class="chip" [attr.data-art]="mein.toLowerCase()" data-testid="mein-aufgebot">{{
              s.mein[mein]
            }}</span>
          </p>
          <p class="nebentext">{{ s.meinText[mein] }}</p>
        }
        @if (darfAufgebot()) {
          <p class="stand">{{ s.stand[termin().callUpStatus ?? 'NONE'] }}</p>
          <a class="button button-sekundaer button-klein" [routerLink]="['/termine', termin().id, 'aufgebot']">{{
            s.aufgebotBearbeiten
          }}</a>
        }
      </section>
    }

    <section class="karte spiele" aria-labelledby="spieltag-spiele-titel">
      <h2 id="spieltag-spiele-titel" class="abschnitt-titel">{{ s.spiele }}</h2>
      @if (meldung(); as m) {
        <p class="meldung-erfolg" role="status">{{ m }}</p>
      }
      @if (fehler(); as m) {
        <p class="meldung-fehler" role="alert">{{ m }}</p>
      }
      @if ((termin().matches ?? []).length === 0) {
        <p class="leer">{{ s.keineSpiele }}</p>
      } @else {
        <ol class="liste" data-testid="spiele" aria-labelledby="spieltag-spiele-titel">
          @for (sp of termin().matches ?? []; track sp.id) {
            <li class="spiel" [attr.data-testid]="'spiel-' + sp.id" [attr.aria-label]="beschreibung(sp)">
              <span class="anspielzeit">{{ zeit(sp.startTime) }}</span>
              <span class="spiel-text">
                <span class="paarung">{{ paarung(sp) }}</span>
                <span class="nebentext">{{ sp.home ? s.heim : s.auswaerts }}</span>
                @if (sp.result) {
                  <span class="resultat">{{ s.resultat(sp.result) }}</span>
                }
                @if (sp.missingAtSu) {
                  <span class="nebentext">{{ s.nichtMehrBeiSu }}</span>
                }
              </span>
              @if (darfResultat()) {
                <button
                  type="button"
                  class="button button-sekundaer button-klein"
                  [attr.aria-label]="s.resultatErfassenFuer(paarung(sp))"
                  [disabled]="laeuft()"
                  (click)="oeffnen(sp)"
                >
                  {{ s.resultatErfassen }}
                </button>
              }
            </li>
          }
        </ol>
      }
    </section>

    <dialog
      #dialog
      class="karte dialog"
      aria-modal="true"
      aria-labelledby="resultat-titel"
      aria-describedby="resultat-text"
      (close)="ziel.set(null)"
    >
      @if (ziel(); as sp) {
        <form class="formular" (submit)="speichern($event)" novalidate>
          <h2 id="resultat-titel">{{ s.resultatTitel(paarung(sp)) }}</h2>
          <p class="nebentext" id="resultat-text">{{ s.resultatText }}</p>
          <div class="feld">
            <label for="resultat-feld">{{ s.resultatFeld }}</label>
            <input
              #resultatFeld
              id="resultat-feld"
              type="text"
              inputmode="numeric"
              maxlength="5"
              autocomplete="off"
              [value]="resultat()"
              (input)="resultat.set($any($event.target).value)"
              [attr.aria-invalid]="resultatFehler() ? 'true' : null"
              [attr.aria-describedby]="resultatFehler() ? 'resultat-fehler' : null"
            />
            @if (resultatFehler(); as m) {
              <p class="feldfehler" id="resultat-fehler">{{ m }}</p>
            }
          </div>
          <div class="dialog-knoepfe">
            <button
              type="submit"
              class="button button-primaer button-klein"
              [disabled]="laeuft()"
              [attr.aria-busy]="laeuft() ? 'true' : null"
            >
              {{ s.resultatSpeichern }}
            </button>
            <button
              type="button"
              class="button button-sekundaer button-klein"
              [disabled]="laeuft()"
              (click)="schliessen()"
            >
              {{ texte.antwort.abbrechen }}
            </button>
          </div>
        </form>
      }
    </dialog>
  `,
  styleUrl: './spieltag.css',
})
export class Spieltag {
  private readonly http = inject(HttpClient);
  protected readonly texte = TEXTE;
  protected readonly s = TEXTE.spieltag;
  protected readonly zeit = uhrzeit;

  readonly termin = input.required<Termin>();
  /** Coach des Teams oder Admin: Stand des Aufgebots und Link dorthin (RR-03). */
  readonly darfAufgebot = input(false);
  /** Coach des Teams oder Admin, Spieltag nicht abgesagt: Resultat erfassen (AUF-15). */
  readonly darfResultat = input(false);

  readonly geaendert = output<void>();

  protected readonly ziel = signal<Spiel | null>(null);
  protected readonly resultat = signal('');
  protected readonly resultatFehler = signal<string | null>(null);
  protected readonly laeuft = signal(false);
  protected readonly meldung = signal<string | null>(null);
  protected readonly fehler = signal<string | null>(null);

  private readonly dialog = viewChild<ElementRef<HTMLDialogElement>>('dialog');
  private readonly resultatFeld = viewChild<ElementRef<HTMLInputElement>>('resultatFeld');
  private readonly team = computed(() => this.termin().teams[0]?.name ?? '');

  /** «Team 1 – UHC Musterdorf» (Heim zuerst). */
  protected paarung(sp: Spiel): string {
    return sp.home ? this.s.paarung(this.team(), sp.opponent) : this.s.paarung(sp.opponent, this.team());
  }

  protected beschreibung(sp: Spiel): string {
    return `${uhrzeit(sp.startTime)} ${this.paarung(sp)}`;
  }

  protected oeffnen(sp: Spiel): void {
    this.ziel.set(sp);
    this.resultat.set(sp.result ?? '');
    this.resultatFehler.set(null);
    this.meldung.set(null);
    this.fehler.set(null);
    const d = this.dialog()?.nativeElement;
    if (d && !d.open) d.showModal();
  }

  protected schliessen(): void {
    this.dialog()?.nativeElement.close();
  }

  protected async speichern(ereignis: Event): Promise<void> {
    ereignis.preventDefault();
    const sp = this.ziel();
    if (!sp || this.laeuft()) return;
    const wert = this.resultat().replace(/\s/g, '');
    if (wert && !RESULTAT.test(wert)) {
      this.resultatFehler.set(this.s.resultatFormat);
      this.resultatFeld()?.nativeElement.focus();
      return;
    }
    this.laeuft.set(true);
    this.resultatFehler.set(null);
    const eingabe: SpielSchreiben = {
      opponent: sp.opponent,
      startTime: sp.startTime,
      home: sp.home,
      result: wert || null,
    };
    try {
      await firstValueFrom(this.http.put<Spiel>(`/api/events/${this.termin().id}/matches/${sp.id}`, eingabe));
      this.schliessen();
      this.meldung.set(this.s.resultatGespeichert(this.paarung(sp)));
      this.geaendert.emit();
    } catch (e) {
      const felder = feldfehler(e);
      if (felder['result']) {
        this.resultatFehler.set(felder['result']);
      } else {
        this.schliessen();
        this.fehler.set(fehlertext(e));
      }
    } finally {
      this.laeuft.set(false);
    }
  }
}
