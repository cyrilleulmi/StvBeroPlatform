import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AnmeldungService } from '../../core/auth.service';
import { fehlertext } from '../../core/error-text';
import { Icon } from '../../core/icon';
import { TEXTE } from '../../core/texts';
import { Modus, ThemeService } from '../../core/theme.service';

/** Eine Wahl im Umschalter. `vorschau` ist der Wert von `data-theme`, in dem sich die Wahl selbst zeigt. */
interface Option {
  wert: Modus;
  text: string;
  vorschau: string | null;
}

/** Profil: Name, Darstellung (NF-06, NF-08), Abmelden. */
@Component({
  selector: 'app-profil',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  templateUrl: './profile.html',
  styleUrl: './profile.css',
})
export class Profil {
  protected readonly anmeldung = inject(AnmeldungService);
  protected readonly theme = inject(ThemeService);
  private readonly router = inject(Router);
  protected readonly texte = TEXTE;
  protected readonly fehler = signal<string | null>(null);
  /** Läuft die Abmeldung, sind weitere Klicks gesperrt (Doppelklick). */
  protected readonly abmeldend = signal(false);

  protected readonly optionen: Option[] = [
    { wert: 'hell', text: TEXTE.profil.hell, vorschau: 'light' },
    { wert: 'duene', text: TEXTE.profil.duene, vorschau: 'duene' },
    { wert: 'github-dark', text: TEXTE.profil.githubDark, vorschau: 'github-dark' },
    { wert: 'system', text: TEXTE.profil.system, vorschau: null },
  ];

  protected async abmelden(): Promise<void> {
    if (this.abmeldend()) return;
    this.abmeldend.set(true);
    try {
      await this.anmeldung.abmelden();
      await this.router.navigateByUrl(this.anmeldung.anmeldeRoute());
    } catch (e) {
      this.fehler.set(fehlertext(e));
    } finally {
      this.abmeldend.set(false);
    }
  }
}
