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
import type { Person } from '../../api/types';
import { AuthService } from '../../core/auth.service';
import { errorText } from '../../core/error-text';
import { name } from '../../core/format';
import { Icon } from '../../core/icon';
import { TEXTS } from '../../core/texts';

/** Personen (Admin, PTF-11): Liste mit Suche (`?suche=`), Einstieg zum Erfassen und Bearbeiten. */
@Component({
  selector: 'app-personen',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon],
  template: `
    <div class="kopf">
      <a class="zurueck" routerLink="/verwaltung">{{ texts.administration.toAdministration }}</a>
      <h1>{{ texts.people.title }}</h1>
    </div>
    @if (!auth.isAdmin()) {
      <p class="meldung-fehler" role="alert">{{ texts.common.noPermission }}</p>
    } @else {
      <a class="button button-primaer erfassen" routerLink="/verwaltung/personen/neu">{{
        texts.people.create
      }}</a>
      <div class="feld">
        <label for="personen-suche">{{ texts.people.search }}</label>
        <input
          id="personen-suche"
          type="search"
          autocomplete="off"
          [value]="searchQuery() ?? ''"
          (input)="search($any($event.target).value)"
        />
      </div>
      @if (error(); as message) {
        <p class="meldung-fehler" role="alert">{{ message }}</p>
      }
      <section
        class="karte"
        aria-labelledby="personen-liste"
        [attr.aria-busy]="loading() ? 'true' : null"
      >
        <h2 id="personen-liste" class="visuell-versteckt">{{ texts.people.title }}</h2>
        @if (loading() && people().length === 0) {
          <p class="leer">{{ texts.common.loading }}</p>
        } @else if (people().length === 0) {
          <p class="leer">{{ texts.people.empty }}</p>
        } @else {
          <ul class="liste" [attr.aria-label]="texts.people.title">
            @for (p of people(); track p.id) {
              <li [attr.data-testid]="'person-' + p.id">
                <a class="zeile-link" [routerLink]="['/verwaltung/personen', p.id]">
                  <span class="zeile-titel">{{ name(p) }}</span>
                  @if (p.admin) {
                    <span class="chip" data-art="admin">{{ texts.people.admin }}</span>
                  }
                  <app-icon name="chevron-right" [size]="20" />
                </a>
              </li>
            }
          </ul>
        }
      </section>
    }
  `,
  styleUrl: '../page.css',
  styles: `
    .erfassen {
      justify-self: start;
    }
  `,
})
export class People {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);
  protected readonly texts = TEXTS;
  protected readonly name = name;

  /** Query `?suche=` */
  readonly searchQuery = input<string | undefined>(undefined, { alias: 'suche' });

  protected readonly people = signal<Person[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  private request = 0;

  constructor() {
    effect(() => {
      const searchQuery = this.searchQuery();
      if (this.auth.isAdmin()) {
        untracked(() => void this.load(searchQuery));
      }
    });
  }

  protected search(text: string): void {
    void this.router.navigate([], {
      queryParams: { suche: text.trim() ? text : null },
      replaceUrl: true,
    });
  }

  private async load(searchQuery: string | undefined): Promise<void> {
    const nr = ++this.request;
    this.loading.set(true);
    try {
      const params: Record<string, string> = searchQuery?.trim() ? { search: searchQuery.trim() } : {};
      const list = await firstValueFrom(this.http.get<Person[]>('/api/persons', { params }));
      if (nr === this.request) {
        this.people.set(list);
        this.error.set(null);
      }
    } catch (e) {
      if (nr === this.request) this.error.set(errorText(e));
    } finally {
      if (nr === this.request) this.loading.set(false);
    }
  }
}
