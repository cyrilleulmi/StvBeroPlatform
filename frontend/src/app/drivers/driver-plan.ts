import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { Car, DriverPlan, DriverPlanMatchDay, DriverSetting } from '../api/types';
import { clockTime, eventDate, yearOf } from '../attendance/time';
import { AuthService } from '../core/auth.service';
import { errorStatus, errorText, fieldErrorsOf } from '../core/error-text';
import { name } from '../core/format';
import { TEXTS } from '../core/texts';

/**
 * Driver plan of a team for a whole season (`/teams/:teamId/fahrerliste`, DRV-14, DRV-17): every match day with
 * its cars, a form to enter one person for several match days at once, and how the trips are shared. Members
 * enter themselves, coaches and admins also others. `?saison=<id>` shows another season.
 */
@Component({
  selector: 'app-driver-plan',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <div class="header">
      <a class="back" [routerLink]="['/teams', teamId()]">{{ data()?.teamName ?? texts.teams.title }}</a>
      <h1 class="page-title">{{ t.title }}</h1>
      @if (data(); as d) {
        <p class="secondary-text" data-testid="driver-plan-season">
          {{ d.teamName }} · {{ texts.teamAdmin.season }} {{ d.seasonName }}
        </p>
      }
    </div>
    @if (success(); as m) {
      <p class="message-success" role="status">{{ m }}</p>
    }
    @if (error(); as m) {
      <p class="message-error" role="alert">{{ m }}</p>
    }
    <div class="sections" [attr.aria-busy]="loading() ? 'true' : null">
      @if (loading()) {
        <p class="empty">{{ texts.common.loading }}</p>
      } @else if (data(); as d) {
        <p class="secondary-text">{{ t.explanation }}</p>

        <section class="card" aria-labelledby="plan-match-days-title" data-testid="driver-plan-match-days">
          <h2 id="plan-match-days-title" class="section-title">
            {{ t.matchDays }} <span class="count">{{ d.matchDays.length }}</span>
          </h2>
          @if (d.matchDays.length === 0) {
            <p class="empty">{{ t.noMatchDays }}</p>
          } @else {
            <ol class="match-days" aria-labelledby="plan-match-days-title">
              @for (m of d.matchDays; track m.eventId) {
                <li
                  class="match-day"
                  [attr.aria-label]="label(m)"
                  [attr.data-testid]="'plan-match-day-' + m.eventId"
                  [attr.data-state]="m.cancelled ? 'cancelled' : m.over ? 'over' : 'upcoming'"
                >
                  <div class="match-day-head">
                    <span class="date">{{ label(m) }}</span>
                    @if (m.cancelled) {
                      <span class="chip" data-kind="cancelled">{{ t.cancelled }}</span>
                    } @else if (m.over) {
                      <span class="chip" data-kind="over">{{ t.over }}</span>
                    }
                    <a
                      class="open"
                      [routerLink]="['/termine', m.eventId]"
                      [attr.aria-label]="t.openFor(label(m))"
                      >{{ t.open }}</a
                    >
                  </div>
                  <p class="secondary-text" data-testid="plan-venue">
                    {{ venue(m) }}
                    @if (m.distanceKm !== undefined && m.distanceKm !== null) {
                      · {{ t.distance(m.distanceKm.toFixed(1), m.durationMinutes ?? null) }}
                    }
                  </p>
                  @if (!m.ready) {
                    <p class="notice" data-testid="plan-missing">{{ t.notReady(missing(m)) }}</p>
                  } @else if (m.cars.length === 0) {
                    <p class="secondary-text" data-testid="plan-no-cars">{{ t.noCars }}</p>
                  } @else {
                    <ul class="cars" [attr.aria-label]="texts.driverList.cars">
                      @for (car of m.cars; track car.id) {
                        <li
                          class="car"
                          [attr.aria-label]="name(car.driver)"
                          [attr.data-testid]="'plan-car-' + car.driver.id"
                          [attr.data-free]="car.freeSeats"
                        >
                          <span class="car-text">
                            <span class="car-title">{{ t.car(name(car.driver), car.freeSeats, car.seats) }}</span>
                            @if (car.passengers.length > 0) {
                              <span class="secondary-text">{{ t.passengers(passengers(car)) }}</span>
                            }
                          </span>
                          @if (m.changeable && (d.canManage || car.driver.id === meId())) {
                            <button
                              type="button"
                              class="button button-secondary button-small"
                              [disabled]="busy()"
                              [attr.aria-busy]="running() === 'remove:' + car.id ? 'true' : null"
                              (click)="remove(m, car)"
                            >
                              {{ t.remove(name(car.driver)) }}
                            </button>
                          }
                        </li>
                      }
                    </ul>
                  }
                </li>
              }
            </ol>
          }
        </section>

        @if (d.canJoin || d.canManage) {
          <form
            class="card form"
            (submit)="enter($event)"
            novalidate
            aria-labelledby="plan-form-title"
            data-testid="driver-plan-form"
          >
            <h2 id="plan-form-title" class="section-title">{{ t.planTitle }}</h2>
            @if (plannable().length === 0) {
              <p class="empty">{{ t.nothingToPlan }}</p>
            } @else {
              <p class="secondary-text">{{ t.planText }}</p>
              <div class="field-row">
                @if (d.canManage) {
                  <div class="field">
                    <label for="plan-driver">{{ t.driver }}</label>
                    <select id="plan-driver" (change)="setDriver($any($event.target).value)">
                      @for (p of d.members ?? []; track p.id) {
                        <option [value]="p.id" [selected]="driver() === p.id">
                          {{ name(p) }}{{ p.id === meId() ? ' (' + t.me + ')' : '' }}
                        </option>
                      }
                    </select>
                  </div>
                }
                <div class="field">
                  <label for="plan-seats">{{ t.seats }}</label>
                  <input
                    id="plan-seats"
                    type="text"
                    inputmode="numeric"
                    maxlength="1"
                    autocomplete="off"
                    [value]="seats()"
                    (input)="seats.set($any($event.target).value)"
                    [attr.aria-invalid]="seatsError() ? 'true' : null"
                    [attr.aria-describedby]="seatsError() ? 'plan-seats-error' : null"
                  />
                  @if (seatsError(); as m) {
                    <p class="field-error" id="plan-seats-error">{{ m }}</p>
                  }
                </div>
              </div>
              <fieldset
                class="choices"
                [attr.aria-invalid]="selectionError() ? 'true' : null"
                [attr.aria-describedby]="selectionError() ? 'plan-selection-error' : null"
              >
                <legend>{{ t.chooseMatchDays }}</legend>
                <div class="choice-buttons">
                  <button type="button" class="button button-secondary button-small" (click)="selectWithoutCar()">
                    {{ t.selectWithoutCar }}
                  </button>
                  <button type="button" class="button button-secondary button-small" (click)="selected.set([])">
                    {{ t.clearSelection }}
                  </button>
                </div>
                @for (m of plannable(); track m.eventId) {
                  <label class="choice" [attr.data-testid]="'plan-choice-' + m.eventId">
                    <input
                      type="checkbox"
                      [checked]="isSelected(m.eventId)"
                      [disabled]="drives(m)"
                      (change)="toggle(m.eventId, $any($event.target).checked)"
                    />
                    <span>
                      {{ t.matchDayOption(label(m), venue(m)) }}
                      @if (drives(m)) {
                        <span class="secondary-text">({{ t.alreadyDrives }})</span>
                      }
                    </span>
                  </label>
                }
              </fieldset>
              @if (selectionError(); as m) {
                <p class="field-error" id="plan-selection-error">{{ m }}</p>
              }
              <button
                type="submit"
                class="button button-primary"
                [disabled]="busy()"
                [attr.aria-busy]="running() === 'enter' ? 'true' : null"
              >
                {{ t.enter }}
              </button>
            }
          </form>
        }

        <section class="card" aria-labelledby="plan-shares-title" data-testid="driver-plan-shares">
          <h2 id="plan-shares-title" class="section-title">{{ t.shares }}</h2>
          <p class="secondary-text">{{ t.sharesText }}</p>
          <div class="scroll" tabindex="0" role="region" [attr.aria-label]="t.shares">
            <table [attr.aria-label]="t.shares">
              <thead>
                <tr>
                  <th scope="col">{{ t.person }}</th>
                  <th scope="col" class="number">{{ t.done }}</th>
                  <th scope="col" class="number">{{ t.planned }}</th>
                  <th scope="col" class="number">{{ t.total }}</th>
                  <th scope="col" class="number">{{ t.kilometres }}</th>
                </tr>
              </thead>
              <tbody>
                @for (share of d.shares; track share.person.id) {
                  <tr
                    [attr.data-testid]="'plan-share-' + share.person.id"
                    [attr.data-total]="share.trips + share.plannedTrips"
                  >
                    <th scope="row">{{ name(share.person) }}</th>
                    <td class="number" data-column="done">{{ share.trips }}</td>
                    <td class="number" data-column="planned">{{ share.plannedTrips }}</td>
                    <td class="number" data-column="total">
                      <span class="bar" aria-hidden="true" [style.width.px]="(share.trips + share.plannedTrips) * 8"></span>
                      {{ share.trips + share.plannedTrips }}
                    </td>
                    <td class="number" data-column="kilometres">
                      {{ t.km(share.kilometres + share.plannedKilometres) }}
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </section>

        <!-- Who can drive, and with how many places: for the proposed driver list (DRV-18) -->
        <section class="card" aria-labelledby="plan-drivers-title" data-testid="driver-plan-drivers">
          <h2 id="plan-drivers-title" class="section-title">{{ texts.driving.planSection }}</h2>
          <p class="secondary-text">{{ texts.driving.planIntro }}</p>
          <ul class="list drivers" aria-labelledby="plan-drivers-title">
            @for (x of d.drivers; track x.person.id) {
              <li
                class="driver-row"
                [attr.aria-label]="name(x.person)"
                [attr.data-testid]="'plan-driver-' + x.person.id"
                [attr.data-can-drive]="x.canDrive"
              >
                <span class="row-title">{{ name(x.person) }}</span>
                @if (x.canChange) {
                  <label class="check">
                    <input
                      type="checkbox"
                      [checked]="x.canDrive"
                      [disabled]="busy()"
                      [attr.aria-label]="texts.driving.canDriveFor(name(x.person))"
                      (change)="setDriving(x, $any($event.target).checked, x.carSeats)"
                    />
                    {{ texts.driving.canDrive }}
                  </label>
                  @if (x.canDrive) {
                    <label class="seats-field">
                      <span>{{ texts.driving.seatsShort }}</span>
                      <select
                        [attr.aria-label]="texts.driving.seatsFor(name(x.person))"
                        [disabled]="busy()"
                        (change)="setDriving(x, true, +$any($event.target).value)"
                      >
                        @for (n of carSeatOptions; track n) {
                          <option [value]="n" [selected]="n === x.carSeats">{{ n }}</option>
                        }
                      </select>
                    </label>
                  }
                } @else {
                  <span class="secondary-text">{{
                    x.canDrive ? texts.driving.yes(x.carSeats) : texts.driving.no
                  }}</span>
                }
              </li>
            }
          </ul>
        </section>
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
    .card {
      display: grid;
      gap: 10px;
      min-width: 0;
    }
    .card p,
    .sections > p {
      margin: 0;
    }
    .match-days,
    .cars {
      list-style: none;
      margin: 0;
      padding: 0;
    }
    .match-day {
      display: grid;
      gap: 6px;
      padding: 12px 0;
      min-width: 0;
    }
    .match-day + .match-day {
      border-top: 1px solid var(--border);
    }
    .match-day[data-state='over'] .date,
    .match-day[data-state='cancelled'] .date {
      color: var(--muted);
    }
    .match-day-head {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 6px 10px;
    }
    .date {
      font-weight: 800;
    }
    .open {
      margin-left: auto;
      min-height: var(--touch);
      display: inline-flex;
      align-items: center;
      font-weight: 700;
      color: var(--primary);
    }
    .chip[data-kind='over'] {
      background: var(--border);
      color: var(--text);
    }
    .chip[data-kind='cancelled'] {
      background: var(--coral-soft);
      color: var(--text);
    }
    .notice {
      padding: 6px 10px;
      border-radius: var(--radius-icon);
      background: var(--mustard-soft);
      border: 1px solid var(--mustard);
      font-size: 14px;
      font-weight: 600;
    }
    .cars {
      display: grid;
      gap: 6px;
    }
    .car {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 6px 10px;
      padding: 8px 10px;
      border-radius: var(--radius-icon);
      background: var(--primary-soft);
      min-width: 0;
    }
    .car[data-free='0'] {
      background: var(--coral-soft);
    }
    .car-text {
      display: grid;
      gap: 2px;
      flex: 1 1 180px;
      min-width: 0;
      overflow-wrap: anywhere;
    }
    .car-title {
      font-weight: 700;
    }
    select,
    #plan-seats {
      min-height: var(--touch);
    }
    select {
      padding: 0 12px;
      border: 1.5px solid var(--control-border);
      border-radius: var(--radius-icon);
      background: var(--surface);
      color: var(--text);
      font: 600 15px/1.2 var(--font);
      max-width: 100%;
    }
    .choices {
      display: grid;
      gap: 4px;
      margin: 0;
      padding: 10px 12px;
      border: 1.5px solid var(--border);
      border-radius: var(--radius-icon);
      min-width: 0;
    }
    .choices legend {
      padding: 0 6px;
      font-weight: 700;
    }
    .choice-buttons {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      margin-bottom: 4px;
    }
    .choice {
      display: flex;
      align-items: center;
      gap: 10px;
      min-height: var(--touch);
      font-weight: 600;
      overflow-wrap: anywhere;
    }
    .choice input {
      flex: none;
      width: 20px;
      height: 20px;
    }
    form .button-primary {
      justify-self: start;
    }
    .scroll {
      overflow-x: auto;
      min-width: 0;
    }
    .driver-row {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 6px 16px;
      padding: 8px 0;
    }
    .driver-row .row-title {
      flex: 1 1 180px;
    }
    .driver-row .check,
    .seats-field {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      min-height: var(--touch);
      font-weight: 600;
    }
    .seats-field select {
      min-height: var(--touch);
      padding: 0 10px;
      border: 1.5px solid var(--control-border);
      border-radius: var(--radius-icon);
      background: var(--surface);
      color: var(--text);
      font: 600 14px/1.2 var(--font);
    }
    table {
      width: 100%;
      border-collapse: collapse;
    }
    th,
    td {
      text-align: left;
      padding: 10px 8px;
      border-bottom: 1px solid var(--border);
      vertical-align: middle;
    }
    thead th {
      font-size: 13px;
      color: var(--muted);
    }
    .number {
      text-align: right;
      white-space: nowrap;
      font-variant-numeric: tabular-nums;
    }
    .bar {
      display: inline-block;
      height: 8px;
      max-width: 64px;
      margin-right: 6px;
      border-radius: 4px;
      background: var(--primary);
      vertical-align: middle;
    }
  `,
})
export class DriverPlanPage {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  protected readonly texts = TEXTS;
  protected readonly t = TEXTS.driverPlan;
  protected readonly name = name;

  /** Route `/teams/:teamId/fahrerliste` */
  readonly teamId = input.required<string>();
  /** Query parameter `?saison=<id>`; without it the current season. */
  readonly saison = input<string | undefined>(undefined);

  protected readonly data = signal<DriverPlan | null>(null);
  protected readonly loading = signal(true);
  /** Which action is running: `enter` or `remove:<carId>`. */
  protected readonly running = signal<string | null>(null);
  protected readonly busy = computed(() => this.running() !== null);
  protected readonly error = signal<string | null>(null);
  protected readonly success = signal<string | null>(null);

  protected readonly driver = signal('');
  protected readonly seats = signal('3');
  protected readonly seatsError = signal<string | null>(null);
  protected readonly selected = signal<string[]>([]);
  protected readonly selectionError = signal<string | null>(null);

  protected readonly meId = computed(() => this.auth.me()?.id ?? null);
  private readonly year = computed(() => yearOf(this.auth.me()?.now));

  /** Match days on which the caller can enter a driver right now. */
  protected readonly plannable = computed(() => (this.data()?.matchDays ?? []).filter((m) => m.changeable));

  constructor() {
    effect(() => {
      const team = this.teamId();
      const season = this.saison();
      untracked(() => void this.load(team, season));
    });
  }

  /** «So 22.11. 09:30» */
  protected label(m: DriverPlanMatchDay): string {
    return `${eventDate(m.start, this.year())} ${clockTime(m.start)}`;
  }

  protected venue(m: DriverPlanMatchDay): string {
    return [m.venue, m.town].filter(Boolean).join(', ');
  }

  protected missing(m: DriverPlanMatchDay): string {
    return m.missing.map((what) => this.t.missing[what]).join(', ');
  }

  protected passengers(car: Car): string {
    return car.passengers.map(name).join(', ');
  }

  /** The chosen driver already has a car on this match day. */
  protected drives(m: DriverPlanMatchDay): boolean {
    const driver = this.driver();
    return m.cars.some((car) => car.driver.id === driver);
  }

  protected isSelected(eventId: string): boolean {
    return this.selected().includes(eventId);
  }

  protected toggle(eventId: string, on: boolean): void {
    this.selected.update((ids) => (on ? [...ids, eventId] : ids.filter((id) => id !== eventId)));
    this.selectionError.set(null);
  }

  protected readonly carSeatOptions = [2, 3, 4, 5, 6, 7, 8, 9];

  /** Seats for passengers the driver usually offers: her places minus herself (DRV-18). */
  private usualSeats(id: string): string {
    const places = this.data()?.drivers.find((x) => x.person.id === id)?.carSeats ?? 5;
    return String(Math.max(1, Math.min(8, places - 1)));
  }

  /** Coach, admin or the person herself: can drive and the places of the car (DRV-18). */
  protected async setDriving(x: DriverSetting, canDrive: boolean, carSeats: number): Promise<void> {
    if (this.busy()) return;
    this.running.set('driving-' + x.person.id);
    this.error.set(null);
    this.success.set(null);
    try {
      await firstValueFrom(
        this.http.put(`/api/persons/${x.person.id}/driving`, { canDrive, carSeats }),
      );
      this.apply(
        await firstValueFrom(
          this.http.get<DriverPlan>(`/api/teams/${this.teamId()}/driver-plan`, {
            params: this.params(),
          }),
        ),
      );
      this.success.set(
        x.person.id === this.meId()
          ? TEXTS.driving.saved
          : TEXTS.driving.savedFor(name(x.person)),
      );
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.running.set(null);
    }
  }

  protected setDriver(id: string): void {
    this.driver.set(id);
    this.seats.set(this.usualSeats(id));
    // Match days on which the new driver already drives are no longer a choice
    this.selected.update((ids) =>
      ids.filter((eventId) => !this.plannable().some((m) => m.eventId === eventId && this.drives(m))),
    );
  }

  /** Every match day that has no car yet and on which the chosen driver does not drive. */
  protected selectWithoutCar(): void {
    this.selected.set(
      this.plannable()
        .filter((m) => m.cars.length === 0 && !this.drives(m))
        .map((m) => m.eventId),
    );
    this.selectionError.set(null);
  }

  private params(): Record<string, string> {
    const season = this.saison();
    return season ? { season } : {};
  }

  private async load(team: string, season: string | undefined): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      const params: Record<string, string> = season ? { season } : {};
      this.apply(await firstValueFrom(this.http.get<DriverPlan>(`/api/teams/${team}/driver-plan`, { params })));
    } catch (e) {
      this.data.set(null);
      this.error.set(errorStatus(e) === 404 ? TEXTS.teams.notFound : errorText(e));
    } finally {
      this.loading.set(false);
    }
  }

  private apply(plan: DriverPlan): void {
    this.data.set(plan);
    const me = this.meId();
    const members = plan.members ?? [];
    if (!this.driver() || (plan.canManage && !members.some((p) => p.id === this.driver()))) {
      // Coaches plan for anyone of the team; everyone else for themselves
      this.driver.set(plan.canManage ? (members.find((p) => p.id === me)?.id ?? members[0]?.id ?? '') : (me ?? ''));
      this.seats.set(this.usualSeats(this.driver()));
    }
  }

  protected async enter(domEvent: Event): Promise<void> {
    domEvent.preventDefault();
    const plan = this.data();
    if (!plan || this.busy()) return;
    const seats = Number(this.seats().trim());
    this.seatsError.set(/^[1-8]$/.test(this.seats().trim()) ? null : this.t.seatsInvalid);
    const eventIds = this.selected();
    this.selectionError.set(eventIds.length === 0 ? this.t.noneSelected : null);
    if (this.seatsError() || this.selectionError()) return;
    const driverId = this.driver();
    const who =
      (plan.members ?? []).find((p) => p.id === driverId) ??
      plan.shares.find((share) => share.person.id === driverId)?.person;
    this.success.set(null);
    this.error.set(null);
    this.running.set('enter');
    try {
      const updated = await firstValueFrom(
        this.http.post<DriverPlan>(
          `/api/teams/${this.teamId()}/driver-plan/cars`,
          { driverId: plan.canManage ? driverId : null, seats, eventIds },
          { params: this.params() },
        ),
      );
      this.apply(updated);
      this.selected.set([]);
      this.success.set(this.t.entered(who ? name(who) : this.t.me, eventIds.length));
    } catch (e) {
      const fields = fieldErrorsOf(e);
      if (fields['seats']) {
        this.seatsError.set(fields['seats']);
      } else {
        this.error.set(fields['eventIds'] ?? errorText(e));
      }
    } finally {
      this.running.set(null);
    }
  }

  /** Takes a driver off a match day (DRV-02); her passengers lose their seat and are told (DRV-11). */
  protected async remove(m: DriverPlanMatchDay, car: Car): Promise<void> {
    if (this.busy()) return;
    this.success.set(null);
    this.error.set(null);
    this.running.set(`remove:${car.id}`);
    try {
      await firstValueFrom(this.http.delete(`/api/events/${m.eventId}/driver-list/cars/${car.id}`));
      this.apply(
        await firstValueFrom(
          this.http.get<DriverPlan>(`/api/teams/${this.teamId()}/driver-plan`, { params: this.params() }),
        ),
      );
      this.success.set(this.t.removed(name(car.driver), this.label(m)));
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.running.set(null);
    }
  }
}
