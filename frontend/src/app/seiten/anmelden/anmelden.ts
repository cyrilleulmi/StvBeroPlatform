import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { umgebung } from '../../../umgebung/umgebung';
import { TEXTE } from '../../kern/texte';

/** Anmeldung. Das produktive Verfahren ist offen (O-01). Im Testmodus Verweis auf /dev-login. */
@Component({
  selector: 'app-anmelden',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <main class="anmelden">
      <div class="karte">
        <p class="eyebrow">{{ texte.appName }}</p>
        <h1>{{ texte.anmelden.titel }}</h1>
        <p>{{ texte.anmelden.verfahrenOffen }}</p>
        @if (testmodus) {
          <a
            class="button button-primaer"
            routerLink="/dev-login"
            [queryParams]="ziel() ? { ziel: ziel() } : {}"
            >{{ texte.anmelden.zumTestmodus }}</a
          >
        }
      </div>
    </main>
  `,
  styleUrl: './anmelden.css',
})
export class Anmelden {
  protected readonly texte = TEXTE;
  protected readonly testmodus = umgebung.testmodus;
  readonly ziel = input<string>();
}
