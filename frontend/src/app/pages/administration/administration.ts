import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { Icon } from '../../core/icon';
import { TEXTS } from '../../core/texts';

/** Verwaltung (nur Admins, RR-04/RR-05): Einstieg zu Personen, Teams und Saisons. */
@Component({
  selector: 'app-verwaltung',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon],
  template: `
    <h1>{{ texts.administration.title }}</h1>
    @if (auth.isAdmin()) {
      <section class="karte" aria-labelledby="verwaltung-bereiche">
        <h2 id="verwaltung-bereiche" class="visuell-versteckt">{{ texts.administration.areas }}</h2>
        <ul class="liste" [attr.aria-label]="texts.administration.areas">
          @for (b of areas; track b.route) {
            <li>
              <a class="zeile-link" [routerLink]="b.route">
                <span class="icon-feld"><app-icon [name]="b.icon" [size]="20" /></span>
                <span class="zeile-titel">
                  {{ b.title }}
                  <span class="nebentext beschreibung">{{ b.text }}</span>
                </span>
                <app-icon name="chevron-right" [size]="20" />
              </a>
            </li>
          }
        </ul>
      </section>
    } @else {
      <p class="meldung-fehler" role="alert">{{ texts.common.noPermission }}</p>
    }
  `,
  styleUrl: '../page.css',
  styles: `
    .icon-feld {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 38px;
      height: 38px;
      flex: none;
      border-radius: var(--radius-icon);
      background: var(--primary-soft);
      color: var(--primary);
    }
    .beschreibung {
      display: block;
      font-weight: 500;
    }
  `,
})
export class Administration {
  protected readonly auth = inject(AuthService);
  protected readonly texts = TEXTS;
  protected readonly areas = [
    {
      route: '/verwaltung/personen',
      title: TEXTS.administration.people,
      text: TEXTS.administration.peopleText,
      icon: 'user',
    },
    {
      route: '/verwaltung/teams',
      title: TEXTS.administration.teams,
      text: TEXTS.administration.teamsText,
      icon: 'users',
    },
    {
      route: '/verwaltung/saisons',
      title: TEXTS.administration.seasons,
      text: TEXTS.administration.seasonsText,
      icon: 'calendar',
    },
  ];
}
