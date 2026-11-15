import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { AnmeldungService } from '../../kern/anmeldung.service';
import { TEXTE } from '../../kern/texte';

/** Meine Teams. Teams und Zugehörigkeiten kommen mit F-01; ohne Zugehörigkeit der leere Zustand. */
@Component({
  selector: 'app-teams',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>{{ texte.teams.titel }}</h1>
    <section class="karte" aria-labelledby="teams-liste">
      <h2 id="teams-liste" class="visuell-versteckt">{{ texte.teams.titel }}</h2>
      @if (zugehoerigkeiten().length === 0) {
        <p class="leer">{{ texte.teams.leer }}</p>
      } @else {
        <ul [attr.aria-label]="texte.teams.titel">
          @for (z of zugehoerigkeiten(); track z.team.id) {
            <li>{{ z.team.name }}</li>
          }
        </ul>
      }
    </section>
  `,
  styleUrl: '../seite.css',
})
export class Teams {
  private readonly anmeldung = inject(AnmeldungService);
  protected readonly texte = TEXTE;
  protected readonly zugehoerigkeiten = computed(
    () => this.anmeldung.ich()?.zugehoerigkeiten ?? [],
  );
}
