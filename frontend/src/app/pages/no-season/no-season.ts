import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService, safeTarget } from '../../core/auth.service';
import { errorText } from '../../core/error-text';
import { TEXTS } from '../../core/texts';

/**
 * Notice when no season is set as current (409 from `GET /api/me`, SAI-03).
 * Outside the shell, because navigation and pages require the current season.
 */
@Component({
  selector: 'app-no-season',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="login">
      <div class="card">
        <p class="eyebrow">{{ texts.appName }}</p>
        <h1>{{ texts.noSeason.title }}</h1>
        <p role="status" data-testid="no-season-notice">{{ texts.noSeason.notice }}</p>
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
  /** While logging out, further clicks are blocked (double click). */
  protected readonly loggingOut = signal(false);
  // eslint-disable-next-line @angular-eslint/no-input-rename -- query parameter `?ziel=` is part of the browser URL and stays German (NF-40)
  readonly target = input<string>(undefined, { alias: 'ziel' });

  /** Back to the target. The guard checks again and shows this notice again if needed. */
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
