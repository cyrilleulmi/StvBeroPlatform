import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, effect, inject, input, output, signal, untracked } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import type { MatchDaySettings } from '../../api/types';
import { errorText } from '../../core/error-text';
import { TEXTS } from '../../core/texts';

/**
 * Match duration, arrival before the first match and usual meeting point of a team (TML-02, TML-03, AUF-32), as a
 * form of its own: in the assistant «Neues Team» (PTF-13) and on «Termine überarbeiten» (TER-21).
 */
@Component({
  selector: 'app-match-day-settings',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form class="form" (submit)="save($event)" novalidate [attr.aria-label]="t.title" data-testid="match-day-settings">
      @if (message(); as m) {
        <p class="message-success" role="status">{{ m }}</p>
      }
      @if (error(); as m) {
        <p class="message-error" role="alert">{{ m }}</p>
      }
      <div class="field-row">
        <div class="field">
          <label [for]="id + '-duration'">{{ t.duration }}</label>
          <input
            spellcheck="false"
            [name]="id + '-duration'"
            [id]="id + '-duration'"
            type="text"
            inputmode="numeric"
            maxlength="3"
            autocomplete="off"
            [value]="duration()"
            (input)="duration.set($any($event.target).value)"
            [attr.aria-invalid]="durationError() ? 'true' : null"
            [attr.aria-describedby]="durationError() ? id + '-duration-error' : null"
          />
          @if (durationError(); as m) {
            <p class="field-error" [id]="id + '-duration-error'">{{ m }}</p>
          }
        </div>
        <div class="field">
          <label [for]="id + '-lead'">{{ t.lead }}</label>
          <input
            spellcheck="false"
            [name]="id + '-lead'"
            [id]="id + '-lead'"
            type="text"
            inputmode="numeric"
            maxlength="3"
            autocomplete="off"
            [value]="lead()"
            (input)="lead.set($any($event.target).value)"
            [attr.aria-invalid]="leadError() ? 'true' : null"
            [attr.aria-describedby]="leadError() ? id + '-lead-error' : null"
          />
          @if (leadError(); as m) {
            <p class="field-error" [id]="id + '-lead-error'">{{ m }}</p>
          }
        </div>
      </div>
      <div class="field">
        <label [for]="id + '-meeting-point'">{{ t.meetingPoint }}</label>
        <input
          [name]="id + '-meeting-point'"
          [id]="id + '-meeting-point'"
          type="text"
          maxlength="120"
          autocomplete="off"
          [value]="meetingPoint()"
          (input)="meetingPoint.set($any($event.target).value)"
          [attr.aria-describedby]="id + '-meeting-point-help'"
        />
        <p class="secondary-text" [id]="id + '-meeting-point-help'">{{ t.meetingPointHelp }}</p>
      </div>
      <button
        type="submit"
        class="button button-secondary button-small"
        [disabled]="saving()"
        [attr.aria-busy]="saving() ? 'true' : null"
      >
        {{ t.save }}
      </button>
    </form>
  `,
  styles: `
    .form {
      display: grid;
      gap: 12px;
    }
    .form > .button {
      justify-self: start;
    }
  `,
})
export class MatchDaySettingsForm {
  private readonly http = inject(HttpClient);
  protected readonly t = TEXTS.matchDaySettings;
  protected readonly id = 'mds';

  readonly teamId = input.required<string>();
  /** The current settings; missing: the defaults of the app. */
  readonly settings = input<MatchDaySettings | null | undefined>(null);
  readonly saved = output<MatchDaySettings>();

  protected readonly duration = signal('50');
  protected readonly lead = signal('70');
  protected readonly meetingPoint = signal('');
  protected readonly durationError = signal<string | null>(null);
  protected readonly leadError = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly message = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    effect(() => {
      const s = this.settings();
      untracked(() => {
        this.duration.set(String(s?.matchDurationMinutes ?? 50));
        this.lead.set(String(s?.arrivalLeadMinutes ?? 70));
        this.meetingPoint.set(s?.meetingPoint ?? '');
      });
    });
  }

  protected async save(domEvent: Event): Promise<void> {
    domEvent.preventDefault();
    if (this.saving()) return;
    const whole = (text: string) => /^[0-9]{1,3}$/.test(text.trim());
    const duration = Number(this.duration().trim());
    const lead = Number(this.lead().trim());
    this.durationError.set(whole(this.duration()) && duration >= 10 && duration <= 240 ? null : this.t.durationInvalid);
    this.leadError.set(whole(this.lead()) && lead >= 0 && lead <= 240 ? null : this.t.leadInvalid);
    if (this.durationError() || this.leadError()) {
      document.getElementById(this.id + (this.durationError() ? '-duration' : '-lead'))?.focus();
      return;
    }
    this.saving.set(true);
    this.message.set(null);
    this.error.set(null);
    try {
      const saved = await firstValueFrom(
        this.http.put<MatchDaySettings>(`/api/teams/${this.teamId()}/match-day-settings`, {
          matchDurationMinutes: duration,
          arrivalLeadMinutes: lead,
          meetingPoint: this.meetingPoint().trim() || null,
        }),
      );
      this.message.set(this.t.saved);
      this.saved.emit(saved);
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.saving.set(false);
    }
  }
}
