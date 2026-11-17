import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import type { PlayerStatistics, StatisticsAverage } from '../api/types';
import { name } from '../core/format';
import { TEXTS } from '../core/texts';
import { COLUMNS, averageValue, playerValue } from './statistics-common';

/**
 * Table of one group (line, goalies, «Ohne Linie») or of the team average (STA-03 to STA-05). On a
 * phone it scrolls sideways inside the card, the name column stays visible (STA-10).
 */
@Component({
  selector: 'app-statistics-table',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <div class="scroll" tabindex="0" role="region" [attr.aria-label]="label()">
      <table [attr.aria-label]="label()">
        <thead>
          <tr>
            <th
              scope="col"
              class="name"
              [attr.aria-sort]="sortColumn() === 'name' ? 'ascending' : null"
            >
              {{ t.column.name }}
            </th>
            @for (c of columns; track c) {
              <th scope="col" [attr.aria-sort]="sortedBy(c)">{{ t.column[c] }}</th>
            }
          </tr>
        </thead>
        <tbody>
          @for (p of rows(); track p.person.id) {
            <tr [attr.data-testid]="'statistics-person-' + p.person.id">
              <th scope="row" class="name">
                <a [routerLink]="['/statistik', p.person.id]" queryParamsHandling="preserve">{{
                  name(p.person)
                }}</a>
              </th>
              @for (c of columns; track c) {
                <td [attr.data-column]="c">{{ value(p, c) }}</td>
              }
            </tr>
          }
          @if (average(); as a) {
            <tr data-testid="statistics-average-row">
              <th scope="row" class="name">{{ t.average }}</th>
              @for (c of columns; track c) {
                <td [attr.data-column]="c">{{ averageValue(a, c) }}</td>
              }
            </tr>
          }
        </tbody>
      </table>
    </div>
  `,
  styles: `
    .scroll {
      overflow-x: auto;
      margin: 0 -4px;
      border-radius: 12px;
    }
    .scroll:focus-visible {
      outline: 2px solid var(--primary);
      outline-offset: 2px;
    }
    table {
      border-collapse: separate;
      border-spacing: 0;
      width: 100%;
      font-variant-numeric: tabular-nums;
    }
    th,
    td {
      padding: 10px 8px;
      text-align: right;
      white-space: nowrap;
      border-bottom: 1px solid var(--border);
    }
    thead th {
      font-size: 12px;
      font-weight: 700;
      color: var(--muted);
      vertical-align: bottom;
      white-space: normal;
      min-width: 64px;
    }
    .name {
      position: sticky;
      left: 0;
      z-index: 1;
      text-align: left;
      background: var(--surface);
      min-width: 120px;
      max-width: 160px;
      white-space: normal;
    }
    tbody .name {
      font-weight: 600;
    }
    tbody .name a {
      color: var(--text);
      display: inline-block;
      min-height: 24px;
    }
    tbody tr:last-child th,
    tbody tr:last-child td {
      border-bottom: 0;
    }
    [data-testid='statistics-average-row'] th,
    [data-testid='statistics-average-row'] td {
      font-weight: 700;
    }
  `,
})
export class StatisticsTable {
  protected readonly t = TEXTS.statistics;
  protected readonly columns = COLUMNS;
  protected readonly name = name;
  protected readonly value = playerValue;
  protected readonly averageValue = averageValue;

  readonly label = input.required<string>();
  readonly rows = input<readonly PlayerStatistics[]>([]);
  readonly average = input<StatisticsAverage | null>(null);
  /** Column the rows are sorted by (for `aria-sort`), otherwise null. */
  readonly sortColumn = input<string | null>(null);

  protected sortedBy(c: string): 'descending' | null {
    return this.sortColumn() === c ? 'descending' : null;
  }
}
