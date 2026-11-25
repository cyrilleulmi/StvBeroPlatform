import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  computed,
  inject,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  NavigationEnd,
  NavigationStart,
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet,
} from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { coachedTeams } from '../core/format';
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

  private readonly main = viewChild.required<ElementRef<HTMLElement>>('main');

  constructor() {
    const router = inject(Router);
    let fromHistory = false;
    let previousPath: string | null = null;
    // The frame is created before the first NavigationEnd, so the first call also loads the counter.
    router.events
      .pipe(
        filter((e) => e instanceof NavigationStart || e instanceof NavigationEnd),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe((e) => {
        if (e instanceof NavigationStart) {
          fromHistory = e.navigationTrigger === 'popstate';
          return;
        }
        void this.notifications.refreshCounter();
        const path = e.urlAfterRedirects.split(/[?#]/)[0];
        // A new page (not the first one, not only a changed filter in the query): start at its top
        // and move focus to the content, so keyboard and screen reader users land on the new page
        // instead of the link they left (WCAG 2.4.3).
        if (previousPath !== null && path !== previousPath) {
          if (!fromHistory) window.scrollTo(0, 0);
          this.main().nativeElement.focus({ preventScroll: true });
        }
        previousPath = path;
      });
  }

  /** Skip link: jump past the navigation without changing the URL (the base href would load «/#main»). */
  protected skipToContent(event: Event): void {
    event.preventDefault();
    this.main().nativeElement.focus();
  }

  /** Teams the person coaches in the current season: «<Team> verwalten» on desktop (E-92). */
  protected readonly coachedTeams = computed(() => coachedTeams(this.auth.me()?.memberships ?? []));

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
