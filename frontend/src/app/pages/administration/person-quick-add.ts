import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import type { Person, PersonInput, TeamRole } from '../../api/types';
import { errorText, fieldErrorsOf } from '../../core/error-text';
import { name } from '../../core/format';
import { TEXTS } from '../../core/texts';

type Field = 'firstName' | 'lastName' | 'birthYear';

/**
 * A new person, right inside the assistant «Neues Team» (PTF-13, PTF-15): first name, last name, gender and year of
 * birth; she joins the team at once with the chosen team role. Nobody has to leave the assistant for it.
 */
@Component({
  selector: 'app-person-quick-add',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form class="form" (submit)="save($event)" novalidate [attr.aria-label]="t.title" data-testid="person-quick-add">
      @if (error(); as m) {
        <p class="message-error" role="alert">{{ m }}</p>
      }
      <div class="field-row">
        @for (f of names; track f.field) {
          <div class="field">
            <label [for]="'quick-' + f.field">{{ f.label }}</label>
            <input
              [id]="'quick-' + f.field"
              [name]="'quick-' + f.field"
              type="text"
              maxlength="60"
              [attr.autocomplete]="'off'"
              [value]="values()[f.field]"
              (input)="set(f.field, $any($event.target).value)"
              [attr.aria-invalid]="errors()[f.field] ? 'true' : null"
              [attr.aria-describedby]="errors()[f.field] ? 'quick-' + f.field + '-error' : null"
            />
            @if (errors()[f.field]; as m) {
              <p class="field-error" [id]="'quick-' + f.field + '-error'">{{ m }}</p>
            }
          </div>
        }
      </div>
      <div class="field-row">
        <div class="field">
          <label for="quick-gender">{{ texts.people.gender }}</label>
          <select id="quick-gender" name="quick-gender" (change)="gender.set($any($event.target).value)">
            <option value="" [selected]="gender() === ''">{{ texts.people.genderNone }}</option>
            <option value="FEMALE" [selected]="gender() === 'FEMALE'">{{ texts.people.genderFemale }}</option>
            <option value="MALE" [selected]="gender() === 'MALE'">{{ texts.people.genderMale }}</option>
          </select>
        </div>
        <div class="field">
          <label for="quick-birthYear">{{ texts.people.birthYear }}</label>
          <input
            id="quick-birthYear"
            name="quick-birthYear"
            type="number"
            inputmode="numeric"
            min="1900"
            max="2100"
            [value]="values().birthYear"
            (input)="set('birthYear', $any($event.target).value)"
            [attr.aria-invalid]="errors().birthYear ? 'true' : null"
            [attr.aria-describedby]="errors().birthYear ? 'quick-birthYear-error' : null"
          />
          @if (errors().birthYear; as m) {
            <p class="field-error" id="quick-birthYear-error">{{ m }}</p>
          }
        </div>
      </div>
      <button
        type="submit"
        class="button button-secondary button-small"
        data-testid="person-quick-add-save"
        [disabled]="saving()"
        [attr.aria-busy]="saving() ? 'true' : null"
      >
        {{ t.save(teamRole() === 'COACH' ? texts.membership.roleCoach : texts.membership.rolePlayer) }}
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
export class PersonQuickAdd {
  private readonly http = inject(HttpClient);
  protected readonly texts = TEXTS;
  protected readonly t = TEXTS.personQuickAdd;
  protected readonly names: { field: 'firstName' | 'lastName'; label: string }[] = [
    { field: 'firstName', label: TEXTS.people.firstName },
    { field: 'lastName', label: TEXTS.people.lastName },
  ];

  readonly teamId = input.required<string>();
  /** Season ID; without it the current season. */
  readonly season = input<string | undefined>(undefined);
  readonly teamRole = input<TeamRole>('PLAYER');
  /** The person is created and in the team. */
  readonly added = output<string>();

  protected readonly values = signal<Record<Field, string>>({ firstName: '', lastName: '', birthYear: '' });
  protected readonly gender = signal<'' | 'FEMALE' | 'MALE'>('');
  protected readonly errors = signal<Partial<Record<Field, string>>>({});
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);

  protected set(field: Field, value: string): void {
    this.values.update((v) => ({ ...v, [field]: value }));
  }

  protected async save(domEvent: Event): Promise<void> {
    domEvent.preventDefault();
    if (this.saving()) return;
    const v = this.values();
    const year = v.birthYear.trim();
    const errors: Partial<Record<Field, string>> = {};
    if (!v.firstName.trim()) errors.firstName = TEXTS.people.required('einen Vornamen');
    if (!v.lastName.trim()) errors.lastName = TEXTS.people.required('einen Nachnamen');
    if (year && !(/^\d{4}$/.test(year) && Number(year) >= 1900 && Number(year) <= 2100))
      errors.birthYear = TEXTS.people.birthYearInvalid;
    this.errors.set(errors);
    const first = Object.keys(errors)[0];
    if (first) {
      document.getElementById('quick-' + first)?.focus();
      return;
    }
    const input: PersonInput = {
      firstName: v.firstName.trim(),
      lastName: v.lastName.trim(),
      gender: this.gender() || null,
      birthYear: year ? Number(year) : null,
    };
    this.saving.set(true);
    this.error.set(null);
    try {
      const person = await firstValueFrom(this.http.post<Person>('/api/persons', input));
      const season = this.season();
      await firstValueFrom(
        this.http.post(
          `/api/teams/${this.teamId()}/members`,
          { personId: person.id, teamRole: this.teamRole() },
          { params: season ? { season } : {} },
        ),
      );
      this.values.set({ firstName: '', lastName: '', birthYear: '' });
      this.added.emit(name(person));
      document.getElementById('quick-firstName')?.focus();
    } catch (e) {
      const fields = fieldErrorsOf(e);
      if (Object.keys(fields).length > 0) this.errors.set(fields as Partial<Record<Field, string>>);
      else this.error.set(errorText(e));
    } finally {
      this.saving.set(false);
    }
  }
}
