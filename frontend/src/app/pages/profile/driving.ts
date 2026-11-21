import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { firstValueFrom } from 'rxjs';
import type { Driving } from '../../api/types';
import { errorText } from '../../core/error-text';
import { TEXTS } from '../../core/texts';

/**
 * Section «Fahren» in the profile (DRV-18): whether the person can drive to match days and how many places
 * her car has, herself included. The proposal of a driver list uses these values (DRV-19).
 */
@Component({
  selector: 'app-my-driving',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card driving" aria-labelledby="profile-driving" data-testid="my-driving">
      <h2 id="profile-driving" class="section-title">{{ s.section }}</h2>
      <p class="secondary-text">{{ s.intro }}</p>
      @if (message(); as m) {
        <p class="message-success" role="status">{{ m }}</p>
      }
      @if (error(); as m) {
        <p class="message-error" role="alert">{{ m }}</p>
      }
      @if (loaded()) {
        <form class="form" (submit)="save($event)" novalidate>
          <label class="checkbox">
            <input
              type="checkbox"
              data-testid="my-can-drive"
              [checked]="canDrive()"
              (change)="canDrive.set($any($event.target).checked)"
            />
            {{ s.canDrive }}
          </label>
          @if (canDrive()) {
            <div class="field seats">
              <label for="my-car-seats">{{ s.seats }}</label>
              <input
                id="my-car-seats"
                type="number"
                inputmode="numeric"
                min="2"
                max="9"
                [value]="seats()"
                (input)="seats.set($any($event.target).value)"
                [attr.aria-invalid]="seatsError() ? 'true' : null"
                [attr.aria-describedby]="seatsError() ? 'my-car-seats-error' : null"
              />
              @if (seatsError(); as m) {
                <p class="field-error" id="my-car-seats-error">{{ m }}</p>
              }
            </div>
          }
          <button
            type="submit"
            class="button button-secondary button-small"
            [disabled]="running()"
            [attr.aria-busy]="running() ? 'true' : null"
          >
            {{ s.save }}
          </button>
        </form>
      }
    </section>
  `,
  styles: `
    :host {
      display: block;
    }

    .driving,
    .form {
      display: grid;
      gap: 10px;
    }

    .driving p {
      margin: 0;
    }

    .seats input {
      max-width: 120px;
    }

    .button {
      justify-self: start;
    }
  `,
})
export class MyDrivingSection {
  private readonly http = inject(HttpClient);
  protected readonly s = TEXTS.driving;

  readonly personId = input.required<string>();

  protected readonly loaded = signal(false);
  protected readonly running = signal(false);
  protected readonly canDrive = signal(true);
  protected readonly seats = signal('5');
  protected readonly seatsError = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    effect(() => {
      const id = this.personId();
      untracked(() => void this.load(id));
    });
  }

  protected async save(domEvent: Event): Promise<void> {
    domEvent.preventDefault();
    if (this.running()) return;
    const seats = Number(this.seats());
    if (this.canDrive() && (!Number.isInteger(seats) || seats < 2 || seats > 9)) {
      this.seatsError.set(this.s.seatsInvalid);
      return;
    }
    this.running.set(true);
    this.seatsError.set(null);
    this.message.set(null);
    this.error.set(null);
    try {
      const saved = await firstValueFrom(
        this.http.put<Driving>(`/api/persons/${this.personId()}/driving`, {
          canDrive: this.canDrive(),
          carSeats: Number.isInteger(seats) && seats >= 2 && seats <= 9 ? seats : 5,
        }),
      );
      this.apply(saved);
      this.message.set(this.s.saved);
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.running.set(false);
    }
  }

  private apply(driving: Driving): void {
    this.canDrive.set(driving.canDrive);
    this.seats.set(String(driving.carSeats));
  }

  private async load(id: string): Promise<void> {
    try {
      this.apply(await firstValueFrom(this.http.get<Driving>(`/api/persons/${id}/driving`)));
      this.loaded.set(true);
    } catch (e) {
      this.error.set(errorText(e));
    }
  }
}
