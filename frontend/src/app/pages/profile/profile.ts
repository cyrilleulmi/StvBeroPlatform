import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  WritableSignal,
  computed,
  inject,
  signal,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { absenceSummary } from '../../attendance/absence-summary';
import type { Absence, Driving, NotificationSettings } from '../../api/types';
import { AuthService } from '../../core/auth.service';
import { errorText } from '../../core/error-text';
import { initials, name, roleLabels } from '../../core/format';
import { Icon } from '../../core/icon';
import { currentSubscription } from '../../core/push';
import { TEXTS } from '../../core/texts';
import { ThemeMode, ThemeService } from '../../core/theme.service';
import { dayKey } from '../../core/time';
import { drivingSummary } from '../../drivers/driving-summary';
import { notificationSummary } from '../../notifications/notification-summary';

/** One choice in the switch. `preview` is the value of `data-theme` in which the choice shows itself. */
interface Option {
  value: ThemeMode;
  text: string;
  preview: string | null;
}

/** A row that leads to a page of the profile: icon box, title, one muted line (the current state), chevron. */
interface Row {
  key: string;
  route: string;
  icon: string;
  title: string;
  testId: string;
  state: string | null;
}

/**
 * Profile hub (E-109): who you are in the club (name, e-mail, teams and roles of the current season), then the
 * group «Meine Saison» (own statistics STA-08, absences ABS-01) and the group «Einstellungen» (notifications F-19,
 * calendar subscription CAL-01, driving DRV-18), each a row with its current state that leads to a page of its
 * own; then the theme (NF-06, NF-08) and «Abmelden».
 */
@Component({
  selector: 'app-profile',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, NgTemplateOutlet, RouterLink],
  templateUrl: './profile.html',
  styleUrls: ['./rows.css', './profile.css'],
})
export class Profile {
  protected readonly auth = inject(AuthService);
  protected readonly theme = inject(ThemeService);
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  protected readonly texts = TEXTS;
  protected readonly error = signal<string | null>(null);
  /** While logging out, further clicks are blocked (double click). */
  protected readonly loggingOut = signal(false);

  /** State lines, loaded from the endpoints of the pages; `null` while loading, the fallback text after an error. */
  private readonly absenceState = signal<string | null>(null);
  private readonly notificationState = signal<string | null>(null);
  private readonly drivingState = signal<string | null>(null);

  protected readonly fullName = computed(() => {
    const me = this.auth.me();
    return me ? name(me) : '';
  });
  protected readonly initials = computed(() => {
    const me = this.auth.me();
    return me ? initials(me) : '';
  });
  protected readonly roles = computed(() => roleLabels(this.auth.me()?.memberships ?? []));

  /** «Meine Statistik» only for players (in at least one team of the current season, STA-08). */
  protected readonly isPlayer = computed(
    () => this.auth.me()?.memberships.some((m) => m.teamRole === 'PLAYER') ?? false,
  );

  protected readonly seasonRows = computed<Row[]>(() => [
    ...(this.isPlayer()
      ? [
          {
            key: 'statistics',
            route: '/profil/statistik',
            icon: 'chart-column',
            title: TEXTS.statistics.myTitle,
            testId: 'my-statistics-link',
            state: TEXTS.statistics.myHint,
          },
        ]
      : []),
    {
      key: 'absences',
      route: '/profil/abwesenheiten',
      icon: 'plane',
      title: TEXTS.absences.title,
      testId: 'my-absences-link',
      state: this.absenceState(),
    },
  ]);

  protected readonly settingRows = computed<Row[]>(() => [
    {
      key: 'notifications',
      route: '/profil/benachrichtigungen',
      icon: 'bell',
      title: TEXTS.notificationSettings.title,
      testId: 'my-notifications-link',
      state: this.notificationState(),
    },
    {
      key: 'calendar',
      route: '/profil/kalender',
      icon: 'calendar',
      title: TEXTS.calendar.title,
      testId: 'my-calendar-link',
      state: TEXTS.profile.calendarState,
    },
    {
      key: 'driving',
      route: '/profil/fahren',
      icon: 'car',
      title: TEXTS.driving.section,
      testId: 'my-driving-link',
      state: this.drivingState(),
    },
  ]);

  protected readonly options: Option[] = [
    { value: 'light', text: TEXTS.profile.light, preview: 'light' },
    { value: 'dark', text: TEXTS.profile.dark, preview: 'dark' },
    { value: 'system', text: TEXTS.profile.system, preview: null },
  ];

  constructor() {
    void this.loadStates();
  }

  /** The state lines load side by side; one that fails shows its fallback, the page stays usable. */
  private async loadStates(): Promise<void> {
    const me = this.auth.me();
    if (!me) return;
    const today = dayKey(me.now);
    await Promise.all([
      this.loadState(this.absenceState, TEXTS.absences.profileHint, async () =>
        absenceSummary(await firstValueFrom(this.http.get<Absence[]>('/api/me/absences')), today),
      ),
      this.loadState(this.notificationState, TEXTS.profile.notificationsFallback, async () => {
        const [settings, subscription] = await Promise.all([
          firstValueFrom(this.http.get<NotificationSettings>('/api/me/notification-settings')),
          currentSubscription().catch(() => null),
        ]);
        return notificationSummary(settings, subscription?.endpoint ?? null);
      }),
      this.loadState(this.drivingState, TEXTS.profile.drivingFallback, async () =>
        drivingSummary(
          await firstValueFrom(this.http.get<Driving>(`/api/persons/${me.id}/driving`)),
        ),
      ),
    ]);
  }

  private async loadState(
    target: WritableSignal<string | null>,
    fallback: string,
    load: () => Promise<string>,
  ): Promise<void> {
    try {
      target.set(await load());
    } catch {
      target.set(fallback);
    }
  }

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
