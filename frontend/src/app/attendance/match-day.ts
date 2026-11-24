import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { Match, MatchInput, EventDetail } from '../api/types';
import { fieldErrorsOf, errorText } from '../core/error-text';
import { Icon } from '../core/icon';
import { TEXTS } from '../core/texts';
import { clockTime } from '../core/time';

const RESULT_PATTERN = /^\d{1,2}:\d{1,2}$/;
/** The number pad of a phone has no colon (iOS), so «5.3» or «5,3» count as «5:3» («5-3» stays invalid, AUF-15). */
const RESULT_SEPARATOR = /^(\d{1,2})[.,](\d{1,2})$/;

/**
 * Part of a match day in the event detail: own call-up status, or for coaches the state with a link to
 * the call-up (AUF-23, AUF-24), matches by start time (AUF-14) and entering results (AUF-15).
 */
@Component({
  selector: 'app-match-day',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon],
  template: `
    @if (part() !== 'matches' && (event().myCallUp || canCallUp())) {
      <section class="card call-up" aria-labelledby="match-day-call-up-title">
        <h2 id="match-day-call-up-title" class="section-title">{{ s.callUp }}</h2>
        @if (event().myCallUp; as mine) {
          <p class="mine">
            <span class="chip" [attr.data-kind]="mine.toLowerCase()" data-testid="my-call-up">{{
              s.mine[mine]
            }}</span>
          </p>
          <p class="secondary-text">{{ s.mineText[mine] }}</p>
        }
        @if (canCallUp()) {
          <p class="state">{{ s.state[event().callUpStatus ?? 'NONE'] }}</p>
          <a class="button button-secondary button-small" [routerLink]="['/termine', event().id, 'aufgebot']">{{
            s.editCallUp
          }}</a>
        }
      </section>
    }

    @if (part() !== 'callUp') {
    <section class="card matches" aria-labelledby="match-day-matches-title">
      <h2 id="match-day-matches-title" class="section-title">{{ s.matches }}</h2>
      <!-- Rank of the own team in the league table last loaded (AUF-34) -->
      @if (event().standing; as st) {
        <p class="standing" data-testid="standing">
          <strong>{{ texts.standings.own(st.rank, st.teams) }}</strong>
          @if (st.league) {
            · {{ texts.standings.league(st.league, st.group) }}
          }
          ·
          <a [routerLink]="['/teams', st.teamId, 'liga']">{{ texts.standings.toLeague }}</a>
        </p>
      }
      @if (message(); as m) {
        <p class="message-success" role="status">{{ m }}</p>
      }
      @if (error(); as m) {
        <p class="message-error" role="alert">{{ m }}</p>
      }
      @if ((event().matches ?? []).length === 0) {
        <p class="empty">{{ s.noMatches }}</p>
      } @else {
        <ol class="list" data-testid="matches" aria-labelledby="match-day-matches-title">
          @for (m of event().matches ?? []; track m.id) {
            <li class="match" [attr.data-testid]="'match-' + m.id" [attr.aria-label]="description(m)">
              <span class="start-time">{{ time(m.startTime) }}</span>
              <span class="match-text">
                <span class="pairing">{{ pairing(m) }}</span>
                <span class="secondary-text"
                  >{{ m.home ? s.home : s.away }}
                  @if (m.opponentRank; as rank) {
                    · <span data-testid="opponent-rank">{{ texts.standings.opponent(rank) }}</span>
                  }
                </span>
                @if (m.result) {
                  <span class="result">{{ s.result(m.result) }}</span>
                }
                @if (m.missingAtSu) {
                  <span class="su-warning" data-testid="match-missing">
                    <app-icon name="triangle-alert" [size]="16" />
                    <span>{{ s.missingAtSu }}</span>
                  </span>
                }
                @if (m.suChange; as change) {
                  <span class="su-warning" data-testid="match-su-change">
                    <app-icon name="triangle-alert" [size]="16" />
                    <span>
                      <strong>{{ s.suChangeTitle }}</strong>
                      @for (item of change.changes; track item.field) {
                        <span class="su-change-item" [attr.data-field]="item.field">{{
                          s.suChangeItem(s.suChangeField[item.field], item.before, item.after)
                        }}</span>
                      }
                    </span>
                  </span>
                }
              </span>
              @if (m.suChange && canConfirm()) {
                <button
                  type="button"
                  class="button button-secondary button-small"
                  [attr.aria-label]="s.confirmSuChangeFor(pairing(m))"
                  [disabled]="running()"
                  [attr.aria-busy]="confirming() === m.id ? 'true' : null"
                  (click)="confirm(m)"
                >
                  {{ s.confirmSuChange }}
                </button>
              }
              @if (canEnterResult()) {
                <button
                  type="button"
                  class="button button-secondary button-small"
                  [attr.aria-label]="s.enterResultFor(pairing(m))"
                  [disabled]="running()"
                  (click)="open(m)"
                >
                  {{ s.enterResult }}
                </button>
              }
            </li>
          }
        </ol>
      }
    </section>
    }

    <dialog
      #dialog
      class="card dialog"
      aria-modal="true"
      aria-labelledby="result-title"
      aria-describedby="result-text"
      (close)="target.set(null)"
    >
      @if (target(); as m) {
        <form class="form" (submit)="save($event)" novalidate>
          <h2 id="result-title">{{ s.resultTitle(pairing(m)) }}</h2>
          <p class="secondary-text" id="result-text">{{ s.resultText }}</p>
          <div class="field">
            <label for="result-field">{{ s.resultField }}</label>
            <input
              #resultField
              id="result-field"
              name="result"
              type="text"
              inputmode="decimal"
              spellcheck="false"
              maxlength="5"
              autocomplete="off"
              [value]="result()"
              (input)="result.set($any($event.target).value)"
              [attr.aria-invalid]="resultError() ? 'true' : null"
              [attr.aria-describedby]="resultError() ? 'result-error' : null"
            />
            @if (resultError(); as m) {
              <p class="field-error" id="result-error">{{ m }}</p>
            }
          </div>
          <div class="dialog-buttons">
            <button
              type="submit"
              class="button button-primary button-small"
              [disabled]="running()"
              [attr.aria-busy]="running() ? 'true' : null"
            >
              {{ s.saveResult }}
            </button>
            <button
              type="button"
              class="button button-secondary button-small"
              [disabled]="running()"
              (click)="close()"
            >
              {{ texts.response.cancel }}
            </button>
          </div>
        </form>
      }
    </dialog>
  `,
  styleUrl: './match-day.css',
})
export class MatchDay {
  private readonly http = inject(HttpClient);
  protected readonly texts = TEXTS;
  protected readonly s = TEXTS.matchDay;
  protected readonly time = clockTime;

  readonly event = input.required<EventDetail>();
  /**
   * Which part to show: the call-up, the matches, or both. The event page places them as separate
   * sections in the order of the event variant (VAR-07).
   */
  readonly part = input<'callUp' | 'matches' | 'both'>('both');
  /** Coach of the team or admin: state of the call-up and link to it (RR-03). */
  readonly canCallUp = input(false);
  /** Coach of the team or admin, match day not cancelled: enter results (AUF-15). */
  readonly canEnterResult = input(false);
  /** Coach of the team or admin: confirm a change by swiss unihockey as seen (SUH-12). */
  readonly canConfirm = input(false);

  readonly changed = output<void>();

  protected readonly target = signal<Match | null>(null);
  protected readonly result = signal('');
  protected readonly resultError = signal<string | null>(null);
  protected readonly running = signal(false);
  /** ID of the match whose change is being confirmed. */
  protected readonly confirming = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  private readonly dialog = viewChild<ElementRef<HTMLDialogElement>>('dialog');
  private readonly resultField = viewChild<ElementRef<HTMLInputElement>>('resultField');
  private readonly team = computed(() => this.event().teams[0]?.name ?? '');

  /** «Team 1 – UHC Musterdorf» (home first). */
  protected pairing(m: Match): string {
    return m.home ? this.s.pairing(this.team(), m.opponent) : this.s.pairing(m.opponent, this.team());
  }

  protected description(m: Match): string {
    return `${clockTime(m.startTime)} ${this.pairing(m)}`;
  }

  protected open(m: Match): void {
    this.target.set(m);
    this.result.set(m.result ?? '');
    this.resultError.set(null);
    this.message.set(null);
    this.error.set(null);
    const d = this.dialog()?.nativeElement;
    if (d && !d.open) d.showModal();
  }

  protected close(): void {
    this.dialog()?.nativeElement.close();
  }

  /** The coach has seen what swiss unihockey changed: the warning disappears (SUH-12). */
  protected async confirm(m: Match): Promise<void> {
    if (this.running()) return;
    this.running.set(true);
    this.confirming.set(m.id);
    this.message.set(null);
    this.error.set(null);
    try {
      await firstValueFrom(
        this.http.post<Match>(`/api/events/${this.event().id}/matches/${m.id}/confirm-su-change`, null),
      );
      this.message.set(this.s.suChangeConfirmed(this.pairing(m)));
      this.changed.emit();
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.running.set(false);
      this.confirming.set(null);
    }
  }

  protected async save(domEvent: Event): Promise<void> {
    domEvent.preventDefault();
    const m = this.target();
    if (!m || this.running()) return;
    const value = this.result().replace(/\s/g, '').replace(RESULT_SEPARATOR, '$1:$2');
    if (value && !RESULT_PATTERN.test(value)) {
      this.resultError.set(this.s.resultFormat);
      this.resultField()?.nativeElement.focus();
      return;
    }
    this.running.set(true);
    this.resultError.set(null);
    const input: MatchInput = {
      opponent: m.opponent,
      startTime: m.startTime,
      home: m.home,
      result: value || null,
    };
    try {
      await firstValueFrom(this.http.put<Match>(`/api/events/${this.event().id}/matches/${m.id}`, input));
      this.close();
      this.message.set(this.s.resultSaved(this.pairing(m)));
      this.changed.emit();
    } catch (e) {
      const fields = fieldErrorsOf(e);
      if (fields['result']) {
        this.resultError.set(fields['result']);
      } else {
        this.close();
        this.error.set(errorText(e));
      }
    } finally {
      this.running.set(false);
    }
  }
}
