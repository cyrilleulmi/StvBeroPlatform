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
import type { Person, PersonDetail, PersonSchreiben, Saison } from '../../api/typen';
import { AnmeldungService } from '../../kern/anmeldung.service';
import { feldfehler, fehlerstatus, fehlertext } from '../../kern/fehlertext';
import { funktionText, name } from '../../kern/format';
import { RueckmeldungService } from '../../kern/rueckmeldung.service';
import { TEXTE } from '../../kern/texte';

type Feld = 'vorname' | 'nachname' | 'email';

/** Person erfassen (`/verwaltung/personen/neu`) oder bearbeiten (`/verwaltung/personen/:personId`). */
@Component({
  selector: 'app-person-formular',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="kopf">
      <a class="zurueck" routerLink="/verwaltung/personen">{{ texte.personen.alle }}</a>
      <h1>{{ personId() ? texte.personen.bearbeiten : texte.personen.erfassen }}</h1>
    </div>
    @if (!anmeldung.istAdmin()) {
      <p class="meldung-fehler" role="alert">{{ texte.allgemein.keineBerechtigung }}</p>
    } @else {
      @if (erfolg(); as meldung) {
        <p class="meldung-erfolg" role="status">{{ meldung }}</p>
      }
      @if (fehler(); as meldung) {
        <p class="meldung-fehler" role="alert">{{ meldung }}</p>
      }
      @if (laedt()) {
        <p class="leer" aria-busy="true">{{ texte.allgemein.laden }}</p>
      } @else if (!nichtGefunden()) {
        <form class="karte formular" [formGroup]="formular" (ngSubmit)="speichern()" novalidate>
          <div class="feldreihe">
            @for (f of namensfelder; track f.feld) {
              <div class="feld">
                <label [for]="'person-' + f.feld">{{ f.label }}</label>
                <input
                  [id]="'person-' + f.feld"
                  type="text"
                  [formControlName]="f.feld"
                  [autocomplete]="f.autocomplete"
                  maxlength="60"
                  [attr.aria-invalid]="feldFehler()[f.feld] ? 'true' : null"
                  [attr.aria-describedby]="
                    feldFehler()[f.feld] ? 'person-' + f.feld + '-fehler' : null
                  "
                />
                @if (feldFehler()[f.feld]; as m) {
                  <p class="feldfehler" [id]="'person-' + f.feld + '-fehler'">{{ m }}</p>
                }
              </div>
            }
          </div>
          <div class="feld">
            <label for="person-email">{{ texte.personen.email }}</label>
            <input
              id="person-email"
              type="email"
              formControlName="email"
              autocomplete="email"
              [attr.aria-invalid]="feldFehler()['email'] ? 'true' : null"
              [attr.aria-describedby]="feldFehler()['email'] ? 'person-email-fehler' : null"
            />
            @if (feldFehler()['email']; as m) {
              <p class="feldfehler" id="person-email-fehler">{{ m }}</p>
            }
          </div>
          <label class="checkbox">
            <input type="checkbox" formControlName="admin" />
            <span>{{ texte.personen.adminRecht }}</span>
          </label>
          <button
            type="submit"
            class="button button-primaer"
            [disabled]="speichert()"
            [attr.aria-busy]="speichert() ? 'true' : null"
          >
            {{ texte.personen.speichern }}
          </button>
        </form>

        @if (detail(); as d) {
          <section class="karte" aria-labelledby="person-zugehoerigkeiten">
            <h2 id="person-zugehoerigkeiten" class="abschnitt-titel">
              {{ texte.personen.zugehoerigkeiten }}
            </h2>
            @if (d.memberships.length === 0) {
              <p class="leer">{{ texte.personen.keineZugehoerigkeiten }}</p>
            } @else {
              <ul class="liste" [attr.aria-label]="texte.personen.zugehoerigkeiten">
                @for (z of d.memberships; track z.team.id + z.seasonId) {
                  <li class="zeile">
                    <a
                      class="zeile-titel"
                      [routerLink]="['/verwaltung/teams', z.team.id]"
                      [queryParams]="{ saison: z.seasonId }"
                      >{{ z.team.name }}</a
                    >
                    <span class="chip" [attr.data-art]="z.teamRole === 'COACH' ? 'coach' : null">{{
                      funktion(z.teamRole)
                    }}</span>
                    <span class="nebentext">{{ saisonName(z.seasonId) }}</span>
                  </li>
                }
              </ul>
            }
          </section>
        }
      }
    }
  `,
  styleUrl: '../seite.css',
  styles: `
    form .button {
      justify-self: start;
    }
  `,
})
export class PersonFormular {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly rueckmeldung = inject(RueckmeldungService);
  protected readonly anmeldung = inject(AnmeldungService);
  protected readonly texte = TEXTE;
  protected readonly funktion = funktionText;

  /** Route `/verwaltung/personen/:personId`; fehlt bei «neu». */
  readonly personId = input<string | undefined>();

  protected readonly formular = inject(NonNullableFormBuilder).group({
    vorname: ['', [Validators.required, Validators.maxLength(60)]],
    nachname: ['', [Validators.required, Validators.maxLength(60)]],
    email: ['', [Validators.email]],
    admin: [false],
  });

  protected readonly namensfelder: { feld: Feld; label: string; autocomplete: string }[] = [
    { feld: 'vorname', label: TEXTE.personen.vorname, autocomplete: 'given-name' },
    { feld: 'nachname', label: TEXTE.personen.nachname, autocomplete: 'family-name' },
  ];

  protected readonly detail = signal<PersonDetail | null>(null);
  private readonly saisons = signal<Saison[]>([]);
  protected readonly laedt = signal(false);
  protected readonly speichert = signal(false);
  protected readonly nichtGefunden = signal(false);
  protected readonly fehler = signal<string | null>(null);
  protected readonly erfolg = signal<string | null>(null);
  protected readonly feldFehler = signal<Record<string, string>>({});

  constructor() {
    effect(() => {
      const id = this.personId();
      if (this.anmeldung.istAdmin()) {
        untracked(() => void this.laden(id));
      }
    });
  }

  protected saisonName(id: string): string {
    return this.saisons().find((s) => s.id === id)?.name ?? '';
  }

  private async laden(id: string | undefined): Promise<void> {
    this.erfolg.set(this.rueckmeldung.abholen());
    this.fehler.set(null);
    this.feldFehler.set({});
    this.nichtGefunden.set(false);
    if (!id) {
      this.detail.set(null);
      this.formular.reset();
      return;
    }
    this.laedt.set(true);
    try {
      const [person, saisons] = await Promise.all([
        firstValueFrom(this.http.get<PersonDetail>(`/api/persons/${id}`)),
        firstValueFrom(this.http.get<Saison[]>('/api/seasons')),
      ]);
      this.saisons.set(saisons);
      this.detail.set(person);
      this.formular.setValue({
        vorname: person.firstName,
        nachname: person.lastName,
        email: person.email ?? '',
        admin: person.admin,
      });
    } catch (e) {
      if (fehlerstatus(e) === 404) {
        this.nichtGefunden.set(true);
        this.fehler.set(TEXTE.personen.nichtGefunden);
      } else {
        this.fehler.set(fehlertext(e));
      }
    } finally {
      this.laedt.set(false);
    }
  }

  protected async speichern(): Promise<void> {
    if (this.speichert()) return;
    this.erfolg.set(null);
    this.fehler.set(null);
    const f = this.formular.getRawValue();
    const fehler: Record<string, string> = {};
    if (!f.vorname.trim()) fehler['vorname'] = TEXTE.personen.pflicht('einen Vornamen');
    if (!f.nachname.trim()) fehler['nachname'] = TEXTE.personen.pflicht('einen Nachnamen');
    if (this.formular.controls.email.invalid)
      fehler['email'] = 'Bitte eine gültige E-Mail-Adresse eingeben.';
    this.feldFehler.set(fehler);
    if (Object.keys(fehler).length > 0) {
      return;
    }

    const eingabe: PersonSchreiben = {
      firstName: f.vorname.trim(),
      lastName: f.nachname.trim(),
      email: f.email.trim() || null,
      admin: f.admin,
    };
    this.speichert.set(true);
    try {
      const id = this.personId();
      if (id) {
        await firstValueFrom(this.http.put<Person>(`/api/persons/${id}`, eingabe));
        this.erfolg.set(TEXTE.personen.gespeichert);
        await this.laden(id);
        this.erfolg.set(TEXTE.personen.gespeichert);
        if (id === this.anmeldung.ich()?.id) {
          await this.anmeldung.laden();
        }
      } else {
        const neu = await firstValueFrom(this.http.post<Person>('/api/persons', eingabe));
        this.rueckmeldung.setzen(TEXTE.personen.erfasst(name(neu)));
        await this.router.navigate(['/verwaltung/personen', neu.id]);
      }
    } catch (e) {
      this.feldFehler.set(feldfehler(e, { firstName: 'vorname', lastName: 'nachname' }));
      this.fehler.set(fehlertext(e));
    } finally {
      this.speichert.set(false);
    }
  }
}
