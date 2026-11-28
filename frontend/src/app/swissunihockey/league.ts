import { ChangeDetectionStrategy, Component, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import type { League as LeagueData } from '../api/types';
import { TEXTS } from '../core/texts';
import { LeagueOverview } from './league-overview';

/**
 * League of a team (`/teams/:teamId/liga`, F-17): the overview of swiss unihockey for this team (SUH-16, the same
 * as on `/swissunihockey`) below the back link and the title.
 */
@Component({
  selector: 'app-league',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, LeagueOverview],
  template: `
    <div class="header">
      <a class="back" [routerLink]="['/teams', teamId()]">{{
        data() ? t.backTo(data()!.teamName) : texts.common.back
      }}</a>
      <h1>{{ t.title }}</h1>
      @if (data(); as d) {
        <p class="secondary-text">{{ d.teamName }}</p>
      }
    </div>
    <app-league-overview [teamId]="teamId()" (loaded)="data.set($event)" />
  `,
  styles: `
    :host {
      display: grid;
      gap: var(--section-gap);
      min-width: 0;
    }
  `,
})
export class League {
  protected readonly texts = TEXTS;
  protected readonly t = TEXTS.league;

  /** Route `/teams/:teamId/liga` */
  readonly teamId = input.required<string>();

  protected readonly data = signal<LeagueData | null>(null);
}
