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
 * Response buttons of the logged-in person (`my-response`): «dabei» and «nicht dabei» with one tap
 * (ANM-08), «nicht dabei» then offers an optional reason (ANM-05), «vielleicht» requires a
 * comment (ANM-04). Disabled for cancelled or past events, with an explanation (TER-06).
 */
@Component({
  selector: 'app-my-response',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  template: `
    <div
      class="response"
      role="group"
      data-testid="my-response"
      [attr.aria-label]="name()"
      [attr.data-response]="current().status"
    >
      <div class="buttons">
        @for (w of choices; track w) {
          <button
            type="button"
            class="response-button"
            [attr.data-kind]="key(w)"
            [attr.data-testid]="'response-' + key(w)"
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
        <p class="secondary-text" [id]="id('lock')">{{ text }}</p>
      }
      @if (details()) {
        @if (current().comment; as k) {
          <p class="secondary-text comment">{{ texts.yourComment(k) }}</p>
        }
        @if (current().enteredBy; as from) {
          <p class="secondary-text">{{ texts.enteredBy(from.firstName + ' ' + from.lastName) }}</p>
        }
        @if (current().lateCancellation) {
          <p><span class="chip" data-kind="late-cancellation">{{ texts.lateCancellation }}</span></p>
        }
      }
      @if (message(); as m) {
        <p class="secondary-text feedback" role="status">{{ m }}</p>
      }
      @if (error(); as m) {
        <p class="message-error" role="alert">{{ m }}</p>
      }
    </div>

    <dialog
      #maybeDialog
      class="card dialog"
      aria-modal="true"
      [attr.aria-labelledby]="id('maybe-title')"
      [attr.aria-describedby]="id('maybe-text')"
      (close)="onDialogClose()"
    >
      <form class="form" (submit)="saveMaybe($event)" novalidate>
        <h2 [id]="id('maybe-title')">{{ texts.maybeTitle }}</h2>
        <p class="secondary-text" [id]="id('maybe-text')">{{ texts.maybeText }}</p>
        <div class="field">
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
            [attr.aria-describedby]="commentError() ? id('comment-error') : null"
          />
          @if (commentError(); as m) {
            <p class="field-error" [id]="id('comment-error')">{{ m }}</p>
          }
        </div>
        <div class="dialog-buttons">
          <button
            type="submit"
            class="button button-primary button-small"
            [disabled]="running() !== null"
            [attr.aria-busy]="running() === 'MAYBE' ? 'true' : null"
          >
            {{ texts.save }}
          </button>
          <button
            type="button"
            class="button button-secondary button-small"
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
      class="card dialog"
      aria-modal="true"
      [attr.aria-labelledby]="id('reason-title')"
      [attr.aria-describedby]="id('reason-text')"
      (close)="onDialogClose()"
    >
      <form class="form" (submit)="saveReason($event)" novalidate>
        <h2 [id]="id('reason-title')">{{ texts.declinedTitle }}</h2>
        <p class="secondary-text" [id]="id('reason-text')">{{ texts.declinedText }}</p>
        <div class="field">
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
            [attr.aria-describedby]="commentError() ? id('reason-error') : null"
          />
          @if (commentError(); as m) {
            <p class="field-error" [id]="id('reason-error')">{{ m }}</p>
          }
        </div>
        <div class="dialog-buttons">
          <button
            type="submit"
            class="button button-primary button-small"
            [disabled]="running() !== null"
            [attr.aria-busy]="running() === 'DECLINED' ? 'true' : null"
          >
            {{ texts.saveReason }}
          </button>
          <button
            type="button"
            class="button button-secondary button-small"
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
  /** Name of the group, e.g. «Meine Antwort für Training Di 17.11. 18:30». */
  readonly name = input<string>(TEXTS.response.myResponse);
  /** Explanation why responding is not possible (cancelled, past). If set: buttons disabled. */
  readonly locked = input<string | null>(null);
  /** Show own comment, «eingetragen von» and «kurzfristig» (detail of an event). */
  readonly details = input(false);

  /** After every save, so that the page reloads counters and overview. */
  readonly changed = output<EventResponse>();

  protected readonly current = linkedSignal(() => this.response());
  protected readonly running = signal<Choice | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly comment = signal('');
  protected readonly commentError = signal<string | null>(null);

  private readonly maybeDialog = viewChild<ElementRef<HTMLDialogElement>>('maybeDialog');
  private readonly reasonDialog = viewChild<ElementRef<HTMLDialogElement>>('reasonDialog');

  private readonly prefix = computed(() => `response-${this.eventId()}`);

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
    // One tap: save right away (ANM-08). For «nicht dabei» then offer the optional reason (ANM-05).
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

  protected onDialogClose(): void {
    this.commentError.set(null);
  }

  private open(dialog: ElementRef<HTMLDialogElement> | undefined): void {
    const d = dialog?.nativeElement;
    if (d && !d.open) d.showModal();
  }

  private focus(part: string): void {
    queueMicrotask(() => document.getElementById(this.id(part))?.focus());
  }

  /** Saves and reports the result. Errors on the comment appear at the field (ANM-04). */
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
        // Close the dialog so that the message is visible (e.g. event cancelled in the meantime, 409).
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
