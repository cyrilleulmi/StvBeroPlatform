import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { Icon } from '../core/icon';
import { TEXTS } from '../core/texts';
import { Design, neighbour } from './pre-training-model';

/**
 * PROTOTYPE (E-118): the bar that switches between the design proposals of the pre-training, in test mode only.
 * It floats above the page and looks unlike the app on purpose, so that nobody takes it for a part of a design.
 * The arrow keys switch too, unless a field has the focus.
 */
@Component({
  selector: 'app-pre-training-switch',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  host: { '(document:keydown)': 'key($event)' },
  template: `
    <div class="bar" role="group" data-testid="pre-training-switch" [attr.aria-label]="t.switch" [attr.data-design]="design()">
      <button type="button" data-testid="pre-training-previous" [attr.aria-label]="t.previous" (click)="step(-1)">
        <app-icon name="chevron-left" [size]="20" />
      </button>
      <span class="name" aria-live="polite">{{ label() }}</span>
      <button type="button" data-testid="pre-training-next" [attr.aria-label]="t.next" (click)="step(1)">
        <app-icon name="chevron-right" [size]="20" />
      </button>
    </div>
  `,
  styles: `
    :host {
      position: fixed;
      z-index: 50;
      left: 0;
      right: 0;
      bottom: calc(92px + env(safe-area-inset-bottom));
      display: flex;
      justify-content: center;
      pointer-events: none;
    }
    .bar {
      pointer-events: auto;
      display: flex;
      align-items: center;
      gap: 2px;
      padding: 3px;
      border-radius: var(--radius-pill);
      background: var(--text);
      color: var(--bg);
      box-shadow: 0 6px 20px rgb(0 0 0 / 0.28);
      font-size: 14px;
      font-weight: 700;
    }
    .name {
      min-width: 168px;
      padding: 0 6px;
      text-align: center;
      white-space: nowrap;
    }
    button {
      display: grid;
      place-items: center;
      width: var(--touch);
      height: var(--touch);
      border: 0;
      border-radius: var(--radius-pill);
      background: transparent;
      color: inherit;
      cursor: pointer;
    }
    button:focus-visible {
      outline: 2px solid var(--bg);
      outline-offset: -4px;
    }
    @media (hover: hover) {
      button:hover {
        background: color-mix(in srgb, var(--bg) 18%, transparent);
      }
    }
    @media (min-width: 1024px) {
      :host {
        bottom: 20px;
      }
    }
  `,
})
export class PreTrainingSwitch {
  protected readonly t = TEXTS.preTraining;

  readonly design = input.required<Design>();
  readonly chosen = output<Design>();

  protected readonly label = computed(() =>
    this.t.switchLabel(this.design().toUpperCase(), this.t.designs[this.design()]),
  );

  protected step(by: 1 | -1): void {
    this.chosen.emit(neighbour(this.design(), by));
  }

  protected key(event: KeyboardEvent): void {
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
    if (event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return;
    const target = event.target as HTMLElement | null;
    // Fields, choices and dialogs keep their arrow keys
    if (target?.closest('input, textarea, select, [contenteditable], [role="radiogroup"], dialog')) return;
    this.step(event.key === 'ArrowRight' ? 1 : -1);
  }
}
