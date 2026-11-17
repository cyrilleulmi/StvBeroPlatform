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
import type { Match, MatchInput, EventDetail } from '../api/types';
import { fieldErrorsOf, errorText } from '../core/error-text';
import { TEXTS } from '../core/texts';
import { clockTime } from './time';

const RESULT_PATTERN = /^\d{1,2}:\d{1,2}$/;

/**
 * Teil eines Spieltags im Termin-Detail: eigener Aufgebotsstatus bzw. für Coaches der Stand mit Link zum
 * Aufgebot (AUF-23, AUF-24), Spiele nach Anspielzeit (AUF-14) und Resultat erfassen (AUF-15).
 */
@Component({
  selector: 'app-spieltag',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    @if (event().myCallUp || canCallUp()) {
      <section class="karte aufgebot" aria-labelledby="spieltag-aufgebot-titel">
        <h2 id="spieltag-aufgebot-titel" class="abschnitt-titel">{{ s.callUp }}</h2>
        @if (event().myCallUp; as mine) {
          <p class="mein">
            <span class="chip" [attr.data-art]="mine.toLowerCase()" data-testid="mein-aufgebot">{{
              s.mine[mine]
            }}</span>
          </p>
          <p class="nebentext">{{ s.mineText[mine] }}</p>
        }
        @if (canCallUp()) {
          <p class="stand">{{ s.state[event().callUpStatus ?? 'NONE'] }}</p>
          <a class="button button-sekundaer button-klein" [routerLink]="['/termine', event().id, 'aufgebot']">{{
            s.editCallUp
          }}</a>
        }
      </section>
    }

    <section class="karte spiele" aria-labelledby="spieltag-spiele-titel">
      <h2 id="spieltag-spiele-titel" class="abschnitt-titel">{{ s.matches }}</h2>
      @if (message(); as m) {
        <p class="meldung-erfolg" role="status">{{ m }}</p>
      }
      @if (error(); as m) {
        <p class="meldung-fehler" role="alert">{{ m }}</p>
      }
      @if ((event().matches ?? []).length === 0) {
        <p class="leer">{{ s.noMatches }}</p>
      } @else {
        <ol class="liste" data-testid="spiele" aria-labelledby="spieltag-spiele-titel">
          @for (m of event().matches ?? []; track m.id) {
            <li class="spiel" [attr.data-testid]="'spiel-' + m.id" [attr.aria-label]="description(m)">
              <span class="anspielzeit">{{ time(m.startTime) }}</span>
              <span class="spiel-text">
                <span class="paarung">{{ pairing(m) }}</span>
                <span class="nebentext">{{ m.home ? s.home : s.away }}</span>
                @if (m.result) {
                  <span class="resultat">{{ s.result(m.result) }}</span>
                }
                @if (m.missingAtSu) {
                  <span class="nebentext">{{ s.missingAtSu }}</span>
                }
              </span>
              @if (canEnterResult()) {
                <button
                  type="button"
                  class="button button-sekundaer button-klein"
                  [attr.aria-label]="s.enterResultFor(pairing(m))"
                  [disabled]="running()"
                  (click)="open(m)"
                >
                  {{ s.enterResult }}
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
      (close)="target.set(null)"
    >
      @if (target(); as m) {
        <form class="formular" (submit)="save($event)" novalidate>
          <h2 id="resultat-titel">{{ s.resultTitle(pairing(m)) }}</h2>
          <p class="nebentext" id="resultat-text">{{ s.resultText }}</p>
          <div class="feld">
            <label for="resultat-feld">{{ s.resultField }}</label>
            <input
              #resultField
              id="resultat-feld"
              type="text"
              inputmode="numeric"
              maxlength="5"
              autocomplete="off"
              [value]="result()"
              (input)="result.set($any($event.target).value)"
              [attr.aria-invalid]="resultError() ? 'true' : null"
              [attr.aria-describedby]="resultError() ? 'resultat-fehler' : null"
            />
            @if (resultError(); as m) {
              <p class="feldfehler" id="resultat-fehler">{{ m }}</p>
            }
          </div>
          <div class="dialog-knoepfe">
            <button
              type="submit"
              class="button button-primaer button-klein"
              [disabled]="running()"
              [attr.aria-busy]="running() ? 'true' : null"
            >
              {{ s.saveResult }}
            </button>
            <button
              type="button"
              class="button button-sekundaer button-klein"
              [disabled]="running()"
              (click)="close()"
            >
              {{ texts.response.cancel }}
            </button>
          </div>
        </form>
      }
    </dialog>
  `,
  styleUrl: './match-day.css',
})
export class MatchDay {
  private readonly http = inject(HttpClient);
  protected readonly texts = TEXTS;
  protected readonly s = TEXTS.matchDay;
  protected readonly time = clockTime;

  readonly event = input.required<EventDetail>();
  /** Coach des Teams oder Admin: Stand des Aufgebots und Link dorthin (RR-03). */
  readonly canCallUp = input(false);
  /** Coach des Teams oder Admin, Spieltag nicht abgesagt: Resultat erfassen (AUF-15). */
  readonly canEnterResult = input(false);

  readonly changed = output<void>();

  protected readonly target = signal<Match | null>(null);
  protected readonly result = signal('');
  protected readonly resultError = signal<string | null>(null);
  protected readonly running = signal(false);
  protected readonly message = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  private readonly dialog = viewChild<ElementRef<HTMLDialogElement>>('dialog');
  private readonly resultField = viewChild<ElementRef<HTMLInputElement>>('resultField');
  private readonly team = computed(() => this.event().teams[0]?.name ?? '');

  /** «Team 1 – UHC Musterdorf» (Heim zuerst). */
  protected pairing(m: Match): string {
    return m.home ? this.s.pairing(this.team(), m.opponent) : this.s.pairing(m.opponent, this.team());
  }

  protected description(m: Match): string {
    return `${clockTime(m.startTime)} ${this.pairing(m)}`;
  }

  protected open(m: Match): void {
    this.target.set(m);
    this.result.set(m.result ?? '');
    this.resultError.set(null);
    this.message.set(null);
    this.error.set(null);
    const d = this.dialog()?.nativeElement;
    if (d && !d.open) d.showModal();
  }

  protected close(): void {
    this.dialog()?.nativeElement.close();
  }

  protected async save(domEvent: Event): Promise<void> {
    domEvent.preventDefault();
    const m = this.target();
    if (!m || this.running()) return;
    const value = this.result().replace(/\s/g, '');
    if (value && !RESULT_PATTERN.test(value)) {
      this.resultError.set(this.s.resultFormat);
      this.resultField()?.nativeElement.focus();
      return;
    }
    this.running.set(true);
    this.resultError.set(null);
    const input: MatchInput = {
      opponent: m.opponent,
      startTime: m.startTime,
      home: m.home,
      result: value || null,
    };
    try {
      await firstValueFrom(this.http.put<Match>(`/api/events/${this.event().id}/matches/${m.id}`, input));
      this.close();
      this.message.set(this.s.resultSaved(this.pairing(m)));
      this.changed.emit();
    } catch (e) {
      const fields = fieldErrorsOf(e);
      if (fields['result']) {
        this.resultError.set(fields['result']);
      } else {
        this.close();
        this.error.set(errorText(e));
      }
    } finally {
      this.running.set(false);
    }
  }
}
