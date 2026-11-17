import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AnmeldungService, sicheresZiel } from '../../core/auth.service';
import { fehlertext } from '../../core/error-text';
import { TEXTE } from '../../core/texts';

/**
 * Hinweis, wenn keine Saison als aktuell festgelegt ist (409 von `GET /api/me`, SAI-03).
 * Ausserhalb der Shell, weil Navigation und Seiten die aktuelle Saison voraussetzen.
 */
@Component({
  selector: 'app-keine-saison',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="anmelden">
      <div class="karte">
        <p class="eyebrow">{{ texte.appName }}</p>
        <h1>{{ texte.keineSaison.titel }}</h1>
        <p role="status" data-testid="keine-saison-hinweis">{{ texte.keineSaison.hinweis }}</p>
        <p>{{ texte.keineSaison.verwaltung }}</p>
        @if (fehler()) {
          <p class="meldung-fehler" role="alert">{{ fehler() }}</p>
        }
        <div class="aktionen">
          <button type="button" class="button button-primaer" (click)="nochmals()">
            {{ texte.keineSaison.nochmals }}
          </button>
          <button
            type="button"
            class="button button-sekundaer"
            [disabled]="abmeldend()"
            [attr.aria-busy]="abmeldend() ? 'true' : null"
            (click)="abmelden()"
          >
            {{ texte.keineSaison.abmelden }}
          </button>
        </div>
      </div>
    </main>
  `,
  styleUrl: '../login/login.css',
})
export class KeineSaison {
  private readonly anmeldung = inject(AnmeldungService);
  private readonly router = inject(Router);
  protected readonly texte = TEXTE;
  protected readonly fehler = signal('');
  /** Läuft die Abmeldung, sind weitere Klicks gesperrt (Doppelklick). */
  protected readonly abmeldend = signal(false);
  readonly ziel = input<string>();

  /** Zurück zum Ziel. Der Guard prüft erneut und zeigt diesen Hinweis wieder, falls nötig. */
  protected async nochmals(): Promise<void> {
    this.fehler.set('');
    await this.router.navigateByUrl(sicheresZiel(this.ziel()));
  }

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
