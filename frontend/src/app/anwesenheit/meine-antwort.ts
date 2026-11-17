import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  input,
  linkedSignal,
  output,
  signal,
  viewChild,
} from '@angular/core';
import type { Antwort, AntwortSetzen, AntwortStatus } from '../api/typen';
import { feldfehler, fehlertext } from '../kern/fehlertext';
import { Icon } from '../kern/icon';
import { TEXTE } from '../kern/texte';
import { ANTWORT_ICON, AntwortService, antwortSchluessel } from './antwort';

type Wahl = AntwortSetzen['status'];

/**
 * Antwort-Buttons der angemeldeten Person (`meine-antwort`): «dabei» und «nicht dabei» mit einem Tap
 * (ANM-08), «nicht dabei» bietet danach einen freiwilligen Grund an (ANM-05), «vielleicht» verlangt einen
 * Kommentar (ANM-04). Bei abgesagten oder vergangenen Terminen deaktiviert, mit Erklärung (TER-06).
 */
@Component({
  selector: 'app-meine-antwort',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  template: `
    <div
      class="antwort"
      role="group"
      data-testid="meine-antwort"
      [attr.aria-label]="name()"
      [attr.data-antwort]="aktuell().status"
    >
      <div class="knoepfe">
        @for (w of wahlen; track w) {
          <button
            type="button"
            class="antwort-knopf"
            [attr.data-art]="schluessel(w)"
            [attr.data-testid]="'antwort-' + schluessel(w)"
            [attr.aria-pressed]="aktuell().status === w ? 'true' : 'false'"
            [attr.aria-busy]="laeuft() === w ? 'true' : null"
            [attr.aria-describedby]="gesperrt() ? id('sperre') : null"
            [disabled]="!!gesperrt() || laeuft() !== null"
            (click)="waehlen(w)"
          >
            <app-icon [name]="icon[w]" [groesse]="18" />
            {{ texte.knopf[w] }}
          </button>
        }
      </div>
      @if (gesperrt(); as text) {
        <p class="nebentext" [id]="id('sperre')">{{ text }}</p>
      }
      @if (details()) {
        @if (aktuell().comment; as k) {
          <p class="nebentext kommentar">{{ texte.dein(k) }}</p>
        }
        @if (aktuell().enteredBy; as von) {
          <p class="nebentext">{{ texte.eingetragenVon(von.firstName + ' ' + von.lastName) }}</p>
        }
        @if (aktuell().lateCancellation) {
          <p><span class="chip" data-art="kurzfristig">{{ texte.kurzfristig }}</span></p>
        }
      }
      @if (meldung(); as m) {
        <p class="nebentext rueckmeldung" role="status">{{ m }}</p>
      }
      @if (fehler(); as m) {
        <p class="meldung-fehler" role="alert">{{ m }}</p>
      }
    </div>

    <dialog
      #vielleichtDialog
      class="karte dialog"
      aria-modal="true"
      [attr.aria-labelledby]="id('vielleicht-titel')"
      [attr.aria-describedby]="id('vielleicht-text')"
      (close)="dialogZu()"
    >
      <form class="formular" (submit)="vielleichtSpeichern($event)" novalidate>
        <h2 [id]="id('vielleicht-titel')">{{ texte.vielleichtTitel }}</h2>
        <p class="nebentext" [id]="id('vielleicht-text')">{{ texte.vielleichtText }}</p>
        <div class="feld">
          <label [for]="id('kommentar')">{{ texte.kommentar }}</label>
          <input
            #kommentarFeld
            type="text"
            maxlength="200"
            autocomplete="off"
            [id]="id('kommentar')"
            [value]="kommentar()"
            (input)="kommentar.set($any($event.target).value)"
            [attr.aria-invalid]="kommentarFehler() ? 'true' : null"
            [attr.aria-describedby]="kommentarFehler() ? id('kommentar-fehler') : null"
          />
          @if (kommentarFehler(); as m) {
            <p class="feldfehler" [id]="id('kommentar-fehler')">{{ m }}</p>
          }
        </div>
        <div class="dialog-knoepfe">
          <button
            type="submit"
            class="button button-primaer button-klein"
            [disabled]="laeuft() !== null"
            [attr.aria-busy]="laeuft() === 'MAYBE' ? 'true' : null"
          >
            {{ texte.speichern }}
          </button>
          <button
            type="button"
            class="button button-sekundaer button-klein"
            [disabled]="laeuft() !== null"
            (click)="schliessen(vielleichtDialog)"
          >
            {{ texte.abbrechen }}
          </button>
        </div>
      </form>
    </dialog>

    <dialog
      #grundDialog
      class="karte dialog"
      aria-modal="true"
      [attr.aria-labelledby]="id('grund-titel')"
      [attr.aria-describedby]="id('grund-text')"
      (close)="dialogZu()"
    >
      <form class="formular" (submit)="grundSpeichern($event)" novalidate>
        <h2 [id]="id('grund-titel')">{{ texte.nichtDabeiTitel }}</h2>
        <p class="nebentext" [id]="id('grund-text')">{{ texte.nichtDabeiText }}</p>
        <div class="feld">
          <label [for]="id('grund')">{{ texte.grund }}</label>
          <input
            #grundFeld
            type="text"
            maxlength="200"
            autocomplete="off"
            [id]="id('grund')"
            [value]="kommentar()"
            (input)="kommentar.set($any($event.target).value)"
            [attr.aria-invalid]="kommentarFehler() ? 'true' : null"
            [attr.aria-describedby]="kommentarFehler() ? id('grund-fehler') : null"
          />
          @if (kommentarFehler(); as m) {
            <p class="feldfehler" [id]="id('grund-fehler')">{{ m }}</p>
          }
        </div>
        <div class="dialog-knoepfe">
          <button
            type="submit"
            class="button button-primaer button-klein"
            [disabled]="laeuft() !== null"
            [attr.aria-busy]="laeuft() === 'DECLINED' ? 'true' : null"
          >
            {{ texte.grundSpeichern }}
          </button>
          <button
            type="button"
            class="button button-sekundaer button-klein"
            [disabled]="laeuft() !== null"
            (click)="schliessen(grundDialog)"
          >
            {{ texte.ueberspringen }}
          </button>
        </div>
      </form>
    </dialog>
  `,
  styleUrl: './antwort.css',
})
export class MeineAntwort {
  private readonly antworten = inject(AntwortService);
  protected readonly texte = TEXTE.antwort;
  protected readonly icon = ANTWORT_ICON;
  protected readonly schluessel = antwortSchluessel;
  protected readonly wahlen: Wahl[] = ['ATTENDING', 'DECLINED', 'MAYBE'];

  readonly terminId = input.required<string>();
  readonly antwort = input.required<Antwort>();
  /** Name der Gruppe, z.B. «Meine Antwort für Training Di 17.11. 18:30». */
  readonly name = input<string>(TEXTE.antwort.meineAntwort);
  /** Erklärung, warum nicht geantwortet werden kann (abgesagt, vorbei). Gesetzt: Buttons deaktiviert. */
  readonly gesperrt = input<string | null>(null);
  /** Eigenen Kommentar, «eingetragen von» und «kurzfristig» anzeigen (Detail eines Termins). */
  readonly details = input(false);

  /** Nach jedem Speichern, damit die Seite Zähler und Übersicht nachlädt. */
  readonly geaendert = output<Antwort>();

  protected readonly aktuell = linkedSignal(() => this.antwort());
  protected readonly laeuft = signal<Wahl | null>(null);
  protected readonly meldung = signal<string | null>(null);
  protected readonly fehler = signal<string | null>(null);
  protected readonly kommentar = signal('');
  protected readonly kommentarFehler = signal<string | null>(null);

  private readonly vielleichtDialog = viewChild<ElementRef<HTMLDialogElement>>('vielleichtDialog');
  private readonly grundDialog = viewChild<ElementRef<HTMLDialogElement>>('grundDialog');

  private readonly praefix = computed(() => `antwort-${this.terminId()}`);

  protected id(teil: string): string {
    return `${this.praefix()}-${teil}`;
  }

  protected async waehlen(w: Wahl): Promise<void> {
    if (this.gesperrt() || this.laeuft()) return;
    const bisher = this.aktuell();
    this.kommentarFehler.set(null);
    if (w === 'MAYBE') {
      this.kommentar.set(bisher.status === 'MAYBE' ? (bisher.comment ?? '') : '');
      this.oeffnen(this.vielleichtDialog());
      return;
    }
    if (w === 'DECLINED' && bisher.status === 'DECLINED') {
      this.kommentar.set(bisher.comment ?? '');
      this.oeffnen(this.grundDialog());
      return;
    }
    // Ein Tap: sofort speichern (ANM-08). Bei «nicht dabei» danach den freiwilligen Grund anbieten (ANM-05).
    if (await this.speichern({ status: w })) {
      if (w === 'DECLINED') {
        this.kommentar.set('');
        this.oeffnen(this.grundDialog());
      }
    }
  }

  protected async vielleichtSpeichern(ereignis: Event): Promise<void> {
    ereignis.preventDefault();
    const kommentar = this.kommentar().trim();
    if (!kommentar) {
      this.kommentarFehler.set(TEXTE.antwort.kommentarPflicht);
      this.fokus('kommentar');
      return;
    }
    if (await this.speichern({ status: 'MAYBE', comment: kommentar })) {
      this.vielleichtDialog()?.nativeElement.close();
    } else {
      this.fokus('kommentar');
    }
  }

  protected async grundSpeichern(ereignis: Event): Promise<void> {
    ereignis.preventDefault();
    const kommentar = this.kommentar().trim();
    if (await this.speichern({ status: 'DECLINED', comment: kommentar || null }, true)) {
      this.grundDialog()?.nativeElement.close();
    } else {
      this.fokus('grund');
    }
  }

  protected schliessen(dialog: HTMLDialogElement): void {
    dialog.close();
  }

  protected dialogZu(): void {
    this.kommentarFehler.set(null);
  }

  private oeffnen(dialog: ElementRef<HTMLDialogElement> | undefined): void {
    const d = dialog?.nativeElement;
    if (d && !d.open) d.showModal();
  }

  private fokus(teil: string): void {
    queueMicrotask(() => document.getElementById(this.id(teil))?.focus());
  }

  /** Speichert und meldet das Ergebnis. Fehler beim Kommentar erscheinen beim Feld (ANM-04). */
  private async speichern(eingabe: AntwortSetzen, nurGrund = false): Promise<boolean> {
    this.laeuft.set(eingabe.status);
    this.meldung.set(null);
    this.fehler.set(null);
    this.kommentarFehler.set(null);
    try {
      const neu = await this.antworten.setzen(this.terminId(), eingabe);
      this.aktuell.set(neu);
      this.meldung.set(
        nurGrund
          ? TEXTE.antwort.grundGespeichert
          : TEXTE.antwort.gespeichert(TEXTE.antwort.status[neu.status as AntwortStatus]),
      );
      this.geaendert.emit(neu);
      return true;
    } catch (e) {
      const felder = feldfehler(e);
      if (felder['comment']) {
        this.kommentarFehler.set(felder['comment']);
      } else {
        // Dialog schliessen, damit die Meldung sichtbar ist (z.B. Termin inzwischen abgesagt, 409).
        this.fehler.set(fehlertext(e));
        this.vielleichtDialog()?.nativeElement.close();
        this.grundDialog()?.nativeElement.close();
      }
      return false;
    } finally {
      this.laeuft.set(null);
    }
  }
}
