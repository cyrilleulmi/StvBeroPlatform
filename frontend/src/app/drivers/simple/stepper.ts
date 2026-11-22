import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { TEXTS } from '../../core/texts';

/** A number with «−» and «+»: quick to change with the thumb, no keyboard. */
@Component({
  selector: 'app-stepper',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="stepper" role="group" [attr.aria-label]="label()">
      <button
        type="button"
        class="step"
        [disabled]="disabled() || value() <= min()"
        [attr.aria-label]="t.fewer(label())"
        (click)="changed.emit(value() - 1)"
      >
        −
      </button>
      <output class="value" [attr.data-testid]="testId()">{{ value() }}</output>
      <button
        type="button"
        class="step"
        [disabled]="disabled() || value() >= max()"
        [attr.aria-label]="t.more(label())"
        (click)="changed.emit(value() + 1)"
      >
        +
      </button>
    </span>
  `,
  styles: `
    .stepper {
      display: inline-flex;
      align-items: center;
      border: 1.5px solid var(--control-border);
      border-radius: var(--radius-control);
      background: var(--surface);
      overflow: hidden;
    }
    .step {
      width: var(--touch);
      height: var(--touch);
      border: 0;
      background: transparent;
      color: var(--text);
      font: 700 20px/1 var(--font);
      cursor: pointer;
    }
    .step:disabled {
      opacity: 0.4;
      cursor: default;
    }
    .value {
      min-width: 34px;
      text-align: center;
      font: 700 18px/1 var(--font);
      font-variant-numeric: tabular-nums;
    }
  `,
})
export class Stepper {
  protected readonly t = TEXTS.simplePlan;
  readonly value = input.required<number>();
  readonly label = input.required<string>();
  readonly min = input(1);
  readonly max = input(60);
  readonly disabled = input(false);
  readonly testId = input<string | null>(null);
  readonly changed = output<number>();
}
