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
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { EventLineups, ResponseStatus } from '../api/types';
import { name } from '../core/format';
import { Icon } from '../core/icon';
import { TEXTS } from '../core/texts';
import { RESPONSE_ICON, responseKey } from './response';

/**
 * Section «Aufstellungen» of a match day (LIN-16): the special line-ups of the team (power play, penalty
 * kill, …) with the response of every player. A place whose player is not coming is marked, so that the
 * coach sees the gap. Without special line-ups nothing is shown.
 */
@Component({
  selector: 'app-event-lineups',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, RouterLink],
  template: `
    @if (data(); as d) {
      @if (d.lineups.length > 0) {
        <section class="card lineups" aria-labelledby="event-lineups-title" data-testid="event-lineups">
          <h2 id="event-lineups-title" class="section-title">{{ s.eventSection }}</h2>
          <div class="grid">
            @for (l of d.lineups; track l.id) {
              <section class="lineup" [attr.aria-labelledby]="'event-lineup-' + l.id">
                <h3 [id]="'event-lineup-' + l.id">{{ l.name }}</h3>
                <ol class="places" [attr.aria-labelledby]="'event-lineup-' + l.id">
                  @for (p of l.places; track p.position) {
                    <li
                      class="place"
                      [attr.data-gap]="p.gap ? 'true' : null"
                      [attr.data-empty]="p.person ? null : 'true'"
                    >
                      <span class="place-label">{{ s.placeText(p.label ?? null, p.position) }}</span>
                      @if (p.person; as person) {
                        <span class="player">
                          <span
                            class="symbol"
                            [attr.data-kind]="key(status(p.response))"
                            aria-hidden="true"
                          >
                            <app-icon [name]="icon[status(p.response)]" [size]="14" />
                          </span>
                          <span class="player-name">{{ name(person) }}</span>
                          <span class="secondary-text">{{
                            p.calledUp === false
                              ? s.notCalledUp
                              : texts.response.status[status(p.response)]
                          }}</span>
                          @if (p.gap) {
                            <span class="chip" data-kind="gap">{{ s.gap }}</span>
                          }
                        </span>
                      } @else {
                        <span class="secondary-text">{{ s.emptyPlace }}</span>
                      }
                    </li>
                  }
                </ol>
              </section>
            }
          </div>
          @if (canEdit() && teamId(); as team) {
            <a class="edit-link" [routerLink]="['/teams', team, 'linien']">{{ s.edit }}</a>
          }
        </section>
      }
    }
  `,
  styles: `
    :host {
      display: contents;
    }

    .lineups {
      display: grid;
      gap: 12px;
      min-width: 0;
    }

    .grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
      gap: 12px;
    }

    .lineup {
      display: grid;
      align-content: start;
      gap: 6px;
      padding: 12px;
      border: 1px solid var(--border);
      border-radius: var(--radius-icon);
      min-width: 0;
    }

    .lineup h3 {
      margin: 0;
      font-size: 15px;
    }

    .places {
      display: grid;
      gap: 6px;
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .place {
      display: grid;
      gap: 1px;
    }

    .place-label {
      font-size: 12px;
      font-weight: 700;
      color: var(--muted);
    }

    .player {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 4px 8px;
    }

    .player-name {
      font-weight: 600;
    }

    .place[data-gap] .player-name {
      text-decoration: line-through;
    }

    .symbol {
      display: inline-grid;
      place-items: center;
      width: 22px;
      height: 22px;
      border-radius: 50%;
      background: var(--border);
      color: var(--text);
      flex: none;
    }

    .symbol[data-kind='attending'] {
      background: var(--primary-soft);
      color: var(--primary);
    }

    .symbol[data-kind='declined'] {
      background: var(--coral-soft);
      color: var(--coral);
    }

    .symbol[data-kind='maybe'] {
      background: var(--mustard-soft);
      color: var(--mustard);
    }

    .chip[data-kind='gap'] {
      background: var(--coral-soft);
      color: var(--text);
    }

    .edit-link {
      justify-self: start;
      display: inline-flex;
      align-items: center;
      min-height: var(--touch);
      font-weight: 700;
    }
  `,
})
export class EventLineupsSection {
  private readonly http = inject(HttpClient);
  protected readonly texts = TEXTS;
  protected readonly s = TEXTS.lineups;
  protected readonly name = name;
  protected readonly icon = RESPONSE_ICON;
  protected readonly key = responseKey;

  readonly eventId = input.required<string>();
  /** Team of the match day, for the link to its line-ups. */
  readonly teamId = input<string | null>(null);
  /** Coach or admin: link to change the line-ups. */
  readonly canEdit = input(false);
  /** Changes when the event page reloads (e.g. after a response), so the gaps stay current. */
  readonly version = input(0);

  protected readonly data = signal<EventLineups | null>(null);

  constructor() {
    effect(() => {
      const id = this.eventId();
      this.version();
      untracked(() => void this.load(id));
    });
  }

  protected status(response: ResponseStatus | undefined): ResponseStatus {
    return response ?? 'PENDING';
  }

  private async load(id: string): Promise<void> {
    try {
      this.data.set(
        await firstValueFrom(this.http.get<EventLineups>(`/api/events/${id}/special-lineups`)),
      );
    } catch {
      // The section is an extra: without it the match day still works
      this.data.set(null);
    }
  }
}
