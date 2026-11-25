import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TEXTS } from '../../core/texts';
import { NotificationSettingsSection } from '../../notifications/notification-settings';

/** `/profil/benachrichtigungen`: e-mail and push (F-19), a sub-page of the profile (E-109). */
@Component({
  selector: 'app-profile-notifications',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, NotificationSettingsSection],
  template: `
    <div class="header">
      <a class="back" routerLink="/profil">{{ texts.profile.toProfile }}</a>
      <h1>{{ texts.notificationSettings.title }}</h1>
    </div>
    <app-notification-settings />
  `,
  styleUrl: './subpage.css',
})
export class ProfileNotificationsPage {
  protected readonly texts = TEXTS;
}
