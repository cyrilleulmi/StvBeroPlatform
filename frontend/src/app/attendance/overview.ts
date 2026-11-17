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
import { TEXTS } from '../core/texts';
import { RESPONSE_ICON, ResponseService, responseKey } from './response';

type Choice = ResponseInput['status'];

interface Target {
  person: PersonSummary;
  response: EventResponse;
}

/**
 * Übersicht eines Termins (ANM-20 bis ANM-32): Gesamtzähler, pro Team Zähler und Gruppen (Linien, Goalies,
 * «Ohne Linie»), Coach-Block. Coaches tragen Antworten für Spielerinnen ihres Teams ein (ANM-11, RR-06),
 * Admins für alle (RR-04). Kommentare liefert die API nur an Berechtigte (ANM-07).
 */
@Component({
  selector: 'app-uebersicht',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, NgTemplateOutlet],
  template: `
    <section class="card overview" data-testid="uebersicht" aria-labelledby="uebersicht-titel">
      <h2 id="uebersicht-titel" class="section-title">{{ t.title }}</h2>
      <p class="counter total" data-testid="uebersicht-total">
        <span class="counter-name">{{ t.total }}</span>
        <span class="numbers">
          <span data-zaehler="dabei">{{ t.attending(data().total.attending) }}</span>
          <span class="separator"> · </span>
          <span data-zaehler="nicht-dabei">{{ t.declined(data().total.declined) }}</span>
          <span class="separator"> · </span>
          <span data-zaehler="vielleicht">{{ t.maybe(data().total.maybe) }}</span>
          <span class="separator"> · </span>
          <span data-zaehler="offen">{{ t.pending(data().total.pending) }}</span>
        </span>
      </p>

      @if (message(); as m) {
        <p class="message-success" role="status">{{ m }}</p>
      }
      @if (error(); as m) {
        <p class="message-error" role="alert">{{ m }}</p>
      }

      @for (block of data().teams; track block.team.id) {
        <section
          class="team"
          [attr.data-testid]="'uebersicht-team-' + block.team.id"
          [attr.aria-labelledby]="'uebersicht-team-titel-' + block.team.id"
        >
          <div class="team-header">
            <h3 [id]="'uebersicht-team-titel-' + block.team.id">{{ block.team.name }}</h3>
            <p
              class="counter"
              [attr.data-testid]="'uebersicht-team-' + block.team.id + '-zaehler'"
            >
              <span data-zaehler="dabei">{{ t.attending(block.counts.attending) }}</span>
              <span class="separator"> · </span>
              <span data-zaehler="nicht-dabei">{{ t.declined(block.counts.declined) }}</span>
              <span class="separator"> · </span>
              <span data-zaehler="vielleicht">{{ t.maybe(block.counts.maybe) }}</span>
              <span class="separator"> · </span>
              <span data-zaehler="offen">{{ t.pending(block.counts.pending) }}</span>
            </p>
          </div>
          @for (g of block.groups; track g.groupId ?? 'ohne-linie') {
            @let groupsId = g.groupId ? 'uebersicht-gruppe-' + g.groupId : 'uebersicht-ohne-linie-' + block.team.id;
            <section class="group" [attr.data-testid]="groupsId" [attr.aria-labelledby]="groupsId + '-titel'">
              <h4 [id]="groupsId + '-titel'">{{ g.name }}</h4>
              <ul class="people" [attr.aria-labelledby]="groupsId + '-titel'">
                @for (e of g.entries; track e.person.id) {
                  <li
                    class="person"
                    [attr.data-testid]="'uebersicht-person-' + e.person.id"
                    [attr.data-antwort]="e.response.status"
                  >
                    <ng-container
                      [ngTemplateOutlet]="personTpl"
                      [ngTemplateOutletContext]="{ $implicit: e, extra: null }"
                    />
                    @if (canForPlayer(block.team.id, e.person.id)) {
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
        <section class="team coaches" data-testid="coach-block" aria-labelledby="uebersicht-coaches-titel">
          <h3 id="uebersicht-coaches-titel">{{ t.coaches }}</h3>
          <ul class="people" aria-labelledby="uebersicht-coaches-titel">
            @for (c of data().coaches; track c.person.id + c.team.id) {
              <li
                class="person"
                [attr.data-testid]="'uebersicht-person-' + c.person.id"
                [attr.data-antwort]="c.response.status"
              >
                <ng-container
                  [ngTemplateOutlet]="personTpl"
                  [ngTemplateOutletContext]="{ $implicit: c, extra: t.coachOf(c.team.name) }"
                />
                @if (canForCoach(c.person.id)) {
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

      <ng-template #personTpl let-e let-extra="extra">
        <span class="symbol" [attr.data-art]="key(e.response.status)" aria-hidden="true">
          <app-icon [name]="icon[status(e.response)]" [size]="16" />
        </span>
        <span class="person-text">
          <span class="person-name">
            {{ name(e.person) }}
            @if (extra) {
              <span class="secondary-text">{{ extra }}</span>
            }
          </span>
          <span class="status-text" [attr.data-art]="key(e.response.status)">{{
            a.status[status(e.response)]
          }}</span>
          @if (e.response.lateCancellation) {
            <span class="chip" data-art="kurzfristig">{{ a.lateCancellation }}</span>
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
      aria-labelledby="stellvertretend-titel"
      aria-describedby="stellvertretend-text"
      (close)="closed()"
    >
      @if (target(); as z) {
        <form class="form" (submit)="save($event)" novalidate>
          <h2 id="stellvertretend-titel">{{ a.onBehalf(name(z.person)) }}</h2>
          <p class="secondary-text" id="stellvertretend-text">{{ a.onBehalfText(z.person.firstName) }}</p>
          <fieldset class="choice">
            <legend class="label">{{ a.response }}</legend>
            @for (w of choices; track w) {
              <label class="checkbox">
                <input
                  type="radio"
                  name="stellvertretend-status"
                  [value]="w"
                  [checked]="choice() === w"
                  (change)="choice.set(w)"
                />
                {{ a.status[w] }}
              </label>
            }
          </fieldset>
          <div class="field">
            <label for="stellvertretend-kommentar">{{ a.commentOptional }}</label>
            <input
              id="stellvertretend-kommentar"
              type="text"
              maxlength="200"
              autocomplete="off"
              [value]="comment()"
              (input)="comment.set($any($event.target).value)"
              [attr.aria-invalid]="commentError() ? 'true' : null"
              [attr.aria-describedby]="commentError() ? 'stellvertretend-kommentar-fehler' : null"
            />
            @if (commentError(); as m) {
              <p class="field-error" id="stellvertretend-kommentar-fehler">{{ m }}</p>
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
  private readonly responses = inject(ResponseService);
  protected readonly t = TEXTS.overview;
  protected readonly a = TEXTS.response;
  protected readonly icon = RESPONSE_ICON;
  protected readonly key = responseKey;
  protected readonly choices: Choice[] = ['ATTENDING', 'DECLINED', 'MAYBE'];

  readonly eventId = input.required<string>();
  readonly data = input.required<OverviewData>();
  /** Teams, in denen die angemeldete Person Coach ist (RR-06). */
  readonly coachIn = input<string[]>([]);
  readonly admin = input(false);
  readonly meId = input<string | null>(null);
  /** Antworten möglich (Termin geplant und nicht vorbei). */
  readonly open = input(true);

  readonly changed = output<void>();

  protected readonly target = signal<Target | null>(null);
  protected readonly choice = signal<Choice>('ATTENDING');
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

  /** Coaches für Spielerinnen ihres Teams, Admins für alle; nicht für sich selbst (ANM-11, RR-06). */
  protected canForPlayer(teamId: string, personId: string): boolean {
    return (
      this.pending() &&
      personId !== this.meId() &&
      (this.admin() || this.coachTeams().has(teamId))
    );
  }

  /** Antworten anderer Coaches trägt nur ein Admin ein. */
  protected canForCoach(personId: string): boolean {
    return this.pending() && this.admin() && personId !== this.meId();
  }

  protected open(z: Target): void {
    this.target.set(z);
    this.choice.set(z.response.status === 'PENDING' ? 'ATTENDING' : (z.response.status as Choice));
    this.comment.set(z.response.comment ?? '');
    this.commentError.set(null);
    this.message.set(null);
    this.error.set(null);
    const d = this.dialog()?.nativeElement;
    if (d && !d.open) d.showModal();
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
      document.getElementById('stellvertretend-kommentar')?.focus();
      return;
    }
    this.running.set(true);
    this.commentError.set(null);
    try {
      const updated = await this.responses.set(
        this.eventId(),
        { status: this.choice(), comment: comment || null },
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
