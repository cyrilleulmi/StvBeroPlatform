import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { AnmeldungService } from '../core/auth.service';
import { BenachrichtigungenService } from '../core/notifications.service';
import { Icon } from '../core/icon';
import { TEXTE } from '../core/texts';

interface NavEintrag {
  route: string;
  text: string;
  icon: string;
  exakt: boolean;
}

/**
 * Rahmen für angemeldete Personen: Navigation (mobil unten, ab 1024px links) und Inhalt. Die Anzahl
 * ungelesener Benachrichtigungen wird bei jedem Seitenwechsel neu geladen.
 */
@Component({
  selector: 'app-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icon],
  templateUrl: './shell.html',
  styleUrl: './shell.css',
})
export class Shell {
  private readonly anmeldung = inject(AnmeldungService);
  private readonly benachrichtigungen = inject(BenachrichtigungenService);
  protected readonly texte = TEXTE;
  protected readonly ungelesen = this.benachrichtigungen.ungelesen;

  constructor() {
    // Der Rahmen entsteht vor dem ersten NavigationEnd, deshalb lädt auch der erste Aufruf den Zähler.
    inject(Router)
      .events.pipe(
        filter((e) => e instanceof NavigationEnd),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe(() => void this.benachrichtigungen.zaehlerAktualisieren());
  }

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
