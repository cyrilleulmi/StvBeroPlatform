import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { Icon } from '../../core/icon';
import { TEXTS } from '../../core/texts';

/**
 * Administration (admins and coaches, RR-03/RR-05, E-69): entry point to people, teams and, for admins only,
 * seasons (RR-04).
 */
@Component({
  selector: 'app-administration',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon],
  template: `
    <h1>{{ texts.administration.title }}</h1>
    @if (auth.canManage()) {
      <section class="card" aria-labelledby="administration-areas">
        <h2 id="administration-areas" class="visually-hidden">{{ texts.administration.areas }}</h2>
        <ul class="list" [attr.aria-label]="texts.administration.areas">
          @for (b of areas(); track b.route) {
            <li>
              <a class="row-link" [routerLink]="b.route">
                <span class="icon-field"><app-icon [name]="b.icon" [size]="20" /></span>
                <span class="row-title">
                  {{ b.title }}
                  <span class="secondary-text description">{{ b.text }}</span>
                </span>
                <app-icon name="chevron-right" [size]="20" />
              </a>
            </li>
          }
        </ul>
      </section>
    } @else {
      <p class="message-error" role="alert">{{ texts.common.noPermission }}</p>
    }
  `,
  styleUrl: '../page.css',
  styles: `
    .icon-field {
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
    .description {
      display: block;
      font-weight: 500;
    }
  `,
})
export class Administration {
  protected readonly auth = inject(AuthService);
  protected readonly texts = TEXTS;
  private readonly allAreas = [
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
      adminOnly: true,
    },
  ];
  /** Seasons only for admins (RR-04). */
  protected readonly areas = computed(() =>
    this.allAreas.filter((a) => !a.adminOnly || this.auth.isAdmin()),
  );
}
