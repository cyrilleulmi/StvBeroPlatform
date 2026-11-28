import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { TEXTS } from '../core/texts';
import { PRE_TRAINING_TITLE_MAX, suggestedTime } from './pre-training-form';

/**
 * The fields of a pre-training («Vortraining», TER-32): the switch «Mit Vortraining» and, when it is on,
 * «Was» and «Beginn». Turning it on suggests the start 15 minutes before the practice. The ids are
 * `<idPrefix>-preTraining`, `-preTrainingTitle` and `-preTrainingTime` (errors: `…-error`), so that
 * several forms on one page do not clash and the form can focus a field by its name.
 */
@Component({
  selector: 'app-pre-training-fields',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="field">
      <label class="checkbox">
        <input
          type="checkbox"
          data-testid="pre-training-toggle"
          [id]="idPrefix() + '-preTraining'"
          [name]="idPrefix() + '-preTraining'"
          [checked]="enabled()"
          [disabled]="disabled()"
          [attr.aria-describedby]="idPrefix() + '-preTraining-hint'"
          (change)="toggle($any($event.target).checked)"
        />
        {{ t.toggle }}
      </label>
      <p class="secondary-text" [id]="idPrefix() + '-preTraining-hint'">{{ t.hint }}</p>
    </div>
    @if (enabled()) {
      <div class="field-row">
        <div class="field">
          <label [for]="idPrefix() + '-preTrainingTitle'">{{ t.what }}</label>
          <input
            type="text"
            data-testid="pre-training-title"
            [id]="idPrefix() + '-preTrainingTitle'"
            [name]="idPrefix() + '-preTrainingTitle'"
            [attr.maxlength]="titleMax"
            autocomplete="off"
            [placeholder]="t.whatPlaceholder"
            [value]="title()"
            [disabled]="disabled()"
            (input)="titleChange.emit($any($event.target).value)"
            [attr.aria-invalid]="titleError() ? 'true' : null"
            [attr.aria-describedby]="titleError() ? idPrefix() + '-preTrainingTitle-error' : null"
          />
          @if (titleError(); as m) {
            <p class="field-error" [id]="idPrefix() + '-preTrainingTitle-error'">{{ m }}</p>
          }
        </div>
        <div class="field">
          <label [for]="idPrefix() + '-preTrainingTime'">{{ t.start }}</label>
          <input
            type="time"
            data-testid="pre-training-time"
            [id]="idPrefix() + '-preTrainingTime'"
            [name]="idPrefix() + '-preTrainingTime'"
            [value]="time()"
            [disabled]="disabled()"
            (input)="timeChange.emit($any($event.target).value)"
            [attr.aria-invalid]="timeError() ? 'true' : null"
            [attr.aria-describedby]="timeError() ? idPrefix() + '-preTrainingTime-error' : null"
          />
          @if (timeError(); as m) {
            <p class="field-error" [id]="idPrefix() + '-preTrainingTime-error'">{{ m }}</p>
          }
        </div>
      </div>
    }
  `,
  styles: `
    :host {
      display: grid;
      gap: 12px;
    }
  `,
})
export class PreTrainingFields {
  protected readonly t = TEXTS.preTrainingForm;
  protected readonly titleMax = PRE_TRAINING_TITLE_MAX;

  readonly enabled = input(false);
  readonly title = input('');
  readonly time = input('');
  /** Start of the practice («HH:mm»): the suggestion for the pre-training is 15 minutes before it. */
  readonly practiceStart = input('');
  readonly titleError = input<string | null | undefined>(null);
  readonly timeError = input<string | null | undefined>(null);
  readonly idPrefix = input('event');
  readonly disabled = input(false);

  readonly enabledChange = output<boolean>();
  readonly titleChange = output<string>();
  readonly timeChange = output<string>();

  protected toggle(on: boolean): void {
    this.enabledChange.emit(on);
    if (on && !this.time()) this.timeChange.emit(suggestedTime(this.practiceStart()));
  }
}
