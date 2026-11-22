import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  effect,
  inject,
  input,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { firstValueFrom } from 'rxjs';
import type {
  PersonSummary,
  SpecialLineup,
  SpecialLineupInput,
  SpecialLineups as SpecialLineupsData,
} from '../../api/types';
import { errorText, fieldErrorsOf } from '../../core/error-text';
import { name } from '../../core/format';
import { TEXTS } from '../../core/texts';

interface PlaceForm {
  label: string;
  personId: string;
}

const MAX_PLACES = 10;

/**
 * Special line-ups of a team (LIN-13 to LIN-15), e.g. power play («Überzahl») and penalty kill
 * («Unterzahl»): a free name, 1 to 10 places, per place an optional position and a player. Coaches and
 * admins change everything in one dialog; members see the line-ups.
 */
@Component({
  selector: 'app-special-lineups',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section
      class="card special"
      aria-labelledby="special-lineups-title"
      data-testid="special-lineups"
      [attr.aria-busy]="loading() ? 'true' : null"
    >
      <h2 id="special-lineups-title" class="section-title">{{ s.section }}</h2>
      <p class="secondary-text">{{ s.intro }}</p>
      @if (message(); as m) {
        <p class="message-success" role="status">{{ m }}</p>
      }
      @if (error(); as m) {
        <p class="message-error" role="alert">{{ m }}</p>
      }
      @if (data(); as d) {
        @if (d.lineups.length === 0) {
          <p class="empty">{{ s.empty }}</p>
        } @else {
          <ul class="list lineups" aria-labelledby="special-lineups-title">
            @for (l of d.lineups; track l.id) {
              <li class="lineup" [attr.aria-label]="l.name" [attr.data-testid]="'special-lineup-' + l.id">
                <div class="lineup-head">
                  <h3 class="lineup-name">{{ l.name }}</h3>
                  @if (d.canEdit) {
                    <span class="lineup-buttons">
                      <button
                        type="button"
                        class="button button-secondary button-small"
                        [attr.aria-label]="s.editFor(l.name)"
                        [disabled]="running()"
                        (click)="open(l)"
                      >
                        {{ s.edit }}
                      </button>
                      <button
                        type="button"
                        class="button button-secondary button-small button-destructive"
                        [attr.aria-label]="s.removeFor(l.name)"
                        [disabled]="running()"
                        (click)="remove(l)"
                      >
                        {{ s.remove }}
                      </button>
                    </span>
                  }
                </div>
                <ol class="places" [attr.aria-label]="l.name">
                  @for (p of l.places; track p.position) {
                    <li class="place" [attr.data-empty]="p.person ? null : 'true'">
                      <span class="place-label">{{ s.placeText(p.label ?? null, p.position) }}</span>
                      <span class="place-player">{{ p.person ? name(p.person) : s.emptyPlace }}</span>
                    </li>
                  }
                </ol>
              </li>
            }
          </ul>
        }
        @if (d.canEdit) {
          <button
            type="button"
            class="button button-primary button-small add"
            data-testid="special-lineup-add"
            [disabled]="running()"
            (click)="open(null)"
          >
            {{ s.add }}
          </button>
        }
      } @else if (loading()) {
        <p class="empty">{{ texts.common.loading }}</p>
      }
    </section>

    <dialog
      #dialog
      class="card dialog special-dialog"
      aria-modal="true"
      aria-labelledby="special-lineup-dialog-title"
    >
      <form class="form" (submit)="save($event)" novalidate>
        <h2 id="special-lineup-dialog-title">
          {{ editing() ? s.dialogEdit(editing()!.name) : s.dialogNew }}
        </h2>
        @if (dialogError(); as m) {
          <p class="message-error" role="alert">{{ m }}</p>
        }
        <div class="field">
          <label for="special-lineup-name">{{ s.name }}</label>
          <input
            #nameField
            id="special-lineup-name"
            type="text"
            maxlength="40"
            autocomplete="off"
            [value]="lineupName()"
            (input)="lineupName.set($any($event.target).value)"
            [attr.aria-invalid]="nameError() ? 'true' : null"
            [attr.aria-describedby]="nameError() ? 'special-lineup-name-error' : null"
          />
          @if (nameError(); as m) {
            <p class="field-error" id="special-lineup-name-error">{{ m }}</p>
          }
          <p class="suggestions">
            @for (suggestion of s.suggestions; track suggestion) {
              <button
                type="button"
                class="button button-secondary button-small"
                [attr.aria-label]="s.suggestion(suggestion)"
                (click)="lineupName.set(suggestion)"
              >
                {{ suggestion }}
              </button>
            }
          </p>
        </div>
        <div class="field count">
          <label for="special-lineup-places">{{ s.places }}</label>
          <input
            id="special-lineup-places"
            type="number"
            inputmode="numeric"
            min="1"
            [max]="maxPlaces"
            [value]="places().length"
            (change)="setCount($any($event.target).value)"
            [attr.aria-invalid]="countError() ? 'true' : null"
            [attr.aria-describedby]="countError() ? 'special-lineup-places-error' : null"
          />
          @if (countError(); as m) {
            <p class="field-error" id="special-lineup-places-error">{{ m }}</p>
          }
        </div>
        <ol class="place-forms">
          @for (p of places(); track $index) {
            <li class="place-form" [attr.aria-label]="s.place($index + 1)">
              <span class="place-number" aria-hidden="true">{{ $index + 1 }}</span>
              <input
                type="text"
                maxlength="30"
                autocomplete="off"
                [attr.aria-label]="s.label($index + 1)"
                [placeholder]="s.labelPlaceholder"
                [value]="p.label"
                (input)="setLabel($index, $any($event.target).value)"
              />
              <select
                [attr.aria-label]="s.player($index + 1)"
                [attr.aria-invalid]="placeErrors()[$index] ? 'true' : null"
                (change)="setPlayer($index, $any($event.target).value)"
              >
                <option value="" [selected]="!p.personId">{{ s.noPlayer }}</option>
                @for (player of players(); track player.id) {
                  <option [value]="player.id" [selected]="player.id === p.personId">
                    {{ name(player) }}
                  </option>
                }
              </select>
              @if (placeErrors()[$index]; as m) {
                <p class="field-error place-error">{{ m }}</p>
              }
            </li>
          }
        </ol>
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

    .special {
      display: grid;
      gap: 10px;
    }

    .special p {
      margin: 0;
    }

    .lineups {
      display: grid;
      gap: 12px;
    }

    .lineup {
      display: grid;
      gap: 8px;
      padding: 12px;
      border: 1px solid var(--border);
      border-radius: var(--radius-icon);
    }

    .lineup-head {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 8px 12px;
    }

    .lineup-name {
      margin: 0;
      font-size: 16px;
    }

    .lineup-buttons {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }

    .places {
      display: grid;
      gap: 4px;
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .place {
      display: flex;
      flex-wrap: wrap;
      gap: 2px 12px;
    }

    .place-label {
      flex: 0 0 150px;
      color: var(--muted);
      font-size: 13px;
      font-weight: 700;
    }

    .place-player {
      font-weight: 600;
    }

    .place[data-empty] .place-player {
      color: var(--muted);
      font-weight: 400;
    }

    .add {
      justify-self: start;
    }


    .dialog {
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

    .special-dialog {
      width: min(560px, calc(100vw - 32px));
    }

    .suggestions {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      margin: 8px 0 0;
    }

    .count input {
      max-width: 120px;
    }

    .place-forms {
      display: grid;
      gap: 10px;
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .place-form {
      display: grid;
      grid-template-columns: 24px minmax(0, 1fr) minmax(0, 1fr);
      align-items: center;
      gap: 6px 8px;
    }

    .place-number {
      font-weight: 800;
      color: var(--muted);
      text-align: center;
    }

    .place-error {
      grid-column: 2 / -1;
      margin: 0;
    }

    @media (max-width: 479px) {
      .place-form {
        grid-template-columns: 24px minmax(0, 1fr);
      }

      .place-form select {
        grid-column: 2;
      }

      .place-label {
        flex-basis: 100%;
      }
    }
  `,
})
export class SpecialLineups {
  private readonly http = inject(HttpClient);
  protected readonly texts = TEXTS;
  protected readonly s = TEXTS.lineups;
  protected readonly name = name;
  protected readonly maxPlaces = MAX_PLACES;

  readonly teamId = input.required<string>();

  protected readonly data = signal<SpecialLineupsData | null>(null);
  protected readonly loading = signal(true);
  protected readonly running = signal(false);
  protected readonly message = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  /** Dialog: the line-up being changed (`null`: a new one), its name and places. */
  protected readonly editing = signal<SpecialLineup | null>(null);
  protected readonly lineupName = signal('');
  protected readonly places = signal<PlaceForm[]>([]);
  protected readonly players = signal<PersonSummary[]>([]);
  protected readonly nameError = signal<string | null>(null);
  protected readonly countError = signal<string | null>(null);
  protected readonly placeErrors = signal<Record<number, string>>({});
  protected readonly dialogError = signal<string | null>(null);

  private readonly dialog = viewChild<ElementRef<HTMLDialogElement>>('dialog');
  private readonly nameField = viewChild<ElementRef<HTMLInputElement>>('nameField');

  constructor() {
    effect(() => {
      const teamId = this.teamId();
      untracked(() => void this.load(teamId));
    });
  }

  protected open(lineup: SpecialLineup | null): void {
    const d = this.data();
    if (!d) return;
    this.editing.set(lineup);
    this.lineupName.set(lineup?.name ?? '');
    this.players.set(d.players);
    this.places.set(
      lineup
        ? lineup.places.map((p) => ({ label: p.label ?? '', personId: p.person?.id ?? '' }))
        : [1, 2, 3].map(() => ({ label: '', personId: '' })),
    );
    this.nameError.set(null);
    this.countError.set(null);
    this.placeErrors.set({});
    this.dialogError.set(null);
    this.message.set(null);
    this.error.set(null);
    const dialog = this.dialog()?.nativeElement;
    if (dialog && !dialog.open) dialog.showModal();
  }

  protected close(): void {
    this.dialog()?.nativeElement.close();
  }

  /** The number of places: more adds empty ones at the end, fewer drops the last ones. */
  protected setCount(value: string): void {
    const count = Number(value);
    if (!Number.isInteger(count) || count < 1 || count > MAX_PLACES) {
      this.countError.set(this.s.placesInvalid);
      return;
    }
    this.countError.set(null);
    this.places.update((places) => {
      const next = places.slice(0, count);
      while (next.length < count) next.push({ label: '', personId: '' });
      return next;
    });
  }

  protected setLabel(index: number, label: string): void {
    this.places.update((places) => places.map((p, i) => (i === index ? { ...p, label } : p)));
  }

  protected setPlayer(index: number, personId: string): void {
    this.places.update((places) => places.map((p, i) => (i === index ? { ...p, personId } : p)));
    this.placeErrors.set({});
  }

  protected async save(domEvent: Event): Promise<void> {
    domEvent.preventDefault();
    if (this.running() || this.countError()) return;
    const lineupName = this.lineupName().trim();
    if (!lineupName) {
      this.nameError.set(this.s.nameRequired);
      this.nameField()?.nativeElement.focus();
      return;
    }
    const input: SpecialLineupInput = {
      name: lineupName,
      places: this.places().map((p) => ({
        label: p.label.trim() || null,
        personId: p.personId || null,
      })),
    };
    const editing = this.editing();
    const url = `/api/teams/${this.teamId()}/special-lineups`;
    this.running.set(true);
    this.nameError.set(null);
    this.placeErrors.set({});
    this.dialogError.set(null);
    try {
      await firstValueFrom(
        editing
          ? this.http.put<SpecialLineup>(`${url}/${editing.id}`, input)
          : this.http.post<SpecialLineup>(url, input),
      );
      this.close();
      await this.load(this.teamId(), false);
      this.message.set(this.s.saved(lineupName));
    } catch (e) {
      const fields = fieldErrorsOf(e);
      const placeErrors: Record<number, string> = {};
      for (const [field, text] of Object.entries(fields)) {
        const match = /^places\[(\d+)\]/.exec(field);
        if (match) placeErrors[Number(match[1])] = text;
      }
      if (fields['name']) {
        this.nameError.set(fields['name']);
        this.nameField()?.nativeElement.focus();
      } else if (Object.keys(placeErrors).length > 0) {
        this.placeErrors.set(placeErrors);
      } else {
        this.dialogError.set(errorText(e));
      }
    } finally {
      this.running.set(false);
    }
  }

  protected async remove(lineup: SpecialLineup): Promise<void> {
    if (this.running()) return;
    this.running.set(true);
    this.message.set(null);
    this.error.set(null);
    try {
      await firstValueFrom(
        this.http.delete(`/api/teams/${this.teamId()}/special-lineups/${lineup.id}`),
      );
      await this.load(this.teamId(), false);
      this.message.set(this.s.removed(lineup.name));
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.running.set(false);
    }
  }

  private async load(teamId: string, showLoading = true): Promise<void> {
    if (showLoading) this.loading.set(true);
    try {
      this.data.set(
        await firstValueFrom(
          this.http.get<SpecialLineupsData>(`/api/teams/${teamId}/special-lineups`),
        ),
      );
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.loading.set(false);
    }
  }
}
