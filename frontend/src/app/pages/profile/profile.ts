import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { errorText } from '../../core/error-text';
import { Icon } from '../../core/icon';
import { TEXTS } from '../../core/texts';
import { ThemeMode, ThemeService } from '../../core/theme.service';

/** One choice in the switch. `preview` is the value of `data-theme` in which the choice shows itself. */
interface Option {
  value: ThemeMode;
  text: string;
  preview: string | null;
}

/** Profile: name, theme (NF-06, NF-08), log out. */
@Component({
  selector: 'app-profile',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  templateUrl: './profile.html',
  styleUrl: './profile.css',
})
export class Profile {
  protected readonly auth = inject(AuthService);
  protected readonly theme = inject(ThemeService);
  private readonly router = inject(Router);
  protected readonly texts = TEXTS;
  protected readonly error = signal<string | null>(null);
  /** While logging out, further clicks are blocked (double click). */
  protected readonly loggingOut = signal(false);

  protected readonly options: Option[] = [
    { value: 'light', text: TEXTS.profile.light, preview: 'light' },
    { value: 'dune', text: TEXTS.profile.dune, preview: 'dune' },
    { value: 'github-dark', text: TEXTS.profile.githubDark, preview: 'github-dark' },
    { value: 'system', text: TEXTS.profile.system, preview: null },
  ];

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
