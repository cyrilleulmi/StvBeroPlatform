import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService, safeTarget } from '../../core/auth.service';
import { errorText } from '../../core/error-text';
import { TEXTS } from '../../core/texts';

/**
 * Hinweis, wenn keine Saison als aktuell festgelegt ist (409 von `GET /api/me`, SAI-03).
 * Ausserhalb der Shell, weil Navigation und Seiten die aktuelle Saison voraussetzen.
 */
@Component({
  selector: 'app-keine-saison',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="login">
      <div class="card">
        <p class="eyebrow">{{ texts.appName }}</p>
        <h1>{{ texts.noSeason.title }}</h1>
        <p role="status" data-testid="keine-saison-hinweis">{{ texts.noSeason.notice }}</p>
        <p>{{ texts.noSeason.administration }}</p>
        @if (error()) {
          <p class="message-error" role="alert">{{ error() }}</p>
        }
        <div class="actions">
          <button type="button" class="button button-primary" (click)="retry()">
            {{ texts.noSeason.retry }}
          </button>
          <button
            type="button"
            class="button button-secondary"
            [disabled]="loggingOut()"
            [attr.aria-busy]="loggingOut() ? 'true' : null"
            (click)="logout()"
          >
            {{ texts.noSeason.logout }}
          </button>
        </div>
      </div>
    </main>
  `,
  styleUrl: '../login/login.css',
})
export class NoSeason {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly texts = TEXTS;
  protected readonly error = signal('');
  /** Läuft die Abmeldung, sind weitere Klicks gesperrt (Doppelklick). */
  protected readonly loggingOut = signal(false);
  readonly target = input<string>(undefined, { alias: 'ziel' });

  /** Zurück zum Ziel. Der Guard prüft erneut und zeigt diesen Hinweis wieder, falls nötig. */
  protected async retry(): Promise<void> {
    this.error.set('');
    await this.router.navigateByUrl(safeTarget(this.target()));
  }

  protected async logout(): Promise<void> {
    if (this.loggingOut()) return;
    this.loggingOut.set(true);
    try {
      await this.auth.logout();
      await this.router.navigateByUrl(this.auth.loginRoute());
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.loggingOut.set(false);
    }
  }
}
