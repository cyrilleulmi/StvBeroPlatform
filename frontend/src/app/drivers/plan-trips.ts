import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import type { DriverPlan, ExtraTrip } from '../api/types';
import { AuthService } from '../core/auth.service';
import { errorText, fieldErrorsOf } from '../core/error-text';
import { date, name } from '../core/format';
import { Icon } from '../core/icon';
import { formatDecimal } from '../core/number';
import { TEXTS } from '../core/texts';

/**
 * Other trips on the season page (DRV-28): trips for something that is not an event in the app. They count like a
 * match day. Members add their own, coaches and admins also for others.
 */
@Component({
  selector: 'app-plan-trips',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  template: `
    <section class="card" aria-labelledby="plan-trips-title" data-testid="plan-trips">
      <h2 id="plan-trips-title" class="section-title">
        <app-icon name="car" [size]="20" />
        {{ t.tripsTitle }}
        <span class="count">{{ plan().extraTrips.length }}</span>
      </h2>
      <p class="secondary-text">{{ t.tripsText }}</p>
      @if (message(); as m) {
        <p class="message-success" role="status">{{ m }}</p>
      }
      @if (error(); as m) {
        <p class="message-error" role="alert">{{ m }}</p>
      }
      @if (plan().extraTrips.length === 0) {
        <p class="empty">{{ t.noTrips }}</p>
      } @else {
        <ul class="list trips" [attr.aria-label]="t.tripsTitle">
          @for (x of plan().extraTrips; track x.id) {
            <li class="trip" [attr.data-testid]="'plan-trip-' + x.id" [attr.aria-label]="x.title">
              <span class="trip-text">
                <span class="trip-title">{{ t.trip(x.title, date(x.date), name(x.driver)) }}</span>
                <span class="secondary-text">{{ t.tripKm(km(x.distanceKm)) }} · {{ x.over ? t.done : t.planned }}</span>
              </span>
              @if (x.canRemove) {
                <button
                  type="button"
                  class="button button-secondary button-small"
                  [attr.aria-label]="t.tripRemoveFor(x.title)"
                  [disabled]="busy()"
                  [attr.aria-busy]="running() === 'remove:' + x.id ? 'true' : null"
                  (click)="remove(x)"
                >
                  {{ t.tripRemove }}
                </button>
              }
            </li>
          }
        </ul>
      }
      @if (plan().canJoin || plan().canManage) {
        <form class="form trip-form" (submit)="add($event)" novalidate data-testid="plan-trip-form">
          <div class="field-row">
            <div class="field">
              <label for="trip-date">{{ t.tripDate }}</label>
              <input
                id="trip-date"
                name="trip-date"
                type="date"
                [value]="tripDate()"
                (input)="tripDate.set($any($event.target).value)"
                [attr.aria-invalid]="errors()['date'] ? 'true' : null"
                [attr.aria-describedby]="errors()['date'] ? 'trip-date-error' : null"
              />
              @if (errors()['date']; as m) {
                <p class="field-error" id="trip-date-error">{{ m }}</p>
              }
            </div>
            <div class="field">
              <label for="trip-km">{{ t.tripKmField }}</label>
              <input
                id="trip-km"
                name="trip-km"
                type="text"
                inputmode="decimal"
                autocomplete="off"
                spellcheck="false"
                maxlength="7"
                [value]="kmValue()"
                (input)="kmValue.set($any($event.target).value)"
                [attr.aria-invalid]="errors()['distanceKm'] ? 'true' : null"
                [attr.aria-describedby]="errors()['distanceKm'] ? 'trip-km-error' : null"
              />
              @if (errors()['distanceKm']; as m) {
                <p class="field-error" id="trip-km-error">{{ m }}</p>
              }
            </div>
          </div>
          <div class="field">
            <label for="trip-title">{{ t.tripTitle }}</label>
            <input
              id="trip-title"
              name="trip-title"
              type="text"
              maxlength="80"
              autocomplete="off"
              [value]="title()"
              (input)="title.set($any($event.target).value)"
              [attr.aria-invalid]="errors()['title'] ? 'true' : null"
              [attr.aria-describedby]="errors()['title'] ? 'trip-title-error' : null"
            />
            @if (errors()['title']; as m) {
              <p class="field-error" id="trip-title-error">{{ m }}</p>
            }
          </div>
          @if (plan().canManage) {
            <div class="field">
              <label for="trip-driver">{{ t.tripDriver }}</label>
              <select id="trip-driver" name="trip-driver" (change)="driver.set($any($event.target).value)">
                @for (p of members(); track p.id) {
                  <option [value]="p.id" [selected]="chosenDriver() === p.id">{{ name(p) }}</option>
                }
              </select>
            </div>
          }
          <button
            type="submit"
            class="button button-primary button-small"
            [disabled]="busy()"
            [attr.aria-busy]="running() === 'add' ? 'true' : null"
          >
            {{ t.tripAdd }}
          </button>
        </form>
      }
    </section>
  `,
  styles: `
    .card {
      display: grid;
      gap: 10px;
      min-width: 0;
    }
    .card p {
      margin: 0;
    }
    .section-title {
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .trips {
      list-style: none;
      margin: 0;
      padding: 0;
      display: grid;
      gap: 6px;
    }
    .trip {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 6px 10px;
      padding: 8px 10px;
      border-radius: var(--radius-icon);
      background: var(--primary-soft);
    }
    .trip-text {
      display: grid;
      gap: 2px;
      flex: 1 1 200px;
      min-width: 0;
      overflow-wrap: anywhere;
    }
    .trip-title {
      font-weight: 700;
    }
    .trip-form {
      display: grid;
      gap: 10px;
      padding-top: 10px;
      border-top: 1px solid var(--border);
    }
    .trip-form .button {
      justify-self: start;
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
  `,
})
export class PlanTrips {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  protected readonly t = TEXTS.driverPlan;
  protected readonly name = name;
  protected readonly date = date;
  protected readonly km = formatDecimal;

  readonly plan = input.required<DriverPlan>();
  readonly params = input<Record<string, string>>({});
  readonly updated = output<DriverPlan>();

  protected readonly running = signal<string | null>(null);
  protected readonly busy = computed(() => this.running() !== null);
  protected readonly message = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly errors = signal<Record<string, string>>({});

  protected readonly tripDate = signal('');
  protected readonly title = signal('');
  protected readonly kmValue = signal('');
  protected readonly driver = signal('');

  protected readonly members = computed(() => this.plan().members ?? this.plan().shares.map((s) => s.person));
  protected readonly chosenDriver = computed(() => this.driver() || this.auth.me()?.id || '');

  protected async add(domEvent: Event): Promise<void> {
    domEvent.preventDefault();
    if (this.busy()) return;
    const errors: Record<string, string> = {};
    const kmText = this.kmValue().trim().replace(',', '.');
    const km = Number(kmText);
    if (!this.tripDate()) errors['date'] = this.t.tripDateInvalid;
    if (!kmText || !Number.isFinite(km) || km < 0.1 || km > 2000) errors['distanceKm'] = this.t.tripKmInvalid;
    if (!this.title().trim()) errors['title'] = this.t.tripTitleInvalid;
    this.errors.set(errors);
    const first = ['date', 'distanceKm', 'title'].find((f) => errors[f]);
    if (first) {
      document.getElementById(first === 'date' ? 'trip-date' : first === 'distanceKm' ? 'trip-km' : 'trip-title')?.focus();
      return;
    }
    this.message.set(null);
    this.error.set(null);
    this.running.set('add');
    try {
      const plan = await firstValueFrom(
        this.http.post<DriverPlan>(
          `/api/teams/${this.plan().teamId}/driver-plan/trips`,
          {
            date: this.tripDate(),
            title: this.title().trim(),
            distanceKm: km,
            driverId: this.plan().canManage ? this.chosenDriver() : null,
          },
          { params: this.params() },
        ),
      );
      this.updated.emit(plan);
      this.tripDate.set('');
      this.title.set('');
      this.kmValue.set('');
      this.message.set(this.t.tripAdded);
    } catch (e) {
      const fields = fieldErrorsOf(e);
      if (Object.keys(fields).length > 0) {
        this.errors.set(fields);
      } else {
        this.error.set(errorText(e));
      }
    } finally {
      this.running.set(null);
    }
  }

  protected async remove(x: ExtraTrip): Promise<void> {
    if (this.busy()) return;
    this.message.set(null);
    this.error.set(null);
    this.running.set(`remove:${x.id}`);
    try {
      const plan = await firstValueFrom(
        this.http.delete<DriverPlan>(`/api/teams/${this.plan().teamId}/driver-plan/trips/${x.id}`, {
          params: this.params(),
        }),
      );
      this.updated.emit(plan);
      this.message.set(this.t.tripRemoved);
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.running.set(null);
    }
  }
}
