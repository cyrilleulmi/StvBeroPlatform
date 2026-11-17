import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { environment } from '../../../environments/environment';
import { TEXTS } from '../../core/texts';

/** Login. The production method is open (O-01). In test mode a link to /dev-login. */
@Component({
  selector: 'app-login',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <main class="login">
      <div class="card">
        <p class="eyebrow">{{ texts.appName }}</p>
        <h1>{{ texts.login.title }}</h1>
        <p>{{ texts.login.methodOpen }}</p>
        @if (testMode) {
          <a
            class="button button-primary"
            routerLink="/dev-login"
            [queryParams]="target() ? { ziel: target() } : {}"
            >{{ texts.login.toTestMode }}</a
          >
        }
      </div>
    </main>
  `,
  styleUrl: './login.css',
})
export class Login {
  protected readonly texts = TEXTS;
  protected readonly testMode = environment.testMode;
  // eslint-disable-next-line @angular-eslint/no-input-rename -- query parameter `?ziel=` is part of the browser URL and stays German (NF-40)
  readonly target = input<string>(undefined, { alias: 'ziel' });
}
