import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import type { DriverPlan, DriverPlanMatchDay, DriverSwap } from '../api/types';
import { clockTime, eventDate, yearOf } from '../attendance/time';
import { AuthService } from '../core/auth.service';
import { errorText } from '../core/error-text';
import { name } from '../core/format';
import { Icon } from '../core/icon';
import { TEXTS } from '../core/texts';

/**
 * Swaps on the season page (DRV-25): open requests with «Annehmen», «Ablehnen», «Zurückziehen», and a form to give
 * a trip away or swap it against a trip of the other person, asked first or entered directly.
 */
@Component({
  selector: 'app-plan-swaps',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  template: `
    @if (message(); as m) {
      <p class="message-success" role="status">{{ m }}</p>
    }
    @if (error(); as m) {
      <p class="message-error" role="alert">{{ m }}</p>
    }
    @if (plan().swaps.length > 0) {
      <section class="card" aria-labelledby="plan-swaps-open-title" data-testid="plan-swaps-open">
        <h2 id="plan-swaps-open-title" class="section-title">
          {{ t.swapsTitle }} <span class="count">{{ plan().swaps.length }}</span>
        </h2>
        <ul class="list requests">
          @for (x of plan().swaps; track x.id) {
            <li class="request" [attr.data-testid]="'plan-swap-' + x.id" [attr.aria-label]="requestText(x)">
              <span class="request-text">{{ requestText(x) }}</span>
              <span class="buttons">
                @if (x.canAnswer) {
                  <button
                    type="button"
                    class="button button-primary button-small"
                    [disabled]="busy()"
                    [attr.aria-busy]="running() === 'accept:' + x.id ? 'true' : null"
                    (click)="answer(x, 'accept')"
                  >
                    {{ t.accept }}
                  </button>
                  <button
                    type="button"
                    class="button button-secondary button-small"
                    [disabled]="busy()"
                    [attr.aria-busy]="running() === 'decline:' + x.id ? 'true' : null"
                    (click)="answer(x, 'decline')"
                  >
                    {{ t.decline }}
                  </button>
                }
                @if (x.canWithdraw) {
                  <button
                    type="button"
                    class="button button-secondary button-small"
                    [disabled]="busy()"
                    [attr.aria-busy]="running() === 'withdraw:' + x.id ? 'true' : null"
                    (click)="answer(x, 'withdraw')"
                  >
                    {{ t.withdraw }}
                  </button>
                }
              </span>
            </li>
          }
        </ul>
      </section>
    }

    @if (plan().canJoin || plan().canManage) {
      <form class="card form" (submit)="start($event, false)" novalidate aria-labelledby="plan-swap-title" data-testid="plan-swap-form">
        <h2 id="plan-swap-title" class="section-title">
          <app-icon name="arrow-left-right" [size]="20" />
          {{ t.swapTitle }}
        </h2>
        <p class="secondary-text">{{ t.swapText }}</p>
        @if (plan().canManage) {
          <div class="field">
            <label for="swap-driver">{{ t.swapDriver }}</label>
            <select id="swap-driver" name="swap-driver" (change)="setDriver($any($event.target).value)">
              @for (p of drivers(); track p.id) {
                <option [value]="p.id" [selected]="driver() === p.id">{{ name(p) }}</option>
              }
            </select>
          </div>
        }
        @if (ownTrips().length === 0) {
          <p class="empty" data-testid="plan-swap-nothing">{{ t.swapNothing }}</p>
        } @else {
          <div class="field">
            <label for="swap-event">{{ t.swapMatchDay }}</label>
            <select id="swap-event" name="swap-event" (change)="event.set($any($event.target).value)">
              <option value="">{{ t.swapChoose }}</option>
              @for (m of ownTrips(); track m.eventId) {
                <option [value]="m.eventId" [selected]="event() === m.eventId">{{ label(m) }}</option>
              }
            </select>
          </div>
          <div class="field">
            <label for="swap-partner">{{ t.swapPartner }}</label>
            <select id="swap-partner" name="swap-partner" (change)="setPartner($any($event.target).value)">
              <option value="">{{ t.swapChoose }}</option>
              @for (p of partners(); track p.id) {
                <option [value]="p.id" [selected]="partner() === p.id">{{ name(p) }}</option>
              }
            </select>
          </div>
          <div class="field">
            <label for="swap-return">{{ t.swapReturn }}</label>
            <select id="swap-return" name="swap-return" (change)="partnerEvent.set($any($event.target).value)">
              <option value="">{{ t.swapNoReturn }}</option>
              @for (m of partnerTrips(); track m.eventId) {
                <option [value]="m.eventId" [selected]="partnerEvent() === m.eventId">{{ label(m) }}</option>
              }
            </select>
          </div>
          @if (formError(); as m) {
            <p class="field-error" id="swap-error">{{ m }}</p>
          }
          <div class="buttons">
            <button
              type="submit"
              class="button button-primary button-small"
              data-testid="plan-swap-ask"
              [disabled]="busy()"
              [attr.aria-busy]="running() === 'ask' ? 'true' : null"
            >
              {{ t.swapAsk }}
            </button>
            <button
              type="button"
              class="button button-secondary button-small"
              data-testid="plan-swap-direct"
              [disabled]="busy()"
              [attr.aria-busy]="running() === 'direct' ? 'true' : null"
              (click)="start($event, true)"
            >
              {{ t.swapDirect }}
            </button>
          </div>
        }
      </form>
    }
  `,
  styles: `
    :host {
      display: contents;
    }
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
    .requests {
      list-style: none;
      margin: 0;
      padding: 0;
      display: grid;
      gap: 8px;
    }
    .request {
      display: grid;
      gap: 8px;
      padding: 10px 12px;
      border-radius: var(--radius-icon);
      background: var(--mustard-soft);
      overflow-wrap: anywhere;
    }
    .request-text {
      font-weight: 600;
    }
    .buttons {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
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
export class PlanSwaps {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  protected readonly t = TEXTS.driverPlan;
  protected readonly name = name;

  readonly plan = input.required<DriverPlan>();
  /** Query parameters of the plan (`season`). */
  readonly params = input<Record<string, string>>({});
  /** The plan after a change. */
  readonly updated = output<DriverPlan>();

  protected readonly running = signal<string | null>(null);
  protected readonly busy = computed(() => this.running() !== null);
  protected readonly message = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly formError = signal<string | null>(null);

  protected readonly driverChoice = signal('');
  protected readonly event = signal('');
  protected readonly partner = signal('');
  protected readonly partnerEvent = signal('');

  private readonly meId = computed(() => this.auth.me()?.id ?? '');
  private readonly year = computed(() => yearOf(this.auth.me()?.now));

  /** Whose trip is given away: coaches choose among the drivers, everyone else gives her own. */
  protected readonly driver = computed(() => {
    if (!this.plan().canManage) return this.meId();
    const chosen = this.driverChoice();
    const drivers = this.drivers();
    return drivers.some((p) => p.id === chosen) ? chosen : (drivers[0]?.id ?? '');
  });

  /** Everyone who has a trip that can still be given away. */
  protected readonly drivers = computed(() => {
    const seen = new Map<string, { id: string; firstName: string; lastName: string }>();
    for (const m of this.plan().matchDays.filter((x) => x.changeable)) {
      for (const car of m.cars) seen.set(car.driver.id, car.driver);
    }
    return [...seen.values()].sort((a, b) => name(a).localeCompare(name(b), 'de-CH'));
  });

  /** Match days on which the chosen driver drives and the trip can still change. */
  protected readonly ownTrips = computed(() =>
    this.plan().matchDays.filter((m) => m.changeable && m.cars.some((c) => c.driver.id === this.driver())),
  );

  /** Members of the team except the driver, by name (the shares list every member). */
  protected readonly partners = computed(() =>
    this.plan()
      .shares.map((s) => s.person)
      .filter((p) => p.id !== this.driver()),
  );

  /** Match days on which the partner drives and the driver does not. */
  protected readonly partnerTrips = computed(() => {
    const partner = this.partner();
    if (!partner) return [];
    return this.plan().matchDays.filter(
      (m) =>
        m.changeable &&
        m.eventId !== this.event() &&
        m.cars.some((c) => c.driver.id === partner) &&
        !m.cars.some((c) => c.driver.id === this.driver()),
    );
  });

  protected label(m: DriverPlanMatchDay): string {
    return `${eventDate(m.start, this.year())} ${clockTime(m.start)}`;
  }

  protected requestText(x: DriverSwap): string {
    return this.t.swapRequest(name(x.requester), name(x.partner), x.eventLabel, x.partnerEventLabel ?? null);
  }

  protected setDriver(id: string): void {
    this.driverChoice.set(id);
    this.event.set('');
    this.partnerEvent.set('');
  }

  protected setPartner(id: string): void {
    this.partner.set(id);
    this.partnerEvent.set('');
  }

  protected async start(domEvent: Event, direct: boolean): Promise<void> {
    domEvent.preventDefault();
    if (this.busy()) return;
    const eventId = this.event();
    const partnerId = this.partner();
    if (!eventId || !partnerId) {
      this.formError.set(!eventId ? this.t.swapEventMissing : this.t.swapPartnerMissing);
      document.getElementById(!eventId ? 'swap-event' : 'swap-partner')?.focus();
      return;
    }
    this.formError.set(null);
    this.message.set(null);
    this.error.set(null);
    this.running.set(direct ? 'direct' : 'ask');
    const partnerName = name(this.partners().find((p) => p.id === partnerId) ?? { firstName: '', lastName: '' });
    try {
      const plan = await firstValueFrom(
        this.http.post<DriverPlan>(
          `/api/teams/${this.plan().teamId}/driver-plan/swaps`,
          {
            eventId,
            driverId: this.plan().canManage ? this.driver() : null,
            partnerId,
            partnerEventId: this.partnerEvent() || null,
            direct,
          },
          { params: this.params() },
        ),
      );
      this.event.set('');
      this.partner.set('');
      this.partnerEvent.set('');
      this.updated.emit(plan);
      this.message.set(direct ? this.t.swapDone : this.t.swapAsked(partnerName.trim()));
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.running.set(null);
    }
  }

  protected async answer(x: DriverSwap, answer: 'accept' | 'decline' | 'withdraw'): Promise<void> {
    if (this.busy()) return;
    this.message.set(null);
    this.error.set(null);
    this.running.set(`${answer}:${x.id}`);
    try {
      const plan = await firstValueFrom(
        this.http.post<DriverPlan>(
          `/api/teams/${this.plan().teamId}/driver-plan/swaps/${x.id}/${answer}`,
          null,
          { params: this.params() },
        ),
      );
      this.updated.emit(plan);
      this.message.set(
        answer === 'accept' ? this.t.accepted : answer === 'decline' ? this.t.declined : this.t.withdrawn,
      );
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.running.set(null);
    }
  }
}
