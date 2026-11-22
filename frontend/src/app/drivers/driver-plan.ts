import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import type { Car, DriverPlanMatchDay, PersonSummary } from '../api/types';
import { AuthService } from '../core/auth.service';
import { name } from '../core/format';
import { Icon } from '../core/icon';
import { formatDecimal } from '../core/number';
import { TEXTS } from '../core/texts';
import { Fairness } from './simple/fairness';
import { PlanStore, QueueEntry } from './simple/plan-store';
import { SeatBoard } from './simple/seat-board';
import { Stepper } from './simple/stepper';

/**
 * Driver list of a team, proposal A «Fahrplan» (`/teams/:teamId/fahrerliste`): the season as one list, like the
 * sheet a coach hands out. Every match day is a row with its drivers; «Fair verteilen» fills all rows
 * (DRV-30), a tap on a driver replaces her, and the next match day is open with its cars to seat people
 * (DRV-03). `?saison=<id>` shows another season.
 */
@Component({
  selector: 'app-driver-plan',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon, Fairness, SeatBoard, Stepper],
  providers: [PlanStore],
  template: `
    <div class="header">
      <a class="back" [routerLink]="['/teams', teamId()]">{{ store.plan()?.teamName ?? texts.teams.title }}</a>
      <h1 class="page-title">{{ t.title }}</h1>
      @if (store.plan(); as d) {
        <p class="secondary-text" data-testid="driver-plan-season">{{ t.seasonOf(d.teamName, d.seasonName) }}</p>
      }
      @if (myTeams().length > 1) {
        <div class="field team-switch">
          <label for="plan-team">{{ t.team }}</label>
          <select id="plan-team" name="plan-team" (change)="switchTeam($any($event.target).value)">
            @for (x of myTeams(); track x.id) {
              <option [value]="x.id" [selected]="x.id === teamId()">{{ x.name }}</option>
            }
          </select>
        </div>
      }
    </div>
    @if (store.success(); as m) {
      <p class="message-success" role="status">{{ m }}</p>
    }
    @if (store.error(); as m) {
      <p class="message-error" role="alert">{{ m }}</p>
    }
    <div class="sections" [attr.aria-busy]="store.loading() ? 'true' : null">
      @if (store.loading()) {
        <p class="empty">{{ texts.common.loading }}</p>
      } @else if (store.plan(); as d) {
        @if (d.canManage) {
          <!-- Planning the season: one number and one button (DRV-29, DRV-30) -->
          <section class="card board planning" aria-labelledby="planning-title" data-testid="planning">
            <h2 id="planning-title" class="section-title">{{ a.planTitle }}</h2>
            <div class="expected">
              <span class="expected-label">{{ t.expected }}</span>
              <app-stepper
                [value]="d.expected"
                [label]="t.expected"
                [disabled]="store.busy()"
                testId="expected"
                (changed)="store.setExpected($event)"
              />
              <span class="secondary-text">{{ t.carsNeeded(carsFor(d.expected)) }}</span>
            </div>
            <p class="secondary-text">
              {{ d.expectedManual ? t.expectedManual(d.expectedDefault) : t.expectedAll(d.expectedDefault) }}
              @if (d.expectedManual) {
                <button type="button" class="link" [disabled]="store.busy()" (click)="store.setExpected(null)">
                  {{ t.expectedReset }}
                </button>
              }
            </p>
            <div class="plan-buttons">
              <button
                type="button"
                class="button button-primary"
                [disabled]="store.busy() || store.short().length === 0"
                [attr.aria-busy]="store.running() === 'distribute' ? 'true' : null"
                data-testid="distribute"
                (click)="store.distribute(false)"
              >
                {{ t.distribute }}
              </button>
              @if (hasPlanned()) {
                <button
                  type="button"
                  class="button button-secondary"
                  [disabled]="store.busy()"
                  data-testid="redistribute"
                  (click)="store.distribute(true)"
                >
                  {{ t.redistribute }}
                </button>
              }
            </div>
            <p class="secondary-text">
              {{ store.short().length === 0 ? a.allPlanned : a.openDays(store.short().length) }}
            </p>
          </section>
        }

        @if (d.canJoin) {
          <p class="my-trips" data-testid="my-trips">
            <app-icon name="car" [size]="18" />
            @if (store.myTrips().length === 0) {
              {{ t.myTripsNone }}
            } @else {
              <span>
                <strong>{{ t.myTrips(store.myTrips().length) }}:</strong> {{ myTripDays() }}
              </span>
            }
          </p>
        }

        <section class="card" aria-labelledby="plan-match-days-title" data-testid="driver-plan-match-days">
          <h2 id="plan-match-days-title" class="section-title">
            {{ a.matchDays }} <span class="count">{{ store.upcoming().length }}</span>
          </h2>
          @if (d.matchDays.length === 0) {
            <p class="empty">{{ t.noMatchDays }}</p>
          } @else if (store.upcoming().length === 0) {
            <p class="empty">{{ a.noUpcoming }}</p>
          }
          <ol class="match-days" aria-labelledby="plan-match-days-title">
            @for (m of store.upcoming(); track m.eventId) {
              <li
                class="match-day"
                [attr.aria-label]="store.dayTime(m)"
                [attr.data-testid]="'plan-match-day-' + m.eventId"
                [attr.data-state]="store.state(m)"
              >
                <div class="when">
                  <span class="date">{{ store.dayTime(m) }}</span>
                  <span class="secondary-text">
                    {{ store.venue(m) }}
                    @if (m.distanceKm !== undefined && m.distanceKm !== null && !m.noTrip) {
                      · {{ t.distance(km(m.distanceKm)) }}
                    }
                  </span>
                </div>
                @if (m.cancelled) {
                  <span class="pill" data-state="none">{{ t.cancelled }}</span>
                } @else if (m.noTrip) {
                  <span class="pill" data-state="none">{{ t.home }}</span>
                } @else if (!m.ready) {
                  <span class="pill" data-state="none">{{ t.notReady }}</span>
                } @else {
                  <span class="pill" [attr.data-state]="store.state(m)" data-testid="plan-places">
                    @if (store.state(m) === 'enough') {
                      <app-icon name="check" [size]="14" />
                      {{ t.places(m.places, m.expected) }}
                    } @else {
                      {{ t.missing(store.missingPlaces(m)) }}
                    }
                  </span>
                  <div class="drivers" [attr.aria-label]="a.drivers">
                    @for (car of m.cars; track car.id) {
                      @if (m.changeable && (d.canManage || car.driver.id === store.meId())) {
                        <button
                          type="button"
                          class="driver-chip"
                          [attr.data-testid]="'plan-car-' + car.driver.id"
                          [attr.data-me]="car.driver.id === store.meId() ? 'true' : null"
                          [attr.title]="name(car.driver)"
                          [disabled]="store.busy()"
                          (click)="openDriver(m, car)"
                        >
                          <app-icon name="car" [size]="16" />
                          {{ store.shortName(car.driver) }}
                          <span class="visually-hidden">{{ a.changeDriver }}</span>
                          <span class="chip-places" aria-hidden="true">{{ car.seats + 1 }}</span>
                        </button>
                      } @else {
                        <span class="driver-chip" [attr.data-testid]="'plan-car-' + car.driver.id">
                          <app-icon name="car" [size]="16" />
                          {{ store.shortName(car.driver) }}
                          <span class="chip-places" aria-hidden="true">{{ car.seats + 1 }}</span>
                        </span>
                      }
                    }
                    @if (m.changeable && d.canManage) {
                      <button
                        type="button"
                        class="driver-chip add"
                        [attr.data-testid]="'plan-add-' + m.eventId"
                        [disabled]="store.busy()"
                        (click)="openAdd(m)"
                      >
                        + {{ a.addDriver }}
                        <span class="visually-hidden">{{ a.addDriverFor(store.day(m)) }}</span>
                      </button>
                    } @else if (m.changeable && d.canJoin && !drives(m)) {
                      <button
                        type="button"
                        class="driver-chip add"
                        [attr.data-testid]="'plan-join-' + m.eventId"
                        [disabled]="store.busy()"
                        (click)="driveMyself(m)"
                      >
                        + {{ a.iDrive }}
                        <span class="visually-hidden">{{ a.iDriveFor(store.day(m)) }}</span>
                      </button>
                    }
                    @if (m.cars.length === 0 && !m.changeable) {
                      <span class="secondary-text">{{ t.noDrivers }}</span>
                    }
                  </div>
                  <div class="row-actions">
                    <button
                      type="button"
                      class="button button-secondary button-small"
                      [attr.aria-expanded]="isOpen(m.eventId)"
                      [attr.data-testid]="'plan-open-' + m.eventId"
                      (click)="toggleOpen(m.eventId)"
                    >
                      <app-icon [name]="isOpen(m.eventId) ? 'chevron-up' : 'users'" [size]="16" />
                      {{ isOpen(m.eventId) ? a.closeCars : a.openCars }}
                      @if (!isOpen(m.eventId) && m.attending > 0) {
                        <span class="count">· {{ t.attending(m.attending) }}</span>
                      }
                    </button>
                    <a class="to-event" [routerLink]="['/termine', m.eventId]">{{ t.openMatchDay }}</a>
                  </div>
                  @if (isOpen(m.eventId)) {
                    <div class="cars-panel" [attr.data-testid]="'plan-editor-' + m.eventId">
                      @if (d.canManage) {
                        <div class="expected small">
                          <span class="expected-label">{{ a.expectedHere }}</span>
                          <app-stepper
                            [value]="m.expected"
                            [label]="t.expectedOf(store.day(m))"
                            [disabled]="store.busy()"
                            (changed)="store.setMatchDayExpected(m.eventId, $event)"
                          />
                          @if (m.expectedManual) {
                            <button
                              type="button"
                              class="link"
                              [disabled]="store.busy()"
                              (click)="store.setMatchDayExpected(m.eventId, null)"
                            >
                              {{ a.expectedLikeTeam(d.expected) }}
                            </button>
                          }
                          @if (m.attending > 0) {
                            <span class="secondary-text">{{ a.attendingNow(m.attending) }}</span>
                          }
                        </div>
                      }
                      <app-seat-board [matchDay]="m" />
                    </div>
                  }
                }
              </li>
            }
          </ol>
          @if (store.past().length > 0) {
            <details class="past" data-testid="plan-past">
              <summary>{{ t.past(store.past().length) }}</summary>
              <ul class="past-list">
                @for (m of store.past(); track m.eventId) {
                  <li [attr.data-testid]="'plan-match-day-' + m.eventId">
                    <a [routerLink]="['/termine', m.eventId]">{{ store.day(m) }}</a>
                    <span class="secondary-text">{{ m.venue }}</span>
                    <span>{{ m.noTrip ? t.home : driverNames(m) }}</span>
                  </li>
                }
              </ul>
            </details>
          }
        </section>

        <section class="card" data-testid="driver-plan-shares">
          <app-fairness />
        </section>
      }
    </div>

    <!-- A driver of a match day: replace her, or take her off (DRV-25, DRV-31) -->
    <dialog #dialog class="card dialog picker" aria-modal="true" aria-labelledby="picker-title" (close)="picked.set(null)">
      @if (picked(); as p) {
        <h2 id="picker-title">{{ p.car ? a.pickerReplace(name(p.car.driver), store.day(p.matchDay)) : a.pickerAdd(store.day(p.matchDay)) }}</h2>
        @if (p.car && !store.canManage()) {
          <p class="secondary-text">{{ a.passText }}</p>
          <button
            type="button"
            class="button button-primary"
            [disabled]="store.busy()"
            data-testid="picker-pass"
            (click)="pass(p.matchDay, p.car)"
          >
            {{ t.cannot }}
          </button>
        } @else {
          <p class="secondary-text">{{ p.car ? a.pickerReplaceText : a.pickerAddText }}</p>
          <ul class="candidates" [attr.aria-label]="a.candidates">
            @for (q of candidates(); track q.person.id; let first = $first) {
              <li>
                <button
                  type="button"
                  class="candidate"
                  [disabled]="store.busy()"
                  [attr.data-testid]="'picker-' + q.person.id"
                  (click)="choose(p.matchDay, p.car, q.person)"
                >
                  <span class="candidate-name">{{ name(q.person) }}</span>
                  @if (first) {
                    <span class="chip">{{ a.nextInLine }}</span>
                  }
                  <span class="secondary-text">{{ t.fairness.trips(q.trips) }}</span>
                </button>
              </li>
            } @empty {
              <li class="empty">{{ a.noCandidates }}</li>
            }
          </ul>
        }
        <div class="dialog-buttons">
          @if (p.car) {
            <button
              type="button"
              class="button button-secondary button-destructive"
              [disabled]="store.busy()"
              data-testid="picker-remove"
              (click)="remove(p.matchDay, p.car)"
            >
              {{ a.remove }}
            </button>
          }
          <button type="button" class="button button-secondary" (click)="close()">{{ a.cancel }}</button>
        </div>
      }
    </dialog>
  `,
  styles: `
    :host {
      display: grid;
      gap: 16px;
      min-width: 0;
    }
    .sections {
      display: grid;
      gap: 16px;
      min-width: 0;
    }
    p {
      margin: 0;
    }
    .card {
      display: grid;
      gap: 10px;
      min-width: 0;
    }
    .team-switch {
      max-width: 320px;
    }
    select {
      min-height: var(--touch);
      padding: 0 12px;
      border: 1.5px solid var(--control-border);
      border-radius: var(--radius-icon);
      background: var(--surface);
      color: var(--text);
      font: 600 15px/1.2 var(--font);
      max-width: 100%;
    }
    .expected {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 8px 12px;
    }
    .expected-label {
      font-weight: 700;
    }
    .expected.small .expected-label {
      font-size: 14px;
    }
    .plan-buttons {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
    }
    .link {
      border: 0;
      padding: 0;
      background: none;
      color: var(--primary);
      font: 600 14px/1.3 var(--font);
      text-decoration: underline;
      cursor: pointer;
      min-height: 32px;
    }
    .my-trips {
      display: flex;
      align-items: flex-start;
      gap: 10px;
      padding: 12px 14px;
      border-radius: var(--radius-control);
      background: var(--primary-soft);
      border: 1.5px solid var(--primary);
    }
    .match-days,
    .past-list,
    .candidates {
      list-style: none;
      margin: 0;
      padding: 0;
    }
    .match-day {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      gap: 8px 12px;
      align-items: start;
      padding: 14px 0;
      min-width: 0;
    }
    .match-day + .match-day {
      border-top: 1px solid var(--border);
    }
    .when {
      display: grid;
      gap: 2px;
      min-width: 0;
    }
    .date {
      font-weight: 800;
      font-size: 17px;
    }
    .pill {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 4px 10px;
      border-radius: var(--radius-pill);
      font-size: 13px;
      font-weight: 700;
      white-space: nowrap;
      justify-self: end;
    }
    .pill[data-state='enough'] {
      background: var(--tag-green-soft);
      color: var(--tag-green);
    }
    .pill[data-state='short'] {
      background: var(--coral-soft);
      color: var(--coral);
    }
    .pill[data-state='none'] {
      background: var(--tag-grey-soft);
      color: var(--tag-grey);
    }
    @media (max-width: 520px) {
      .pill {
        grid-column: 1 / -1;
        justify-self: start;
      }
    }
    .drivers,
    .row-actions,
    .cars-panel {
      grid-column: 1 / -1;
      min-width: 0;
    }
    .drivers {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }
    .driver-chip {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      min-height: var(--touch);
      padding: 0 12px;
      border-radius: var(--radius-control);
      border: 1.5px solid var(--control-border);
      background: var(--surface);
      color: var(--text);
      font: 700 15px/1.2 var(--font);
    }
    button.driver-chip {
      cursor: pointer;
    }
    .driver-chip[data-me='true'] {
      border-color: var(--primary);
      background: var(--primary-soft);
    }
    .driver-chip.add {
      border-style: dashed;
      color: var(--primary);
      border-color: var(--primary);
      background: transparent;
    }
    .match-day[data-state='short'] .driver-chip.add {
      border-color: var(--coral);
      color: var(--coral);
    }
    .chip-places {
      display: inline-grid;
      place-items: center;
      min-width: 22px;
      height: 22px;
      padding: 0 5px;
      border-radius: var(--radius-chip);
      background: var(--tag-grey-soft);
      color: var(--text);
      font-size: 12px;
      font-variant-numeric: tabular-nums;
    }
    .row-actions {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 6px 16px;
    }
    .to-event {
      display: inline-flex;
      align-items: center;
      min-height: var(--touch);
      font-weight: 700;
      font-size: 14px;
      color: var(--primary);
    }
    .cars-panel {
      display: grid;
      gap: 12px;
      padding: 14px;
      border-radius: var(--radius-control);
      background: var(--bg);
    }
    .past {
      border-top: 1px solid var(--border);
      padding-top: 6px;
    }
    .past summary {
      min-height: var(--touch);
      display: flex;
      align-items: center;
      cursor: pointer;
      font-weight: 700;
      color: var(--muted);
    }
    .past-list li {
      display: grid;
      grid-template-columns: 84px minmax(0, 1fr);
      gap: 0 10px;
      padding: 8px 0;
      border-top: 1px solid var(--border);
      font-size: 14px;
    }
    .past-list li span:last-child {
      grid-column: 2;
    }
    .dialog {
      width: min(440px, calc(100vw - 32px));
      max-height: min(640px, calc(100dvh - 32px));
      gap: 12px;
      overflow-y: auto;
    }
    .dialog:not([open]) {
      display: none;
    }
    .dialog h2 {
      margin: 0;
      font-size: 19px;
    }
    .candidates {
      display: grid;
      gap: 6px;
      max-height: 46dvh;
      overflow-y: auto;
      padding: 2px;
    }
    .candidate {
      display: flex;
      align-items: center;
      gap: 10px;
      width: 100%;
      min-height: 48px;
      padding: 0 12px;
      border: 1.5px solid var(--border);
      border-radius: var(--radius-control);
      background: var(--surface);
      color: var(--text);
      font: 600 15px/1.2 var(--font);
      text-align: left;
      cursor: pointer;
    }
    .candidate-name {
      flex: 1;
      overflow-wrap: anywhere;
    }
    .dialog-buttons {
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      gap: 10px;
    }
  `,
})
export class DriverPlanPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly store = inject(PlanStore);
  protected readonly texts = TEXTS;
  protected readonly t = TEXTS.simplePlan;
  protected readonly a = TEXTS.planA;
  protected readonly km = formatDecimal;
  protected readonly name = name;

  /** Route `/teams/:teamId/fahrerliste` */
  readonly teamId = input.required<string>();
  /** Query parameter `?saison=<id>`; without it the current season. */
  readonly saison = input<string | undefined>(undefined);

  /** Match days whose cars are shown. The next match day is open from the start. */
  protected readonly opened = signal<string[] | null>(null);
  /** The driver (or the free slot) the dialog is about. */
  protected readonly picked = signal<{ matchDay: DriverPlanMatchDay; car: Car | null } | null>(null);
  private readonly dialog = viewChild<ElementRef<HTMLDialogElement>>('dialog');

  /** Own teams of the current season, for the choice «Team». */
  protected readonly myTeams = computed(() => {
    const seen = new Map<string, { id: string; name: string }>();
    for (const m of this.auth.me()?.memberships ?? []) seen.set(m.team.id, m.team);
    return [...seen.values()];
  });

  protected readonly hasPlanned = computed(() =>
    this.store.upcoming().some((m) => !m.cancelled && m.cars.length > 0),
  );

  protected readonly myTripDays = computed(() =>
    this.store
      .myTrips()
      .map((m) => this.store.day(m))
      .join(' · '),
  );

  /** Who can take the trip: can drive and does not drive on that match day, the fewest trips first. */
  protected readonly candidates = computed<QueueEntry[]>(() => {
    const p = this.picked();
    if (!p) return [];
    const driving = new Set(p.matchDay.cars.map((car) => car.driver.id));
    return this.store.queue().filter((q) => q.canDrive && !driving.has(q.person.id));
  });

  constructor() {
    effect(() => {
      const team = this.teamId();
      const season = this.saison();
      untracked(() => {
        this.opened.set(null);
        void this.store.load(team, season);
      });
    });
  }

  protected switchTeam(team: string): void {
    void this.router.navigate(['/teams', team, 'fahrerliste']);
  }

  /** Cars needed for so many people with the usual five places. */
  protected carsFor(people: number): number {
    return Math.ceil(people / 5);
  }

  protected isOpen(eventId: string): boolean {
    const opened = this.opened();
    return opened ? opened.includes(eventId) : this.store.nextMatchDay()?.eventId === eventId;
  }

  protected toggleOpen(eventId: string): void {
    const current = this.opened() ?? [this.store.nextMatchDay()?.eventId ?? ''];
    this.opened.set(current.includes(eventId) ? current.filter((id) => id !== eventId) : [...current, eventId]);
  }

  protected drives(m: DriverPlanMatchDay): boolean {
    return m.cars.some((car) => car.driver.id === this.store.meId());
  }

  protected driverNames(m: DriverPlanMatchDay): string {
    return m.cars.map((car) => name(car.driver)).join(', ') || this.t.noDrivers;
  }

  protected openDriver(m: DriverPlanMatchDay, car: Car): void {
    this.picked.set({ matchDay: m, car });
    this.dialog()?.nativeElement.showModal();
  }

  protected openAdd(m: DriverPlanMatchDay): void {
    this.picked.set({ matchDay: m, car: null });
    this.dialog()?.nativeElement.showModal();
  }

  protected close(): void {
    this.dialog()?.nativeElement.close();
  }

  protected async choose(m: DriverPlanMatchDay, car: Car | null, person: PersonSummary): Promise<void> {
    this.close();
    await (car ? this.store.handOver(m, car.driver, person) : this.store.addDriver(m, person));
  }

  protected async remove(m: DriverPlanMatchDay, car: Car): Promise<void> {
    this.close();
    await this.store.removeCar(m, car.id, car.driver);
  }

  protected async pass(m: DriverPlanMatchDay, car: Car): Promise<void> {
    this.close();
    await this.store.next(m, car.driver);
  }

  protected async driveMyself(m: DriverPlanMatchDay): Promise<void> {
    const me = this.store.plan()?.drivers.find((d) => d.person.id === this.store.meId())?.person;
    if (me) await this.store.addDriver(m, me);
  }
}
