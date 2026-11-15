import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { Team } from '../../api/typen';
import { fehlertext } from '../../kern/fehlertext';
import { funktionText } from '../../kern/format';
import { Icon } from '../../kern/icon';
import { TEXTE } from '../../kern/texte';

/** Meine Teams: alle Teams der aktuellen Saison, denen ich angehöre, mit meiner Funktion (PTF-12). */
@Component({
  selector: 'app-teams',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon],
  template: `
    <h1>{{ texte.teams.titel }}</h1>
    @if (fehler(); as meldung) {
      <p class="meldung-fehler" role="alert">{{ meldung }}</p>
    }
    <section class="karte" aria-labelledby="teams-liste" [attr.aria-busy]="laedt() ? 'true' : null">
      <h2 id="teams-liste" class="visuell-versteckt">{{ texte.teams.titel }}</h2>
      @if (laedt()) {
        <p class="leer">{{ texte.allgemein.laden }}</p>
      } @else if (teams().length === 0) {
        <p class="leer">{{ texte.teams.leer }}</p>
      } @else {
        <ul class="liste" [attr.aria-label]="texte.teams.titel">
          @for (t of teams(); track t.id) {
            <li [attr.data-testid]="'team-karte-' + t.id">
              <a class="zeile-link" [routerLink]="['/teams', t.id]">
                <span class="zeile-titel">{{ t.name }}</span>
                <span class="chip" [attr.data-art]="t.meineFunktion === 'COACH' ? 'coach' : null">{{
                  funktion(t.meineFunktion)
                }}</span>
                <app-icon name="chevron-right" [groesse]="20" />
              </a>
            </li>
          }
        </ul>
      }
    </section>
  `,
  styleUrl: '../seite.css',
})
export class Teams implements OnInit {
  private readonly http = inject(HttpClient);
  protected readonly texte = TEXTE;
  protected readonly funktion = funktionText;
  protected readonly teams = signal<Team[]>([]);
  protected readonly laedt = signal(true);
  protected readonly fehler = signal<string | null>(null);

  async ngOnInit(): Promise<void> {
    try {
      const alle = await firstValueFrom(this.http.get<Team[]>('/api/teams'));
      // Admins erhalten alle Teams; hier zählen nur die eigenen.
      this.teams.set(alle.filter((t) => t.meineFunktion != null));
    } catch (e) {
      this.fehler.set(fehlertext(e));
    } finally {
      this.laedt.set(false);
    }
  }
}
