import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { errorText } from '../../core/error-text';
import { Icon } from '../../core/icon';
import { TEXTS } from '../../core/texts';
import { ThemeMode, ThemeService } from '../../core/theme.service';
import { CalendarSubscriptionSection } from '../../attendance/calendar-subscription';
import { NotificationSettingsSection } from '../../notifications/notification-settings';
import { MyDrivingSection } from '../../drivers/driving';

/** One choice in the switch. `preview` is the value of `data-theme` in which the choice shows itself. */
interface Option {
  value: ThemeMode;
  text: string;
  preview: string | null;
}

/**
 * Profile: name, own statistics (STA-08), notification channels (F-19), calendar subscription (CAL-01), theme
 * (NF-06, NF-08), log out.
 */
@Component({
  selector: 'app-profile',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    Icon,
    RouterLink,
    CalendarSubscriptionSection,
    NotificationSettingsSection,
    MyDrivingSection,
  ],
  templateUrl: './profile.html',
  styleUrl: './profile.css',
})
export class Profile {
  protected readonly auth = inject(AuthService);
  protected readonly theme = inject(ThemeService);
  private readonly router = inject(Router);
  protected readonly texts = TEXTS;
  protected readonly error = signal<string | null>(null);
  /** While logging out, further clicks are blocked (double click). */
  protected readonly loggingOut = signal(false);
  /** «Meine Statistik» only for players (in at least one team of the current season, STA-08). */
  protected readonly isPlayer = computed(
    () => this.auth.me()?.memberships.some((m) => m.teamRole === 'PLAYER') ?? false,
  );

  protected readonly options: Option[] = [
    { value: 'light', text: TEXTS.profile.light, preview: 'light' },
    { value: 'dark', text: TEXTS.profile.dark, preview: 'dark' },
    { value: 'system', text: TEXTS.profile.system, preview: null },
  ];

  protected async logout(): Promise<void> {
    if (this.loggingOut()) return;
    this.loggingOut.set(true);
    try {
      await this.auth.logout();
      await this.router.navigateByUrl(this.auth.loginRoute());
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.loggingOut.set(false);
    }
  }
}
