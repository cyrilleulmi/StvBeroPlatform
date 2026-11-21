import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { firstValueFrom } from 'rxjs';
import type {
  EventSection,
  EventType,
  EventVariant,
  EventVariantInput,
  VariantColor,
} from '../../api/types';
import { errorText, fieldErrorsOf } from '../../core/error-text';
import { TEXTS } from '../../core/texts';

const COLORS: VariantColor[] = [
  'GREEN',
  'TEAL',
  'BLUE',
  'VIOLET',
  'PINK',
  'RED',
  'ORANGE',
  'YELLOW',
  'GREY',
];
const TYPES: EventType[] = ['PRACTICE', 'MATCH_DAY', 'OTHER'];
const MATCH_DAY_SECTIONS: EventSection[] = [
  'RESPONSES',
  'MATCHES',
  'CALL_UP',
  'TIMELINE',
  'DRIVER_LIST',
  'LINEUPS',
  'ATTENDANCE',
  'DETAILS',
];
const PLAIN_SECTIONS: EventSection[] = ['RESPONSES', 'ATTENDANCE', 'DETAILS'];

interface SectionRow {
  section: EventSection;
  shown: boolean;
}

function sectionsOf(type: EventType): EventSection[] {
  return type === 'MATCH_DAY' ? MATCH_DAY_SECTIONS : PLAIN_SECTIONS;
}

/**
 * Section «Terminvarianten» of a team (VAR): the four standard variants and the team's own, each with name,
 * colour, call-up or responses only, automatic departure, the defaults for new events and the sections of
 * the event page in their order. Coaches and admins change a variant in a dialog.
 */
@Component({
  selector: 'app-event-variants',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section
      class="card variants"
      aria-labelledby="event-variants-title"
      data-testid="event-variants"
      [attr.aria-busy]="loading() ? 'true' : null"
    >
      <h2 id="event-variants-title">{{ s.section }}</h2>
      <p class="secondary-text">{{ s.intro }}</p>
      @if (message(); as m) {
        <p class="message-success" role="status">{{ m }}</p>
      }
      @if (error(); as m) {
        <p class="message-error" role="alert">{{ m }}</p>
      }
      @if (loading() && variants().length === 0) {
        <p class="empty">{{ texts.common.loading }}</p>
      } @else {
        <ul class="list" aria-labelledby="event-variants-title">
          @for (v of variants(); track v.id) {
            <li
              class="variant"
              [attr.aria-label]="v.name"
              [attr.data-testid]="'variant-' + (v.standard ? v.kind : v.id)"
              [attr.data-color]="v.color"
              [attr.data-call-up]="v.callUp"
            >
              <div class="variant-text">
                <span class="variant-name">
                  <span class="tag-chip" [attr.data-tag]="v.color">{{ v.name }}</span>
                  <span class="secondary-text">
                    {{ texts.events.type[v.eventType] }} · {{ s.colors[v.color] }}
                    @if (v.standard) {
                      · {{ s.standard }}
                    }
                  </span>
                </span>
                <span class="secondary-text" data-testid="variant-summary">{{ summary(v) }}</span>
                <span class="secondary-text">{{ sectionNames(v.sections) }}</span>
              </div>
              <span class="variant-buttons">
                <button
                  type="button"
                  class="button button-secondary button-small"
                  [attr.aria-label]="s.editFor(v.name)"
                  [disabled]="running()"
                  (click)="open(v)"
                >
                  {{ s.edit }}
                </button>
                @if (!v.standard) {
                  <button
                    type="button"
                    class="button button-secondary button-small"
                    [attr.aria-label]="s.removeFor(v.name)"
                    [disabled]="running()"
                    (click)="remove(v)"
                  >
                    {{ s.remove }}
                  </button>
                }
              </span>
            </li>
          }
        </ul>
        <button
          type="button"
          class="button button-secondary button-small add"
          data-testid="variant-add"
          [disabled]="running()"
          (click)="open(null)"
        >
          {{ s.add }}
        </button>
      }
    </section>

    <dialog #dialog class="card dialog" aria-modal="true" aria-labelledby="variant-dialog-title">
      <form class="form" (submit)="save($event)" novalidate>
        <h2 id="variant-dialog-title">
          {{ editing() ? s.dialogEdit(editing()!.name) : s.dialogNew }}
        </h2>
        @if (dialogError(); as m) {
          <p class="message-error" role="alert">{{ m }}</p>
        }
        <div class="field">
          <label for="variant-name">{{ s.name }}</label>
          <input
            #nameField
            id="variant-name"
            type="text"
            maxlength="40"
            autocomplete="off"
            [value]="variantName()"
            (input)="variantName.set($any($event.target).value)"
            [attr.aria-invalid]="nameError() ? 'true' : null"
            [attr.aria-describedby]="nameError() ? 'variant-name-error' : null"
          />
          @if (nameError(); as m) {
            <p class="field-error" id="variant-name-error">{{ m }}</p>
          }
        </div>
        @if (!editing()) {
          <div class="field">
            <label for="variant-type">{{ s.eventType }}</label>
            <select id="variant-type" (change)="setType($any($event.target).value)">
              @for (type of types; track type) {
                <option [value]="type" [selected]="type === eventType()">
                  {{ texts.events.type[type] }}
                </option>
              }
            </select>
          </div>
        }
        <fieldset class="colors">
          <legend class="label">{{ s.color }}</legend>
          <div class="color-options">
            @for (c of colors; track c) {
              <label class="color" [attr.data-tag]="c">
                <input
                  type="radio"
                  name="variant-color"
                  [value]="c"
                  [checked]="color() === c"
                  (change)="color.set(c)"
                />
                <span class="tag-dot" aria-hidden="true"></span>
                {{ s.colors[c] }}
              </label>
            }
          </div>
        </fieldset>
        @if (eventType() === 'MATCH_DAY') {
          <div class="switches">
            <label class="checkbox">
              <input
                type="checkbox"
                data-testid="variant-call-up"
                [checked]="callUp()"
                (change)="callUp.set($any($event.target).checked)"
                aria-describedby="variant-call-up-hint"
              />
              {{ s.callUp }}
            </label>
            <p class="secondary-text hint" id="variant-call-up-hint">{{ s.callUpHint }}</p>
            <label class="checkbox">
              <input
                type="checkbox"
                data-testid="variant-auto-departure"
                [checked]="autoDeparture()"
                (change)="autoDeparture.set($any($event.target).checked)"
                aria-describedby="variant-auto-hint"
              />
              {{ s.autoDeparture }}
            </label>
            <p class="secondary-text hint" id="variant-auto-hint">{{ s.autoDepartureHint }}</p>
          </div>
        }
        <div class="field deadline">
          <label for="variant-deadline">{{ s.responseDeadline }}</label>
          <input
            id="variant-deadline"
            type="number"
            inputmode="numeric"
            min="0"
            max="336"
            [value]="deadline()"
            (input)="deadline.set($any($event.target).value)"
            [attr.aria-invalid]="deadlineError() ? 'true' : null"
            [attr.aria-describedby]="deadlineError() ? 'variant-deadline-error' : null"
          />
          @if (deadlineError(); as m) {
            <p class="field-error" id="variant-deadline-error">{{ m }}</p>
          }
        </div>
        <label class="checkbox">
          <input
            type="checkbox"
            [checked]="attendanceCheck()"
            (change)="attendanceCheck.set($any($event.target).checked)"
          />
          {{ s.attendanceCheck }}
        </label>

        <fieldset class="sections">
          <legend class="label">{{ s.sections }}</legend>
          <p class="secondary-text">{{ s.sectionsHint }}</p>
          <ol class="section-rows" data-testid="variant-sections">
            @for (row of rows(); track row.section; let i = $index, last = $last) {
              <li class="section-row" [attr.data-section]="row.section" [attr.data-shown]="row.shown">
                <label class="checkbox">
                  <input
                    type="checkbox"
                    [checked]="row.shown"
                    [disabled]="row.section === 'DETAILS'"
                    [attr.aria-label]="s.show(s.sectionName[row.section])"
                    (change)="setShown(i, $any($event.target).checked)"
                  />
                  <span>
                    {{ s.sectionName[row.section] }}
                    @if (row.section === 'DETAILS') {
                      <span class="secondary-text">({{ s.alwaysShown }})</span>
                    }
                  </span>
                </label>
                <span class="move">
                  <button
                    type="button"
                    class="button button-secondary button-small"
                    [attr.aria-label]="s.moveUp(s.sectionName[row.section])"
                    [disabled]="i === 0"
                    (click)="move(i, -1)"
                  >
                    ↑
                  </button>
                  <button
                    type="button"
                    class="button button-secondary button-small"
                    [attr.aria-label]="s.moveDown(s.sectionName[row.section])"
                    [disabled]="last"
                    (click)="move(i, 1)"
                  >
                    ↓
                  </button>
                </span>
              </li>
            }
          </ol>
        </fieldset>

        <div class="dialog-buttons">
          <button
            type="submit"
            class="button button-primary button-small"
            [disabled]="running()"
            [attr.aria-busy]="running() ? 'true' : null"
          >
            {{ s.save }}
          </button>
          <button type="button" class="button button-secondary button-small" (click)="close()">
            {{ s.cancel }}
          </button>
        </div>
      </form>
    </dialog>
  `,
  styles: `
    :host {
      display: block;
    }

    .variants {
      display: grid;
      gap: 10px;
    }

    .variants h2,
    .variants p {
      margin: 0;
    }

    .variant {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 8px 16px;
      padding: 12px 0;
    }

    .variant-text {
      display: grid;
      gap: 2px;
      min-width: 0;
      flex: 1 1 260px;
    }

    .variant-name {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 6px 10px;
    }

    .variant-buttons {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }

    .add {
      justify-self: start;
    }

    .dialog {
      width: min(560px, calc(100vw - 32px));
      max-width: min(560px, calc(100vw - 32px));
      color: var(--text);
    }

    .dialog:not([open]) {
      display: none;
    }

    .dialog h2,
    .dialog p {
      margin: 0;
    }

    .dialog::backdrop {
      background: color-mix(in srgb, var(--bg) 80%, transparent);
    }

    .dialog-buttons {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
    }

    fieldset {
      margin: 0;
      padding: 0;
      border: 0;
      min-width: 0;
      display: grid;
      gap: 6px;
    }

    .color-options {
      display: flex;
      flex-wrap: wrap;
      gap: 6px 8px;
    }

    .color {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      min-height: var(--touch);
      padding: 0 12px;
      border: 1.5px solid var(--border);
      border-radius: var(--radius-pill);
      font-weight: 600;
      cursor: pointer;
    }

    .color:has(input:checked) {
      border-color: var(--tag);
      background: var(--tag-soft);
      color: var(--tag);
    }

    .color input {
      accent-color: var(--tag);
      margin: 0;
    }

    .switches {
      display: grid;
      gap: 2px;
    }

    .hint {
      padding-left: 34px;
    }

    .deadline input {
      max-width: 140px;
    }

    .section-rows {
      display: grid;
      gap: 2px;
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .section-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
      padding: 2px 0;
      border-bottom: 1px solid var(--border);
    }

    .section-row[data-shown='false'] .checkbox span {
      color: var(--muted);
    }

    .move {
      display: flex;
      gap: 6px;
      flex: none;
    }

    .move .button {
      min-width: var(--touch);
      padding: 0 10px;
    }
  `,
})
export class EventVariantsSection {
  private readonly http = inject(HttpClient);
  protected readonly texts = TEXTS;
  protected readonly s = TEXTS.variants;
  protected readonly colors = COLORS;
  protected readonly types = TYPES;

  readonly teamId = input.required<string>();

  protected readonly variants = signal<EventVariant[]>([]);
  protected readonly loading = signal(true);
  protected readonly running = signal(false);
  protected readonly message = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  /** Dialog: the variant being changed (`null`: a new one) and its fields. */
  protected readonly editing = signal<EventVariant | null>(null);
  protected readonly variantName = signal('');
  protected readonly eventType = signal<EventType>('PRACTICE');
  protected readonly color = signal<VariantColor>('GREY');
  protected readonly callUp = signal(false);
  protected readonly autoDeparture = signal(true);
  protected readonly deadline = signal('24');
  protected readonly attendanceCheck = signal(false);
  protected readonly rows = signal<SectionRow[]>([]);
  protected readonly nameError = signal<string | null>(null);
  protected readonly deadlineError = signal<string | null>(null);
  protected readonly dialogError = signal<string | null>(null);

  private readonly dialog = viewChild<ElementRef<HTMLDialogElement>>('dialog');
  private readonly nameField = viewChild<ElementRef<HTMLInputElement>>('nameField');
  private readonly url = computed(() => `/api/teams/${this.teamId()}/event-variants`);

  constructor() {
    effect(() => {
      this.teamId();
      untracked(() => void this.load());
    });
  }

  /** «mit Aufgebot · Abfahrt automatisch» for match days, nothing for the others. */
  protected summary(v: EventVariant): string {
    if (v.eventType !== 'MATCH_DAY') return '';
    return [v.callUp ? this.s.withCallUp : this.s.responsesOnly, v.autoDeparture ? this.s.autoOn : null]
      .filter(Boolean)
      .join(' · ');
  }

  protected sectionNames(sections: EventSection[]): string {
    return this.s.sectionsSummary(sections.map((x) => this.s.sectionName[x]));
  }

  protected open(v: EventVariant | null): void {
    this.editing.set(v);
    this.variantName.set(v?.name ?? '');
    this.eventType.set(v?.eventType ?? 'PRACTICE');
    this.color.set(v?.color ?? 'GREY');
    this.callUp.set(v?.callUp ?? false);
    this.autoDeparture.set(v?.autoDeparture ?? true);
    this.deadline.set(String(v?.responseDeadlineHours ?? 24));
    this.attendanceCheck.set(v?.attendanceCheck ?? false);
    this.rows.set(this.rowsFor(v?.eventType ?? 'PRACTICE', v?.sections));
    this.nameError.set(null);
    this.deadlineError.set(null);
    this.dialogError.set(null);
    this.message.set(null);
    this.error.set(null);
    const dialog = this.dialog()?.nativeElement;
    if (dialog && !dialog.open) dialog.showModal();
  }

  protected close(): void {
    this.dialog()?.nativeElement.close();
  }

  /** A new variant of another type has other sections. */
  protected setType(type: EventType): void {
    this.eventType.set(type);
    this.rows.set(this.rowsFor(type, undefined));
  }

  protected setShown(index: number, shown: boolean): void {
    this.rows.update((rows) => rows.map((r, i) => (i === index ? { ...r, shown } : r)));
  }

  protected move(index: number, step: -1 | 1): void {
    this.rows.update((rows) => {
      const target = index + step;
      if (target < 0 || target >= rows.length) return rows;
      const next = [...rows];
      [next[index], next[target]] = [next[target], next[index]];
      return next;
    });
  }

  protected async save(domEvent: Event): Promise<void> {
    domEvent.preventDefault();
    if (this.running()) return;
    const variantName = this.variantName().trim();
    if (!variantName) {
      this.nameError.set(this.s.nameRequired);
      this.nameField()?.nativeElement.focus();
      return;
    }
    const hours = Number(this.deadline());
    if (this.deadline().trim() === '' || !Number.isInteger(hours) || hours < 0 || hours > 336) {
      this.deadlineError.set(this.s.responseDeadlineInvalid);
      return;
    }
    const editing = this.editing();
    const matchDay = this.eventType() === 'MATCH_DAY';
    const input: EventVariantInput = {
      name: variantName,
      eventType: this.eventType(),
      color: this.color(),
      callUp: matchDay ? this.callUp() : false,
      autoDeparture: matchDay ? this.autoDeparture() : true,
      responseDeadlineHours: hours,
      attendanceCheck: this.attendanceCheck(),
      sections: this.rows()
        .filter((r) => r.shown)
        .map((r) => r.section),
    };
    this.running.set(true);
    this.nameError.set(null);
    this.deadlineError.set(null);
    this.dialogError.set(null);
    try {
      await firstValueFrom(
        editing
          ? this.http.put<EventVariant>(`${this.url()}/${editing.id}`, input)
          : this.http.post<EventVariant>(this.url(), input),
      );
      this.close();
      await this.load(false);
      this.message.set(editing ? this.s.saved(variantName) : this.s.created(variantName));
    } catch (e) {
      const fields = fieldErrorsOf(e);
      if (fields['name']) {
        this.nameError.set(fields['name']);
        this.nameField()?.nativeElement.focus();
      } else if (fields['responseDeadlineHours']) {
        this.deadlineError.set(fields['responseDeadlineHours']);
      } else {
        this.dialogError.set(errorText(e));
      }
    } finally {
      this.running.set(false);
    }
  }

  protected async remove(v: EventVariant): Promise<void> {
    if (this.running()) return;
    this.running.set(true);
    this.message.set(null);
    this.error.set(null);
    try {
      await firstValueFrom(this.http.delete(`${this.url()}/${v.id}`));
      await this.load(false);
      this.message.set(this.s.removed(v.name));
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.running.set(false);
    }
  }

  /** The shown sections in their order, then the hidden ones the event type has. */
  private rowsFor(type: EventType, shown: EventSection[] | undefined): SectionRow[] {
    const all = sectionsOf(type);
    const visible = (shown ?? all).filter((x) => all.includes(x));
    return [
      ...visible.map((section) => ({ section, shown: true })),
      ...all.filter((x) => !visible.includes(x)).map((section) => ({ section, shown: false })),
    ];
  }

  private async load(showLoading = true): Promise<void> {
    if (showLoading) this.loading.set(true);
    try {
      this.variants.set(await firstValueFrom(this.http.get<EventVariant[]>(this.url())));
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.loading.set(false);
    }
  }
}
