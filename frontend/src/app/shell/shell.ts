import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { NotificationsService } from '../core/notifications.service';
import { Icon } from '../core/icon';
import { TEXTS } from '../core/texts';

interface NavEntry {
  route: string;
  text: string;
  icon: string;
  exact: boolean;
}

/**
 * Frame for logged-in people: navigation (mobile at the bottom, from 1024px on the left) and content.
 * The number of unread notifications is reloaded on every page change.
 */
@Component({
  selector: 'app-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icon],
  templateUrl: './shell.html',
  styleUrl: './shell.css',
})
export class Shell {
  private readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationsService);
  protected readonly texts = TEXTS;
  protected readonly unread = this.notifications.unread;

  constructor() {
    // The frame is created before the first NavigationEnd, so the first call also loads the counter.
    inject(Router)
      .events.pipe(
        filter((e) => e instanceof NavigationEnd),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe(() => void this.notifications.refreshCounter());
  }

  protected readonly entries = computed<NavEntry[]>(() => {
    const n = TEXTS.navigation;
    const list: NavEntry[] = [
      { route: '/', text: n.start, icon: 'house', exact: true },
      { route: '/termine', text: n.events, icon: 'calendar', exact: false },
      { route: '/teams', text: n.teams, icon: 'users', exact: false },
      { route: '/profil', text: n.profile, icon: 'user', exact: false },
    ];
    // Admins and coaches (RR-03, RR-05, E-69)
    if (this.auth.canManage()) {
      list.push({ route: '/verwaltung', text: n.administration, icon: 'shield', exact: false });
    }
    return list;
  });
}
