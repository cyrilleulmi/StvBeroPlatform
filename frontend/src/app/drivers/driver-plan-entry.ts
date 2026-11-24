import { ChangeDetectionStrategy, Component, computed, effect, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { TEXTS } from '../core/texts';

/**
 * `/fahrerliste` (DRV-24): opens the driver plan of the own team. With several teams the page offers the choice
 * «Team» there; without a team it says so.
 */
@Component({
  selector: 'app-driver-plan-entry',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <h1 class="page-title">{{ texts.driverPlan.title }}</h1>
    @if (!team()) {
      <p class="empty" data-testid="driver-plan-no-team">{{ texts.driverPlan.noTeam }}</p>
      <a class="button button-secondary button-small" routerLink="/teams">{{ texts.navigation.teams }}</a>
    }
  `,
  styles: `
    :host {
      display: grid;
      gap: 16px;
      justify-items: start;
    }
  `,
})
export class DriverPlanEntry {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly texts = TEXTS;

  /** The first own team of the current season: players first, then coached teams. */
  protected readonly team = computed(() => {
    const memberships = this.auth.me()?.memberships ?? [];
    return (memberships.find((m) => m.teamRole === 'PLAYER') ?? memberships[0])?.team.id ?? null;
  });

  constructor() {
    effect(() => {
      const team = this.team();
      if (team) void this.router.navigate(['/teams', team, 'fahrerliste'], { replaceUrl: true });
    });
  }
}
