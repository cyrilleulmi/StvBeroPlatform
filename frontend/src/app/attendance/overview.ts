import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  ElementRef,
  computed,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import type {
  EventResponse,
  ResponseInput,
  ResponseStatus,
  PersonSummary,
  Overview as OverviewData,
} from '../api/types';
import { NgTemplateOutlet } from '@angular/common';
import { fieldErrorsOf, errorText } from '../core/error-text';
import { Icon } from '../core/icon';
import { Scoreboard } from '../core/scoreboard';
import { TEXTS } from '../core/texts';
import { RESPONSE_ICON, ResponseService, responseKey } from './response';
import { Warnings } from './warnings';
import { showModal } from '../core/dialog';
import {
  PRE_TRAINING_CHOICES,
  PreTrainingChoice,
  preTrainingChoice,
  preTrainingInput,
} from './pre-training-response';

type Choice = ResponseInput['status'];

interface Target {
  person: PersonSummary;
  response: EventResponse;
}

/**
 * Overview of an event (ANM-20 to ANM-32): total counter, per team counters and groups (lines, goalies,
 * «Ohne Linie»), coach block. Coaches (of any team) and admins respond on behalf of everyone (ANM-11,
 * RR-03, E-69). The API only delivers comments to those permitted (ANM-07). A practice with a pre-training shows who
 * is at the pre-training: a count and a mark per person who is «dabei» (ANM-17).
 */
@Component({
  selector: 'app-overview',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, NgTemplateOutlet, Warnings, Scoreboard],
  template: `
    <section class="card overview" data-testid="overview" aria-labelledby="overview-title">
      <h2 id="overview-title" class="section-title">{{ t.title }}</h2>
      <div class="total board" data-testid="overview-total">
        <span class="counter-name">{{ t.total }}</span>
        <p class="scoreboard numbers">
          <app-scoreboard [counts]="data().total" />
        </p>
      </div>
      @if (data().preTrainingTotal; as c) {
        <ng-container
          [ngTemplateOutlet]="preCounter"
          [ngTemplateOutletContext]="{ $implicit: c, testId: 'overview-pre-training-total' }"
        />
      }

      @if (data().warnings; as w) {
        <!-- Only coaches and admins get warnings from the API (F-16) -->
        <app-warnings [warnings]="w" [withTeam]="data().teams.length > 1" testId="event-warnings" />
      }

      @if (message(); as m) {
        <p class="message-success" role="status">{{ m }}</p>
      }
      @if (error(); as m) {
        <p class="message-error" role="alert">{{ m }}</p>
      }

      @for (block of data().teams; track block.team.id) {
        <section
          class="team"
          [attr.data-testid]="'overview-team-' + block.team.id"
          [attr.aria-labelledby]="'overview-team-title-' + block.team.id"
        >
          <div class="team-header">
            <h3 [id]="'overview-team-title-' + block.team.id">
              {{ block.team.name }}
              @if (block.optional) {
                <!-- Invited without obligation: only those who responded are counted (TER-16) -->
                <span class="chip" data-kind="optional" data-testid="overview-team-optional">{{
                  texts.participation.teamChip
                }}</span>
              }
            </h3>
            <p
              class="counter"
              [attr.data-testid]="'overview-team-' + block.team.id + '-counter'"
            >
              <span data-counter="attending">{{ t.attending(block.counts.attending) }}</span>
              <span class="separator"> · </span>
              <span data-counter="declined">{{ t.declined(block.counts.declined) }}</span>
              <span class="separator"> · </span>
              <span data-counter="maybe">{{ t.maybe(block.counts.maybe) }}</span>
              <span class="separator"> · </span>
              <span data-counter="pending">{{ t.pending(block.counts.pending) }}</span>
            </p>
            @if (data().teams.length > 1 && block.preTrainingCounts; as c) {
              <ng-container
                [ngTemplateOutlet]="preCounter"
                [ngTemplateOutletContext]="{
                  $implicit: c,
                  testId: 'overview-team-' + block.team.id + '-pre-training',
                }"
              />
            }
          </div>
          @for (g of block.groups; track g.groupId ?? 'no-line') {
            @let groupKey = g.groupId ? 'overview-group-' + g.groupId : 'overview-no-line-' + block.team.id;
            <section class="group" [attr.data-testid]="groupKey" [attr.aria-labelledby]="groupKey + '-title'">
              <h4 [id]="groupKey + '-title'">{{ g.name }}</h4>
              <ul class="people" [attr.aria-labelledby]="groupKey + '-title'">
                @for (e of g.entries; track e.person.id) {
                  <li
                    class="person"
                    [attr.data-testid]="'overview-person-' + e.person.id"
                    [attr.data-response]="e.response.status"
                  >
                    <ng-container
                      [ngTemplateOutlet]="personTpl"
                      [ngTemplateOutletContext]="{ $implicit: e, extra: null }"
                    />
                    @if (canRespondForPlayer(block.team.id, e.person.id)) {
                      <button
                        type="button"
                        class="icon-button"
                        [attr.aria-label]="a.onBehalf(name(e.person))"
                        [disabled]="running()"
                        (click)="open(e)"
                      >
                        <app-icon name="pencil" [size]="18" />
                      </button>
                    }
                  </li>
                }
              </ul>
            </section>
          }
        </section>
      }

      @if (data().coaches.length > 0) {
        <section class="team coaches" data-testid="coach-block" aria-labelledby="overview-coaches-title">
          <h3 id="overview-coaches-title">{{ t.coaches }}</h3>
          <ul class="people" aria-labelledby="overview-coaches-title">
            @for (c of data().coaches; track c.person.id + c.team.id) {
              <li
                class="person"
                [attr.data-testid]="'overview-person-' + c.person.id"
                [attr.data-response]="c.response.status"
              >
                <ng-container
                  [ngTemplateOutlet]="personTpl"
                  [ngTemplateOutletContext]="{ $implicit: c, extra: t.coachOf(c.team.name) }"
                />
                @if (canRespondForCoach(c.person.id)) {
                  <button
                    type="button"
                    class="icon-button"
                    [attr.aria-label]="a.onBehalf(name(c.person))"
                    [disabled]="running()"
                    (click)="open(c)"
                  >
                    <app-icon name="pencil" [size]="18" />
                  </button>
                }
              </li>
            }
          </ul>
        </section>
      }

      <!-- Who is at the pre-training (ANM-17): the same four numbers, one line -->
      <ng-template #preCounter let-c let-testId="testId">
        <p class="counter pre-counter" [attr.data-testid]="testId">
          <span class="pre-counter-name">{{ p.counter }}</span>
          <span data-counter="attending">{{ t.attending(c.attending) }}</span>
          <span class="separator"> · </span>
          <span data-counter="declined">{{ t.declined(c.declined) }}</span>
          <span class="separator"> · </span>
          <span data-counter="maybe">{{ t.maybe(c.maybe) }}</span>
          <span class="separator"> · </span>
          <span data-counter="pending">{{ t.pending(c.pending) }}</span>
        </p>
      </ng-template>

      <ng-template #personTpl let-e let-extra="extra">
        <span class="symbol" [attr.data-kind]="key(e.response.status)" aria-hidden="true">
          <app-icon [name]="icon[status(e.response)]" [size]="16" />
        </span>
        <span class="person-text">
          <span class="person-name">
            {{ name(e.person) }}
            @if (extra) {
              <span class="secondary-text">{{ extra }}</span>
            }
          </span>
          <span class="status-text" [attr.data-kind]="key(e.response.status)">{{
            a.status[status(e.response)]
          }}</span>
          @if (e.response.lateCancellation) {
            <span class="chip" data-kind="late-cancellation">{{ a.lateCancellation }}</span>
          }
          @if (preChip(e.response); as kind) {
            <span
              class="chip"
              data-testid="overview-pre-training"
              [attr.data-kind]="'pre-' + kind.toLowerCase()"
              >{{ p.chip[kind] }}</span
            >
          }
          @if (e.response.travelsIndividually) {
            <span class="chip" data-kind="individual" data-testid="overview-individual">{{
              texts.individual.short
            }}</span>
          }
          @if (e.response.comment; as k) {
            <span class="secondary-text comment">«{{ k }}»</span>
          }
          @if (e.response.enteredBy; as from) {
            <span class="secondary-text">{{ a.enteredBy(name(from)) }}</span>
          }
        </span>
      </ng-template>
    </section>

    <dialog
      #dialog
      class="card dialog"
      aria-modal="true"
      aria-labelledby="on-behalf-title"
      aria-describedby="on-behalf-text"
      (close)="closed()"
    >
      @if (target(); as z) {
        <form class="form" (submit)="save($event)" novalidate>
          <h2 id="on-behalf-title">{{ a.onBehalf(name(z.person)) }}</h2>
          <p class="secondary-text" id="on-behalf-text">{{ a.onBehalfText(z.person.firstName) }}</p>
          <fieldset class="choice">
            <legend class="label">{{ a.response }}</legend>
            @for (w of choices; track w) {
              <label class="checkbox">
                <input
                  type="radio"
                  name="on-behalf-status"
                  [value]="w"
                  [checked]="choice() === w"
                  (change)="choice.set(w)"
                />
                {{ a.status[w] }}
              </label>
            }
          </fieldset>
          @if (data().preTrainingTotal) {
            <!-- The pre-training goes with «dabei» only (ANM-15, ANM-16) -->
            <fieldset class="choice" data-testid="on-behalf-pre-training" [disabled]="choice() !== 'ATTENDING'">
              <legend class="label">{{ p.onBehalf }}</legend>
              @for (w of preChoices; track w) {
                <label class="checkbox">
                  <input
                    type="radio"
                    name="on-behalf-pre-training"
                    [value]="w"
                    [checked]="choice() === 'ATTENDING' && preChoice() === w"
                    (change)="preChoice.set(w)"
                  />
                  {{ a.status[w] }}
                </label>
              }
              @if (choice() !== 'ATTENDING') {
                <p class="secondary-text">{{ p.onBehalfHint }}</p>
              } @else if (!preChoice()) {
                <p class="secondary-text">{{ p.onBehalfOpen }}</p>
              }
            </fieldset>
          }
          <div class="field">
            <label for="on-behalf-comment">{{ a.commentOptional }}</label>
            <input
              name="on-behalf-comment"
              id="on-behalf-comment"
              type="text"
              maxlength="200"
              autocomplete="off"
              [value]="comment()"
              (input)="comment.set($any($event.target).value)"
              [attr.aria-invalid]="commentError() ? 'true' : null"
              [attr.aria-describedby]="commentError() ? 'on-behalf-comment-error' : null"
            />
            @if (commentError(); as m) {
              <p class="field-error" id="on-behalf-comment-error">{{ m }}</p>
            }
          </div>
          <div class="dialog-buttons">
            <button
              type="submit"
              class="button button-primary button-small"
              [disabled]="running()"
              [attr.aria-busy]="running() ? 'true' : null"
            >
              {{ a.save }}
            </button>
            <button
              type="button"
              class="button button-secondary button-small"
              [disabled]="running()"
              (click)="close()"
            >
              {{ a.cancel }}
            </button>
          </div>
        </form>
      }
    </dialog>
  `,
  styleUrls: ['./response.css', './overview.css'],
})
export class OverviewSection {
  private readonly changes = inject(ChangeDetectorRef);
  private readonly responses = inject(ResponseService);
  protected readonly t = TEXTS.overview;
  protected readonly texts = TEXTS;
  protected readonly a = TEXTS.response;
  protected readonly p = TEXTS.preTrainingResponse;
  protected readonly preChoices = PRE_TRAINING_CHOICES;
  protected readonly icon = RESPONSE_ICON;
  protected readonly key = responseKey;
  protected readonly choices: Choice[] = ['ATTENDING', 'DECLINED', 'MAYBE'];

  readonly eventId = input.required<string>();
  readonly data = input.required<OverviewData>();
  /** Teams in which the logged-in person is coach (RR-06). */
  readonly coachIn = input<string[]>([]);
  /** Admin or coach of any team: responds on behalf of everyone (RR-03, E-69). */
  readonly allTeams = input(false);
  readonly meId = input<string | null>(null);
  /** Responding possible (event planned and not past). */
  readonly responsesOpen = input(true);

  readonly changed = output<void>();

  protected readonly target = signal<Target | null>(null);
  protected readonly choice = signal<Choice>('ATTENDING');
  protected readonly preChoice = signal<PreTrainingChoice | null>(null);
  protected readonly comment = signal('');
  protected readonly commentError = signal<string | null>(null);
  protected readonly running = signal(false);
  protected readonly message = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  private readonly dialog = viewChild<ElementRef<HTMLDialogElement>>('dialog');
  private readonly coachTeams = computed(() => new Set(this.coachIn()));

  protected name(p: PersonSummary): string {
    return `${p.firstName} ${p.lastName}`;
  }

  protected status(a: EventResponse): ResponseStatus {
    return a.status;
  }

  /** The mark of a person who is «dabei» at a practice with a pre-training (ANM-17); nothing for everyone else. */
  protected preChip(r: EventResponse): 'ATTENDING' | 'DECLINED' | 'PENDING' | null {
    if (r.status !== 'ATTENDING' || !r.preTraining) return null;
    return preTrainingChoice(r.preTraining) ?? 'PENDING';
  }

  /** Coaches and admins for everyone; not for themselves (ANM-11, RR-03, E-69). */
  protected canRespondForPlayer(teamId: string, personId: string): boolean {
    return (
      this.responsesOpen() &&
      personId !== this.meId() &&
      (this.allTeams() || this.coachTeams().has(teamId))
    );
  }

  /** Coaches and admins also enter responses of other coaches (E-69). */
  protected canRespondForCoach(personId: string): boolean {
    return this.responsesOpen() && this.allTeams() && personId !== this.meId();
  }

  protected open(z: Target): void {
    this.target.set(z);
    this.choice.set(z.response.status === 'PENDING' ? 'ATTENDING' : (z.response.status as Choice));
    this.preChoice.set(z.response.status === 'ATTENDING' ? preTrainingChoice(z.response.preTraining) : null);
    this.comment.set(z.response.comment ?? '');
    this.commentError.set(null);
    this.message.set(null);
    this.error.set(null);
    showModal(this.dialog()?.nativeElement, this.changes);
  }

  protected close(): void {
    this.dialog()?.nativeElement.close();
  }

  protected closed(): void {
    this.target.set(null);
  }

  protected async save(domEvent: Event): Promise<void> {
    domEvent.preventDefault();
    const z = this.target();
    if (!z || this.running()) return;
    const comment = this.comment().trim();
    if (this.choice() === 'MAYBE' && !comment) {
      this.commentError.set(this.a.commentRequired);
      document.getElementById('on-behalf-comment')?.focus();
      return;
    }
    this.running.set(true);
    this.commentError.set(null);
    try {
      const updated = await this.responses.set(
        this.eventId(),
        {
          status: this.choice(),
          comment: comment || null,
          ...preTrainingInput(this.choice(), this.preChoice()),
        },
        z.person.id,
      );
      this.close();
      this.message.set(
        this.a.onBehalfSaved(this.name(z.person), this.a.status[updated.status]),
      );
      this.changed.emit();
    } catch (e) {
      const fields = fieldErrorsOf(e);
      if (fields['comment']) {
        this.commentError.set(fields['comment']);
      } else {
        this.close();
        this.error.set(errorText(e));
      }
    } finally {
      this.running.set(false);
    }
  }
}
