import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { Absence, AbsenceInput, AbsenceReason } from '../../api/types';
import { dayKey } from '../../attendance/time';
import { AuthService } from '../../core/auth.service';
import { errorText, fieldErrorsOf } from '../../core/error-text';
import { date } from '../../core/format';
import { TEXTS } from '../../core/texts';

const REASONS: AbsenceReason[] = ['VACATION', 'INJURY', 'OTHER'];

/**
 * Own absences (ABS) under `/profil/abwesenheiten`: enter, change and delete longer absences such as holidays or
 * an injury. Practices in this time are declined by the backend with the reason as comment (ABS-03).
 */
@Component({
  selector: 'app-absences',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <a class="back" routerLink="/profil">{{ s.toProfile }}</a>
    <h1>{{ s.title }}</h1>
    <p class="secondary-text intro">{{ s.intro }}</p>

    @if (success(); as m) {
      <p class="message-success" role="status" data-testid="absence-feedback">{{ m }}</p>
    }

    <section class="card" aria-labelledby="absence-form-title" data-testid="absence-form">
      <h2 id="absence-form-title" class="section-title">
        {{ editing() ? s.editTitle : s.newTitle }}
      </h2>
      <form class="form" (submit)="save($event)" novalidate>
        <div class="field-row">
          <div class="field">
            <label for="absence-from">{{ s.from }}</label>
            <input
              id="absence-from"
              name="absence-from"
              type="date"
              required
              [min]="minDate()"
              [value]="from()"
              (input)="from.set($any($event.target).value)"
              [attr.aria-invalid]="errors()['from'] ? 'true' : null"
              [attr.aria-describedby]="errors()['from'] ? 'absence-from-error' : null"
            />
            @if (errors()['from']; as m) {
              <p class="field-error" id="absence-from-error">{{ m }}</p>
            }
          </div>
          <div class="field">
            <label for="absence-to">{{ s.to }}</label>
            <input
              id="absence-to"
              name="absence-to"
              type="date"
              required
              [min]="from() || minDate()"
              [value]="to()"
              (input)="to.set($any($event.target).value)"
              [attr.aria-invalid]="errors()['to'] ? 'true' : null"
              [attr.aria-describedby]="errors()['to'] ? 'absence-to-error' : null"
            />
            @if (errors()['to']; as m) {
              <p class="field-error" id="absence-to-error">{{ m }}</p>
            }
          </div>
        </div>
        <fieldset class="pill-group">
          <legend>{{ s.reason }}</legend>
          <div class="pill-options">
            @for (r of reasons; track r) {
              <label class="pill-option">
                <input
                  type="radio"
                  name="absence-reason"
                  [value]="r"
                  [checked]="reason() === r"
                  (change)="reason.set(r)"
                />
                <span>{{ s.reasons[r] }}</span>
              </label>
            }
          </div>
        </fieldset>
        <div class="field">
          <label for="absence-note">{{ reason() === 'OTHER' ? s.noteRequired : s.note }}</label>
          <input
            id="absence-note"
            name="absence-note"
            type="text"
            maxlength="120"
            autocomplete="off"
            [required]="reason() === 'OTHER'"
            [value]="note()"
            (input)="note.set($any($event.target).value)"
            [attr.aria-invalid]="errors()['note'] ? 'true' : null"
            [attr.aria-describedby]="errors()['note'] ? 'absence-note-error' : null"
          />
          @if (errors()['note']; as m) {
            <p class="field-error" id="absence-note-error">{{ m }}</p>
          }
        </div>
        @if (error(); as m) {
          <p class="message-error" role="alert">{{ m }}</p>
        }
        <div class="actions">
          <button
            type="submit"
            class="button button-primary button-small"
            [disabled]="saving()"
            [attr.aria-busy]="saving() ? 'true' : null"
            data-testid="absence-save"
          >
            {{ editing() ? s.save : s.create }}
          </button>
          @if (editing()) {
            <button type="button" class="button button-secondary button-small" (click)="reset()">
              {{ s.cancel }}
            </button>
          }
        </div>
      </form>
    </section>

    <section
      class="card"
      aria-labelledby="absence-list-title"
      [attr.aria-busy]="loading() ? 'true' : null"
    >
      <h2 id="absence-list-title" class="section-title">{{ s.listTitle }}</h2>
      @if (loading()) {
        <p class="empty">{{ texts.common.loading }}</p>
      } @else if (absences().length === 0) {
        <p class="empty" data-testid="absences-empty">{{ s.empty }}</p>
      } @else {
        <ul class="list" [attr.aria-label]="s.listTitle">
          @for (a of absences(); track a.id) {
            <li
              class="absence"
              [attr.data-testid]="'absence-' + a.id"
              [attr.data-reason]="a.reason"
              [attr.aria-label]="entry(a)"
            >
              <span class="absence-text">
                <span class="row-title">{{ period(a) }}</span>
                <span class="secondary-text">
                  {{ s.reasons[a.reason] }}
                  @if (a.note) {
                    · {{ a.note }}
                  }
                </span>
                <span class="secondary-text" data-testid="absence-declined">{{
                  s.declined(a.declinedPractices)
                }}</span>
              </span>
              <span class="absence-actions">
                <button
                  type="button"
                  class="button button-secondary button-small"
                  [attr.aria-label]="s.edit(entry(a))"
                  (click)="edit(a)"
                >
                  {{ s.editShort }}
                </button>
                <button
                  type="button"
                  class="button button-destructive button-small"
                  [attr.aria-label]="s.delete(entry(a))"
                  (click)="askDelete(a)"
                >
                  {{ s.deleteShort }}
                </button>
              </span>
            </li>
          }
        </ul>
      }
    </section>

    <dialog
      #deleteDialog
      class="card dialog"
      aria-modal="true"
      aria-labelledby="absence-delete-title"
      aria-describedby="absence-delete-text"
      data-testid="absence-delete-dialog"
    >
      <h2 id="absence-delete-title">{{ s.deleteTitle }}</h2>
      @if (deleting(); as a) {
        <p class="row-title">{{ entry(a) }}</p>
      }
      <p class="secondary-text" id="absence-delete-text">{{ s.deleteText }}</p>
      <div class="dialog-buttons">
        <button
          type="button"
          class="button button-destructive button-small"
          [disabled]="saving()"
          [attr.aria-busy]="saving() ? 'true' : null"
          (click)="confirmDelete()"
        >
          {{ s.deleteShort }}
        </button>
        <button
          type="button"
          class="button button-secondary button-small"
          [disabled]="saving()"
          (click)="deleteDialog.close()"
        >
          {{ s.cancel }}
        </button>
      </div>
    </dialog>
  `,
  styles: `
    :host {
      display: grid;
      gap: var(--section-gap);
      min-width: 0;
    }
    h1,
    .intro {
      margin: 0;
    }
    .actions,
    .absence-actions {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }
    .absence {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 10px 16px;
      padding: 12px 0;
      border-bottom: 1px solid var(--border);
    }
    .absence:last-child {
      border-bottom: 0;
    }
    .absence-text {
      display: grid;
      gap: 2px;
      min-width: 0;
      overflow-wrap: anywhere;
    }
    dialog h2 {
      margin-top: 0;
    }
  `,
})
export class AbsencesPage {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  protected readonly texts = TEXTS;
  protected readonly s = TEXTS.absences;
  protected readonly reasons = REASONS;

  private readonly deleteDialog = viewChild<ElementRef<HTMLDialogElement>>('deleteDialog');

  protected readonly absences = signal<Absence[]>([]);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly success = signal<string | null>(null);
  protected readonly errors = signal<Record<string, string>>({});

  protected readonly editing = signal<Absence | null>(null);
  protected readonly deleting = signal<Absence | null>(null);
  protected readonly from = signal('');
  protected readonly to = signal('');
  protected readonly reason = signal<AbsenceReason>('VACATION');
  protected readonly note = signal('');

  /** Today from the backend clock: an absence may not lie completely in the past (ABS-02). */
  protected readonly minDate = computed(() => {
    const now = this.auth.me()?.now;
    return now ? dayKey(now) : '';
  });

  constructor() {
    void this.load();
  }

  protected period(a: Absence): string {
    return this.s.period(date(a.from), date(a.to));
  }

  protected entry(a: Absence): string {
    return this.s.entry(this.s.reasons[a.reason], this.period(a));
  }

  protected edit(a: Absence): void {
    this.editing.set(a);
    this.from.set(a.from);
    this.to.set(a.to);
    this.reason.set(a.reason);
    this.note.set(a.note ?? '');
    this.errors.set({});
    this.error.set(null);
    this.success.set(null);
    queueMicrotask(() => document.getElementById('absence-from')?.focus());
  }

  protected reset(): void {
    this.editing.set(null);
    this.from.set('');
    this.to.set('');
    this.reason.set('VACATION');
    this.note.set('');
    this.errors.set({});
    this.error.set(null);
  }

  protected async save(event: Event): Promise<void> {
    event.preventDefault();
    if (this.saving()) return;
    const errors: Record<string, string> = {};
    if (!this.from()) errors['from'] = this.s.fromMissing;
    if (!this.to()) errors['to'] = this.s.toMissing;
    if (this.reason() === 'OTHER' && !this.note().trim()) errors['note'] = this.s.noteMissing;
    this.errors.set(errors);
    this.error.set(null);
    this.success.set(null);
    if (Object.keys(errors).length > 0) {
      const first = ['from', 'to', 'note'].find((f) => errors[f]);
      document.getElementById(`absence-${first}`)?.focus();
      return;
    }
    const body: AbsenceInput = {
      from: this.from(),
      to: this.to(),
      reason: this.reason(),
      note: this.note().trim() || undefined,
    };
    const editing = this.editing();
    this.saving.set(true);
    try {
      const saved = editing
        ? await firstValueFrom(this.http.put<Absence>(`/api/me/absences/${editing.id}`, body))
        : await firstValueFrom(this.http.post<Absence>('/api/me/absences', body));
      this.reset();
      this.success.set(
        editing ? this.s.updated(saved.declinedPractices) : this.s.created(saved.declinedPractices),
      );
      await this.load();
    } catch (e) {
      const fields = fieldErrorsOf(e);
      if (Object.keys(fields).length > 0) {
        this.errors.set(fields);
      } else {
        this.error.set(errorText(e));
      }
    } finally {
      this.saving.set(false);
    }
  }

  protected askDelete(a: Absence): void {
    this.deleting.set(a);
    const d = this.deleteDialog()?.nativeElement;
    if (d && !d.open) d.showModal();
  }

  protected async confirmDelete(): Promise<void> {
    const a = this.deleting();
    if (!a || this.saving()) return;
    this.saving.set(true);
    this.error.set(null);
    try {
      await firstValueFrom(this.http.delete(`/api/me/absences/${a.id}`));
      this.deleteDialog()?.nativeElement.close();
      if (this.editing()?.id === a.id) this.reset();
      this.success.set(this.s.deleted);
      await this.load();
    } catch (e) {
      this.deleteDialog()?.nativeElement.close();
      this.error.set(errorText(e));
    } finally {
      this.saving.set(false);
      this.deleting.set(null);
    }
  }

  private async load(): Promise<void> {
    try {
      this.absences.set(await firstValueFrom(this.http.get<Absence[]>('/api/me/absences')));
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.loading.set(false);
    }
  }
}
