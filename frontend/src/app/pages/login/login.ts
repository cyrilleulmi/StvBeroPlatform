import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { environment } from '../../../environments/environment';
import { TEXTS } from '../../core/texts';

/** Anmeldung. Das produktive Verfahren ist offen (O-01). Im Testmodus Verweis auf /dev-login. */
@Component({
  selector: 'app-anmelden',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <main class="anmelden">
      <div class="karte">
        <p class="eyebrow">{{ texts.appName }}</p>
        <h1>{{ texts.login.title }}</h1>
        <p>{{ texts.login.methodOpen }}</p>
        @if (testMode) {
          <a
            class="button button-primaer"
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
  readonly target = input<string>(undefined, { alias: 'ziel' });
}
