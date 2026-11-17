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
import type { EventResponse, ResponseInput, ResponseStatus } from '../api/types';
import { fieldErrorsOf, errorText } from '../core/error-text';
import { Icon } from '../core/icon';
import { TEXTS } from '../core/texts';
import { RESPONSE_ICON, ResponseService, responseKey } from './response';

type Choice = ResponseInput['status'];

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
      [attr.data-antwort]="current().status"
    >
      <div class="knoepfe">
        @for (w of choices; track w) {
          <button
            type="button"
            class="antwort-knopf"
            [attr.data-art]="key(w)"
            [attr.data-testid]="'antwort-' + key(w)"
            [attr.aria-pressed]="current().status === w ? 'true' : 'false'"
            [attr.aria-busy]="running() === w ? 'true' : null"
            [attr.aria-describedby]="locked() ? id('lock') : null"
            [disabled]="!!locked() || running() !== null"
            (click)="choose(w)"
          >
            <app-icon [name]="icon[w]" [size]="18" />
            {{ texts.button[w] }}
          </button>
        }
      </div>
      @if (locked(); as text) {
        <p class="nebentext" [id]="id('lock')">{{ text }}</p>
      }
      @if (details()) {
        @if (current().comment; as k) {
          <p class="nebentext kommentar">{{ texts.yourComment(k) }}</p>
        }
        @if (current().enteredBy; as from) {
          <p class="nebentext">{{ texts.enteredBy(from.firstName + ' ' + from.lastName) }}</p>
        }
        @if (current().lateCancellation) {
          <p><span class="chip" data-art="kurzfristig">{{ texts.kurzfristig }}</span></p>
        }
      }
      @if (message(); as m) {
        <p class="nebentext rueckmeldung" role="status">{{ m }}</p>
      }
      @if (error(); as m) {
        <p class="meldung-fehler" role="alert">{{ m }}</p>
      }
    </div>

    <dialog
      #maybeDialog
      class="karte dialog"
      aria-modal="true"
      [attr.aria-labelledby]="id('vielleicht-titel')"
      [attr.aria-describedby]="id('vielleicht-text')"
      (close)="dialogClosedHandler()"
    >
      <form class="formular" (submit)="saveMaybe($event)" novalidate>
        <h2 [id]="id('vielleicht-titel')">{{ texts.maybeTitle }}</h2>
        <p class="nebentext" [id]="id('vielleicht-text')">{{ texts.maybeText }}</p>
        <div class="feld">
          <label [for]="id('comment')">{{ texts.comment }}</label>
          <input
            #commentField
            type="text"
            maxlength="200"
            autocomplete="off"
            [id]="id('comment')"
            [value]="comment()"
            (input)="comment.set($any($event.target).value)"
            [attr.aria-invalid]="commentError() ? 'true' : null"
            [attr.aria-describedby]="commentError() ? id('kommentar-fehler') : null"
          />
          @if (commentError(); as m) {
            <p class="feldfehler" [id]="id('kommentar-fehler')">{{ m }}</p>
          }
        </div>
        <div class="dialog-knoepfe">
          <button
            type="submit"
            class="button button-primaer button-klein"
            [disabled]="running() !== null"
            [attr.aria-busy]="running() === 'MAYBE' ? 'true' : null"
          >
            {{ texts.save }}
          </button>
          <button
            type="button"
            class="button button-sekundaer button-klein"
            [disabled]="running() !== null"
            (click)="close(maybeDialog)"
          >
            {{ texts.cancel }}
          </button>
        </div>
      </form>
    </dialog>

    <dialog
      #reasonDialog
      class="karte dialog"
      aria-modal="true"
      [attr.aria-labelledby]="id('grund-titel')"
      [attr.aria-describedby]="id('grund-text')"
      (close)="dialogClosedHandler()"
    >
      <form class="formular" (submit)="saveReason($event)" novalidate>
        <h2 [id]="id('grund-titel')">{{ texts.declinedTitle }}</h2>
        <p class="nebentext" [id]="id('grund-text')">{{ texts.declinedText }}</p>
        <div class="feld">
          <label [for]="id('reason')">{{ texts.reason }}</label>
          <input
            #reasonField
            type="text"
            maxlength="200"
            autocomplete="off"
            [id]="id('reason')"
            [value]="comment()"
            (input)="comment.set($any($event.target).value)"
            [attr.aria-invalid]="commentError() ? 'true' : null"
            [attr.aria-describedby]="commentError() ? id('grund-fehler') : null"
          />
          @if (commentError(); as m) {
            <p class="feldfehler" [id]="id('grund-fehler')">{{ m }}</p>
          }
        </div>
        <div class="dialog-knoepfe">
          <button
            type="submit"
            class="button button-primaer button-klein"
            [disabled]="running() !== null"
            [attr.aria-busy]="running() === 'DECLINED' ? 'true' : null"
          >
            {{ texts.saveReason }}
          </button>
          <button
            type="button"
            class="button button-sekundaer button-klein"
            [disabled]="running() !== null"
            (click)="close(reasonDialog)"
          >
            {{ texts.skip }}
          </button>
        </div>
      </form>
    </dialog>
  `,
  styleUrl: './response.css',
})
export class MyResponse {
  private readonly responses = inject(ResponseService);
  protected readonly texts = TEXTS.response;
  protected readonly icon = RESPONSE_ICON;
  protected readonly key = responseKey;
  protected readonly choices: Choice[] = ['ATTENDING', 'DECLINED', 'MAYBE'];

  readonly eventId = input.required<string>();
  readonly response = input.required<EventResponse>();
  /** Name der Gruppe, z.B. «Meine Antwort für Training Di 17.11. 18:30». */
  readonly name = input<string>(TEXTS.response.myResponse);
  /** Erklärung, warum nicht geantwortet werden kann (abgesagt, vorbei). Gesetzt: Buttons deaktiviert. */
  readonly locked = input<string | null>(null);
  /** Eigenen Kommentar, «eingetragen von» und «kurzfristig» anzeigen (Detail eines Termins). */
  readonly details = input(false);

  /** Nach jedem Speichern, damit die Seite Zähler und Übersicht nachlädt. */
  readonly changed = output<EventResponse>();

  protected readonly current = linkedSignal(() => this.response());
  protected readonly running = signal<Choice | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly comment = signal('');
  protected readonly commentError = signal<string | null>(null);

  private readonly maybeDialog = viewChild<ElementRef<HTMLDialogElement>>('maybeDialog');
  private readonly reasonDialog = viewChild<ElementRef<HTMLDialogElement>>('reasonDialog');

  private readonly prefix = computed(() => `antwort-${this.eventId()}`);

  protected id(part: string): string {
    return `${this.prefix()}-${part}`;
  }

  protected async choose(w: Choice): Promise<void> {
    if (this.locked() || this.running()) return;
    const previous = this.current();
    this.commentError.set(null);
    if (w === 'MAYBE') {
      this.comment.set(previous.status === 'MAYBE' ? (previous.comment ?? '') : '');
      this.open(this.maybeDialog());
      return;
    }
    if (w === 'DECLINED' && previous.status === 'DECLINED') {
      this.comment.set(previous.comment ?? '');
      this.open(this.reasonDialog());
      return;
    }
    // Ein Tap: sofort speichern (ANM-08). Bei «nicht dabei» danach den freiwilligen Grund anbieten (ANM-05).
    if (await this.save({ status: w })) {
      if (w === 'DECLINED') {
        this.comment.set('');
        this.open(this.reasonDialog());
      }
    }
  }

  protected async saveMaybe(domEvent: Event): Promise<void> {
    domEvent.preventDefault();
    const comment = this.comment().trim();
    if (!comment) {
      this.commentError.set(TEXTS.response.commentRequired);
      this.focus('comment');
      return;
    }
    if (await this.save({ status: 'MAYBE', comment: comment })) {
      this.maybeDialog()?.nativeElement.close();
    } else {
      this.focus('comment');
    }
  }

  protected async saveReason(domEvent: Event): Promise<void> {
    domEvent.preventDefault();
    const comment = this.comment().trim();
    if (await this.save({ status: 'DECLINED', comment: comment || null }, true)) {
      this.reasonDialog()?.nativeElement.close();
    } else {
      this.focus('reason');
    }
  }

  protected close(dialog: HTMLDialogElement): void {
    dialog.close();
  }

  protected dialogClosedHandler(): void {
    this.commentError.set(null);
  }

  private open(dialog: ElementRef<HTMLDialogElement> | undefined): void {
    const d = dialog?.nativeElement;
    if (d && !d.open) d.showModal();
  }

  private focus(part: string): void {
    queueMicrotask(() => document.getElementById(this.id(part))?.focus());
  }

  /** Speichert und meldet das Ergebnis. Fehler beim Kommentar erscheinen beim Feld (ANM-04). */
  private async save(input: ResponseInput, reasonOnly = false): Promise<boolean> {
    this.running.set(input.status);
    this.message.set(null);
    this.error.set(null);
    this.commentError.set(null);
    try {
      const updated = await this.responses.set(this.eventId(), input);
      this.current.set(updated);
      this.message.set(
        reasonOnly
          ? TEXTS.response.reasonSaved
          : TEXTS.response.saved(TEXTS.response.status[updated.status as ResponseStatus]),
      );
      this.changed.emit(updated);
      return true;
    } catch (e) {
      const fields = fieldErrorsOf(e);
      if (fields['comment']) {
        this.commentError.set(fields['comment']);
      } else {
        // Dialog schliessen, damit die Meldung sichtbar ist (z.B. Termin inzwischen abgesagt, 409).
        this.error.set(errorText(e));
        this.maybeDialog()?.nativeElement.close();
        this.reasonDialog()?.nativeElement.close();
      }
      return false;
    } finally {
      this.running.set(null);
    }
  }
}
