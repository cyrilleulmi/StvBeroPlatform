import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import type { Car, DriverPlanMatchDay, PersonSummary } from '../../api/types';
import { name } from '../../core/format';
import { Icon } from '../../core/icon';
import { TEXTS } from '../../core/texts';
import { PlanStore } from './plan-store';
import { Stepper } from './stepper';

/**
 * The cars of one match day with the people in them, for the last changes before the match (DRV-03, DRV-19).
 * Coaches and admins tap a person and then the car she rides in; «Alle verteilen» seats everyone at once.
 * Members take a free seat themselves.
 */
@Component({
  selector: 'app-seat-board',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, Stepper],
  template: `
    @if (list(); as l) {
      <div class="seat-board" [attr.data-testid]="'seat-board-' + matchDay().eventId">
        <p class="status" [attr.data-state]="status().state" data-testid="seat-status">
          <app-icon
            [name]="status().state === 'short' ? 'triangle-alert' : status().state === 'ok' ? 'check' : 'users'"
            [size]="16"
          />
          {{ status().text }}
        </p>
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
                    @if (l.canManage) {
                      <button
                        type="button"
                        class="person"
                        [attr.aria-pressed]="selected()?.id === p.id"
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
                <div class="car-actions">
                  @if (canEnter(car)) {
                    <button
                      type="button"
                      class="button button-primary button-small"
                      [disabled]="store.busy()"
                      [attr.data-testid]="'seat-enter-' + car.driver.id"
                      (click)="enter(car)"
                    >
                      {{ l.canManage ? t.putHere(active()!.firstName) : t.rideAlong }}
                    </button>
                  }
                  @if (canLeave(car)) {
                    <button
                      type="button"
                      class="button button-secondary button-small"
                      [disabled]="store.busy()"
                      (click)="leave(car)"
                    >
                      {{ l.canManage ? t.takeOut(active()!.firstName) : t.leave }}
                    </button>
                  }
                  @if (l.canManage || car.driver.id === store.meId()) {
                    <span class="seats">
                      <span class="secondary-text">{{ t.seats }}</span>
                      <app-stepper
                        [value]="car.seats"
                        [min]="max(1, car.passengers.length)"
                        [max]="8"
                        [label]="t.seatsOf(name(car.driver))"
                        [disabled]="store.busy()"
                        (changed)="store.setSeats(matchDay().eventId, car.id, $event)"
                      />
                    </span>
                  }
                </div>
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
                @if (l.canManage) {
                  <button
                    type="button"
                    class="person waiting-person"
                    [attr.aria-pressed]="selected()?.id === p.id"
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
            @if (l.canManage) {
              <p class="secondary-text hint">{{ selected() ? t.hintCar(selected()!.firstName) : t.hintPerson }}</p>
              <button
                type="button"
                class="button button-primary button-small"
                [disabled]="store.busy()"
                [attr.aria-busy]="store.running() === 'fill:' + matchDay().eventId ? 'true' : null"
                data-testid="seat-fill"
                (click)="fill()"
              >
                {{ t.fill }}
              </button>
            }
          </div>
        }
        @if (l.individual.length > 0) {
          <p class="secondary-text">{{ t.individual(names(l.individual)) }}</p>
        }
      </div>
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
      grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
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
      margin-left: auto;
    }
    .waiting {
      display: grid;
      gap: 8px;
      justify-items: start;
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

  readonly matchDay = input.required<DriverPlanMatchDay>();

  protected readonly list = computed(() => this.store.lists()[this.matchDay().eventId] ?? null);
  /** The person a coach tapped. */
  protected readonly selected = signal<PersonSummary | null>(null);

  /** The person who is being seated: the tapped one, or the member herself. */
  protected readonly active = computed<PersonSummary | null>(() => {
    const l = this.list();
    if (!l) return null;
    if (l.canManage) return this.selected();
    if (!l.canJoin) return null;
    const me = this.store.meId();
    if (!me || l.cars.some((car) => car.driver.id === me)) return null;
    const known = [...l.needSeat, ...l.cars.flatMap((car) => car.passengers)].find((p) => p.id === me);
    const own = this.store.plan()?.drivers.find((d) => d.person.id === me)?.person;
    return known ?? own ?? null;
  });

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
      const id = this.matchDay().eventId;
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
    if (p && (await this.store.seat(this.matchDay().eventId, car.id, p))) this.selected.set(null);
  }

  protected async leave(car: Car): Promise<void> {
    const p = this.active();
    if (p && (await this.store.unseat(this.matchDay().eventId, car.id, p))) this.selected.set(null);
  }

  protected async fill(): Promise<void> {
    this.selected.set(null);
    await this.store.fill(this.matchDay().eventId);
  }
}
