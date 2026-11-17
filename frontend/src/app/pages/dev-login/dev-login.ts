import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { TestPerson } from '../../api/types';
import { AnmeldungService, sicheresZiel } from '../../core/auth.service';
import { fehlertext } from '../../core/error-text';
import { Icon } from '../../core/icon';
import { TEXTE } from '../../core/texts';

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
  /** Schlüssel der Person, deren Anmeldung gerade läuft; sperrt weitere Klicks (Doppelklick). */
  protected readonly anmeldend = signal<string | null>(null);

  async ngOnInit(): Promise<void> {
    try {
      this.personen.set(await firstValueFrom(this.http.get<TestPerson[]>('/api/test/persons')));
    } catch (e) {
      this.fehler.set(fehlertext(e));
    } finally {
      this.laedt.set(false);
    }
  }

  protected async anmelden(person: TestPerson): Promise<void> {
    if (this.anmeldend()) return;
    this.anmeldend.set(person.loginKey);
    this.fehler.set(null);
    try {
      await this.anmeldung.alsTestpersonAnmelden(person.loginKey);
      await this.router.navigateByUrl(sicheresZiel(this.ziel()));
    } catch (e) {
      this.fehler.set(fehlertext(e));
    } finally {
      this.anmeldend.set(null);
    }
  }
}
