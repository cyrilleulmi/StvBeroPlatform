import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import type { Car, DriverPlanMatchDay } from '../api/types';
import { AuthService } from '../core/auth.service';
import { name } from '../core/format';
import { Icon } from '../core/icon';
import { formatDecimal } from '../core/number';
import { TEXTS } from '../core/texts';
import { Fairness } from './simple/fairness';
import { PlanStore } from './simple/plan-store';
import { SeatBoard } from './simple/seat-board';
import { Stepper } from './simple/stepper';

/**
 * Driver list of a team, proposal C «Reihum» (`/teams/:teamId/fahrerliste`): nobody picks drivers by hand. The
 * team drives in turns: whoever has driven the least is next (DRV-31). The coach only says «one car more», «one
 * car less» or «she cannot», and the app names the next in line; «Autos anpassen» fits the cars of a match day to
 * the people who come. The season is planned with one button (DRV-30). `?saison=<id>` shows another season.
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
        @if (d.canManage && store.short().length > 0) {
          <!-- Planning the season: one button (DRV-30) -->
          <section class="card board call" aria-labelledby="call-title" data-testid="planning">
            <h2 id="call-title" class="section-title">{{ c.callTitle(store.short().length) }}</h2>
            <p>{{ c.callText(d.expected) }}</p>
            <button
              type="button"
              class="button button-primary"
              [disabled]="store.busy()"
              [attr.aria-busy]="store.running() === 'distribute' ? 'true' : null"
              data-testid="distribute"
              (click)="store.distribute(false)"
            >
              {{ c.distribute }}
            </button>
          </section>
        }

        @if (d.canJoin) {
          <!-- What it means for me -->
          <section class="card mine" aria-labelledby="mine-title" data-testid="my-trips">
            @if (store.myTrips()[0]; as m) {
              <p class="eyebrow">{{ c.mineNext }}</p>
              <h2 id="mine-title" class="mine-title">
                <app-icon name="car" [size]="22" />
                {{ c.mineDrive(store.day(m), m.town ?? m.venue ?? '') }}
              </h2>
              @if (store.myTrips().length > 1) {
                <p class="secondary-text">{{ c.mineLater(laterDays()) }}</p>
              }
              @if (m.changeable) {
                <button
                  type="button"
                  class="button button-secondary button-small"
                  [disabled]="store.busy()"
                  [attr.aria-label]="t.cannotFor(store.day(m))"
                  data-testid="my-pass"
                  (click)="pass(m, myCar(m)!)"
                >
                  {{ t.cannot }}
                </button>
              }
            } @else {
              <p class="eyebrow">{{ c.mineNone }}</p>
              <h2 id="mine-title" class="mine-title">
                {{ myRank() ? c.mineRank(myRank()!) : c.mineNoDriver }}
              </h2>
            }
          </section>
        }

        <section class="card" aria-labelledby="plan-match-days-title" data-testid="driver-plan-match-days">
          <h2 id="plan-match-days-title" class="section-title">
            {{ c.matchDays }} <span class="count">{{ store.upcoming().length }}</span>
          </h2>
          @if (store.upcoming().length === 0) {
            <p class="empty">{{ d.matchDays.length === 0 ? t.noMatchDays : c.noUpcoming }}</p>
          }
          <ol class="match-days" aria-labelledby="plan-match-days-title">
            @for (m of store.upcoming(); track m.eventId) {
              <li
                class="match-day"
                [attr.aria-label]="store.dayTime(m)"
                [attr.data-testid]="'plan-match-day-' + m.eventId"
                [attr.data-state]="store.state(m)"
                [attr.data-open]="isOpen(m.eventId)"
              >
                <button
                  type="button"
                  class="day-head"
                  [attr.aria-expanded]="isOpen(m.eventId)"
                  [attr.data-testid]="'plan-open-' + m.eventId"
                  [disabled]="m.cancelled || m.noTrip || !m.ready"
                  (click)="toggleOpen(m.eventId)"
                >
                  <span class="marker" aria-hidden="true"></span>
                  <span class="when">
                    <span class="date">{{ store.dayTime(m) }}</span>
                    <span class="secondary-text">
                      {{ store.venue(m) }}
                      @if (m.distanceKm !== undefined && m.distanceKm !== null && !m.noTrip) {
                        · {{ t.distance(km(m.distanceKm)) }}
                      }
                    </span>
                    <span class="summary" data-testid="plan-places">
                      @if (m.cancelled) {
                        {{ t.cancelled }}
                      } @else if (m.noTrip) {
                        {{ t.home }}
                      } @else if (!m.ready) {
                        {{ t.notReady }}
                      } @else if (m.cars.length === 0) {
                        {{ c.nobodyYet }}
                      } @else {
                        {{ driverNames(m) }}
                      }
                    </span>
                  </span>
                  @if (!m.cancelled && !m.noTrip && m.ready) {
                    <span class="pill" [attr.data-state]="store.state(m)">
                      {{ store.state(m) === 'enough' ? c.cars(m.cars.length) : c.carMissing(carsMissing(m)) }}
                    </span>
                    <app-icon [name]="isOpen(m.eventId) ? 'chevron-up' : 'chevron-down'" [size]="18" />
                  }
                </button>

                @if (isOpen(m.eventId) && !m.cancelled && !m.noTrip && m.ready) {
                  <div class="panel" [attr.data-testid]="'plan-editor-' + m.eventId">
                    <ul class="drivers" [attr.aria-label]="c.drivers">
                      @for (car of m.cars; track car.id) {
                        <li class="driver" [attr.data-testid]="'plan-car-' + car.driver.id">
                          <app-icon name="car" [size]="18" />
                          <span class="driver-text">
                            <span class="driver-name">{{ name(car.driver) }}</span>
                            <span class="secondary-text">{{ c.placesOf(car.seats + 1) }}</span>
                          </span>
                          @if (m.changeable && (d.canManage || car.driver.id === store.meId())) {
                            <button
                              type="button"
                              class="button button-secondary button-small cannot"
                              [disabled]="store.busy()"
                              [attr.aria-label]="c.cannotFor(name(car.driver))"
                              [attr.data-testid]="'plan-pass-' + car.driver.id"
                              (click)="pass(m, car)"
                            >
                              {{ c.cannot }}
                            </button>
                          }
                        </li>
                      }
                    </ul>
                    <p class="need" [attr.data-state]="store.state(m)">
                      {{ needText(m) }}
                    </p>
                    @if (m.changeable && d.canManage) {
                      <div class="auto-buttons">
                        <button
                          type="button"
                          class="button button-small"
                          [class.button-primary]="store.state(m) === 'short'"
                          [class.button-secondary]="store.state(m) !== 'short'"
                          [disabled]="store.busy()"
                          [attr.data-testid]="'plan-more-' + m.eventId"
                          (click)="store.next(m, null)"
                        >
                          + {{ c.oneMore }}
                        </button>
                        <button
                          type="button"
                          class="button button-secondary button-small"
                          [disabled]="store.busy() || !spare(m)"
                          [attr.data-testid]="'plan-less-' + m.eventId"
                          (click)="less(m)"
                        >
                          − {{ c.oneLess }}
                        </button>
                        @if (m.attending > 0) {
                          <button
                            type="button"
                            class="button button-primary button-small"
                            [disabled]="store.busy()"
                            [attr.data-testid]="'plan-adjust-' + m.eventId"
                            (click)="adjust(m)"
                          >
                            {{ c.adjust }}
                          </button>
                        }
                      </div>
                      @if (m.attending > 0) {
                        <p class="secondary-text">{{ c.adjustText(m.attending) }}</p>
                      }
                      <div class="expected">
                        <span class="expected-label">{{ c.expectedHere }}</span>
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
                            {{ c.likeTeam(d.expected) }}
                          </button>
                        }
                      </div>
                    } @else if (m.changeable && d.canJoin && store.state(m) === 'short' && !myCar(m) && canDrive()) {
                      <button
                        type="button"
                        class="button button-primary button-small"
                        [disabled]="store.busy()"
                        [attr.data-testid]="'plan-join-' + m.eventId"
                        (click)="driveMyself(m)"
                      >
                        {{ c.iDrive }}
                      </button>
                    }
                    <h3 class="seats-title">{{ c.seatsTitle }}</h3>
                    <app-seat-board [matchDay]="m" />
                    <a class="to-event" [routerLink]="['/termine', m.eventId]">{{ t.openMatchDay }}</a>
                  </div>
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
                    <span>{{ m.noTrip ? t.home : driverNames(m) || t.noDrivers }}</span>
                  </li>
                }
              </ul>
            </details>
          }
        </section>

        <section class="card" data-testid="driver-plan-shares">
          <app-fairness order="queue" [title]="c.queueTitle" [text]="c.queueText" />
        </section>

        @if (d.canManage) {
          <section class="card" aria-labelledby="settings-title" data-testid="plan-settings">
            <h2 id="settings-title" class="section-title">{{ c.settingsTitle }}</h2>
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
            <div class="auto-buttons">
              <button
                type="button"
                class="button button-secondary button-small"
                [disabled]="store.busy()"
                data-testid="redistribute"
                (click)="store.distribute(true)"
              >
                {{ c.redistribute }}
              </button>
            </div>
            <p class="secondary-text">{{ c.redistributeText }}</p>
          </section>
        }
      }
    </div>
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
    p,
    h3 {
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
    .call,
    .mine {
      justify-items: start;
    }
    .mine {
      border-color: var(--primary);
      border-left-width: 6px;
    }
    .mine-title {
      display: flex;
      align-items: center;
      gap: 10px;
      margin: 0;
      font: 800 20px/1.25 var(--font);
    }
    .match-days,
    .drivers,
    .past-list {
      list-style: none;
      margin: 0;
      padding: 0;
    }
    .match-day {
      position: relative;
      min-width: 0;
    }
    /* The season as a line with one stop per match day */
    .match-day::before {
      content: '';
      position: absolute;
      left: 8px;
      top: 0;
      bottom: 0;
      width: 2px;
      background: var(--border);
    }
    .match-day:first-child::before {
      top: 26px;
    }
    .match-day:last-child::before {
      bottom: calc(100% - 26px);
    }
    .day-head {
      position: relative;
      display: grid;
      grid-template-columns: 18px minmax(0, 1fr) auto auto;
      align-items: center;
      gap: 6px 12px;
      width: 100%;
      padding: 12px 0;
      border: 0;
      background: transparent;
      color: var(--text);
      font: inherit;
      text-align: left;
      cursor: pointer;
    }
    .day-head:disabled {
      cursor: default;
    }
    .marker {
      width: 18px;
      height: 18px;
      border-radius: 50%;
      background: var(--surface);
      border: 3px solid var(--tag-grey);
      z-index: 1;
    }
    .match-day[data-state='enough'] .marker {
      border-color: var(--tag-green);
      background: var(--tag-green);
    }
    .match-day[data-state='short'] .marker {
      border-color: var(--coral);
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
    .summary {
      font-weight: 600;
      font-size: 14px;
      overflow-wrap: anywhere;
    }
    .pill {
      display: inline-flex;
      align-items: center;
      padding: 4px 10px;
      border-radius: var(--radius-pill);
      font-size: 13px;
      font-weight: 700;
      white-space: nowrap;
    }
    .pill[data-state='enough'] {
      background: var(--tag-green-soft);
      color: var(--tag-green);
    }
    .pill[data-state='short'] {
      background: var(--coral-soft);
      color: var(--coral);
    }
    @media (max-width: 520px) {
      .day-head {
        grid-template-columns: 18px minmax(0, 1fr) auto;
      }
      .pill {
        grid-column: 2;
        grid-row: 2;
        justify-self: start;
      }
    }
    .panel {
      display: grid;
      gap: 12px;
      margin: 0 0 14px 30px;
      padding: 14px;
      border-radius: var(--radius-control);
      background: var(--bg);
      min-width: 0;
    }
    @media (max-width: 520px) {
      .panel {
        margin-left: 20px;
        padding: 12px;
      }
    }
    .drivers {
      display: grid;
      gap: 6px;
    }
    .driver {
      display: grid;
      grid-template-columns: auto minmax(0, 1fr) auto;
      align-items: center;
      gap: 4px 10px;
      padding: 6px 6px 6px 12px;
      min-height: 52px;
      border-radius: var(--radius-control);
      border: 1.5px solid var(--border);
      background: var(--surface);
    }
    .driver-text {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      gap: 0 10px;
      min-width: 0;
    }
    .driver-name {
      font-weight: 800;
    }
    .need {
      font-weight: 700;
    }
    .need[data-state='enough'] {
      color: var(--tag-green);
    }
    .need[data-state='short'] {
      color: var(--coral);
    }
    .auto-buttons,
    .expected {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 8px 12px;
    }
    .expected-label {
      font-weight: 700;
      font-size: 14px;
    }
    .seats-title {
      font: 700 15px/1.3 var(--font);
      padding-top: 8px;
      border-top: 1px solid var(--border);
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
    .to-event {
      display: inline-flex;
      align-items: center;
      min-height: var(--touch);
      font-weight: 700;
      font-size: 14px;
      color: var(--primary);
      justify-self: start;
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
  `,
})
export class DriverPlanPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly store = inject(PlanStore);
  protected readonly texts = TEXTS;
  protected readonly t = TEXTS.simplePlan;
  protected readonly c = TEXTS.planC;
  protected readonly km = formatDecimal;
  protected readonly name = name;

  /** Route `/teams/:teamId/fahrerliste` */
  readonly teamId = input.required<string>();
  /** Query parameter `?saison=<id>`; without it the current season. */
  readonly saison = input<string | undefined>(undefined);

  /** Match days that are open. The next match day is open from the start. */
  protected readonly opened = signal<string[] | null>(null);

  /** Own teams of the current season, for the choice «Team». */
  protected readonly myTeams = computed(() => {
    const seen = new Map<string, { id: string; name: string }>();
    for (const m of this.auth.me()?.memberships ?? []) seen.set(m.team.id, m.team);
    return [...seen.values()];
  });

  /** My place in the row of those who can drive, 1 is next; `null` if I cannot drive. */
  protected readonly myRank = computed(() => {
    const index = this.store.queue().findIndex((q) => q.person.id === this.store.meId() && q.canDrive);
    return index < 0 ? null : index + 1;
  });

  protected readonly canDrive = computed(() => this.myRank() !== null);

  protected readonly laterDays = computed(() =>
    this.store
      .myTrips()
      .slice(1)
      .map((m) => this.store.day(m))
      .join(' · '),
  );

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

  protected carsMissing(m: DriverPlanMatchDay): number {
    return Math.max(1, Math.ceil(this.store.missingPlaces(m) / 5));
  }

  protected isOpen(eventId: string): boolean {
    const opened = this.opened();
    return opened ? opened.includes(eventId) : this.store.nextMatchDay()?.eventId === eventId;
  }

  protected toggleOpen(eventId: string): void {
    const current = this.opened() ?? [this.store.nextMatchDay()?.eventId ?? ''];
    this.opened.set(current.includes(eventId) ? current.filter((id) => id !== eventId) : [...current, eventId]);
  }

  protected driverNames(m: DriverPlanMatchDay): string {
    return m.cars.map((car) => this.store.shortName(car.driver)).join(', ');
  }

  protected myCar(m: DriverPlanMatchDay): Car | null {
    return m.cars.find((car) => car.driver.id === this.store.meId()) ?? null;
  }

  protected needText(m: DriverPlanMatchDay): string {
    return this.store.state(m) === 'short'
      ? this.c.needShort(m.places, m.expected, this.carsMissing(m))
      : this.c.needEnough(m.places, m.expected);
  }

  /** The car that can go: empty, and its driver has driven the most. */
  protected spare(m: DriverPlanMatchDay): Car | null {
    const empty = m.cars.filter((car) => car.passengers.length === 0);
    if (empty.length === 0) return null;
    return empty.reduce((a, b) => (this.store.tripsOf(b.driver.id) > this.store.tripsOf(a.driver.id) ? b : a));
  }

  /** The driver cannot: the next in line takes over (DRV-31). */
  protected async pass(m: DriverPlanMatchDay, car: Car): Promise<void> {
    await this.store.next(m, car.driver);
  }

  /** One car less: the empty car of the person who has driven the most. */
  protected async less(m: DriverPlanMatchDay): Promise<void> {
    const car = this.spare(m);
    if (car) await this.store.removeCar(m, car.id, car.driver);
  }

  protected async driveMyself(m: DriverPlanMatchDay): Promise<void> {
    const me = this.store.plan()?.drivers.find((d) => d.person.id === this.store.meId())?.person;
    if (me) await this.store.addDriver(m, me);
  }

  /**
   * «Autos anpassen»: fits the cars to the people who come. Everyone gets a seat (the next in line bring cars if
   * some are missing), then cars that stayed empty are taken off, as long as everyone still has a seat.
   */
  protected async adjust(m: DriverPlanMatchDay): Promise<void> {
    const removed: string[] = [];
    for (let round = 0; round < 8; round++) {
      if (!(await this.store.fill(m.eventId))) return;
      const list = this.store.lists()[m.eventId];
      const day = this.store.matchDays().find((x) => x.eventId === m.eventId);
      if (!list || !day || list.needSeat.length > 0 || list.cars.length <= 1) break;
      if (!list.cars.some((car) => car.passengers.length > 0)) break;
      // An empty car can go if its driver finds a seat in another car
      const car = this.spare(day);
      const freeElsewhere = list.cars.filter((x) => x.id !== car?.id).reduce((sum, x) => sum + x.freeSeats, 0);
      if (!car || freeElsewhere < 1) break;
      if (!(await this.store.removeCar(day, car.id, car.driver))) return;
      removed.push(name(car.driver));
    }
    const after = this.store.lists()[m.eventId];
    if (after && after.needSeat.length === 0 && after.cars.some((car) => car.passengers.length > 0)) {
      // From now on the match day counts with the people who really come, not with the assumption of the season
      const people = after.cars.reduce((sum, car) => sum + 1 + car.passengers.length, 0);
      await this.store.setMatchDayExpected(m.eventId, people);
      this.store.success.set(this.c.adjusted(after.cars.length, people, removed.join(', ')));
    }
  }
}
