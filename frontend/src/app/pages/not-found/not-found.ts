import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TEXTE } from '../../core/texts';

@Component({
  selector: 'app-nicht-gefunden',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <h1>{{ texte.allgemein.nichtGefunden }}</h1>
    <p>
      <a routerLink="/">{{ texte.allgemein.zurStartseite }}</a>
    </p>
  `,
  styleUrl: '../page.css',
})
export class NichtGefunden {
  protected readonly texte = TEXTE;
}
