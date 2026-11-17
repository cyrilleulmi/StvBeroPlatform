import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  untracked,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { Person, PersonDetail, PersonInput, Season } from '../../api/types';
import { AuthService } from '../../core/auth.service';
import { fieldErrorsOf, errorStatus, errorText } from '../../core/error-text';
import { teamRoleText, name } from '../../core/format';
import { FeedbackService } from '../../core/feedback.service';
import { TEXTS } from '../../core/texts';

type Field = 'firstName' | 'lastName' | 'email';

/** Person erfassen (`/verwaltung/personen/neu`) oder bearbeiten (`/verwaltung/personen/:personId`). */
@Component({
  selector: 'app-person-formular',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="kopf">
      <a class="zurueck" routerLink="/verwaltung/personen">{{ texts.people.all }}</a>
      <h1>{{ personId() ? texts.people.edit : texts.people.create }}</h1>
    </div>
    @if (!auth.isAdmin()) {
      <p class="meldung-fehler" role="alert">{{ texts.common.noPermission }}</p>
    } @else {
      @if (success(); as message) {
        <p class="meldung-erfolg" role="status">{{ message }}</p>
      }
      @if (error(); as message) {
        <p class="meldung-fehler" role="alert">{{ message }}</p>
      }
      @if (loading()) {
        <p class="leer" aria-busy="true">{{ texts.common.loading }}</p>
      } @else if (!notFound()) {
        <form class="karte formular" [formGroup]="form" (ngSubmit)="save()" novalidate>
          <div class="feldreihe">
            @for (f of nameFields; track f.field) {
              <div class="feld">
                <label [for]="'person-' + f.field">{{ f.label }}</label>
                <input
                  [id]="'person-' + f.field"
                  type="text"
                  [formControlName]="f.field"
                  [autocomplete]="f.autocomplete"
                  maxlength="60"
                  [attr.aria-invalid]="fieldErrors()[f.field] ? 'true' : null"
                  [attr.aria-describedby]="
                    fieldErrors()[f.field] ? 'person-' + f.field + '-fehler' : null
                  "
                />
                @if (fieldErrors()[f.field]; as m) {
                  <p class="feldfehler" [id]="'person-' + f.field + '-fehler'">{{ m }}</p>
                }
              </div>
            }
          </div>
          <div class="feld">
            <label for="person-email">{{ texts.people.email }}</label>
            <input
              id="person-email"
              type="email"
              formControlName="email"
              autocomplete="email"
              [attr.aria-invalid]="fieldErrors()['email'] ? 'true' : null"
              [attr.aria-describedby]="fieldErrors()['email'] ? 'person-email-fehler' : null"
            />
            @if (fieldErrors()['email']; as m) {
              <p class="feldfehler" id="person-email-fehler">{{ m }}</p>
            }
          </div>
          <label class="checkbox">
            <input type="checkbox" formControlName="admin" />
            <span>{{ texts.people.adminPermission }}</span>
          </label>
          <button
            type="submit"
            class="button button-primaer"
            [disabled]="saving()"
            [attr.aria-busy]="saving() ? 'true' : null"
          >
            {{ texts.people.save }}
          </button>
        </form>

        @if (detail(); as d) {
          <section class="karte" aria-labelledby="person-zugehoerigkeiten">
            <h2 id="person-zugehoerigkeiten" class="abschnitt-titel">
              {{ texts.people.memberships }}
            </h2>
            @if (d.memberships.length === 0) {
              <p class="leer">{{ texts.people.noMemberships }}</p>
            } @else {
              <ul class="liste" [attr.aria-label]="texts.people.memberships">
                @for (z of d.memberships; track z.team.id + z.seasonId) {
                  <li class="zeile">
                    <a
                      class="zeile-titel"
                      [routerLink]="['/verwaltung/teams', z.team.id]"
                      [queryParams]="{ saison: z.seasonId }"
                      >{{ z.team.name }}</a
                    >
                    <span class="chip" [attr.data-art]="z.teamRole === 'COACH' ? 'coach' : null">{{
                      teamRole(z.teamRole)
                    }}</span>
                    <span class="nebentext">{{ seasonName(z.seasonId) }}</span>
                  </li>
                }
              </ul>
            }
          </section>
        }
      }
    }
  `,
  styleUrl: '../page.css',
  styles: `
    form .button {
      justify-self: start;
    }
  `,
})
export class PersonForm {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly feedback = inject(FeedbackService);
  protected readonly auth = inject(AuthService);
  protected readonly texts = TEXTS;
  protected readonly teamRole = teamRoleText;

  /** Route `/verwaltung/personen/:personId`; fehlt bei «neu». */
  readonly personId = input<string | undefined>();

  protected readonly form = inject(NonNullableFormBuilder).group({
    firstName: ['', [Validators.required, Validators.maxLength(60)]],
    lastName: ['', [Validators.required, Validators.maxLength(60)]],
    email: ['', [Validators.email]],
    admin: [false],
  });

  protected readonly nameFields: { field: Field; label: string; autocomplete: string }[] = [
    { field: 'firstName', label: TEXTS.people.firstName, autocomplete: 'given-name' },
    { field: 'lastName', label: TEXTS.people.lastName, autocomplete: 'family-name' },
  ];

  protected readonly detail = signal<PersonDetail | null>(null);
  private readonly seasons = signal<Season[]>([]);
  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly notFound = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly success = signal<string | null>(null);
  protected readonly fieldErrors = signal<Record<string, string>>({});

  constructor() {
    effect(() => {
      const id = this.personId();
      if (this.auth.isAdmin()) {
        untracked(() => void this.load(id));
      }
    });
  }

  protected seasonName(id: string): string {
    return this.seasons().find((s) => s.id === id)?.name ?? '';
  }

  private async load(id: string | undefined): Promise<void> {
    this.success.set(this.feedback.take());
    this.error.set(null);
    this.fieldErrors.set({});
    this.notFound.set(false);
    if (!id) {
      this.detail.set(null);
      this.form.reset();
      return;
    }
    this.loading.set(true);
    try {
      const [person, seasons] = await Promise.all([
        firstValueFrom(this.http.get<PersonDetail>(`/api/persons/${id}`)),
        firstValueFrom(this.http.get<Season[]>('/api/seasons')),
      ]);
      this.seasons.set(seasons);
      this.detail.set(person);
      this.form.setValue({
        firstName: person.firstName,
        lastName: person.lastName,
        email: person.email ?? '',
        admin: person.admin,
      });
    } catch (e) {
      if (errorStatus(e) === 404) {
        this.notFound.set(true);
        this.error.set(TEXTS.people.notFound);
      } else {
        this.error.set(errorText(e));
      }
    } finally {
      this.loading.set(false);
    }
  }

  protected async save(): Promise<void> {
    if (this.saving()) return;
    this.success.set(null);
    this.error.set(null);
    const f = this.form.getRawValue();
    const error: Record<string, string> = {};
    if (!f.firstName.trim()) error['firstName'] = TEXTS.people.required('einen Vornamen');
    if (!f.lastName.trim()) error['lastName'] = TEXTS.people.required('einen Nachnamen');
    if (this.form.controls.email.invalid)
      error['email'] = 'Bitte eine gültige E-Mail-Adresse eingeben.';
    this.fieldErrors.set(error);
    if (Object.keys(error).length > 0) {
      return;
    }

    const input: PersonInput = {
      firstName: f.firstName.trim(),
      lastName: f.lastName.trim(),
      email: f.email.trim() || null,
      admin: f.admin,
    };
    this.saving.set(true);
    try {
      const id = this.personId();
      if (id) {
        await firstValueFrom(this.http.put<Person>(`/api/persons/${id}`, input));
        this.success.set(TEXTS.people.saved);
        await this.load(id);
        this.success.set(TEXTS.people.saved);
        if (id === this.auth.me()?.id) {
          await this.auth.load();
        }
      } else {
        const created = await firstValueFrom(this.http.post<Person>('/api/persons', input));
        this.feedback.set(TEXTS.people.created(name(created)));
        await this.router.navigate(['/verwaltung/personen', created.id]);
      }
    } catch (e) {
      this.fieldErrors.set(fieldErrorsOf(e, { firstName: 'firstName', lastName: 'lastName' }));
      this.error.set(errorText(e));
    } finally {
      this.saving.set(false);
    }
  }
}
