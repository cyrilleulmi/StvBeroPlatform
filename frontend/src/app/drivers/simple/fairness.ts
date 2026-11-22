import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { name } from '../../core/format';
import { Icon } from '../../core/icon';
import { formatDecimal } from '../../core/number';
import { TEXTS } from '../../core/texts';
import { PlanStore, QueueEntry } from './plan-store';
import { Stepper } from './stepper';

/**
 * Who drives how often in the season (DRV-17), as bars: done trips solid, planned ones lighter. «Anpassen» shows
 * who can drive and the places of each car (DRV-18). `order` `queue` lists the next in line first (DRV-31).
 */
@Component({
  selector: 'app-fairness',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, Stepper],
  template: `
    <div class="fair-head">
      <h2 id="fairness-title" class="section-title">{{ title() ?? t.title }}</h2>
      @if (editable()) {
        <button
          type="button"
          class="button button-secondary button-small"
          [attr.aria-expanded]="editing()"
          data-testid="fairness-edit"
          (click)="editing.set(!editing())"
        >
          <app-icon [name]="editing() ? 'check' : 'pencil'" [size]="16" />
          {{ editing() ? t.done : t.edit }}
        </button>
      }
    </div>
    <p class="secondary-text">{{ editing() ? t.editText : (text() ?? t.text) }}</p>
    <ol class="rows" aria-labelledby="fairness-title" data-testid="fairness">
      @for (q of rows(); track q.person.id; let i = $index) {
        <li
          class="fair-row"
          [attr.data-testid]="'fair-' + q.person.id"
          [attr.data-trips]="q.trips"
          [attr.data-can-drive]="q.canDrive"
          [attr.data-me]="q.person.id === store.meId() ? 'true' : null"
        >
          @if (order() === 'queue') {
            <span class="rank" aria-hidden="true">{{ q.canDrive ? i + 1 : '–' }}</span>
          }
          <span class="who">{{ name(q.person) }}</span>
          @if (editing() && q.canChange) {
            <label class="check">
              <input
                type="checkbox"
                [checked]="q.canDrive"
                [disabled]="store.busy()"
                [attr.aria-label]="texts.driving.canDriveFor(name(q.person))"
                (change)="store.setDriving(q.person, $any($event.target).checked, q.carSeats)"
              />
              {{ t.drives }}
            </label>
            @if (q.canDrive) {
              <span class="places">
                <app-stepper
                  [value]="q.carSeats"
                  [min]="2"
                  [max]="9"
                  [label]="texts.driving.seatsFor(name(q.person))"
                  [disabled]="store.busy()"
                  (changed)="store.setDriving(q.person, true, $event)"
                />
                <span class="secondary-text">{{ t.places }}</span>
              </span>
            }
          } @else if (!q.canDrive) {
            <span class="secondary-text number">{{ texts.driving.no }}</span>
          } @else {
            <span class="bar" aria-hidden="true">
              @for (n of dots(q.done); track n) {
                <span class="dot done"></span>
              }
              @for (n of dots(q.planned); track n) {
                <span class="dot planned"></span>
              }
            </span>
            <span class="number" [attr.title]="q.kilometres > 0 ? t.km(km(q.kilometres)) : null">{{ t.trips(q.trips) }}</span>
          }
        </li>
      }
    </ol>
    @if (!editing()) {
      <p class="legend secondary-text" aria-hidden="true">
        <span class="dot done"></span> {{ t.legendDone }} <span class="dot planned"></span> {{ t.legendPlanned }}
      </p>
    }
  `,
  styles: `
    :host {
      display: grid;
      gap: 8px;
      min-width: 0;
    }
    p {
      margin: 0;
    }
    .fair-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
    }
    .fair-head .section-title {
      margin: 0;
    }
    .rows {
      list-style: none;
      margin: 0;
      padding: 0;
      display: grid;
    }
    .fair-row {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 4px 12px;
      min-height: 40px;
      padding: 6px 0;
      border-bottom: 1px solid var(--border);
    }
    .fair-row[data-me='true'] .who {
      font-weight: 800;
    }
    .fair-row[data-can-drive='false'] .who {
      color: var(--muted);
    }
    .rank {
      width: 22px;
      color: var(--muted);
      font-weight: 700;
      font-variant-numeric: tabular-nums;
    }
    .who {
      flex: 1 1 110px;
      font-weight: 600;
      overflow-wrap: anywhere;
    }
    .bar {
      display: inline-flex;
      flex-wrap: wrap;
      gap: 3px;
      max-width: 82px;
    }
    .dot {
      display: inline-block;
      width: 14px;
      height: 14px;
      border-radius: 4px;
      background: var(--primary);
    }
    .dot.planned {
      background: var(--primary-soft);
      border: 1.5px solid var(--primary);
    }
    .number {
      min-width: 74px;
      text-align: right;
      font-variant-numeric: tabular-nums;
      font-size: 14px;
    }
    .check,
    .places {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      min-height: var(--touch);
      font-weight: 600;
    }
    .check input {
      width: 20px;
      height: 20px;
    }
    .legend {
      display: flex;
      align-items: center;
      gap: 6px;
    }
  `,
})
export class Fairness {
  protected readonly store = inject(PlanStore);
  protected readonly texts = TEXTS;
  protected readonly t = TEXTS.simplePlan.fairness;
  protected readonly name = name;
  protected readonly km = formatDecimal;

  /** `queue`: the next in line first; `name`: by name. */
  readonly order = input<'queue' | 'name'>('name');
  readonly title = input<string | null>(null);
  readonly text = input<string | null>(null);

  protected readonly editing = signal(false);
  protected readonly editable = computed(() => this.store.queue().some((q) => q.canChange));

  protected readonly rows = computed<QueueEntry[]>(() => {
    const queue = this.store.queue();
    if (this.order() === 'queue') return queue;
    const byName = new Map(queue.map((q) => [q.person.id, q]));
    return (this.store.plan()?.drivers ?? []).map((d) => byName.get(d.person.id)!).filter(Boolean);
  });

  protected dots(n: number): number[] {
    return Array.from({ length: n }, (_, i) => i);
  }
}
