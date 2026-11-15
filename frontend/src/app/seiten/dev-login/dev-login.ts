import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { TestPerson } from '../../api/typen';
import { AnmeldungService, sicheresZiel } from '../../kern/anmeldung.service';
import { fehlertext } from '../../kern/fehlertext';
import { Icon } from '../../kern/icon';
import { TEXTE } from '../../kern/texte';

/** Testmodus-Anmeldung: alle Seed-Personen, ein Klick meldet an (nur dev/test). */
@Component({
  selector: 'app-dev-login',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  templateUrl: './dev-login.html',
  styleUrl: './dev-login.css',
})
export class DevLogin implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly anmeldung = inject(AnmeldungService);
  private readonly router = inject(Router);
  protected readonly texte = TEXTE;

  /** Wohin nach der Anmeldung (`?ziel=`). */
  readonly ziel = input<string>();

  protected readonly personen = signal<TestPerson[]>([]);
  protected readonly laedt = signal(true);
  protected readonly fehler = signal<string | null>(null);

  async ngOnInit(): Promise<void> {
    try {
      this.personen.set(await firstValueFrom(this.http.get<TestPerson[]>('/api/test/personen')));
    } catch (e) {
      this.fehler.set(fehlertext(e));
    } finally {
      this.laedt.set(false);
    }
  }

  protected async anmelden(person: TestPerson): Promise<void> {
    this.fehler.set(null);
    try {
      await this.anmeldung.alsTestpersonAnmelden(person.schluessel);
      await this.router.navigateByUrl(sicheresZiel(this.ziel()));
    } catch (e) {
      this.fehler.set(fehlertext(e));
    }
  }
}
