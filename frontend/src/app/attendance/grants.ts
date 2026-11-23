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
  viewChild,
} from '@angular/core';
import { firstValueFrom } from 'rxjs';
import type { EventGrants, EventGrantsInput } from '../api/types';
import { errorText } from '../core/error-text';
import { TEXTS } from '../core/texts';

/**
 * «Sonderberechtigungen» of an event (part of the section «Angaben») or a series (a section of its own), RR-09 to
 * RR-14: who manages it like a coach. Coaches and admins choose the people among the members of the teams;
 * members see the names. Shown when there is someone, or when the caller may change it.
 */
@Component({
  selector: 'app-grants',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (data(); as d) {
      @if (d.grants.length > 0 || d.canManage || mine()) {
        <section
          class="grants"
          [class.card]="kind() === 'series'"
          aria-labelledby="grants-title"
          data-testid="grants"
        >
          <!-- In the event page a part of «Angaben», on the series page a section of its own -->
          @if (kind() === 'series') {
            <h2 id="grants-title" class="section-title">{{ t.title }}</h2>
          } @else {
            <h3 id="grants-title" class="subtitle">{{ t.title }}</h3>
          }
          @if (mine()) {
            <p class="mine" data-testid="grants-mine">{{ t.mine }}</p>
          }
          <p class="secondary-text">{{ kind() === 'series' ? t.introSeries : t.intro }}</p>
          @if (d.grants.length > 0) {
            <ul class="names" data-testid="grants-list">
              @for (g of d.grants; track g.person.id) {
                <li>
                  {{ g.person.firstName }} {{ g.person.lastName }}
                  @if (g.viaSeries && kind() === 'event') {
                    <span class="secondary-text">· {{ t.viaSeries }}</span>
                  }
                </li>
              }
            </ul>
          } @else {
            <p class="secondary-text" data-testid="grants-empty">{{ t.none }}</p>
          }
          @if (d.canManage && editable()) {
            <button
              type="button"
              class="button button-secondary button-small"
              data-testid="grants-change"
              (click)="open()"
            >
              {{ t.change }}
            </button>
          }
          @if (message(); as m) {
            <p class="message-success" role="status">{{ m }}</p>
          }
          @if (error(); as m) {
            <p class="message-error" role="alert">{{ m }}</p>
          }
        </section>
      }
    }

    <dialog
      #dialog
      class="card dialog"
      aria-modal="true"
      aria-labelledby="grants-dialog-title"
      aria-describedby="grants-dialog-text"
      data-testid="grants-dialog"
    >
      <form class="form" (submit)="save($event)" novalidate>
        <h2 id="grants-dialog-title">{{ t.dialogTitle }}</h2>
        <p class="secondary-text" id="grants-dialog-text">{{ t.dialogText }}</p>
        <fieldset class="people">
          <legend class="visually-hidden">{{ t.title }}</legend>
          @for (p of data()?.candidates ?? []; track p.id) {
            <label class="checkbox">
              <input
                type="checkbox"
                [name]="'grant-' + p.id"
                [attr.data-testid]="'grant-' + p.id"
                [checked]="chosen().has(p.id) || viaSeries().has(p.id)"
                [disabled]="viaSeries().has(p.id) || saving()"
                (change)="toggle(p.id, $any($event.target).checked)"
              />
              <span>
                {{ p.firstName }} {{ p.lastName }}
                @if (viaSeries().has(p.id)) {
                  <span class="secondary-text">· {{ t.viaSeries }}</span>
                }
              </span>
            </label>
          }
        </fieldset>
        @if (dialogError(); as m) {
          <p class="field-error" role="alert">{{ m }}</p>
        }
        <div class="dialog-buttons">
          <button
            type="submit"
            class="button button-primary button-small"
            [disabled]="saving()"
            [attr.aria-busy]="saving() ? 'true' : null"
          >
            {{ t.save }}
          </button>
          <button
            type="button"
            class="button button-secondary button-small"
            [disabled]="saving()"
            (click)="dialog.close()"
          >
            {{ t.cancel }}
          </button>
        </div>
      </form>
    </dialog>
  `,
  styles: `
    .grants {
      display: grid;
      gap: 10px;
    }
    .grants h2,
    .grants h3,
    .grants p {
      margin: 0;
    }
    .grants > button {
      justify-self: start;
    }
    .subtitle {
      font-size: 15px;
      font-weight: 700;
    }
    .mine {
      font-weight: 700;
    }
    .names {
      margin: 0;
      padding-left: 20px;
      display: grid;
      gap: 4px;
    }
    .people {
      border: 0;
      margin: 0;
      padding: 0;
      display: grid;
      max-height: 50vh;
      overflow-y: auto;
    }
    .dialog {
      max-width: min(440px, calc(100vw - 32px));
      color: var(--text);
    }
    .dialog:not([open]) {
      display: none;
    }
    .dialog[open] {
      display: grid;
      gap: 12px;
    }
    .dialog h2,
    .dialog p {
      margin: 0;
    }
    .dialog::backdrop {
      background: color-mix(in srgb, var(--bg) 80%, transparent);
    }
    .form {
      display: grid;
      gap: 12px;
    }
    .dialog-buttons {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }
  `,
})
export class Grants {
  private readonly http = inject(HttpClient);
  protected readonly t = TEXTS.grants;

  /** Event or series. */
  readonly kind = input.required<'event' | 'series'>();
  readonly targetId = input.required<string>();
  /** The caller has a special permission for this event. */
  readonly mine = input(false);
  /** Changes are possible (event planned and not over). */
  readonly editable = input(true);

  protected readonly data = signal<EventGrants | null>(null);
  protected readonly chosen = signal<Set<string>>(new Set());
  protected readonly saving = signal(false);
  protected readonly message = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly dialogError = signal<string | null>(null);

  private readonly dialog = viewChild<ElementRef<HTMLDialogElement>>('dialog');

  /** People who have it through the series: shown, but changed on the series (RR-10). */
  protected readonly viaSeries = computed(
    () =>
      new Set(
        this.kind() === 'event'
          ? (this.data()?.grants ?? []).filter((g) => g.viaSeries).map((g) => g.person.id)
          : [],
      ),
  );

  private readonly url = computed(
    () => `/api/${this.kind() === 'series' ? 'series' : 'events'}/${this.targetId()}/grants`,
  );

  constructor() {
    effect(() => {
      void this.load(this.url());
    });
  }

  private async load(url: string): Promise<void> {
    try {
      this.data.set(await firstValueFrom(this.http.get<EventGrants>(url)));
    } catch {
      // Not shown without permission (403) or for an unknown event
      this.data.set(null);
    }
  }

  protected open(): void {
    const d = this.data();
    if (!d) return;
    const own = d.grants
      .filter((g) => this.kind() === 'series' || !g.viaSeries)
      .map((g) => g.person.id);
    this.chosen.set(new Set(own));
    this.dialogError.set(null);
    this.message.set(null);
    const el = this.dialog()?.nativeElement;
    if (el && !el.open) el.showModal();
  }

  protected toggle(id: string, on: boolean): void {
    const next = new Set(this.chosen());
    if (on) next.add(id);
    else next.delete(id);
    this.chosen.set(next);
  }

  protected async save(domEvent: Event): Promise<void> {
    domEvent.preventDefault();
    if (this.saving()) return;
    this.saving.set(true);
    this.dialogError.set(null);
    const body: EventGrantsInput = { personIds: [...this.chosen()] };
    try {
      this.data.set(await firstValueFrom(this.http.put<EventGrants>(this.url(), body)));
      this.dialog()?.nativeElement.close();
      this.message.set(this.t.saved);
    } catch (e) {
      this.dialogError.set(errorText(e));
    } finally {
      this.saving.set(false);
    }
  }
}
