import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import type { EventDetail } from '../api/types';
import { errorText } from '../core/error-text';
import { Icon } from '../core/icon';
import { TEXTS } from '../core/texts';

/**
 * Marker «Angeheftet» of a pinned event (TER-26, E-114): on the event page, the cards of `/termine` and the start
 * page. The icon is decoration; the word is the name for everyone.
 */
@Component({
  selector: 'app-pinned-chip',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  template: `<span class="chip pinned-chip" data-kind="pinned" data-testid="event-pinned"
    ><app-icon name="pin" [size]="14" />{{ texts.events.pinned }}</span
  >`,
})
export class PinnedChip {
  protected readonly texts = TEXTS;
}

/**
 * «An Startseite anheften» / «Nicht mehr anheften» on the event page for whoever may edit the event (TER-27).
 * Reports the changed event and a status message; errors (403, 409) as an alert of the page.
 */
@Component({
  selector: 'app-pin-action',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  template: `
    <button
      type="button"
      class="button button-secondary button-small"
      data-testid="event-pin"
      [attr.data-pinned]="event().pinned"
      [disabled]="busy()"
      (click)="toggle()"
    >
      <app-icon [name]="event().pinned ? 'pin-off' : 'pin'" [size]="18" />
      {{ event().pinned ? texts.events.unpin : texts.events.pin }}
    </button>
  `,
})
export class PinAction {
  private readonly http = inject(HttpClient);
  protected readonly texts = TEXTS;

  readonly event = input.required<EventDetail>();
  /** The event after pinning or unpinning, with the message for the status line. */
  readonly changed = output<{ event: EventDetail; message: string }>();
  readonly failed = output<string>();

  protected readonly busy = signal(false);

  protected async toggle(): Promise<void> {
    const t = this.event();
    const pin = !t.pinned;
    this.busy.set(true);
    try {
      const event = await firstValueFrom(
        this.http.post<EventDetail>(`/api/events/${t.id}/${pin ? 'pin' : 'unpin'}`, null),
      );
      this.changed.emit({ event, message: pin ? TEXTS.events.pinDone : TEXTS.events.unpinDone });
    } catch (e) {
      this.failed.emit(errorText(e));
    } finally {
      this.busy.set(false);
    }
  }
}
