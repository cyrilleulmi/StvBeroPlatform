import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CalendarSubscriptionSection } from '../../attendance/calendar-subscription';
import { TEXTS } from '../../core/texts';

/** `/profil/kalender`: subscribe to the own events in a calendar app (F-18), a sub-page of the profile (E-109). */
@Component({
  selector: 'app-profile-calendar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, CalendarSubscriptionSection],
  template: `
    <div class="header">
      <a class="back" routerLink="/profil">{{ texts.profile.toProfile }}</a>
      <h1>{{ texts.calendar.title }}</h1>
    </div>
    <app-calendar-subscription />
  `,
  styleUrl: './subpage.css',
})
export class ProfileCalendarPage {
  protected readonly texts = TEXTS;
}
