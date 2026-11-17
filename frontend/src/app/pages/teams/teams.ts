import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { Team } from '../../api/types';
import { errorText } from '../../core/error-text';
import { teamRoleText } from '../../core/format';
import { Icon } from '../../core/icon';
import { TEXTS } from '../../core/texts';

/** My teams: all teams of the current season I belong to, with my team role (PTF-12). */
@Component({
  selector: 'app-teams',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon],
  template: `
    <h1>{{ texts.teams.title }}</h1>
    @if (error(); as message) {
      <p class="message-error" role="alert">{{ message }}</p>
    }
    <section class="card" aria-labelledby="teams-list" [attr.aria-busy]="loading() ? 'true' : null">
      <h2 id="teams-list" class="visually-hidden">{{ texts.teams.title }}</h2>
      @if (loading()) {
        <p class="empty">{{ texts.common.loading }}</p>
      } @else if (teams().length === 0) {
        <p class="empty">{{ texts.teams.empty }}</p>
      } @else {
        <ul class="list" [attr.aria-label]="texts.teams.title">
          @for (t of teams(); track t.id) {
            <li [attr.data-testid]="'team-card-' + t.id">
              <a class="row-link" [routerLink]="['/teams', t.id]">
                <span class="row-title">{{ t.name }}</span>
                <span class="chip" [attr.data-kind]="t.myTeamRole === 'COACH' ? 'coach' : null">{{
                  teamRole(t.myTeamRole)
                }}</span>
                <app-icon name="chevron-right" [size]="20" />
              </a>
            </li>
          }
        </ul>
      }
    </section>
  `,
  styleUrl: '../page.css',
})
export class Teams implements OnInit {
  private readonly http = inject(HttpClient);
  protected readonly texts = TEXTS;
  protected readonly teamRole = teamRoleText;
  protected readonly teams = signal<Team[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  async ngOnInit(): Promise<void> {
    try {
      const all = await firstValueFrom(this.http.get<Team[]>('/api/teams'));
      // Admins get all teams; only the own ones count here.
      this.teams.set(all.filter((t) => t.myTeamRole != null));
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.loading.set(false);
    }
  }
}
