import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { Observable, firstValueFrom } from 'rxjs';
import type { DriverList, PersonSummary, Route } from '../api/types';
import { dateTime } from '../core/format';
import { Icon } from '../core/icon';
import { errorText, fieldErrorsOf } from '../core/error-text';
import { name } from '../core/format';
import { formatDecimal } from '../core/number';
import { TEXTS } from '../core/texts';
import { PlanStore } from './simple/plan-store';
import { SeatBoard } from './simple/seat-board';

/** «23.4» */
export function km(value: number | null | undefined): string {
  return formatDecimal(value ?? 0);
}

/**
 * Section «Fahrerliste» of a match day (DRV-01 to DRV-11, DRV-20 to DRV-23, DRV-32): the route with its credit,
 * and how many places the cars have for the people who travel together. «Plätze zuweisen» opens the cars as
 * bubbles: everyone of the team enters drivers and puts people into cars there. Travelling on one's own stays
 * here. The backend decides what is allowed (RR-07).
 */
@Component({
  selector: 'app-driver-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon, SeatBoard],
  providers: [PlanStore],
  template: `
    <section
      class="card driver-list"
      aria-labelledby="driver-list-title"
      data-testid="driver-list"
      [attr.aria-busy]="loading() ? 'true' : null"
    >
      <h2 id="driver-list-title" class="section-title">{{ s.title }}</h2>
      @if (message() ?? store.success(); as m) {
        <p class="message-success" role="status">{{ m }}</p>
      }
      @if (error() ?? store.error(); as m) {
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
                  @if (r.durationManual) {
                    <span class="chip" data-kind="manual" data-testid="driver-route-duration-manual">{{ s.durationManual }}</span>
                  }
                </p>
              } @else if (has(r.durationMinutes)) {
                <p class="distance" data-testid="driver-route-distance">
                  <strong>{{ s.durationOnly(r.durationMinutes) }}</strong>
                  <span class="chip" data-kind="manual" data-testid="driver-route-duration-manual">{{ s.durationManual }}</span>
                </p>
              }
              <details class="route-info" data-testid="driver-route-info">
                <summary>
                  <app-icon name="info" [size]="18" />
                  <span>{{ s.infoTitle }}</span>
                </summary>
                <ul class="info-list">
                  @for (line of infoLines(r); track $index) {
                    <li>{{ line }}</li>
                  }
                </ul>
              </details>
              @if (has(r.distanceKm)) {
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
                  data-testid="driver-route-duration"
                  [disabled]="running()"
                  (click)="openDuration()"
                >
                  {{ s.setDuration }}
                </button>
                @if (d.route?.durationManual) {
                  <button
                    type="button"
                    class="button button-secondary button-small"
                    data-testid="driver-route-duration-reset"
                    [disabled]="running()"
                    [attr.aria-busy]="busy() === 'duration-reset' ? 'true' : null"
                    (click)="resetDuration()"
                  >
                    {{ s.durationAuto }}
                  </button>
                }
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

          <!-- By default only the places and the people who travel together (DRV-32) -->
          <div class="seats-row">
            <h3 class="subtitle" id="driver-list-cars">{{ s.cars }}</h3>
            <p
              class="seat-summary"
              data-testid="driver-seat-summary"
              [attr.data-state]="summary().state"
              [attr.data-places]="summary().places"
              [attr.data-people]="summary().people"
            >
              <app-icon [name]="summary().state === 'short' ? 'triangle-alert' : 'car'" [size]="20" />
              <strong>{{ s.seatSummary(summary().places, summary().people) }}</strong>
              <span class="secondary-text">
                {{ s.seatCars(d.cars.length) }} ·
                {{ summary().missing > 0 ? s.seatMissing(summary().missing) : s.seatEnough }}
              </span>
            </p>
            <p class="secondary-text">{{ s.seatSummaryHint }}</p>
            <button
              type="button"
              class="button button-small assign"
              [class.button-primary]="!assigning() && d.canArrange"
              [class.button-secondary]="assigning() || !d.canArrange"
              data-testid="driver-assign"
              aria-controls="driver-list-board"
              [attr.aria-expanded]="assigning()"
              (click)="assigning.set(!assigning())"
            >
              <app-icon [name]="assigning() ? 'chevron-up' : 'users'" [size]="16" />
              {{ assigning() ? s.assignDone : d.canArrange ? s.assignSeats : s.showCars }}
            </button>
          </div>
          @if (assigning()) {
            <div id="driver-list-board" class="assign-board">
              <app-seat-board [eventId]="eventId()" />
            </div>
          }

          <!-- People who travel on their own need no seat (DRV-20) -->
          @if (d.individual.length > 0 || canMarkSelf(d)) {
            <h3 class="subtitle" id="driver-list-individual">{{ texts.individual.title }}</h3>
            @if (d.individual.length > 0) {
              <ul class="list" aria-labelledby="driver-list-individual" data-testid="individual">
                @for (p of d.individual; track p.id) {
                  <li class="row without-seat" [attr.aria-label]="name(p)">
                    <span>
                      {{ name(p) }}
                      @if (p.id === meId()) {
                        <span class="secondary-text">{{ s.you }}</span>
                      }
                    </span>
                    @if ((p.id === meId() && d.canJoin) || d.canManage) {
                      <button
                        type="button"
                        class="button button-secondary button-small"
                        [attr.aria-label]="
                          p.id === meId() ? texts.individual.selfUndo : texts.individual.undoFor(name(p))
                        "
                        [disabled]="running()"
                        (click)="setIndividual(p, false)"
                      >
                        {{ texts.individual.undo }}
                      </button>
                    }
                  </li>
                }
              </ul>
            }
            @if (canMarkSelf(d)) {
              <button
                type="button"
                class="button button-secondary button-small individual-self"
                data-testid="individual-self"
                [disabled]="running()"
                [attr.aria-busy]="busy() === 'individual-' + meId() ? 'true' : null"
                (click)="markSelf()"
              >
                {{ texts.individual.self }}
              </button>
            }
          }
        }
        <div class="links">
          @if (teamId(); as team) {
            <!-- The season is planned on the page «Fahrerliste» (DRV-24) -->
            <a
              [routerLink]="['/teams', team, 'fahrerliste']"
              class="button button-secondary button-small"
              data-testid="driver-plan-link"
              >{{ s.toPlan }}</a
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
            spellcheck="false"
            name="driver-km-field"
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
      #durationDialog
      class="card dialog"
      aria-modal="true"
      aria-labelledby="driver-duration-title"
      aria-describedby="driver-duration-text"
    >
      <form class="form" (submit)="saveDuration($event)" novalidate>
        <h2 id="driver-duration-title">{{ s.durationTitle }}</h2>
        <p class="secondary-text" id="driver-duration-text">{{ s.durationText }}</p>
        <div class="field">
          <label for="driver-duration-field">{{ s.durationField }}</label>
          <input
            spellcheck="false"
            name="driver-duration-field"
            #durationField
            id="driver-duration-field"
            type="text"
            inputmode="numeric"
            autocomplete="off"
            maxlength="3"
            [value]="durationValue()"
            (input)="durationValue.set($any($event.target).value)"
            [attr.aria-invalid]="durationError() ? 'true' : null"
            [attr.aria-describedby]="durationError() ? 'driver-duration-error' : null"
          />
          @if (durationError(); as m) {
            <p class="field-error" id="driver-duration-error">{{ m }}</p>
          }
        </div>
        <div class="dialog-buttons">
          <button
            type="submit"
            class="button button-primary button-small"
            [disabled]="running()"
            [attr.aria-busy]="busy() === 'duration' ? 'true' : null"
          >
            {{ s.durationSave }}
          </button>
          <button type="button" class="button button-secondary button-small" (click)="closeDuration()">
            {{ s.cancel }}
          </button>
        </div>
      </form>
    </dialog>
  `,
  styleUrl: './driver-list.css',
})
export class DriverListSection {
  private readonly http = inject(HttpClient);
  protected readonly store = inject(PlanStore);
  protected readonly texts = TEXTS;
  protected readonly s = TEXTS.driverList;
  protected readonly name = name;
  protected readonly km = km;
  protected readonly has = (v: number | null | undefined): v is number => v !== null && v !== undefined;

  readonly eventId = input.required<string>();
  /** Team of the match day, for the link to its driver list of the season (DRV-24). */
  readonly teamId = input<string | null>(null);
  /** ID of the signed-in person. */
  readonly meId = input<string | null>(null);
  /** Coach or admin who may edit the event: hint where meeting point and venue are entered. */
  readonly canEditEvent = input(false);
  /** Changes when the event page reloads (e.g. after a response), so the list stays current. */
  readonly version = input(0);
  /** After a change that shows elsewhere on the event page, e.g. «reist individuell an» in the responses. */
  readonly changed = output<void>();

  /** The driver list; the cars behind «Plätze zuweisen» change it too. */
  protected readonly data = computed<DriverList | null>(() => this.store.lists()[this.eventId()] ?? null);
  /** «Plätze zuweisen» is open. */
  protected readonly assigning = signal(false);
  protected readonly loading = signal(true);
  protected readonly busy = signal<string | null>(null);
  protected readonly running = computed(() => this.busy() !== null || this.store.busy());
  protected readonly message = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly kmValue = signal('');
  protected readonly kmError = signal<string | null>(null);
  private readonly kmDialog = viewChild<ElementRef<HTMLDialogElement>>('kmDialog');
  private readonly durationDialog = viewChild<ElementRef<HTMLDialogElement>>('durationDialog');
  private readonly durationField = viewChild<ElementRef<HTMLInputElement>>('durationField');
  protected readonly durationValue = signal('');
  protected readonly durationError = signal<string | null>(null);
  private readonly kmField = viewChild<ElementRef<HTMLInputElement>>('kmField');

  /**
   * Places of all cars (drivers included) and the people who travel together: drivers, passengers and everyone
   * who still needs a seat; whoever travels on her own does not count (DRV-20, DRV-32).
   */
  protected readonly summary = computed(() => {
    const d = this.data();
    if (!d) return { places: 0, people: 0, missing: 0, state: 'none' };
    const places = d.cars.reduce((sum, car) => sum + car.seats + 1, 0);
    const people = d.cars.reduce((sum, car) => sum + 1 + car.passengers.length, 0) + d.needSeat.length;
    const missing = Math.max(0, people - places);
    return { places, people, missing, state: people === 0 ? 'none' : missing > 0 ? 'short' : 'enough' };
  });

  constructor() {
    effect(() => {
      const id = this.eventId();
      this.version();
      untracked(() => void this.load(id));
    });
    // A change in the cars replaces the last message of this section
    effect(() => {
      if (this.store.running()) {
        untracked(() => {
          this.message.set(null);
          this.error.set(null);
        });
      }
    });
    // A new match day starts closed
    effect(() => {
      this.eventId();
      untracked(() => this.assigning.set(false));
    });
  }

  protected missingText(d: DriverList): string {
    return this.s.missing(d.missing.map((m) => this.s.missingPart[m] ?? m));
  }

  // ------------------------------------------------------------------ Travelling on one's own (DRV-20)

  /** Member who neither drives nor is marked yet. */
  protected canMarkSelf(d: DriverList): boolean {
    const me = this.meId();
    return (
      d.canJoin && !!me && !d.cars.some((c) => c.driver.id === me) && !d.individual.some((p) => p.id === me)
    );
  }

  protected markSelf(): void {
    const me = this.meId();
    if (!me) return;
    void this.individual(me, true, TEXTS.individual.savedSelf);
  }

  protected setIndividual(p: PersonSummary, on: boolean): void {
    const self = p.id === this.meId();
    const text = on
      ? self
        ? TEXTS.individual.savedSelf
        : TEXTS.individual.saved(name(p))
      : self
        ? TEXTS.individual.removedSelf
        : TEXTS.individual.removed(name(p));
    void this.individual(p.id, on, text);
  }

  private async individual(personId: string, on: boolean, text: string): Promise<void> {
    const url = this.url(`/individual/${personId}`);
    await this.run(
      'individual-' + personId,
      on ? this.http.put<DriverList>(url, {}) : this.http.delete<DriverList>(url),
      () => text,
    );
    this.changed.emit();
  }

  // ------------------------------------------------------------------ Route (DRV-05 to DRV-08)

  protected recalculate(): void {
    void this.run('recalculate', this.http.post<DriverList>(this.url('/route/recalculate'), {}), () =>
      this.s.recalculated,
    );
  }

  protected openKm(): void {
    const r = this.data()?.route;
    // Plain digits in the field, so that the value parses back (no thousands separator).
    this.kmValue.set(r?.distanceKm != null ? r.distanceKm.toFixed(1) : '');
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
    this.clearMessages();
    try {
      this.store.put(await firstValueFrom(this.http.put<DriverList>(this.url('/route'), { distanceKm: value })));
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

  // ------------------------------------------------------------------ Driving time (DRV-21, DRV-22)

  /** How the driving time came about, one sentence per line (DRV-22). */
  protected infoLines(r: Route): string[] {
    const lines: string[] = [];
    lines.push(r.provider ? this.s.infoProvider(this.s.providers[r.provider]) : this.s.infoByHand);
    lines.push(r.departureTimeUsed ? this.s.infoDepartureUsed : this.s.infoDepartureNotUsed);
    lines.push(this.s.infoCalculatedAt(dateTime(r.calculatedAt)));
    if (r.durationManual) {
      lines.push(
        this.s.infoManual(
          r.durationChangedBy ? name(r.durationChangedBy) : '',
          r.durationChangedAt ? dateTime(r.durationChangedAt) : '',
          r.calculatedMinutes ?? null,
        ),
      );
    }
    return lines;
  }

  protected openDuration(): void {
    const r = this.data()?.route;
    this.durationValue.set(r?.durationMinutes != null ? String(r.durationMinutes) : '');
    this.durationError.set(null);
    const dialog = this.durationDialog()?.nativeElement;
    if (dialog && !dialog.open) dialog.showModal();
  }

  protected closeDuration(): void {
    this.durationDialog()?.nativeElement.close();
  }

  protected async saveDuration(domEvent: Event): Promise<void> {
    domEvent.preventDefault();
    if (this.running()) return;
    const text = this.durationValue().trim();
    const value = Number(text);
    if (!/^\d{1,3}$/.test(text) || value < 1 || value > 600) {
      this.durationError.set(this.s.durationFormat);
      this.durationField()?.nativeElement.focus();
      return;
    }
    this.durationError.set(null);
    this.busy.set('duration');
    this.clearMessages();
    try {
      this.store.put(await firstValueFrom(this.http.put<DriverList>(this.url('/route/duration'), { minutes: value })));
      this.closeDuration();
      this.message.set(this.s.durationSaved);
      this.changed.emit();
    } catch (e) {
      const fields = fieldErrorsOf(e);
      if (fields['minutes']) {
        this.durationError.set(fields['minutes']);
      } else {
        this.closeDuration();
        this.error.set(errorText(e));
      }
    } finally {
      this.busy.set(null);
    }
  }

  protected async resetDuration(): Promise<void> {
    await this.run('duration-reset', this.http.delete<DriverList>(this.url('/route/duration')), () => {
      this.changed.emit();
      return this.s.durationReset;
    });
  }

  // ------------------------------------------------------------------ Helpers

  private url(path = ''): string {
    return `/api/events/${this.eventId()}/driver-list${path}`;
  }

  private clearMessages(): void {
    this.message.set(null);
    this.error.set(null);
    this.store.success.set(null);
    this.store.error.set(null);
  }

  private async run(key: string, request: Observable<DriverList>, success: () => string): Promise<void> {
    if (this.running()) return;
    this.busy.set(key);
    this.clearMessages();
    try {
      this.store.put(await firstValueFrom(request));
      this.message.set(success());
    } catch (e) {
      this.error.set(errorText(e));
      // The list may have changed meanwhile: show the current state
      await this.load(this.eventId(), false);
    } finally {
      this.busy.set(null);
    }
  }

  private async load(id: string, showLoading = true): Promise<void> {
    if (showLoading) this.loading.set(true);
    try {
      this.store.put(await firstValueFrom(this.http.get<DriverList>(`/api/events/${id}/driver-list`)));
    } catch (e) {
      if (showLoading) this.error.set(errorText(e));
    } finally {
      this.loading.set(false);
    }
  }
}
