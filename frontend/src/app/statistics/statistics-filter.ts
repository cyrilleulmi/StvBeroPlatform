import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { Router } from '@angular/router';
import type { StatisticsTeam } from '../api/types';
import { TEXTS } from '../core/texts';

/**
 * Filters of the statistics (STA-02): team, period (from/to, default the whole season) and event type.
 * Every change goes into the URL (`?team=`, `?von=`, `?bis=`, `?art=`), the screen loads from there.
 */
@Component({
  selector: 'app-statistics-filter',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section
      class="card filter"
      aria-labelledby="statistics-filter-title"
      data-testid="statistics-filter"
    >
      <h2 id="statistics-filter-title" class="visually-hidden">{{ t.filter }}</h2>
      @if (teams().length > 0) {
        <div class="field wide">
          <label for="statistics-team">{{ t.team }}</label>
          <select id="statistics-team" (change)="set({ team: $any($event.target).value || null })">
            @for (team of teams(); track team.id) {
              <option [value]="team.id" [selected]="team.id === teamId()">{{ team.name }}</option>
            }
          </select>
        </div>
      }
      <div class="field">
        <label for="statistics-from">{{ t.from }}</label>
        <input
          id="statistics-from"
          type="date"
          [value]="from()"
          [min]="seasonStart()"
          [max]="seasonEnd()"
          (change)="setDate('von', $any($event.target).value)"
        />
      </div>
      <div class="field">
        <label for="statistics-to">{{ t.to }}</label>
        <input
          id="statistics-to"
          type="date"
          [value]="to()"
          [min]="seasonStart()"
          [max]="seasonEnd()"
          (change)="setDate('bis', $any($event.target).value)"
        />
      </div>
      <div class="field wide">
        <label for="statistics-type">{{ t.type }}</label>
        <select id="statistics-type" (change)="set({ art: $any($event.target).value || null })">
          <option value="" [selected]="!type()">{{ t.allTypes }}</option>
          <option value="training" [selected]="type() === 'training'">{{ t.practices }}</option>
          <option value="spieltag" [selected]="type() === 'spieltag'">{{ t.matchDays }}</option>
        </select>
      </div>
      @if (customPeriod()) {
        <button
          type="button"
          class="button button-secondary button-small"
          (click)="set({ von: null, bis: null })"
        >
          {{ t.wholeSeason }}
        </button>
      }
    </section>
  `,
  styles: `
    .filter {
      display: grid;
      grid-template-columns: minmax(0, 1fr);
      gap: 12px 16px;
      align-items: end;
    }
    .filter .button {
      justify-self: start;
    }
    .field input,
    .field select {
      min-width: 0;
      width: 100%;
    }
    @media (min-width: 640px) {
      .filter {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }
    }
    @media (min-width: 1024px) {
      .filter {
        grid-template-columns: repeat(4, minmax(0, 1fr)) auto;
      }
    }
  `,
})
export class StatisticsFilter {
  private readonly router = inject(Router);
  protected readonly t = TEXTS.statistics;

  readonly teams = input<readonly StatisticsTeam[]>([]);
  readonly teamId = input<string | null | undefined>(null);
  readonly from = input.required<string>();
  readonly to = input.required<string>();
  readonly type = input<string | undefined>(undefined);
  readonly seasonStart = input.required<string>();
  readonly seasonEnd = input.required<string>();
  /** A period other than the whole season is chosen (`?von=` or `?bis=` in the URL). */
  readonly customPeriod = input(false);

  protected set(params: Record<string, string | null>): void {
    void this.router.navigate([], { queryParams: params, queryParamsHandling: 'merge' });
  }

  /** Only complete dates; an emptied field goes back to the season default. */
  protected setDate(param: 'von' | 'bis', value: string): void {
    this.set({ [param]: /^\d{4}-\d{2}-\d{2}$/.test(value) ? value : null });
  }
}
