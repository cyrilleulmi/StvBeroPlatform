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
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { Person } from '../../api/typen';
import { AnmeldungService } from '../../kern/anmeldung.service';
import { fehlertext } from '../../kern/fehlertext';
import { name } from '../../kern/format';
import { Icon } from '../../kern/icon';
import { TEXTE } from '../../kern/texte';

/** Personen (Admin, PTF-11): Liste mit Suche (`?suche=`), Einstieg zum Erfassen und Bearbeiten. */
@Component({
  selector: 'app-personen',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon],
  template: `
    <div class="kopf">
      <a class="zurueck" routerLink="/verwaltung">{{ texte.verwaltung.zurVerwaltung }}</a>
      <h1>{{ texte.personen.titel }}</h1>
    </div>
    @if (!anmeldung.istAdmin()) {
      <p class="meldung-fehler" role="alert">{{ texte.allgemein.keineBerechtigung }}</p>
    } @else {
      <a class="button button-primaer erfassen" routerLink="/verwaltung/personen/neu">{{
        texte.personen.erfassen
      }}</a>
      <div class="feld">
        <label for="personen-suche">{{ texte.personen.suchen }}</label>
        <input
          id="personen-suche"
          type="search"
          autocomplete="off"
          [value]="suche() ?? ''"
          (input)="suchen($any($event.target).value)"
        />
      </div>
      @if (fehler(); as meldung) {
        <p class="meldung-fehler" role="alert">{{ meldung }}</p>
      }
      <section
        class="karte"
        aria-labelledby="personen-liste"
        [attr.aria-busy]="laedt() ? 'true' : null"
      >
        <h2 id="personen-liste" class="visuell-versteckt">{{ texte.personen.titel }}</h2>
        @if (laedt() && personen().length === 0) {
          <p class="leer">{{ texte.allgemein.laden }}</p>
        } @else if (personen().length === 0) {
          <p class="leer">{{ texte.personen.leer }}</p>
        } @else {
          <ul class="liste" [attr.aria-label]="texte.personen.titel">
            @for (p of personen(); track p.id) {
              <li [attr.data-testid]="'person-' + p.id">
                <a class="zeile-link" [routerLink]="['/verwaltung/personen', p.id]">
                  <span class="zeile-titel">{{ name(p) }}</span>
                  @if (p.admin) {
                    <span class="chip" data-art="admin">{{ texte.personen.admin }}</span>
                  }
                  <app-icon name="chevron-right" [groesse]="20" />
                </a>
              </li>
            }
          </ul>
        }
      </section>
    }
  `,
  styleUrl: '../seite.css',
  styles: `
    .erfassen {
      justify-self: start;
    }
  `,
})
export class Personen {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  protected readonly anmeldung = inject(AnmeldungService);
  protected readonly texte = TEXTE;
  protected readonly name = name;

  /** Query `?suche=` */
  readonly suche = input<string | undefined>();

  protected readonly personen = signal<Person[]>([]);
  protected readonly laedt = signal(true);
  protected readonly fehler = signal<string | null>(null);
  private anfrage = 0;

  constructor() {
    effect(() => {
      const suche = this.suche();
      if (this.anmeldung.istAdmin()) {
        untracked(() => void this.laden(suche));
      }
    });
  }

  protected suchen(text: string): void {
    void this.router.navigate([], {
      queryParams: { suche: text.trim() ? text : null },
      replaceUrl: true,
    });
  }

  private async laden(suche: string | undefined): Promise<void> {
    const nr = ++this.anfrage;
    this.laedt.set(true);
    try {
      const params: Record<string, string> = suche?.trim() ? { search: suche.trim() } : {};
      const liste = await firstValueFrom(this.http.get<Person[]>('/api/persons', { params }));
      if (nr === this.anfrage) {
        this.personen.set(liste);
        this.fehler.set(null);
      }
    } catch (e) {
      if (nr === this.anfrage) this.fehler.set(fehlertext(e));
    } finally {
      if (nr === this.anfrage) this.laedt.set(false);
    }
  }
}
