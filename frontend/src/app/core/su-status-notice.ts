import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { SuStatus } from '../api/types';
import { clockTime, eventDate } from './time';
import { Icon } from './icon';
import { TEXTS } from './texts';

/**
 * Hint for coaches and admins when the last sync with swiss unihockey failed (SUH-13): which teams, since when,
 * and that the app keeps working with the data it has (SUH-09). Shows nothing while everything is fine.
 */
@Component({
  selector: 'app-su-status-notice',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon],
  template: `
    @if (failures().length > 0) {
      <div class="notice" role="status" data-testid="su-status-notice">
        <app-icon name="triangle-alert" [size]="18" />
        <div class="text">
          <p>{{ text() }}</p>
          <ul class="teams" [attr.aria-label]="s.statusOpenTeam">
            @for (f of teams(); track f.teamId) {
              <li>
                <a [routerLink]="['/verwaltung/teams', f.teamId]">{{ s.statusOpenTeam }}: {{ f.teamName }}</a>
              </li>
            }
          </ul>
        </div>
      </div>
    }
  `,
  styles: `
    :host {
      display: contents;
    }
    .notice {
      display: flex;
      align-items: flex-start;
      gap: 10px;
      padding: 12px 14px;
      border-radius: var(--radius-card);
      background: var(--mustard-soft);
      border: 1px solid var(--mustard);
      color: var(--text);
      font-weight: 600;
    }
    .notice app-icon {
      flex: none;
      color: var(--mustard);
      padding-top: 2px;
    }
    .text {
      display: grid;
      gap: 6px;
      min-width: 0;
    }
    .text p {
      margin: 0;
    }
    .teams {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-wrap: wrap;
      gap: 4px 16px;
    }
    .teams a {
      display: inline-flex;
      align-items: center;
      min-height: var(--touch);
      color: var(--text);
      font-weight: 700;
    }
  `,
})
export class SuStatusNotice implements OnInit {
  private readonly http = inject(HttpClient);
  protected readonly s = TEXTS.swissUnihockey;

  private readonly status = signal<SuStatus | null>(null);
  protected readonly failures = computed(() => this.status()?.failures ?? []);

  /** One entry per team, even if several of its links failed. */
  protected readonly teams = computed(() => {
    const seen = new Set<string>();
    return this.failures().filter((f) => !seen.has(f.teamId) && seen.add(f.teamId));
  });

  protected readonly text = computed(() => {
    const failures = this.failures();
    if (failures.length === 0) return '';
    const latest = failures.map((f) => f.at).sort().at(-1)!;
    return this.s.statusFailed(
      this.teams()
        .map((f) => f.teamName)
        .join(', '),
      `${eventDate(latest, null)} ${clockTime(latest)}`,
    );
  });

  async ngOnInit(): Promise<void> {
    try {
      this.status.set(await firstValueFrom(this.http.get<SuStatus>('/api/swiss-unihockey/status')));
    } catch {
      // The hint is an extra: without it the page works as before (e.g. no current season)
    }
  }
}
