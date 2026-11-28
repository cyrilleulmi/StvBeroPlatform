import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import type { PreTraining, ResponseInput, ResponseStatus } from '../api/types';
import { Icon } from '../core/icon';
import { TEXTS } from '../core/texts';
import { clockTime } from '../core/time';
import { RESPONSE_ICON } from './response';

/** What a person can say to a pre-training: «dabei» or «nicht dabei» (ANM-14). */
export type PreTrainingChoice = NonNullable<ResponseInput['preTraining']>;

export const PRE_TRAINING_CHOICES: PreTrainingChoice[] = ['ATTENDING', 'DECLINED'];

/** Key for `data-kind` and test IDs, e.g. `declined`. */
const KEY: Record<PreTrainingChoice, string> = { ATTENDING: 'attending', DECLINED: 'declined' };

/** What the request says about the pre-training: only with «dabei» and only once it is answered (ANM-15). */
export function preTrainingInput(
  practice: ResponseInput['status'],
  choice: PreTrainingChoice | null,
): Pick<ResponseInput, 'preTraining'> {
  return practice === 'ATTENDING' && choice ? { preTraining: choice } : {};
}

/** The answer that is given, `null` while the pre-training is still open or follows the practice. */
export function preTrainingChoice(status: ResponseStatus | undefined): PreTrainingChoice | null {
  return status === 'ATTENDING' || status === 'DECLINED' ? status : null;
}

/**
 * The second question of a practice with a pre-training (ANM-14): someone who is «dabei» says whether she is at the
 * pre-training too. Sits right below the response buttons; the host saves the answer. Until it is given the block
 * asks («Auch beim Vortraining?»), afterwards it shows the answer and lets her change it with one tap.
 */
@Component({
  selector: 'app-pre-training-response',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  template: `
    <div
      class="pre"
      role="group"
      data-testid="pre-training-response"
      [attr.aria-label]="t.group"
      [attr.data-response]="status()"
    >
      <p class="pre-head">
        <app-icon name="timer" [size]="16" />
        <span class="pre-ask" data-testid="pre-training-question">{{
          status() === 'PENDING' ? t.question : t.label
        }}</span>
        @if (what(); as w) {
          <span class="pre-what">{{ w }}</span>
        }
      </p>
      <div class="pre-buttons">
        @for (w of choices; track w) {
          <button
            type="button"
            class="response-button"
            [attr.data-kind]="key[w]"
            [attr.data-testid]="'pre-training-response-' + key[w]"
            [attr.aria-pressed]="status() === w ? 'true' : 'false'"
            [disabled]="disabled()"
            (click)="chosen.emit(w)"
          >
            <app-icon [name]="icon[w]" [size]="16" />
            {{ t.button[w] }}
          </button>
        }
      </div>
    </div>
  `,
  styleUrl: './response.css',
})
export class PreTrainingResponse {
  protected readonly t = TEXTS.preTrainingResponse;
  protected readonly icon = RESPONSE_ICON;
  protected readonly key = KEY;
  protected readonly choices = PRE_TRAINING_CHOICES;

  /** The response to the pre-training: `ATTENDING`, `DECLINED` or `PENDING`. */
  readonly status = input.required<ResponseStatus>();
  /** Title and start for the line next to the question; without it only «Vortraining». */
  readonly preTraining = input<PreTraining | null>(null);
  readonly disabled = input(false);

  readonly chosen = output<PreTrainingChoice>();

  /** «20:00 · Joggen» */
  protected readonly what = computed(() => {
    const p = this.preTraining();
    return p ? TEXTS.preTraining.fact(clockTime(p.start), p.title) : null;
  });
}
