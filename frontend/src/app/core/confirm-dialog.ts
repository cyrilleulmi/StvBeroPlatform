import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  ElementRef,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { showModal } from './dialog';
import { TEXTS } from './texts';

/** What the dialog asks, and what happens on «yes». */
export interface Confirmation {
  title: string;
  text: string;
  confirm: string;
  action: () => Promise<unknown>;
}

let next = 0;

/**
 * Asks before something that cannot be undone with one tap, e.g. removing a driver whose car has passengers
 * (DRV-02) or every planned driver (DRV-33). The caller opens it with {@link ask}.
 */
@Component({
  selector: 'app-confirm-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dialog
      #dialog
      class="card dialog"
      aria-modal="true"
      [attr.aria-labelledby]="id + '-title'"
      [attr.aria-describedby]="id + '-text'"
      [attr.data-testid]="testId()"
      (close)="question.set(null)"
    >
      @if (question(); as q) {
        <h2 [id]="id + '-title'">{{ q.title }}</h2>
        <p [id]="id + '-text'">{{ q.text }}</p>
        <div class="dialog-buttons">
          <button
            type="button"
            class="button button-primary button-small"
            [attr.data-testid]="testId() + '-confirm'"
            [disabled]="running()"
            [attr.aria-busy]="running() ? 'true' : null"
            (click)="confirm(q)"
          >
            {{ q.confirm }}
          </button>
          <button type="button" class="button button-secondary button-small" [disabled]="running()" (click)="close()">
            {{ texts.common.cancel }}
          </button>
        </div>
      }
    </dialog>
  `,
  styles: `
    .dialog {
      max-width: min(440px, calc(100vw - 32px));
      color: var(--text);
    }
    .dialog:not([open]) {
      display: none;
    }
    .dialog[open] {
      display: grid;
      gap: 12px;
    }
    h2,
    p {
      margin: 0;
    }
    h2 {
      font: 800 20px/1.25 var(--font-display, var(--font));
    }
    .dialog::backdrop {
      background: color-mix(in srgb, var(--bg) 80%, transparent);
    }
    .dialog-buttons {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
    }
  `,
})
export class ConfirmDialog {
  protected readonly texts = TEXTS;
  protected readonly id = `confirm-${++next}`;
  readonly testId = input('confirm-dialog');

  private readonly changes = inject(ChangeDetectorRef);
  protected readonly question = signal<Confirmation | null>(null);
  protected readonly running = signal(false);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  ask(question: Confirmation): void {
    this.question.set(question);
    showModal(this.dialog().nativeElement, this.changes);
  }

  protected close(): void {
    this.dialog().nativeElement.close();
  }

  protected async confirm(q: Confirmation): Promise<void> {
    this.running.set(true);
    try {
      await q.action();
    } finally {
      this.running.set(false);
      this.close();
    }
  }
}
