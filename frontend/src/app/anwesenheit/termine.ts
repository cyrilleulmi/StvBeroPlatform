import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TEXTE } from '../kern/texte';

/** Kachel Anwesenheit: Terminliste. Termine kommen mit F-03; bis dahin der leere Zustand. */
@Component({
  selector: 'app-termine',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>{{ texte.termine.titel }}</h1>
    <section class="karte" aria-labelledby="termine-kommend">
      <h2 id="termine-kommend" class="visuell-versteckt">Kommende Termine</h2>
      <p class="leer">{{ texte.termine.leer }}</p>
    </section>
  `,
  styleUrl: '../seiten/seite.css',
})
export class Termine {
  protected readonly texte = TEXTE;
}
