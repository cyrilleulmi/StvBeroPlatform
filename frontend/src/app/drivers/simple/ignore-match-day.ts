import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import type { DriverPlanMatchDay } from '../../api/types';
import { Icon } from '../../core/icon';
import { TEXTS } from '../../core/texts';
import { PlanStore } from './plan-store';

/**
 * «Spieltag auslassen» and «Wieder einbeziehen» below the grid (DRV-35): a match day left out of the driver list
 * counts no trips and «Fair verteilen» skips it. Members only read the hint; coaches and admins switch it.
 */
@Component({
  selector: 'app-ignore-match-day',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  template: `
    @if (matchDay().ignored) {
      <div class="row" data-testid="plan-ignored">
        <p class="empty">{{ t.ignoredText }}</p>
        @if (store.canManage()) {
          <button
            type="button"
            class="button button-secondary button-small"
            data-testid="plan-include"
            [disabled]="store.busy()"
            (click)="store.setIgnored(matchDay(), false)"
          >
            <app-icon name="check" [size]="16" />
            {{ t.include }}
          </button>
        }
      </div>
    } @else if (store.canManage()) {
      <div class="row small">
        <button
          type="button"
          class="link"
          data-testid="plan-ignore"
          [disabled]="store.busy()"
          (click)="store.setIgnored(matchDay(), true)"
        >
          {{ t.ignore }}
        </button>
        <span class="secondary-text">{{ t.ignoreHint }}</span>
      </div>
    }
  `,
  styles: `
    .row {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 8px 12px;
    }
    .small {
      margin-top: 4px;
      font-size: 14px;
    }
    .link {
      border: 0;
      padding: 0;
      min-height: var(--touch);
      background: none;
      color: var(--primary);
      font: 700 14px/1.3 var(--font);
      text-decoration: underline;
      cursor: pointer;
    }
  `,
})
export class IgnoreMatchDay {
  protected readonly store = inject(PlanStore);
  protected readonly t = TEXTS.simplePlan;
  readonly matchDay = input.required<DriverPlanMatchDay>();
}
