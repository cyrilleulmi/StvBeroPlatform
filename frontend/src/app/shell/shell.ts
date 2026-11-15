import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AnmeldungService } from '../kern/anmeldung.service';
import { Icon } from '../kern/icon';
import { TEXTE } from '../kern/texte';

interface NavEintrag {
  route: string;
  text: string;
  icon: string;
  exakt: boolean;
}

/** Rahmen für angemeldete Personen: Navigation (mobil unten, ab 1024px links) und Inhalt. */
@Component({
  selector: 'app-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icon],
  templateUrl: './shell.html',
  styleUrl: './shell.css',
})
export class Shell {
  private readonly anmeldung = inject(AnmeldungService);
  protected readonly texte = TEXTE;

  protected readonly eintraege = computed<NavEintrag[]>(() => {
    const n = TEXTE.navigation;
    const liste: NavEintrag[] = [
      { route: '/', text: n.start, icon: 'house', exakt: true },
      { route: '/termine', text: n.termine, icon: 'calendar', exakt: false },
      { route: '/teams', text: n.teams, icon: 'users', exakt: false },
      { route: '/profil', text: n.profil, icon: 'user', exakt: false },
    ];
    if (this.anmeldung.istAdmin()) {
      liste.push({ route: '/verwaltung', text: n.verwaltung, icon: 'shield', exakt: false });
    }
    return liste;
  });
}
