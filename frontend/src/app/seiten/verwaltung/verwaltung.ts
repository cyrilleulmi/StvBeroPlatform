import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { AnmeldungService } from '../../kern/anmeldung.service';
import { TEXTE } from '../../kern/texte';

/** Verwaltung (nur Admins, RR-04/RR-05). Die Verwaltungs-Screens kommen mit F-01. */
@Component({
  selector: 'app-verwaltung',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>{{ texte.verwaltung.titel }}</h1>
    @if (anmeldung.istAdmin()) {
      <section class="karte" aria-labelledby="verwaltung-bereiche">
        <h2 id="verwaltung-bereiche" class="visuell-versteckt">{{ texte.verwaltung.titel }}</h2>
        <p class="leer">{{ texte.verwaltung.leer }}</p>
      </section>
    } @else {
      <p class="meldung-fehler" role="alert">{{ texte.allgemein.keineBerechtigung }}</p>
    }
  `,
  styleUrl: '../seite.css',
})
export class Verwaltung {
  protected readonly anmeldung = inject(AnmeldungService);
  protected readonly texte = TEXTE;
}
