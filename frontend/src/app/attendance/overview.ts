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
import type {
  Antwort,
  AntwortSetzen,
  AntwortStatus,
  PersonKurz,
  Uebersicht as UebersichtDaten,
} from '../api/types';
import { NgTemplateOutlet } from '@angular/common';
import { feldfehler, fehlertext } from '../core/error-text';
import { Icon } from '../core/icon';
import { TEXTE } from '../core/texts';
import { ANTWORT_ICON, AntwortService, antwortSchluessel } from './response';

type Wahl = AntwortSetzen['status'];

interface Ziel {
  person: PersonKurz;
  response: Antwort;
}

/**
 * Übersicht eines Termins (ANM-20 bis ANM-32): Gesamtzähler, pro Team Zähler und Gruppen (Linien, Goalies,
 * «Ohne Linie»), Coach-Block. Coaches tragen Antworten für Spielerinnen ihres Teams ein (ANM-11, RR-06),
 * Admins für alle (RR-04). Kommentare liefert die API nur an Berechtigte (ANM-07).
 */
@Component({
  selector: 'app-uebersicht',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, NgTemplateOutlet],
  template: `
    <section class="karte uebersicht" data-testid="uebersicht" aria-labelledby="uebersicht-titel">
      <h2 id="uebersicht-titel" class="abschnitt-titel">{{ t.titel }}</h2>
      <p class="zaehler total" data-testid="uebersicht-total">
        <span class="zaehler-name">{{ t.total }}</span>
        <span class="zahlen">
          <span data-zaehler="dabei">{{ t.dabei(daten().total.attending) }}</span>
          <span class="trenner"> · </span>
          <span data-zaehler="nicht-dabei">{{ t.nichtDabei(daten().total.declined) }}</span>
          <span class="trenner"> · </span>
          <span data-zaehler="vielleicht">{{ t.vielleicht(daten().total.maybe) }}</span>
          <span class="trenner"> · </span>
          <span data-zaehler="offen">{{ t.offen(daten().total.pending) }}</span>
        </span>
      </p>

      @if (meldung(); as m) {
        <p class="meldung-erfolg" role="status">{{ m }}</p>
      }
      @if (fehler(); as m) {
        <p class="meldung-fehler" role="alert">{{ m }}</p>
      }

      @for (block of daten().teams; track block.team.id) {
        <section
          class="team"
          [attr.data-testid]="'uebersicht-team-' + block.team.id"
          [attr.aria-labelledby]="'uebersicht-team-titel-' + block.team.id"
        >
          <div class="team-kopf">
            <h3 [id]="'uebersicht-team-titel-' + block.team.id">{{ block.team.name }}</h3>
            <p
              class="zaehler"
              [attr.data-testid]="'uebersicht-team-' + block.team.id + '-zaehler'"
            >
              <span data-zaehler="dabei">{{ t.dabei(block.counts.attending) }}</span>
              <span class="trenner"> · </span>
              <span data-zaehler="nicht-dabei">{{ t.nichtDabei(block.counts.declined) }}</span>
              <span class="trenner"> · </span>
              <span data-zaehler="vielleicht">{{ t.vielleicht(block.counts.maybe) }}</span>
              <span class="trenner"> · </span>
              <span data-zaehler="offen">{{ t.offen(block.counts.pending) }}</span>
            </p>
          </div>
          @for (g of block.groups; track g.groupId ?? 'ohne-linie') {
            @let gruppenId = g.groupId ? 'uebersicht-gruppe-' + g.groupId : 'uebersicht-ohne-linie-' + block.team.id;
            <section class="gruppe" [attr.data-testid]="gruppenId" [attr.aria-labelledby]="gruppenId + '-titel'">
              <h4 [id]="gruppenId + '-titel'">{{ g.name }}</h4>
              <ul class="personen" [attr.aria-labelledby]="gruppenId + '-titel'">
                @for (e of g.entries; track e.person.id) {
                  <li
                    class="person"
                    [attr.data-testid]="'uebersicht-person-' + e.person.id"
                    [attr.data-antwort]="e.response.status"
                  >
                    <ng-container
                      [ngTemplateOutlet]="personTpl"
                      [ngTemplateOutletContext]="{ $implicit: e, zusatz: null }"
                    />
                    @if (darfFuerSpielerin(block.team.id, e.person.id)) {
                      <button
                        type="button"
                        class="icon-knopf"
                        [attr.aria-label]="a.stellvertretend(name(e.person))"
                        [disabled]="laeuft()"
                        (click)="oeffnen(e)"
                      >
                        <app-icon name="pencil" [groesse]="18" />
                      </button>
                    }
                  </li>
                }
              </ul>
            </section>
          }
        </section>
      }

      @if (daten().coaches.length > 0) {
        <section class="team coaches" data-testid="coach-block" aria-labelledby="uebersicht-coaches-titel">
          <h3 id="uebersicht-coaches-titel">{{ t.coaches }}</h3>
          <ul class="personen" aria-labelledby="uebersicht-coaches-titel">
            @for (c of daten().coaches; track c.person.id + c.team.id) {
              <li
                class="person"
                [attr.data-testid]="'uebersicht-person-' + c.person.id"
                [attr.data-antwort]="c.response.status"
              >
                <ng-container
                  [ngTemplateOutlet]="personTpl"
                  [ngTemplateOutletContext]="{ $implicit: c, zusatz: t.coachVon(c.team.name) }"
                />
                @if (darfFuerCoach(c.person.id)) {
                  <button
                    type="button"
                    class="icon-knopf"
                    [attr.aria-label]="a.stellvertretend(name(c.person))"
                    [disabled]="laeuft()"
                    (click)="oeffnen(c)"
                  >
                    <app-icon name="pencil" [groesse]="18" />
                  </button>
                }
              </li>
            }
          </ul>
        </section>
      }

      <ng-template #personTpl let-e let-zusatz="zusatz">
        <span class="symbol" [attr.data-art]="schluessel(e.response.status)" aria-hidden="true">
          <app-icon [name]="icon[status(e.response)]" [groesse]="16" />
        </span>
        <span class="person-text">
          <span class="person-name">
            {{ name(e.person) }}
            @if (zusatz) {
              <span class="nebentext">{{ zusatz }}</span>
            }
          </span>
          <span class="status-text" [attr.data-art]="schluessel(e.response.status)">{{
            a.status[status(e.response)]
          }}</span>
          @if (e.response.lateCancellation) {
            <span class="chip" data-art="kurzfristig">{{ a.kurzfristig }}</span>
          }
          @if (e.response.comment; as k) {
            <span class="nebentext kommentar">«{{ k }}»</span>
          }
          @if (e.response.enteredBy; as von) {
            <span class="nebentext">{{ a.eingetragenVon(name(von)) }}</span>
          }
        </span>
      </ng-template>
    </section>

    <dialog
      #dialog
      class="karte dialog"
      aria-modal="true"
      aria-labelledby="stellvertretend-titel"
      aria-describedby="stellvertretend-text"
      (close)="geschlossen()"
    >
      @if (ziel(); as z) {
        <form class="formular" (submit)="speichern($event)" novalidate>
          <h2 id="stellvertretend-titel">{{ a.stellvertretend(name(z.person)) }}</h2>
          <p class="nebentext" id="stellvertretend-text">{{ a.stellvertretendText(z.person.firstName) }}</p>
          <fieldset class="wahl">
            <legend class="label">{{ a.antwort }}</legend>
            @for (w of wahlen; track w) {
              <label class="checkbox">
                <input
                  type="radio"
                  name="stellvertretend-status"
                  [value]="w"
                  [checked]="wahl() === w"
                  (change)="wahl.set(w)"
                />
                {{ a.status[w] }}
              </label>
            }
          </fieldset>
          <div class="feld">
            <label for="stellvertretend-kommentar">{{ a.kommentarFreiwillig }}</label>
            <input
              id="stellvertretend-kommentar"
              type="text"
              maxlength="200"
              autocomplete="off"
              [value]="kommentar()"
              (input)="kommentar.set($any($event.target).value)"
              [attr.aria-invalid]="kommentarFehler() ? 'true' : null"
              [attr.aria-describedby]="kommentarFehler() ? 'stellvertretend-kommentar-fehler' : null"
            />
            @if (kommentarFehler(); as m) {
              <p class="feldfehler" id="stellvertretend-kommentar-fehler">{{ m }}</p>
            }
          </div>
          <div class="dialog-knoepfe">
            <button
              type="submit"
              class="button button-primaer button-klein"
              [disabled]="laeuft()"
              [attr.aria-busy]="laeuft() ? 'true' : null"
            >
              {{ a.speichern }}
            </button>
            <button
              type="button"
              class="button button-sekundaer button-klein"
              [disabled]="laeuft()"
              (click)="schliessen()"
            >
              {{ a.abbrechen }}
            </button>
          </div>
        </form>
      }
    </dialog>
  `,
  styleUrls: ['./response.css', './overview.css'],
})
export class Uebersicht {
  private readonly antworten = inject(AntwortService);
  protected readonly t = TEXTE.uebersicht;
  protected readonly a = TEXTE.antwort;
  protected readonly icon = ANTWORT_ICON;
  protected readonly schluessel = antwortSchluessel;
  protected readonly wahlen: Wahl[] = ['ATTENDING', 'DECLINED', 'MAYBE'];

  readonly terminId = input.required<string>();
  readonly daten = input.required<UebersichtDaten>();
  /** Teams, in denen die angemeldete Person Coach ist (RR-06). */
  readonly coachIn = input<string[]>([]);
  readonly admin = input(false);
  readonly ichId = input<string | null>(null);
  /** Antworten möglich (Termin geplant und nicht vorbei). */
  readonly offen = input(true);

  readonly geaendert = output<void>();

  protected readonly ziel = signal<Ziel | null>(null);
  protected readonly wahl = signal<Wahl>('ATTENDING');
  protected readonly kommentar = signal('');
  protected readonly kommentarFehler = signal<string | null>(null);
  protected readonly laeuft = signal(false);
  protected readonly meldung = signal<string | null>(null);
  protected readonly fehler = signal<string | null>(null);

  private readonly dialog = viewChild<ElementRef<HTMLDialogElement>>('dialog');
  private readonly coachTeams = computed(() => new Set(this.coachIn()));

  protected name(p: PersonKurz): string {
    return `${p.firstName} ${p.lastName}`;
  }

  protected status(a: Antwort): AntwortStatus {
    return a.status;
  }

  /** Coaches für Spielerinnen ihres Teams, Admins für alle; nicht für sich selbst (ANM-11, RR-06). */
  protected darfFuerSpielerin(teamId: string, personId: string): boolean {
    return (
      this.offen() &&
      personId !== this.ichId() &&
      (this.admin() || this.coachTeams().has(teamId))
    );
  }

  /** Antworten anderer Coaches trägt nur ein Admin ein. */
  protected darfFuerCoach(personId: string): boolean {
    return this.offen() && this.admin() && personId !== this.ichId();
  }

  protected oeffnen(z: Ziel): void {
    this.ziel.set(z);
    this.wahl.set(z.response.status === 'PENDING' ? 'ATTENDING' : (z.response.status as Wahl));
    this.kommentar.set(z.response.comment ?? '');
    this.kommentarFehler.set(null);
    this.meldung.set(null);
    this.fehler.set(null);
    const d = this.dialog()?.nativeElement;
    if (d && !d.open) d.showModal();
  }

  protected schliessen(): void {
    this.dialog()?.nativeElement.close();
  }

  protected geschlossen(): void {
    this.ziel.set(null);
  }

  protected async speichern(ereignis: Event): Promise<void> {
    ereignis.preventDefault();
    const z = this.ziel();
    if (!z || this.laeuft()) return;
    const kommentar = this.kommentar().trim();
    if (this.wahl() === 'MAYBE' && !kommentar) {
      this.kommentarFehler.set(this.a.kommentarPflicht);
      document.getElementById('stellvertretend-kommentar')?.focus();
      return;
    }
    this.laeuft.set(true);
    this.kommentarFehler.set(null);
    try {
      const neu = await this.antworten.setzen(
        this.terminId(),
        { status: this.wahl(), comment: kommentar || null },
        z.person.id,
      );
      this.schliessen();
      this.meldung.set(
        this.a.stellvertretendGespeichert(this.name(z.person), this.a.status[neu.status]),
      );
      this.geaendert.emit();
    } catch (e) {
      const felder = feldfehler(e);
      if (felder['comment']) {
        this.kommentarFehler.set(felder['comment']);
      } else {
        this.schliessen();
        this.fehler.set(fehlertext(e));
      }
    } finally {
      this.laeuft.set(false);
    }
  }
}
