import { HttpClient } from '@angular/common/http';
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
import { RouterLink } from '@angular/router';
import { Observable, firstValueFrom } from 'rxjs';
import type { Car, DriverList, PersonSummary } from '../api/types';
import { errorText, fieldErrorsOf } from '../core/error-text';
import { name } from '../core/format';
import { TEXTS } from '../core/texts';

const SEAT_OPTIONS = [1, 2, 3, 4, 5, 6, 7, 8];

/** «23.4» */
export function km(value: number | null | undefined): string {
  return (value ?? 0).toFixed(1);
}

/**
 * Section «Fahrerliste» of a match day (DRV-01 to DRV-11): route with credit, cars with passengers, who has no
 * seat. Members enter themselves as driver or passenger; coaches and admins also others and the kilometres by
 * hand. Every change answers with the whole list; the backend decides what is allowed (RR-07).
 */
@Component({
  selector: 'app-driver-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <section
      class="card driver-list"
      aria-labelledby="driver-list-title"
      data-testid="driver-list"
      [attr.aria-busy]="loading() ? 'true' : null"
    >
      <h2 id="driver-list-title" class="section-title">{{ s.title }}</h2>
      @if (message(); as m) {
        <p class="message-success" role="status">{{ m }}</p>
      }
      @if (error(); as m) {
        <p class="message-error" role="alert">{{ m }}</p>
      }
      @if (loading() && !data()) {
        <p class="empty">{{ texts.common.loading }}</p>
      } @else if (data(); as d) {
        @if (!d.ready) {
          <p data-testid="driver-list-missing">{{ missingText(d) }}</p>
          @if (canEditEvent()) {
            <p class="secondary-text">{{ s.missingCoach }}</p>
          }
        } @else {
          @if (d.cancelled) {
            <p class="message-notice" data-testid="driver-list-cancelled">{{ s.cancelled }}</p>
          }
          <div
            class="route"
            data-testid="driver-route"
            [attr.data-route-status]="d.route?.status ?? 'NONE'"
          >
            <h3 class="subtitle">{{ s.route }}</h3>
            @if (d.route; as r) {
              <p>{{ s.routeFromTo(r.from, r.to) }}</p>
              @if (has(r.distanceKm)) {
                <p class="distance" data-testid="driver-route-distance">
                  <strong>{{ s.distance(km(r.distanceKm), r.durationMinutes) }}</strong>
                  @if (r.status === 'MANUAL') {
                    <span class="chip" data-kind="manual">{{ s.manual }}</span>
                  }
                </p>
                @if (has(r.creditKm)) {
                  <p class="secondary-text" data-testid="driver-route-credit">{{ s.credit(km(r.creditKm)) }}</p>
                }
              } @else if (r.status === 'UNAVAILABLE') {
                <p class="message-notice">{{ s.unavailable }}</p>
              } @else if (r.status === 'NOT_FOUND') {
                <p class="message-notice">{{ s.notFound }}</p>
              }
            } @else {
              <p class="secondary-text">{{ s.pending }}</p>
            }
            @if (d.canManage) {
              <div class="buttons">
                <button
                  type="button"
                  class="button button-secondary button-small"
                  [disabled]="running()"
                  (click)="openKm()"
                >
                  {{ s.enterKm }}
                </button>
                <button
                  type="button"
                  class="button button-secondary button-small"
                  [disabled]="running()"
                  [attr.aria-busy]="busy() === 'recalculate' ? 'true' : null"
                  (click)="recalculate()"
                >
                  {{ s.recalculate }}
                </button>
              </div>
            }
          </div>

          <h3 class="subtitle" id="driver-list-cars">{{ s.cars }}</h3>
          @if (d.cars.length === 0) {
            <p class="empty">{{ s.noCars }}</p>
          } @else {
            <ul class="list cars" aria-labelledby="driver-list-cars" data-testid="cars">
              @for (c of d.cars; track c.id) {
                <li
                  class="car"
                  [attr.aria-label]="s.carOf(name(c.driver))"
                  [attr.data-testid]="'car-' + c.id"
                  [attr.data-free-seats]="c.freeSeats"
                >
                  <div class="car-head">
                    <span class="row-title">
                      {{ name(c.driver) }}
                      @if (c.driver.id === meId()) {
                        <span class="secondary-text">{{ s.you }}</span>
                      }
                    </span>
                    <span
                      class="chip"
                      [attr.data-kind]="c.freeSeats === 0 ? 'full' : 'free'"
                      data-testid="car-seats"
                      >{{ c.freeSeats === 0 ? s.full : s.seatsFree(c.freeSeats, c.seats) }}</span
                    >
                    @if (has(c.creditKm)) {
                      <span class="secondary-text">{{ s.carCredit(km(c.creditKm)) }}</span>
                    }
                  </div>
                  @if (c.passengers.length === 0) {
                    <p class="empty">{{ s.noPassengers }}</p>
                  } @else {
                    <ul class="passengers" [attr.aria-label]="s.passengersOf(name(c.driver))">
                      @for (p of c.passengers; track p.id) {
                        <li [attr.aria-label]="name(p)">
                          <span>
                            {{ name(p) }}
                            @if (p.id === meId()) {
                              <span class="secondary-text">{{ s.you }}</span>
                            }
                          </span>
                          @if (p.id === meId() && canChange(d)) {
                            <button
                              type="button"
                              class="button button-secondary button-small"
                              [disabled]="running()"
                              (click)="leave(c, p)"
                            >
                              {{ s.leave }}
                            </button>
                          } @else if (d.canManage) {
                            <button
                              type="button"
                              class="button button-secondary button-small"
                              [disabled]="running()"
                              (click)="leave(c, p)"
                            >
                              {{ s.removePassenger(name(p)) }}
                            </button>
                          }
                        </li>
                      }
                    </ul>
                  }
                  <div class="car-actions">
                    @if (canRide(d, c)) {
                      <button
                        type="button"
                        class="button button-primary button-small"
                        [disabled]="running()"
                        [attr.aria-busy]="busy() === 'ride-' + c.id ? 'true' : null"
                        (click)="ride(c)"
                      >
                        {{ s.rideWith(name(c.driver)) }}
                      </button>
                    }
                    @if (canEditCar(d, c)) {
                      <div class="field inline">
                        <label [for]="'seats-' + c.id">{{ s.seatsOf(name(c.driver)) }}</label>
                        <select
                          [id]="'seats-' + c.id"
                          [value]="c.seats"
                          [disabled]="running()"
                          (change)="changeSeats(c, $any($event.target))"
                        >
                          @for (n of seatOptionsFor(c); track n) {
                            <option [value]="n" [selected]="n === c.seats">{{ n }}</option>
                          }
                        </select>
                      </div>
                      <button
                        type="button"
                        class="button button-secondary button-small"
                        [disabled]="running()"
                        (click)="askRemove(c)"
                      >
                        {{ c.driver.id === meId() ? s.stopDriving : s.removeCar(name(c.driver)) }}
                      </button>
                    }
                    @if (d.canManage && c.freeSeats > 0) {
                      <form class="assign" (submit)="assign($event, c)">
                        <div class="field inline">
                          <label [for]="'assign-' + c.id">{{ s.assignTo(name(c.driver)) }}</label>
                          <select [id]="'assign-' + c.id" [disabled]="running()">
                            <option value="">{{ s.choose }}</option>
                            @for (p of candidatesFor(d, c); track p.person.id) {
                              <option [value]="p.person.id">{{ p.label }}</option>
                            }
                          </select>
                        </div>
                        <button
                          type="submit"
                          class="button button-secondary button-small"
                          [disabled]="running()"
                        >
                          {{ s.assign }}
                        </button>
                      </form>
                    }
                  </div>
                </li>
              }
            </ul>
          }

          @if (canChange(d) && !isDriver(d)) {
            <form class="own-car" (submit)="drive($event)">
              <div class="field inline">
                <label for="driver-list-my-seats">{{ s.mySeats }}</label>
                <select id="driver-list-my-seats" #mySeats [disabled]="running()">
                  @for (n of seatOptions; track n) {
                    <option [value]="n" [selected]="n === 3">{{ n }}</option>
                  }
                </select>
              </div>
              <button
                type="submit"
                class="button button-primary button-small"
                [disabled]="running()"
                [attr.aria-busy]="busy() === 'drive' ? 'true' : null"
              >
                {{ s.iDrive }}
              </button>
            </form>
          }

          @if (d.canManage) {
            <form class="enter-driver" aria-labelledby="driver-list-enter" (submit)="enterDriver($event)">
              <h3 class="subtitle" id="driver-list-enter">{{ s.enterDriver }}</h3>
              <div class="field-row">
                <div class="field">
                  <label for="driver-list-driver">{{ s.driver }}</label>
                  <select id="driver-list-driver" #driver [disabled]="running()">
                    <option value="">{{ s.choose }}</option>
                    @for (p of driverCandidates(d); track p.person.id) {
                      <option [value]="p.person.id">{{ p.label }}</option>
                    }
                  </select>
                </div>
                <div class="field">
                  <label for="driver-list-seats">{{ s.seats }}</label>
                  <select id="driver-list-seats" #driverSeats [disabled]="running()">
                    @for (n of seatOptions; track n) {
                      <option [value]="n" [selected]="n === 3">{{ n }}</option>
                    }
                  </select>
                </div>
              </div>
              <button type="submit" class="button button-secondary button-small" [disabled]="running()">
                {{ s.enter }}
              </button>
            </form>
          }

          <h3 class="subtitle" id="driver-list-without-seat">{{ s.withoutSeat }}</h3>
          <p class="secondary-text">
            {{ d.callUpPublished ? s.withoutSeatCalledUp : s.withoutSeatAttending }}
          </p>
          @if (d.withoutSeat.length === 0) {
            <p class="empty" data-testid="without-seat-empty">{{ s.everyoneSeated }}</p>
          } @else {
            <ul class="list" aria-labelledby="driver-list-without-seat" data-testid="without-seat">
              @for (p of d.withoutSeat; track p.id) {
                <li class="row" [attr.aria-label]="name(p)">{{ name(p) }}</li>
              }
            </ul>
          }
        }
        <div class="links">
          @if (teamId(); as team) {
            <!-- Often one person plans the drivers of the whole season (DRV-14) -->
            <a
              [routerLink]="['/teams', team, 'fahrerliste']"
              class="summary-link"
              data-testid="driver-plan-link"
              >{{ texts.driverPlan.link }}</a
            >
          }
          <a routerLink="/fahrten" class="summary-link" data-testid="driver-summary-link">{{ s.summaryLink }}</a>
        </div>
      }
    </section>

    <dialog
      #kmDialog
      class="card dialog"
      aria-modal="true"
      aria-labelledby="driver-km-title"
      aria-describedby="driver-km-text"
    >
      <form class="form" (submit)="saveKm($event)" novalidate>
        <h2 id="driver-km-title">{{ s.kmTitle }}</h2>
        <p class="secondary-text" id="driver-km-text">{{ s.kmText }}</p>
        <div class="field">
          <label for="driver-km-field">{{ s.kmField }}</label>
          <input
            #kmField
            id="driver-km-field"
            type="text"
            inputmode="decimal"
            autocomplete="off"
            [value]="kmValue()"
            (input)="kmValue.set($any($event.target).value)"
            [attr.aria-invalid]="kmError() ? 'true' : null"
            [attr.aria-describedby]="kmError() ? 'driver-km-error' : null"
          />
          @if (kmError(); as m) {
            <p class="field-error" id="driver-km-error">{{ m }}</p>
          }
        </div>
        <div class="dialog-buttons">
          <button
            type="submit"
            class="button button-primary button-small"
            [disabled]="running()"
            [attr.aria-busy]="running() ? 'true' : null"
          >
            {{ s.kmSave }}
          </button>
          <button type="button" class="button button-secondary button-small" (click)="closeKm()">
            {{ s.cancel }}
          </button>
        </div>
      </form>
    </dialog>

    <dialog
      #removeDialog
      class="card dialog"
      aria-modal="true"
      aria-labelledby="driver-remove-title"
      aria-describedby="driver-remove-text"
      (close)="removing.set(null)"
    >
      @if (removing(); as c) {
        <h2 id="driver-remove-title">
          {{ c.driver.id === meId() ? s.removeOwnTitle : s.removeTitle(name(c.driver)) }}
        </h2>
        <p id="driver-remove-text">{{ s.removeText(c.passengers.length) }}</p>
        <div class="dialog-buttons">
          <button
            type="button"
            class="button button-primary button-small"
            [disabled]="running()"
            [attr.aria-busy]="running() ? 'true' : null"
            (click)="removeCar(c)"
          >
            {{ s.remove }}
          </button>
          <button type="button" class="button button-secondary button-small" (click)="closeRemove()">
            {{ s.cancel }}
          </button>
        </div>
      }
    </dialog>
  `,
  styleUrl: './driver-list.css',
})
export class DriverListSection {
  private readonly http = inject(HttpClient);
  protected readonly texts = TEXTS;
  protected readonly s = TEXTS.driverList;
  protected readonly name = name;
  protected readonly km = km;
  protected readonly seatOptions = SEAT_OPTIONS;
  protected readonly has = (v: number | null | undefined): v is number => v !== null && v !== undefined;

  readonly eventId = input.required<string>();
  /** Team of the match day, for the link to its driver plan of the season (DRV-14). */
  readonly teamId = input<string | null>(null);
  /** ID of the signed-in person. */
  readonly meId = input<string | null>(null);
  /** Coach or admin who may edit the event: hint where meeting point and venue are entered. */
  readonly canEditEvent = input(false);
  /** Changes when the event page reloads (e.g. after a response), so «Ohne Platz» stays current. */
  readonly version = input(0);

  protected readonly data = signal<DriverList | null>(null);
  protected readonly loading = signal(true);
  protected readonly busy = signal<string | null>(null);
  protected readonly running = computed(() => this.busy() !== null);
  protected readonly message = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly removing = signal<Car | null>(null);
  protected readonly kmValue = signal('');
  protected readonly kmError = signal<string | null>(null);

  private readonly kmDialog = viewChild<ElementRef<HTMLDialogElement>>('kmDialog');
  private readonly kmField = viewChild<ElementRef<HTMLInputElement>>('kmField');
  private readonly removeDialog = viewChild<ElementRef<HTMLDialogElement>>('removeDialog');
  private readonly mySeats = viewChild<ElementRef<HTMLSelectElement>>('mySeats');
  private readonly driverSelect = viewChild<ElementRef<HTMLSelectElement>>('driver');
  private readonly driverSeats = viewChild<ElementRef<HTMLSelectElement>>('driverSeats');

  constructor() {
    effect(() => {
      const id = this.eventId();
      this.version();
      untracked(() => void this.load(id));
    });
  }

  protected missingText(d: DriverList): string {
    return this.s.missing(d.missing.map((m) => this.s.missingPart[m] ?? m));
  }

  /** The caller can enter herself right now (member, list usable). */
  protected canChange(d: DriverList): boolean {
    return d.canJoin;
  }

  protected isDriver(d: DriverList): boolean {
    return d.cars.some((c) => c.driver.id === this.meId());
  }

  private seatOf(d: DriverList, personId: string): Car | undefined {
    return d.cars.find((c) => c.passengers.some((p) => p.id === personId));
  }

  /** «Bei <Name> mitfahren»: member, not a driver, not already in this car, a seat free. */
  protected canRide(d: DriverList, c: Car): boolean {
    const me = this.meId();
    return (
      d.canJoin &&
      !!me &&
      c.freeSeats > 0 &&
      c.driver.id !== me &&
      !this.isDriver(d) &&
      !c.passengers.some((p) => p.id === me)
    );
  }

  protected canEditCar(d: DriverList, c: Car): boolean {
    return d.canManage || (d.canJoin && c.driver.id === this.meId());
  }

  /** Members who can be put into this car: not driving, not already in it; «(bei X)» if seated elsewhere. */
  protected candidatesFor(d: DriverList, c: Car): { person: PersonSummary; label: string }[] {
    const drivers = new Set(d.cars.map((x) => x.driver.id));
    return (d.members ?? [])
      .filter((p) => !drivers.has(p.id) && !c.passengers.some((x) => x.id === p.id))
      .map((p) => {
        const elsewhere = this.seatOf(d, p.id);
        return {
          person: p,
          label: elsewhere ? `${name(p)} ${this.s.seatedWith(name(elsewhere.driver))}` : name(p),
        };
      });
  }

  /** Members who do not drive yet; «(bei X)» if they sit in a car. */
  protected driverCandidates(d: DriverList): { person: PersonSummary; label: string }[] {
    const drivers = new Set(d.cars.map((x) => x.driver.id));
    return (d.members ?? [])
      .filter((p) => !drivers.has(p.id))
      .map((p) => {
        const seat = this.seatOf(d, p.id);
        return { person: p, label: seat ? `${name(p)} ${this.s.seatedWith(name(seat.driver))}` : name(p) };
      });
  }

  private personName(d: DriverList, id: string): string {
    const p = (d.members ?? []).find((m) => m.id === id);
    return p ? name(p) : '';
  }

  // ------------------------------------------------------------------ Actions

  protected drive(domEvent: Event): void {
    domEvent.preventDefault();
    const seats = Number(this.mySeats()?.nativeElement.value ?? 3);
    void this.run('drive', this.http.post<DriverList>(this.url('/cars'), { seats }), () =>
      this.s.driving(null, seats),
    );
  }

  protected enterDriver(domEvent: Event): void {
    domEvent.preventDefault();
    const d = this.data();
    const driverId = this.driverSelect()?.nativeElement.value ?? '';
    if (!d || !driverId) {
      this.error.set(this.s.choose);
      return;
    }
    const seats = Number(this.driverSeats()?.nativeElement.value ?? 3);
    const who = driverId === this.meId() ? null : this.personName(d, driverId);
    void this.run('enter', this.http.post<DriverList>(this.url('/cars'), { driverId, seats }), () =>
      this.s.driving(who, seats),
    );
  }

  /** Seats 1–8, but never fewer than the passengers already in the car (DRV-03). */
  protected seatOptionsFor(c: Car): number[] {
    return SEAT_OPTIONS.filter((n) => n >= c.passengers.length);
  }

  protected async changeSeats(c: Car, select: HTMLSelectElement): Promise<void> {
    const seats = Number(select.value);
    await this.run('seats-' + c.id, this.http.put<DriverList>(this.url(`/cars/${c.id}`), { seats }), () =>
      this.s.seatsSaved(seats),
    );
    // After a rejection the bindings do not change, so Angular leaves the DOM on the rejected value: show the saved one
    const saved = this.data()?.cars.find((car) => car.id === c.id)?.seats;
    if (saved != null) select.value = String(saved);
  }

  protected askRemove(c: Car): void {
    this.removing.set(c);
    const dialog = this.removeDialog()?.nativeElement;
    if (dialog && !dialog.open) dialog.showModal();
  }

  protected closeRemove(): void {
    this.removeDialog()?.nativeElement.close();
  }

  protected async removeCar(c: Car): Promise<void> {
    const driver = name(c.driver);
    await this.run('remove-' + c.id, this.http.delete<DriverList>(this.url(`/cars/${c.id}`)), () =>
      this.s.carRemoved(driver),
    );
    this.closeRemove();
  }

  protected ride(c: Car): void {
    const me = this.meId();
    if (!me) return;
    void this.run('ride-' + c.id, this.http.put<DriverList>(this.url(`/cars/${c.id}/passengers/${me}`), {}), () =>
      this.s.riding(null, name(c.driver)),
    );
  }

  protected assign(domEvent: Event, c: Car): void {
    domEvent.preventDefault();
    const select = (domEvent.target as HTMLFormElement).querySelector('select');
    const personId = select?.value ?? '';
    const d = this.data();
    if (!d || !personId) {
      this.error.set(this.s.choose);
      return;
    }
    const who = personId === this.meId() ? null : this.personName(d, personId);
    void this.run(
      'assign-' + c.id,
      this.http.put<DriverList>(this.url(`/cars/${c.id}/passengers/${personId}`), {}),
      () => this.s.riding(who, name(c.driver)),
    );
  }

  protected leave(c: Car, p: PersonSummary): void {
    const who = p.id === this.meId() ? null : name(p);
    void this.run(
      'leave-' + c.id + p.id,
      this.http.delete<DriverList>(this.url(`/cars/${c.id}/passengers/${p.id}`)),
      () => this.s.left(who, name(c.driver)),
    );
  }

  protected recalculate(): void {
    void this.run('recalculate', this.http.post<DriverList>(this.url('/route/recalculate'), {}), () =>
      this.s.recalculated,
    );
  }

  protected openKm(): void {
    const r = this.data()?.route;
    this.kmValue.set(r?.distanceKm != null ? km(r.distanceKm) : '');
    this.kmError.set(null);
    const dialog = this.kmDialog()?.nativeElement;
    if (dialog && !dialog.open) dialog.showModal();
  }

  protected closeKm(): void {
    this.kmDialog()?.nativeElement.close();
  }

  protected async saveKm(domEvent: Event): Promise<void> {
    domEvent.preventDefault();
    if (this.running()) return;
    const value = Number(this.kmValue().trim().replace(',', '.'));
    if (!this.kmValue().trim() || !Number.isFinite(value) || value < 0.1 || value > 1000) {
      this.kmError.set(this.s.kmFormat);
      this.kmField()?.nativeElement.focus();
      return;
    }
    this.kmError.set(null);
    this.busy.set('km');
    this.message.set(null);
    this.error.set(null);
    try {
      this.data.set(await firstValueFrom(this.http.put<DriverList>(this.url('/route'), { distanceKm: value })));
      this.closeKm();
      this.message.set(this.s.kmSaved);
    } catch (e) {
      const fields = fieldErrorsOf(e);
      if (fields['distanceKm']) {
        this.kmError.set(fields['distanceKm']);
      } else {
        this.closeKm();
        this.error.set(errorText(e));
      }
    } finally {
      this.busy.set(null);
    }
  }

  // ------------------------------------------------------------------ Helpers

  private url(path = ''): string {
    return `/api/events/${this.eventId()}/driver-list${path}`;
  }

  private async run(key: string, request: Observable<DriverList>, success: () => string): Promise<void> {
    if (this.running()) return;
    this.busy.set(key);
    this.message.set(null);
    this.error.set(null);
    try {
      this.data.set(await firstValueFrom(request));
      this.message.set(success());
    } catch (e) {
      this.error.set(errorText(e));
      // The list may have changed meanwhile (e.g. car full): show the current state
      await this.load(this.eventId(), false);
    } finally {
      this.busy.set(null);
    }
  }

  private async load(id: string, showLoading = true): Promise<void> {
    if (showLoading) this.loading.set(true);
    try {
      this.data.set(await firstValueFrom(this.http.get<DriverList>(`/api/events/${id}/driver-list`)));
    } catch (e) {
      if (showLoading) this.error.set(errorText(e));
    } finally {
      this.loading.set(false);
    }
  }
}
