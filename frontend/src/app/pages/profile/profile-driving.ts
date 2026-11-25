import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { Icon } from '../../core/icon';
import { TEXTS } from '../../core/texts';
import { MyDrivingSection } from '../../drivers/driving';

/**
 * `/profil/fahren`: whether the person can drive and how many places her car has (DRV-18), and below the way to
 * her trips of the season (`/fahrten`, F-20). A sub-page of the profile (E-109).
 */
@Component({
  selector: 'app-profile-driving',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon, MyDrivingSection],
  template: `
    <div class="header">
      <a class="back" routerLink="/profil">{{ texts.profile.toProfile }}</a>
      <h1>{{ texts.driving.section }}</h1>
    </div>
    @if (auth.me(); as me) {
      <app-my-driving [personId]="me.id" />
    }
    <div class="card links">
      <a class="row-link" routerLink="/fahrten" data-testid="my-trips-link">
        <span class="icon-field"><app-icon name="car" [size]="20" /></span>
        <span class="row-text">
          <span class="row-title">{{ texts.trips.myLink }}</span>
          <span class="secondary-text">{{ texts.trips.myHint }}</span>
        </span>
        <app-icon name="chevron-right" [size]="20" />
      </a>
    </div>
  `,
  styleUrls: ['./subpage.css', './rows.css'],
})
export class ProfileDrivingPage {
  protected readonly auth = inject(AuthService);
  protected readonly texts = TEXTS;
}
