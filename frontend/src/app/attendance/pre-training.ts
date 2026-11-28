import { ChangeDetectionStrategy, Component, computed, effect, inject, input } from '@angular/core';
import { Icon } from '../core/icon';
import { TEXTS } from '../core/texts';
import { PreTrainingDesigns } from './pre-training-designs';
import { durationText, preTrainingView, WithPreTraining } from './pre-training-model';

/**
 * When an event takes place, with its pre-training if it has one (TER-31): «Do 03.12. · 20:20–21:50», and for a
 * practice with a pre-training the design proposal that is chosen (E-118; PROTOTYPE, see `PreTrainingDesigns`).
 *
 * `place="card"` stands in for the time line of a card of `/termine` and the start page; an event without a
 * pre-training reads exactly as before. `place="page"` is the block below the title of the event page; the host
 * mounts it only for an event with a pre-training. Every design has one element `data-testid="pre-training"` that
 * contains the title and the start («Joggen», «20:00»).
 */
@Component({
  selector: 'app-pre-training',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  host: {
    '[attr.data-design]': 'view() ? design() : null',
    '[attr.data-tag]': 'tag() ?? null',
  },
  templateUrl: './pre-training.html',
  styleUrl: './pre-training.css',
})
export class PreTrainingDisplay {
  private readonly designs = inject(PreTrainingDesigns);
  protected readonly t = TEXTS.preTraining;

  readonly event = input.required<WithPreTraining & { title?: string }>();
  /** «Do 03.12.» */
  readonly day = input('');
  /** «20:20–21:50» */
  readonly period = input('');
  readonly place = input<'card' | 'page'>('card');
  /** Colour of the kind of event (VAR-04) where no card around sets it. */
  readonly tag = input<string | null>();

  protected readonly design = this.designs.design;
  protected readonly view = computed(() => preTrainingView(this.event()));
  protected readonly name = computed(() => this.event().title || this.t.practice);
  protected readonly lead = computed(() => durationText(this.view()?.leadMinutes ?? 0));
  protected readonly practice = computed(() => durationText(this.view()?.practiceMinutes ?? 0));

  constructor() {
    // The switch bar of the proposals exists while a pre-training is on the page
    effect((onCleanup) => {
      if (this.view()) onCleanup(this.designs.show());
    });
  }
}
