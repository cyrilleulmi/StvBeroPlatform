import { NgTemplateOutlet } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked, viewChild } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import type { components } from '../../api/schema';
import type { Person, TeamRole } from '../../api/types';
import { ConfirmDialog } from '../../core/confirm-dialog';
import { errorText } from '../../core/error-text';
import { name } from '../../core/format';
import { Icon } from '../../core/icon';
import { TEXTS } from '../../core/texts';

type Overview = components['schemas']['MembershipOverview'];
type TeamColumn = components['schemas']['MembershipTeam'];
type BatchResult = components['schemas']['MembershipBatchResult'];
type GenderKey = 'FEMALE' | 'MALE' | 'NONE';
type GenderFilter = 'ALL' | 'FEMALE' | 'MALE';
type SortBy = 'gender-year' | 'year-gender' | 'name' | 'previous';

/** Women first, then men, then people without a gender (PTF-15). */
const GENDER_ORDER: Record<GenderKey, number> = { FEMALE: 0, MALE: 1, NONE: 2 };

/** One person of the table with what the filters and the columns need, taken from the last load. */
interface ViewRow {
  person: Person;
  name: string;
  gender: GenderKey;
  /** Teams of this season at the last load: the team filter uses them, so a tap never moves a row. */
  teams: Set<string>;
  previous: Set<string>;
  /** «Junioren C» or «Herren 1 (Coach), Junioren C»: the teams of the season before. */
  previousText: string;
  /** «w · 2008 · vorher Junioren C» for narrow screens, where the columns are hidden. */
  meta: string;
}

const key = (personId: string, teamId: string) => `${personId}|${teamId}`;

/**
 * People and teams as one table (PTF-15 to PTF-17): a row per person, a column per team, a tap on a field puts the
 * person into the team. The tap shows at once and is saved in the background, so one can go on tapping; rows never
 * move or disappear while tapping (only filters, sorting and the actions for all shown people change the list).
 * With {@link team} the table shows this one team: the step «Personen» of the assistant «Neues Team». Without it,
 * every team of the season: the page «Teamzuteilung».
 */
@Component({
  selector: 'app-membership-table',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, ConfirmDialog, NgTemplateOutlet],
  template: `
    <div class="membership" data-testid="membership-table">
      <p class="visually-hidden" role="status" data-testid="membership-status">{{ announce() }}</p>
      @if (error(); as m) {
        <p class="message-error" role="alert">{{ m }}</p>
      }
      @if (data(); as d) {
        @if (!d.writable) {
          <p class="message-notice">{{ t.archived }}</p>
        }
        <div class="filters">
          <div class="pills">
          <fieldset class="pill-group">
            <legend>{{ t.filterGender }}</legend>
            <div class="pill-options">
              @for (g of genderOptions; track g.value) {
                <label class="pill-option" [attr.data-testid]="'membership-gender-' + g.value">
                  <input
                    type="radio"
                    name="membership-gender"
                    [value]="g.value"
                    [checked]="gender() === g.value"
                    (change)="gender.set(g.value)"
                  />
                  {{ g.label }}
                </label>
              }
            </div>
          </fieldset>
          @if (d.writable) {
            <fieldset class="pill-group">
              <legend>{{ t.role }}</legend>
              <div class="pill-options">
                @for (r of roleOptions; track r.value) {
                  <label class="pill-option" [attr.data-testid]="'membership-role-' + r.value">
                    <input
                      type="radio"
                      name="membership-role"
                      [value]="r.value"
                      [checked]="role() === r.value"
                      (change)="role.set(r.value)"
                    />
                    {{ r.label }}
                  </label>
                }
              </div>
            </fieldset>
          }
          </div>
          <div class="selects">
            <div class="field">
              <label for="membership-search">{{ t.filterName }}</label>
              <input
                id="membership-search"
                name="membership-search"
                type="search"
                autocomplete="off"
                data-testid="membership-search"
                [value]="search()"
                (input)="search.set($any($event.target).value)"
              />
            </div>
            <div class="field">
              <label for="membership-from">{{ t.filterFrom }}</label>
              <select id="membership-from" name="membership-from" (change)="yearFrom.set(toYear($any($event.target).value))">
                <option value="" [selected]="yearFrom() === null">{{ t.anyYear }}</option>
                @for (y of years(); track y) {
                  <option [value]="y" [selected]="yearFrom() === y">{{ y }}</option>
                }
              </select>
            </div>
            <div class="field">
              <label for="membership-to">{{ t.filterTo }}</label>
              <select id="membership-to" name="membership-to" (change)="yearTo.set(toYear($any($event.target).value))">
                <option value="" [selected]="yearTo() === null">{{ t.anyYear }}</option>
                @for (y of years(); track y) {
                  <option [value]="y" [selected]="yearTo() === y">{{ y }}</option>
                }
              </select>
            </div>
            <div class="field">
              <label for="membership-team-filter">{{ t.filterTeam }}</label>
              <select
                id="membership-team-filter"
                name="membership-team-filter"
                data-testid="membership-team-filter"
                (change)="teamFilter.set($any($event.target).value)"
              >
                <option value="" [selected]="teamFilter() === ''">{{ t.teamAny }}</option>
                <option value="none" [selected]="teamFilter() === 'none'">{{ t.teamNone }}</option>
                <optgroup [label]="t.teamNow">
                  @for (c of seasonTeams(); track c.id) {
                    <option [value]="'now:' + c.id" [selected]="teamFilter() === 'now:' + c.id">{{ c.name }}</option>
                  }
                </optgroup>
                @if (d.previousSeason; as before) {
                  <optgroup [label]="t.teamBefore(before.name)">
                    @for (c of d.previousTeams; track c.id) {
                      <option [value]="'prev:' + c.id" [selected]="teamFilter() === 'prev:' + c.id">{{ c.name }}</option>
                    }
                  </optgroup>
                }
              </select>
            </div>
            <div class="field">
              <label for="membership-sort">{{ t.sort }}</label>
              <select id="membership-sort" name="membership-sort" data-testid="membership-sort" (change)="sortBy.set($any($event.target).value)">
                @for (o of sortOptions(); track o.value) {
                  <option [value]="o.value" [selected]="sortBy() === o.value">{{ o.label }}</option>
                }
              </select>
            </div>
          </div>
        </div>

        @if (!team() && d.teams.length > 1) {
          <details class="columns">
            <summary>{{ t.columns }} ({{ columns().length }}/{{ d.teams.length }})</summary>
            <div class="pill-options">
              @for (c of d.teams; track c.id) {
                <label class="pill-option" [attr.data-testid]="'membership-column-' + c.id">
                  <input type="checkbox" [checked]="!hidden().has(c.id)" (change)="showColumn(c.id, $any($event.target).checked)" />
                  {{ c.name }}
                </label>
              }
            </div>
          </details>
        }

        <div class="bar">
          <p class="shown" data-testid="membership-count">
            {{ t.shown(rows().length, d.people.length) }}
            @if (focus(); as f) {
              · <span data-testid="membership-in-team">{{ t.inTeam(count(f.id).coaches, count(f.id).players) }}</span>
            }
            @if (filtersActive()) {
              · <button type="button" class="link-button" (click)="resetFilters()">{{ t.resetFilters }}</button>
            }
          </p>
          @if (d.writable && rows().length > 0) {
            <div class="bulk">
              @if (focus()) {
                <button
                  type="button"
                  class="button button-secondary button-small"
                  data-testid="membership-bulk-add"
                  [disabled]="busy()"
                  (click)="bulkAdd()"
                >
                  {{ t.bulkAddHere(rows().length) }}
                </button>
              } @else {
                <span class="bulk-text">{{ t.bulk(rows().length) }}</span>
                <div class="field bulk-field">
                <select
                  id="membership-bulk-team"
                  name="membership-bulk-team"
                  data-testid="membership-bulk-team"
                  [attr.aria-label]="t.bulkTeam"
                  (change)="bulkTeam.set($any($event.target).value)"
                >
                  <option value="" [selected]="bulkTeam() === ''">{{ t.bulkChoose }}</option>
                  @for (c of d.teams; track c.id) {
                    <option [value]="c.id" [selected]="bulkTeam() === c.id">{{ c.name }}</option>
                  }
                </select>
                </div>
                <button
                  type="button"
                  class="button button-secondary button-small"
                  data-testid="membership-bulk-add"
                  [disabled]="busy() || bulkTeam() === ''"
                  (click)="bulkAdd()"
                >
                  {{ t.bulkAdd }}
                </button>
                @if (d.canRemove) {
                  <button
                    type="button"
                    class="button button-secondary button-small button-destructive"
                    data-testid="membership-bulk-remove"
                    [disabled]="busy() || bulkTeam() === ''"
                    (click)="bulkRemove()"
                  >
                    {{ t.bulkRemove }}
                  </button>
                }
              }
            </div>
          }
        </div>

        <div class="table-wrap" role="region" tabindex="0" [attr.aria-label]="t.tableLabel">
          <table>
            <thead>
              <tr>
                <th scope="col" class="person corner">{{ t.person }}</th>
                <!-- One team (assistant): its field comes right beside the name, so it never scrolls out of sight -->
                @if (team()) {
                  <ng-container [ngTemplateOutlet]="teamHead" />
                }
                <th scope="col" class="meta num" [attr.title]="t.year">{{ t.yearShort }}</th>
                <th scope="col" class="meta">{{ t.gender }}</th>
                @if (team()) {
                  <th scope="col" class="meta text">{{ t.otherTeams }}</th>
                }
                @if (d.previousSeason; as before) {
                  <th scope="col" class="meta text">{{ t.previous(before.name) }}</th>
                }
                @if (!team()) {
                  <ng-container [ngTemplateOutlet]="teamHead" />
                }
              </tr>
            </thead>
            <tbody>
              @for (r of rows(); track r.person.id) {
                <tr [attr.data-testid]="'membership-row-' + r.person.id">
                  <th scope="row" class="person">
                    <span class="person-name">{{ r.name }}</span>
                    <span class="person-meta">{{ r.meta }}</span>
                  </th>
                  @if (team()) {
                    <ng-container [ngTemplateOutlet]="teamCells" [ngTemplateOutletContext]="{ $implicit: r }" />
                  }
                  <td class="meta num">{{ r.person.birthYear ?? t.none }}</td>
                  <td class="meta">
                    <span aria-hidden="true">{{ t.genderShort[r.gender] }}</span>
                    <span class="visually-hidden">{{ t.genderLong[r.gender] }}</span>
                  </td>
                  @if (focus(); as f) {
                    <td class="meta text">{{ others(r, f.id) }}</td>
                  }
                  @if (d.previousSeason) {
                    <td class="meta text">{{ r.previousText || t.none }}</td>
                  }
                  @if (!team()) {
                    <ng-container [ngTemplateOutlet]="teamCells" [ngTemplateOutletContext]="{ $implicit: r }" />
                  }
                </tr>
              } @empty {
                <tr>
                  <td class="none" [attr.colspan]="columns().length + 5">{{ t.empty }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <ng-template #teamHead>
                @for (c of columns(); track c.id) {
                  <th
                    scope="col"
                    class="team"
                    [attr.data-testid]="'membership-team-' + c.id"
                    [attr.data-players]="count(c.id).players"
                    [attr.data-coaches]="count(c.id).coaches"
                    [attr.data-in-season]="c.inSeason"
                    [attr.title]="c.inSeason ? null : t.newInSeason"
                  >
                    <span class="team-name">{{ c.name }}</span>
                    <span class="team-count" [attr.aria-label]="t.countLabel(c.name, count(c.id).players, count(c.id).coaches)">{{
                      t.count(count(c.id).players, count(c.id).coaches)
                    }}</span>
                  </th>
                }
        </ng-template>
        <ng-template #teamCells let-r>
          @let d = data()!;
                  @for (c of columns(); track c.id) {
                    <td class="cell">
                      <button
                        type="button"
                        class="toggle"
                        [attr.data-role]="cells()[r.person.id + '|' + c.id] ?? 'none'"
                        [attr.aria-pressed]="cells()[r.person.id + '|' + c.id] ? 'true' : 'false'"
                        [attr.aria-label]="label(r, c)"
                        [attr.data-testid]="'membership-cell-' + r.person.id + '-' + c.id"
                        [disabled]="!d.writable"
                        (click)="toggle(r, c)"
                      >
                        @switch (cells()[r.person.id + '|' + c.id]) {
                          @case ('PLAYER') {
                            <app-icon name="check" [size]="18" />
                          }
                          @case ('COACH') {
                            <span aria-hidden="true">{{ t.coachMark }}</span>
                          }
                        }
                      </button>
                    </td>
                  }
        </ng-template>
        <p class="legend secondary-text">
          <span class="toggle small" data-role="PLAYER" aria-hidden="true"><app-icon name="check" [size]="12" /></span>
          {{ t.legendPlayer }}
          <span class="toggle small" data-role="COACH" aria-hidden="true">{{ t.coachMark }}</span> {{ t.legendCoach }}
          <span class="toggle small" data-role="none" aria-hidden="true"></span> {{ t.legendOff }}
          @if (d.writable) {
            <span>· {{ t.legendRemove }}</span>
          }
        </p>
      } @else if (!error()) {
        <p class="secondary-text">{{ t.loading }}</p>
      }
      <app-confirm-dialog testId="membership-confirm" />
    </div>
  `,
  styles: `
    .membership {
      display: grid;
      gap: 12px;
      min-width: 0;
    }
    .membership > p {
      margin: 0;
    }
    .filters {
      display: grid;
      gap: 12px;
    }
    .pills {
      display: flex;
      flex-wrap: wrap;
      gap: 12px 32px;
    }
    .selects {
      display: grid;
      gap: 10px 12px;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 132px), 1fr));
      align-items: end;
    }
    .columns summary {
      cursor: pointer;
      font-weight: 700;
      min-height: var(--touch);
      display: list-item;
      list-style: revert;
      padding-top: 10px;
    }
    .bar {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 8px 16px;
      min-height: 44px;
    }
    .shown {
      margin: 0;
      font-variant-numeric: tabular-nums;
    }
    .bulk {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 8px;
    }
    .bulk-field {
      display: block;
    }
    .bulk-field select {
      width: auto;
      max-width: 230px;
    }
    .bulk-text {
      font-weight: 600;
    }
    .link-button {
      border: 0;
      background: none;
      padding: 0;
      font: inherit;
      color: var(--primary);
      font-weight: 700;
      text-decoration: underline;
      cursor: pointer;
    }
    /* The table scrolls inside its own box: the page keeps its height whatever is tapped */
    .table-wrap {
      /* Positioned, so the visually hidden texts in the fields stay inside the box and do not stretch the page */
      position: relative;
      max-height: min(70vh, 660px);
      overflow: auto;
      border: 1px solid var(--border);
      border-radius: var(--radius-icon);
      background: var(--surface);
    }
    table {
      border-collapse: separate;
      border-spacing: 0;
      width: 100%;
    }
    th,
    td {
      height: 48px;
      padding: 0 10px;
      border-bottom: 1px solid var(--border);
      text-align: left;
      white-space: nowrap;
      background: var(--surface);
    }
    tbody tr:last-child > * {
      border-bottom: 0;
    }
    thead th {
      position: sticky;
      top: 0;
      z-index: 2;
      height: auto;
      padding-top: 8px;
      padding-bottom: 8px;
      vertical-align: bottom;
      font-size: 13px;
      color: var(--muted);
      border-bottom: 1.5px solid var(--border);
    }
    .person {
      position: sticky;
      left: 0;
      z-index: 1;
      min-width: 150px;
      max-width: 220px;
      font-weight: 600;
      border-right: 1.5px solid var(--border);
    }
    thead .corner {
      z-index: 3;
    }
    .person-name,
    .person-meta {
      display: block;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .person-meta {
      display: none;
      font-weight: 500;
      font-size: 12px;
      color: var(--muted);
    }
    .num {
      text-align: right;
      font-variant-numeric: tabular-nums;
    }
    .text {
      max-width: 200px;
      overflow: hidden;
      text-overflow: ellipsis;
      color: var(--muted);
    }
    thead .text {
      color: var(--muted);
    }
    .team {
      min-width: 72px;
      max-width: 96px;
      text-align: center;
      white-space: normal;
      padding-left: 4px;
      padding-right: 4px;
    }
    .team[data-in-season='false'] .team-name {
      font-style: italic;
    }
    .team-name {
      display: block;
      line-height: 1.2;
      color: var(--text);
      font-weight: 700;
      overflow-wrap: anywhere;
    }
    .team-count {
      display: block;
      margin-top: 2px;
      font-size: 12px;
      font-variant-numeric: tabular-nums;
    }
    .cell {
      text-align: center;
      padding: 0 4px;
    }
    .none {
      color: var(--muted);
      text-align: center;
    }
    .toggle {
      display: inline-grid;
      place-items: center;
      width: 44px;
      height: 40px;
      padding: 0;
      border-radius: var(--radius-icon);
      border: 1.5px dashed var(--border);
      background: transparent;
      color: var(--on-primary);
      font: 800 15px/1 var(--font);
      cursor: pointer;
      vertical-align: middle;
    }
    .toggle:hover:not(:disabled)[data-role='none'] {
      border-color: var(--primary);
      background: var(--primary-soft);
    }
    .toggle[data-role='PLAYER'] {
      border: 0;
      background: var(--primary);
    }
    .toggle[data-role='COACH'] {
      border: 2px solid var(--primary);
      background: var(--primary-soft);
      color: var(--primary);
    }
    .toggle:disabled {
      cursor: default;
    }
    .toggle.small {
      width: 22px;
      height: 20px;
      border-radius: 5px;
      font-size: 11px;
      cursor: default;
      margin-left: 8px;
    }
    .toggle.small:first-child {
      margin-left: 0;
    }
    .legend {
      margin: 0;
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 6px;
    }
    @media (max-width: 760px) {
      .meta {
        display: none;
      }
      .person-meta {
        display: block;
      }
      .person {
        min-width: 132px;
        max-width: 160px;
        padding-left: 8px;
        padding-right: 6px;
      }
      .team {
        min-width: 60px;
        font-size: 12px;
      }
    }
  `,
})
export class MembershipTable {
  private readonly http = inject(HttpClient);
  protected readonly t = TEXTS.membership;
  private readonly confirm = viewChild(ConfirmDialog);

  /** Season ID; without it the current season. */
  readonly season = input<string | undefined>(undefined);
  /** Only this team as a column (assistant «Neues Team»); without it every team of the season. */
  readonly team = input<string | undefined>(undefined);
  /** A membership was saved. */
  readonly changed = output<void>();

  protected readonly data = signal<Overview | null>(null);
  /** Who is in which team right now, taps included: `personId|teamId` → team role. */
  protected readonly cells = signal<Record<string, TeamRole>>({});
  protected readonly error = signal<string | null>(null);
  protected readonly announce = signal('');
  protected readonly busy = signal(false);

  protected readonly genderOptions: { value: GenderFilter; label: string }[] = [
    { value: 'ALL', label: TEXTS.membership.genderAll },
    { value: 'FEMALE', label: TEXTS.membership.genderFemale },
    { value: 'MALE', label: TEXTS.membership.genderMale },
  ];
  protected readonly roleOptions: { value: TeamRole; label: string }[] = [
    { value: 'PLAYER', label: TEXTS.membership.rolePlayer },
    { value: 'COACH', label: TEXTS.membership.roleCoach },
  ];
  protected readonly gender = signal<GenderFilter>('ALL');
  protected readonly yearFrom = signal<number | null>(null);
  protected readonly yearTo = signal<number | null>(null);
  protected readonly search = signal('');
  /** '' everyone · 'none' without a team · 'now:<teamId>' · 'prev:<teamId>' */
  protected readonly teamFilter = signal('');
  protected readonly sortBy = signal<SortBy>('gender-year');
  /** A tap on an empty field puts the person into the team with this team role. */
  protected readonly role = signal<TeamRole>('PLAYER');
  protected readonly hidden = signal<ReadonlySet<string>>(new Set());
  protected readonly bulkTeam = signal('');

  /** One request after the other per field, so quick taps on the same field stay in order. */
  private readonly queue = new Map<string, Promise<void>>();

  protected readonly sortOptions = computed(() => {
    const options: { value: SortBy; label: string }[] = [
      { value: 'gender-year', label: this.t.sortGenderYear },
      { value: 'year-gender', label: this.t.sortYearGender },
      { value: 'name', label: this.t.sortName },
    ];
    if (this.data()?.previousSeason) options.push({ value: 'previous', label: this.t.sortPrevious });
    return options;
  });

  protected readonly seasonTeams = computed(() => (this.data()?.teams ?? []).filter((c) => c.inSeason));
  protected readonly focus = computed<TeamColumn | null>(() => {
    const id = this.team();
    return id ? ((this.data()?.teams ?? []).find((c) => c.id === id) ?? null) : null;
  });
  protected readonly columns = computed<TeamColumn[]>(() => {
    const focus = this.focus();
    if (this.team()) return focus ? [focus] : [];
    const hidden = this.hidden();
    return (this.data()?.teams ?? []).filter((c) => !hidden.has(c.id));
  });

  private readonly teamNames = computed(() => new Map((this.data()?.teams ?? []).map((c) => [c.id, c.name])));

  private readonly viewRows = computed<ViewRow[]>(() => {
    const d = this.data();
    if (!d) return [];
    const before = new Map(d.previousTeams.map((c) => [c.id, c.name]));
    return d.people.map((row) => {
      const gender: GenderKey = row.person.gender ?? 'NONE';
      const previousText = row.previousTeams
        .map((e) => (before.get(e.teamId) ?? '') + (e.teamRole === 'COACH' ? this.t.coachSuffix : ''))
        .sort((a, b) => a.localeCompare(b, 'de-CH'))
        .join(', ');
      const meta = [
        gender === 'NONE' ? null : this.t.genderShort[gender],
        row.person.birthYear ?? null,
        previousText ? this.t.previousShort(previousText) : null,
      ]
        .filter((x) => x !== null)
        .join(' · ');
      return {
        person: row.person,
        name: name(row.person),
        gender,
        teams: new Set(row.teams.map((e) => e.teamId)),
        previous: new Set(row.previousTeams.map((e) => e.teamId)),
        previousText,
        meta,
      };
    });
  });

  protected readonly years = computed(() =>
    [...new Set(this.viewRows().map((r) => r.person.birthYear).filter((y): y is number => !!y))].sort((a, b) => a - b),
  );

  protected readonly filtersActive = computed(
    () =>
      this.gender() !== 'ALL' ||
      this.yearFrom() !== null ||
      this.yearTo() !== null ||
      this.search().trim() !== '' ||
      this.teamFilter() !== '',
  );

  /** The people who pass the filters, in the chosen order. Taps do not change this list. */
  protected readonly rows = computed<ViewRow[]>(() => {
    const gender = this.gender();
    const from = this.yearFrom();
    const to = this.yearTo();
    const team = this.teamFilter();
    const words = this.search().trim().toLowerCase().split(/\s+/).filter(Boolean);
    const list = this.viewRows().filter((r) => {
      if (gender !== 'ALL' && r.gender !== gender) return false;
      const year = r.person.birthYear;
      if (from !== null && (!year || year < from)) return false;
      if (to !== null && (!year || year > to)) return false;
      if (team === 'none' && r.teams.size > 0) return false;
      if (team.startsWith('now:') && !r.teams.has(team.slice(4))) return false;
      if (team.startsWith('prev:') && !r.previous.has(team.slice(5))) return false;
      const full = r.name.toLowerCase();
      return words.every((w) => full.includes(w));
    });
    const byName = (a: ViewRow, b: ViewRow) =>
      a.person.lastName.localeCompare(b.person.lastName, 'de-CH') || a.person.firstName.localeCompare(b.person.firstName, 'de-CH');
    const byGender = (a: ViewRow, b: ViewRow) => GENDER_ORDER[a.gender] - GENDER_ORDER[b.gender];
    // People without a year come last
    const byYear = (a: ViewRow, b: ViewRow) => (a.person.birthYear ?? 9999) - (b.person.birthYear ?? 9999);
    const byPrevious = (a: ViewRow, b: ViewRow) =>
      Number(!a.previousText) - Number(!b.previousText) || a.previousText.localeCompare(b.previousText, 'de-CH');
    const sort = this.sortBy();
    return [...list].sort((a, b) =>
      sort === 'name'
        ? byName(a, b)
        : sort === 'previous'
          ? byPrevious(a, b) || byName(a, b)
          : sort === 'year-gender'
            ? byYear(a, b) || byGender(a, b) || byName(a, b)
            : byGender(a, b) || byYear(a, b) || byName(a, b),
    );
  });

  /** Players and coaches per team, taps included. */
  private readonly counts = computed(() => {
    const counts = new Map<string, { players: number; coaches: number }>();
    for (const [k, role] of Object.entries(this.cells())) {
      const teamId = k.slice(k.indexOf('|') + 1);
      const c = counts.get(teamId) ?? { players: 0, coaches: 0 };
      if (role === 'COACH') c.coaches++;
      else c.players++;
      counts.set(teamId, c);
    }
    return counts;
  });

  constructor() {
    effect(() => {
      this.season();
      untracked(() => void this.load());
    });
  }

  protected count(teamId: string): { players: number; coaches: number } {
    return this.counts().get(teamId) ?? { players: 0, coaches: 0 };
  }

  protected toYear(value: string): number | null {
    return value ? Number(value) : null;
  }

  protected resetFilters(): void {
    this.gender.set('ALL');
    this.yearFrom.set(null);
    this.yearTo.set(null);
    this.search.set('');
    this.teamFilter.set('');
  }

  protected showColumn(teamId: string, show: boolean): void {
    const hidden = new Set(this.hidden());
    if (show) hidden.delete(teamId);
    else hidden.add(teamId);
    this.hidden.set(hidden);
  }

  /** The other teams of the person in this season, e.g. «Herren 1, Junioren A (Coach)». */
  protected others(r: ViewRow, focusId: string): string {
    const names = this.teamNames();
    const cells = this.cells();
    const list: string[] = [];
    for (const teamId of r.teams) {
      if (teamId === focusId) continue;
      const role = cells[key(r.person.id, teamId)];
      if (role) list.push((names.get(teamId) ?? '') + (role === 'COACH' ? this.t.coachSuffix : ''));
    }
    return list.sort((a, b) => a.localeCompare(b, 'de-CH')).join(', ') || this.t.none;
  }

  protected label(r: ViewRow, c: TeamColumn): string {
    const role = this.cells()[key(r.person.id, c.id)];
    return role === 'COACH' ? this.t.cellCoach(r.name, c.name) : role ? this.t.cellPlayer(r.name, c.name) : this.t.cellOff(r.name, c.name);
  }

  private params(): Record<string, string> {
    const season = this.season();
    return season ? { season } : {};
  }

  private async load(): Promise<void> {
    try {
      const d = await firstValueFrom(this.http.get<Overview>('/api/team-memberships', { params: this.params() }));
      const cells: Record<string, TeamRole> = {};
      for (const row of d.people) for (const e of row.teams) cells[key(row.person.id, e.teamId)] = e.teamRole;
      this.cells.set(cells);
      this.data.set(d);
      this.error.set(null);
    } catch (e) {
      this.error.set(errorText(e));
    }
  }

  private setCell(k: string, role: TeamRole | undefined): void {
    const next = { ...this.cells() };
    if (role) next[k] = role;
    else delete next[k];
    this.cells.set(next);
  }

  /**
   * A tap on a field: into the team with the chosen team role, another team role for someone who is in it, or out
   * of the team again (E-101). Shows at once; the request follows, and a failure puts the field back.
   */
  protected toggle(r: ViewRow, c: TeamColumn): void {
    const d = this.data();
    if (!d || !d.writable) return;
    const k = key(r.person.id, c.id);
    const before = this.cells()[k];
    const chosen = this.role();
    let after: TeamRole | undefined;
    if (!before || before !== chosen) {
      after = chosen;
    } else {
      // The same team role again: out of the team (E-101)
      after = undefined;
    }
    this.setCell(k, after);
    this.error.set(null);
    const url = `/api/teams/${c.id}/members`;
    const params = this.params();
    const save = async () => {
      try {
        if (!before && after) {
          await firstValueFrom(this.http.post(url, { personId: r.person.id, teamRole: after }, { params }));
        } else if (before && after) {
          await firstValueFrom(this.http.put(`${url}/${r.person.id}`, { teamRole: after }, { params }));
        } else {
          await firstValueFrom(this.http.delete(`${url}/${r.person.id}`, { params }));
        }
        this.announce.set(
          after
            ? this.t.added(r.name, c.name, after === 'COACH' ? this.t.roleCoach : this.t.rolePlayer)
            : this.t.removed(r.name, c.name),
        );
        this.changed.emit();
      } catch (e) {
        // Only put it back if nothing else happened to the field meanwhile
        if (this.cells()[k] === after) this.setCell(k, before);
        this.error.set(errorText(e));
      }
    };
    const chain = (this.queue.get(k) ?? Promise.resolve()).then(save);
    this.queue.set(k, chain);
    void chain.then(() => {
      if (this.queue.get(k) === chain) this.queue.delete(k);
    });
  }

  private bulkTarget(): TeamColumn | null {
    const focus = this.focus();
    if (focus) return focus;
    return (this.data()?.teams ?? []).find((c) => c.id === this.bulkTeam()) ?? null;
  }

  /** Everyone shown joins the team (PTF-17), after a question. */
  protected bulkAdd(): void {
    const target = this.bulkTarget();
    if (!target) return;
    const cells = this.cells();
    const people = this.rows().filter((r) => !cells[key(r.person.id, target.id)]);
    if (people.length === 0) {
      this.announce.set(this.t.bulkNothing);
      this.error.set(this.t.bulkNothing);
      return;
    }
    const teamRole = this.role();
    this.confirm()?.ask({
      title: this.t.bulkAddTitle(people.length, target.name),
      text: this.t.bulkAddText(teamRole === 'COACH' ? this.t.roleCoach : this.t.rolePlayer),
      confirm: this.t.bulkAddConfirm,
      action: () =>
        this.batch({ add: people.map((r) => ({ teamId: target.id, personId: r.person.id, teamRole })) }, (result) =>
          this.t.bulkAdded(result.added, target.name),
        ),
    });
  }

  /** Everyone shown leaves the team, after a question (E-101). */
  protected bulkRemove(): void {
    const target = this.bulkTarget();
    if (!target) return;
    const cells = this.cells();
    const people = this.rows().filter((r) => !!cells[key(r.person.id, target.id)]);
    if (people.length === 0) {
      this.announce.set(this.t.bulkNothing);
      this.error.set(this.t.bulkNothing);
      return;
    }
    this.confirm()?.ask({
      title: this.t.bulkRemoveTitle(people.length, target.name),
      text: this.t.bulkRemoveText,
      confirm: this.t.bulkRemoveConfirm,
      action: () =>
        this.batch({ remove: people.map((r) => ({ teamId: target.id, personId: r.person.id })) }, (result) =>
          this.t.bulkRemoved(result.removed, target.name),
        ),
    });
  }

  private async batch(input: components['schemas']['MembershipBatchInput'], text: (result: BatchResult) => string): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    try {
      // Taps that are still on their way first, so the batch sees them
      await Promise.all(this.queue.values());
      const result = await firstValueFrom(
        this.http.post<BatchResult>('/api/team-memberships/batch', input, { params: this.params() }),
      );
      await this.load();
      this.announce.set(text(result));
      this.changed.emit();
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.busy.set(false);
    }
  }
}
