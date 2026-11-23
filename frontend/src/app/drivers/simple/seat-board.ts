import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked, viewChild } from '@angular/core';
import type { Car, DriverList, PersonSummary } from '../../api/types';
import { name } from '../../core/format';
import { Icon } from '../../core/icon';
import { TEXTS } from '../../core/texts';
import { ConfirmDialog } from './confirm-dialog';
import { PlanStore } from './plan-store';
import { Stepper } from './stepper';

/**
 * The cars of one match day with the people in them, as bubbles (DRV-03, DRV-19, DRV-32): everyone of the team
 * arranges them. Tap a person, then the car she rides in; «Alle verteilen» seats everyone at once. Drivers are
 * added and taken off here; a driver with passengers only after a question. Used on the season page below the grid
 * and on the match day behind «Plätze zuweisen».
 */
@Component({
  selector: 'app-seat-board',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, Stepper, ConfirmDialog],
  template: `
    @if (list(); as l) {
      <div class="seat-board" [attr.data-testid]="'seat-board-' + eventId()">
        <p class="status" [attr.data-state]="status().state" data-testid="seat-status">
          <app-icon
            [name]="status().state === 'short' ? 'triangle-alert' : status().state === 'ok' ? 'check' : 'users'"
            [size]="16"
          />
          {{ status().text }}
        </p>
        @if (!l.canArrange) {
          <p class="secondary-text">{{ t.readOnly }}</p>
        }
        @if (l.cars.length === 0) {
          <p class="empty">{{ t.noCars }}</p>
        } @else {
          <ul class="cars" [attr.aria-label]="t.cars">
            @for (car of l.cars; track car.id) {
              <li
                class="car"
                [attr.aria-label]="t.carOf(name(car.driver))"
                [attr.data-testid]="'seat-car-' + car.driver.id"
                [attr.data-free]="car.freeSeats"
                [attr.data-target]="canEnter(car) ? 'true' : null"
              >
                <div class="car-head">
                  <app-icon name="car" [size]="18" />
                  <span class="driver">{{ name(car.driver) }}</span>
                  <span class="secondary-text">{{ t.occupied(car.passengers.length, car.seats) }}</span>
                </div>
                <div class="people">
                  @for (p of car.passengers; track p.id) {
                    @if (l.canArrange) {
                      <button
                        type="button"
                        class="person"
                        [attr.aria-pressed]="selected()?.id === p.id"
                        [attr.data-me]="p.id === store.meId() ? 'true' : null"
                        [disabled]="store.busy()"
                        (click)="toggle(p)"
                      >
                        {{ name(p) }}
                      </button>
                    } @else {
                      <span class="person" [attr.data-me]="p.id === store.meId() ? 'true' : null">{{ name(p) }}</span>
                    }
                  }
                  @for (i of free(car); track i) {
                    <span class="person free" aria-hidden="true">{{ t.free }}</span>
                  }
                </div>
                @if (l.canArrange) {
                  <div class="car-actions">
                    @if (canEnter(car)) {
                      <button
                        type="button"
                        class="button button-primary button-small"
                        [disabled]="store.busy()"
                        [attr.data-testid]="'seat-enter-' + car.driver.id"
                        (click)="enter(car)"
                      >
                        {{ t.putHere(active()!.firstName) }}
                      </button>
                    }
                    @if (canLeave(car)) {
                      <button
                        type="button"
                        class="button button-secondary button-small"
                        [disabled]="store.busy()"
                        (click)="leave(car)"
                      >
                        {{ t.takeOut(active()!.firstName) }}
                      </button>
                    }
                    <span class="seats">
                      <span class="secondary-text">{{ t.seats }}</span>
                      <app-stepper
                        [value]="car.seats"
                        [min]="max(1, car.passengers.length)"
                        [max]="8"
                        [label]="t.seatsOf(name(car.driver))"
                        [disabled]="store.busy()"
                        (changed)="store.setSeats(eventId(), car.id, $event)"
                      />
                    </span>
                    <button
                      type="button"
                      class="button button-secondary button-small button-destructive remove"
                      [attr.data-testid]="'seat-remove-' + car.driver.id"
                      [disabled]="store.busy()"
                      (click)="remove(car)"
                    >
                      <app-icon name="x" [size]="16" />
                      {{ t.removeDriver(name(car.driver)) }}
                    </button>
                  </div>
                }
              </li>
            }
          </ul>
        }

        @if (l.needSeat.length > 0) {
          <div class="waiting" data-testid="seat-waiting">
            <h4>
              {{ t.waiting }} <span class="count">{{ l.needSeat.length }}</span>
            </h4>
            <div class="people">
              @for (p of l.needSeat; track p.id) {
                @if (l.canArrange) {
                  <button
                    type="button"
                    class="person waiting-person"
                    [attr.aria-pressed]="selected()?.id === p.id"
                    [attr.data-me]="p.id === store.meId() ? 'true' : null"
                    [disabled]="store.busy()"
                    (click)="toggle(p)"
                  >
                    {{ name(p) }}
                  </button>
                } @else {
                  <span class="person waiting-person">{{ name(p) }}</span>
                }
              }
            </div>
            @if (l.canArrange) {
              <p class="secondary-text hint">{{ selected() ? t.hintCar(selected()!.firstName) : t.hintPerson }}</p>
              <div class="waiting-actions">
                <button
                  type="button"
                  class="button button-primary button-small"
                  [disabled]="store.busy()"
                  [attr.aria-busy]="store.running() === 'fill:' + eventId() ? 'true' : null"
                  data-testid="seat-fill"
                  (click)="fill()"
                >
                  {{ t.fill }}
                </button>
                @if (selectedWaiting(); as p) {
                  @if (l.canManage || p.id === store.meId()) {
                    <button
                      type="button"
                      class="button button-secondary button-small"
                      data-testid="seat-individual"
                      [disabled]="store.busy()"
                      (click)="individual(p)"
                    >
                      {{ t.individual(p.firstName) }}
                    </button>
                  }
                }
              </div>
            }
          </div>
        }

        @if (l.canArrange && candidates(l).length > 0) {
          <!-- One more car: anyone of the team (DRV-02, DRV-32) -->
          <form class="add" aria-labelledby="seat-add-title" data-testid="seat-add-driver" (submit)="add($event)">
            <h4 id="seat-add-title">{{ t.addTitle }}</h4>
            <div class="add-fields">
              <div class="field">
                <label [for]="'seat-add-who-' + eventId()">{{ t.addWho }}</label>
                <select
                  [id]="'seat-add-who-' + eventId()"
                  name="seat-add-who"
                  [value]="newDriver()"
                  [disabled]="store.busy()"
                  (change)="pickDriver($any($event.target).value)"
                >
                  <option value="">{{ t.choose }}</option>
                  @for (c of candidates(l); track c.person.id) {
                    <option [value]="c.person.id" [selected]="c.person.id === newDriver()">{{ c.label }}</option>
                  }
                </select>
              </div>
              <span class="seats">
                <span class="secondary-text">{{ t.addSeats }}</span>
                <app-stepper
                  [value]="newSeats()"
                  [min]="1"
                  [max]="8"
                  [label]="t.addSeats"
                  [disabled]="store.busy()"
                  (changed)="newSeats.set($event)"
                />
              </span>
              <button
                type="submit"
                class="button button-secondary button-small"
                data-testid="seat-add"
                [disabled]="store.busy() || !newDriver()"
              >
                {{ t.add }}
              </button>
            </div>
          </form>
        }
        @if (l.individual.length > 0) {
          <p class="secondary-text">{{ t.individuals(names(l.individual)) }}</p>
        }
      </div>
      <app-confirm-dialog testId="seat-remove-dialog" />
    } @else {
      <p class="empty">{{ texts.common.loading }}</p>
    }
  `,
  styles: `
    .seat-board {
      display: grid;
      gap: 12px;
      min-width: 0;
    }
    p,
    h4 {
      margin: 0;
    }
    .status {
      display: flex;
      align-items: center;
      gap: 8px;
      font-weight: 700;
    }
    .status[data-state='ok'] {
      color: var(--tag-green);
    }
    .status[data-state='short'] {
      color: var(--coral);
    }
    .status[data-state='open'] {
      color: var(--text);
    }
    .cars {
      list-style: none;
      margin: 0;
      padding: 0;
      display: grid;
      gap: 10px;
      grid-template-columns: repeat(auto-fill, minmax(min(100%, 260px), 1fr));
    }
    .car {
      display: grid;
      gap: 8px;
      align-content: start;
      padding: 12px;
      border: 1.5px solid var(--border);
      border-radius: var(--radius-control);
      background: var(--surface);
      min-width: 0;
    }
    .car[data-target='true'] {
      border-color: var(--primary);
      background: var(--primary-soft);
    }
    .car-head {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 4px 8px;
    }
    .driver {
      font-weight: 800;
    }
    .people {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
    }
    .person {
      display: inline-flex;
      align-items: center;
      min-height: 36px;
      padding: 0 12px;
      border-radius: var(--radius-pill);
      border: 1.5px solid var(--control-border);
      background: var(--surface);
      color: var(--text);
      font: 600 14px/1.2 var(--font);
    }
    button.person {
      min-height: var(--touch);
      cursor: pointer;
    }
    .person[aria-pressed='true'] {
      background: var(--primary);
      border-color: var(--primary);
      color: var(--on-primary);
    }
    .person[data-me='true'] {
      border-color: var(--primary);
    }
    .person.free {
      border-style: dashed;
      border-color: var(--border);
      color: var(--muted);
      font-weight: 500;
    }
    .waiting-person {
      background: var(--mustard-soft);
      border-color: var(--mustard);
    }
    .car-actions {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 8px;
    }
    .seats {
      display: inline-flex;
      align-items: center;
      gap: 8px;
    }
    .car-actions .seats {
      margin-left: auto;
    }
    .remove {
      display: inline-flex;
      align-items: center;
      gap: 6px;
    }
    .waiting,
    .add {
      display: grid;
      gap: 8px;
      justify-items: start;
    }
    .waiting-actions,
    .add-fields {
      display: flex;
      flex-wrap: wrap;
      align-items: flex-end;
      gap: 10px 14px;
    }
    .add {
      padding-top: 12px;
      border-top: 1px solid var(--border);
      justify-items: stretch;
    }
    .add .field {
      min-width: min(100%, 220px);
    }
    .add select {
      min-height: var(--touch);
      padding: 0 12px;
      border: 1.5px solid var(--control-border);
      border-radius: var(--radius-icon);
      background: var(--surface);
      color: var(--text);
      font: 600 15px/1.2 var(--font);
      max-width: 100%;
    }
    h4 {
      font: 700 15px/1.3 var(--font);
    }
  `,
})
export class SeatBoard {
  protected readonly store = inject(PlanStore);
  protected readonly texts = TEXTS;
  protected readonly t = TEXTS.simplePlan.board;
  protected readonly name = name;
  protected readonly max = Math.max;

  readonly eventId = input.required<string>();

  protected readonly list = computed(() => this.store.lists()[this.eventId()] ?? null);
  /** The person who was tapped. */
  protected readonly selected = signal<PersonSummary | null>(null);
  private readonly confirm = viewChild(ConfirmDialog);

  /** The person who is being seated. */
  protected readonly active = computed<PersonSummary | null>(() => (this.list()?.canArrange ? this.selected() : null));

  /** The tapped person, if she is still waiting for a seat. */
  protected readonly selectedWaiting = computed(() => {
    const p = this.selected();
    return p && this.list()?.needSeat.some((x) => x.id === p.id) ? p : null;
  });

  /** The driver that is being added, and her seats for passengers. */
  protected readonly newDriver = signal('');
  protected readonly newSeats = signal(4);

  protected readonly status = computed(() => {
    const l = this.list();
    if (!l) return { state: 'open', text: '' };
    const free = l.cars.reduce((sum, car) => sum + car.freeSeats, 0);
    const waiting = l.needSeat.length;
    if (waiting > free) return { state: 'short', text: this.t.short(waiting - free, waiting) };
    if (waiting > 0) return { state: 'open', text: this.t.open(waiting, free) };
    const spare = l.cars.find((car) => car.passengers.length === 0 && l.cars.length > 1);
    if (spare) return { state: 'open', text: this.t.spare(name(spare.driver)) };
    return { state: 'ok', text: l.cars.length === 0 ? this.t.nobody : this.t.allSeated(free) };
  });

  constructor() {
    effect(() => {
      const id = this.eventId();
      untracked(() => {
        if (!this.store.lists()[id]) void this.store.open(id);
      });
    });
  }

  protected free(car: Car): number[] {
    return Array.from({ length: car.freeSeats }, (_, i) => i);
  }

  protected names(people: PersonSummary[]): string {
    return people.map(name).join(', ');
  }

  /** Members who do not drive yet; «(bei X)» if they sit in a car. */
  protected candidates(l: DriverList): { person: PersonSummary; label: string }[] {
    const drivers = new Set(l.cars.map((car) => car.driver.id));
    const individual = new Set(l.individual.map((p) => p.id));
    return (l.members ?? [])
      .filter((p) => !drivers.has(p.id) && !individual.has(p.id))
      .map((p) => {
        const seat = l.cars.find((car) => car.passengers.some((x) => x.id === p.id));
        return { person: p, label: seat ? `${name(p)} ${this.t.seatedWith(name(seat.driver))}` : name(p) };
      });
  }

  protected toggle(p: PersonSummary): void {
    this.selected.update((current) => (current?.id === p.id ? null : p));
  }

  protected canEnter(car: Car): boolean {
    const p = this.active();
    return !!p && car.freeSeats > 0 && !car.passengers.some((x) => x.id === p.id) && car.driver.id !== p.id;
  }

  protected canLeave(car: Car): boolean {
    const p = this.active();
    return !!p && car.passengers.some((x) => x.id === p.id);
  }

  protected async enter(car: Car): Promise<void> {
    const p = this.active();
    this.selected.set(null);
    if (p) await this.store.seat(this.eventId(), car.id, p);
  }

  protected async leave(car: Car): Promise<void> {
    const p = this.active();
    this.selected.set(null);
    if (p) await this.store.unseat(this.eventId(), car.id, p);
  }

  protected async fill(): Promise<void> {
    this.selected.set(null);
    await this.store.fill(this.eventId());
  }

  protected async individual(p: PersonSummary): Promise<void> {
    this.selected.set(null);
    await this.store.setIndividual(this.eventId(), p, true);
  }

  protected pickDriver(id: string): void {
    this.newDriver.set(id);
    if (!id) return;
    // On the match day there is no plan: the list knows the usual seats of the signed-in person (DRV-18)
    const own = id === this.store.meId() ? this.list()?.defaultSeats : undefined;
    this.newSeats.set(this.store.plan() ? this.store.usualSeats(id) : (own ?? 4));
  }

  protected async add(domEvent: Event): Promise<void> {
    domEvent.preventDefault();
    const person = this.list()?.members?.find((p) => p.id === this.newDriver());
    if (!person) return;
    if (await this.store.enterDriver(this.eventId(), person, this.newSeats())) this.newDriver.set('');
  }

  /** Takes a driver off; with passengers only after a question (DRV-02). */
  protected remove(car: Car): void {
    this.selected.set(null);
    if (car.passengers.length === 0) {
      void this.store.dropDriver(this.eventId(), car.id, car.driver);
      return;
    }
    this.confirm()?.ask({
      title: this.t.removeTitle(name(car.driver)),
      text: this.t.removeText(name(car.driver), car.passengers.length),
      confirm: this.t.removeConfirm,
      action: () => this.store.dropDriver(this.eventId(), car.id, car.driver),
    });
  }
}
