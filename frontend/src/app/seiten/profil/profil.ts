import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AnmeldungService } from '../../kern/anmeldung.service';
import { fehlertext } from '../../kern/fehlertext';
import { Icon } from '../../kern/icon';
import { TEXTE } from '../../kern/texte';
import { Modus, ThemeService } from '../../kern/theme.service';

/** Profil: Name, Hell/Dunkel/System (NF-06), Abmelden. */
@Component({
  selector: 'app-profil',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  templateUrl: './profil.html',
  styleUrl: './profil.css',
})
export class Profil {
  protected readonly anmeldung = inject(AnmeldungService);
  protected readonly theme = inject(ThemeService);
  private readonly router = inject(Router);
  protected readonly texte = TEXTE;
  protected readonly fehler = signal<string | null>(null);

  protected readonly modi: { wert: Modus; text: string }[] = [
    { wert: 'hell', text: TEXTE.profil.hell },
    { wert: 'dunkel', text: TEXTE.profil.dunkel },
    { wert: 'system', text: TEXTE.profil.system },
  ];

  protected async abmelden(): Promise<void> {
    try {
      await this.anmeldung.abmelden();
      await this.router.navigateByUrl(this.anmeldung.anmeldeRoute());
    } catch (e) {
      this.fehler.set(fehlertext(e));
    }
  }
}
