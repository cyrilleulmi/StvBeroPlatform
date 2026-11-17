import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AnmeldungService } from '../../core/auth.service';
import { Icon } from '../../core/icon';
import { TEXTE } from '../../core/texts';

/** Verwaltung (nur Admins, RR-04/RR-05): Einstieg zu Personen, Teams und Saisons. */
@Component({
  selector: 'app-verwaltung',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon],
  template: `
    <h1>{{ texte.verwaltung.titel }}</h1>
    @if (anmeldung.istAdmin()) {
      <section class="karte" aria-labelledby="verwaltung-bereiche">
        <h2 id="verwaltung-bereiche" class="visuell-versteckt">{{ texte.verwaltung.bereiche }}</h2>
        <ul class="liste" [attr.aria-label]="texte.verwaltung.bereiche">
          @for (b of bereiche; track b.route) {
            <li>
              <a class="zeile-link" [routerLink]="b.route">
                <span class="icon-feld"><app-icon [name]="b.icon" [groesse]="20" /></span>
                <span class="zeile-titel">
                  {{ b.titel }}
                  <span class="nebentext beschreibung">{{ b.text }}</span>
                </span>
                <app-icon name="chevron-right" [groesse]="20" />
              </a>
            </li>
          }
        </ul>
      </section>
    } @else {
      <p class="meldung-fehler" role="alert">{{ texte.allgemein.keineBerechtigung }}</p>
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
export class Verwaltung {
  protected readonly anmeldung = inject(AnmeldungService);
  protected readonly texte = TEXTE;
  protected readonly bereiche = [
    {
      route: '/verwaltung/personen',
      titel: TEXTE.verwaltung.personen,
      text: TEXTE.verwaltung.personenText,
      icon: 'user',
    },
    {
      route: '/verwaltung/teams',
      titel: TEXTE.verwaltung.teams,
      text: TEXTE.verwaltung.teamsText,
      icon: 'users',
    },
    {
      route: '/verwaltung/saisons',
      titel: TEXTE.verwaltung.saisons,
      text: TEXTE.verwaltung.saisonsText,
      icon: 'calendar',
    },
  ];
}
