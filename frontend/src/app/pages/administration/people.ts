import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  untracked,
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

/**
 * People (admins and coaches, PTF-11, E-69): list with search (`?suche=`) and count, entry point to create and edit.
 * Rows as in the hubs (E-110).
 */
@Component({
  selector: 'app-people',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon],
  template: `
    <div class="header">
      <a class="back" routerLink="/verwaltung">{{ texts.administration.toAdministration }}</a>
      <h1>{{ texts.people.title }}</h1>
    </div>
    @if (!auth.canManage()) {
      <p class="message-error" role="alert">{{ texts.common.noPermission }}</p>
    } @else {
      <div class="toolbar">
        <div class="field search">
          <label for="people-search">{{ texts.people.search }}</label>
          <input
            spellcheck="false"
            name="people-search"
            id="people-search"
            type="search"
            autocomplete="off"
            [value]="searchQuery() ?? ''"
            (input)="search($any($event.target).value)"
          />
        </div>
        <a class="button button-primary create" routerLink="/verwaltung/personen/neu">
          <app-icon name="user-plus" [size]="20" />{{ texts.people.create }}
        </a>
      </div>
      @if (error(); as message) {
        <p class="message-error" role="alert">{{ message }}</p>
      }
      <section class="list-section" aria-labelledby="people-list" [attr.aria-busy]="loading() ? 'true' : null">
        <h2 id="people-list" class="visually-hidden">{{ texts.people.title }}</h2>
        <p class="secondary-text count-line" data-testid="people-count">{{ countText() }}</p>
        <div class="card links">
          @if (loading() && people().length === 0) {
            <p class="empty">{{ texts.common.loading }}</p>
          } @else if (people().length === 0) {
            <p class="empty">{{ texts.people.empty }}</p>
          } @else {
            <ul class="list" [attr.aria-label]="texts.people.title">
              @for (p of people(); track p.id) {
                <li [attr.data-testid]="'person-' + p.id">
                  <a class="row-link" [routerLink]="['/verwaltung/personen', p.id]">
                    <span class="icon-field"><app-icon name="user" [size]="20" /></span>
                    <span class="row-title">{{ name(p) }}</span>
                    @if (p.admin) {
                      <span class="chip" data-kind="admin">{{ texts.people.admin }}</span>
                    }
                    <app-icon name="chevron-right" [size]="20" />
                  </a>
                </li>
              }
            </ul>
          }
        </div>
      </section>
    }
  `,
  styleUrls: ['../page.css', '../rows.css'],
  styles: `
    :host {
      min-width: 0;
    }
    /* Search and «Person erfassen» side by side from 560px, stacked below */
    .toolbar {
      display: grid;
      gap: 12px;
    }
    .create {
      justify-self: start;
    }
    @media (min-width: 560px) {
      .toolbar {
        grid-template-columns: minmax(0, 1fr) auto;
        align-items: end;
      }
      .create {
        min-height: var(--touch);
      }
    }
    .list-section {
      display: grid;
      gap: 8px;
      min-width: 0;
    }
    .count-line {
      margin: 0;
    }
    .count-line:empty::before {
      content: '\\00a0';
    }
    .links > .empty {
      padding: 12px 0;
    }
    .row-link .icon-field {
      width: 32px;
      height: 32px;
    }
    @media (min-width: 1024px) {
      :host {
        max-width: 760px;
      }
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
  // eslint-disable-next-line @angular-eslint/no-input-rename -- query parameter `?suche=` is part of the browser URL and stays German (NF-40)
  readonly searchQuery = input<string | undefined>(undefined, { alias: 'suche' });

  protected readonly people = signal<Person[]>([]);
  /** All people of the club, for «3 von 17 Personen» while searching. */
  private readonly total = signal<number | null>(null);
  protected readonly countText = computed(() =>
    this.loading() && this.people().length === 0
      ? ''
      : TEXTS.people.count(this.people().length, this.total(), !!this.searchQuery()?.trim()),
  );
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  private request = 0;

  constructor() {
    effect(() => {
      const searchQuery = this.searchQuery();
      if (this.auth.canManage()) {
        untracked(() => void this.load(searchQuery));
      }
    });
    // Opened with a search: count everybody once, for «3 von 17 Personen».
    effect(() => {
      if (this.auth.canManage() && this.searchQuery()?.trim()) {
        untracked(() => void this.loadTotal());
      }
    });
  }

  private async loadTotal(): Promise<void> {
    if (this.total() !== null) return;
    try {
      const all = await firstValueFrom(this.http.get<Person[]>('/api/persons'));
      this.total.set(all.length);
    } catch {
      // Only the count «von 17» is missing; the list itself reports its errors.
    }
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
        if (!params['search']) this.total.set(list.length);
        this.error.set(null);
      }
    } catch (e) {
      if (nr === this.request) this.error.set(errorText(e));
    } finally {
      if (nr === this.request) this.loading.set(false);
    }
  }
}
