import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { Team } from '../../api/types';
import { errorText } from '../../core/error-text';
import { teamRoleText } from '../../core/format';
import { Icon } from '../../core/icon';
import { TEXTS } from '../../core/texts';

/** Meine Teams: alle Teams der aktuellen Saison, denen ich angehöre, mit meiner Funktion (PTF-12). */
@Component({
  selector: 'app-teams',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon],
  template: `
    <h1>{{ texts.teams.title }}</h1>
    @if (error(); as message) {
      <p class="meldung-fehler" role="alert">{{ message }}</p>
    }
    <section class="karte" aria-labelledby="teams-liste" [attr.aria-busy]="loading() ? 'true' : null">
      <h2 id="teams-liste" class="visuell-versteckt">{{ texts.teams.title }}</h2>
      @if (loading()) {
        <p class="leer">{{ texts.common.loading }}</p>
      } @else if (teams().length === 0) {
        <p class="leer">{{ texts.teams.empty }}</p>
      } @else {
        <ul class="liste" [attr.aria-label]="texts.teams.title">
          @for (t of teams(); track t.id) {
            <li [attr.data-testid]="'team-karte-' + t.id">
              <a class="zeile-link" [routerLink]="['/teams', t.id]">
                <span class="zeile-titel">{{ t.name }}</span>
                <span class="chip" [attr.data-art]="t.myTeamRole === 'COACH' ? 'coach' : null">{{
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
      // Admins erhalten alle Teams; hier zählen nur die eigenen.
      this.teams.set(all.filter((t) => t.myTeamRole != null));
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.loading.set(false);
    }
  }
}
