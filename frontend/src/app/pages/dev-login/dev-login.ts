import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { TestPerson } from '../../api/types';
import { AuthService, safeTarget } from '../../core/auth.service';
import { errorText } from '../../core/error-text';
import { Icon } from '../../core/icon';
import { TEXTS } from '../../core/texts';

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
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly texts = TEXTS;

  /** Wohin nach der Anmeldung (`?ziel=`). */
  readonly target = input<string>(undefined, { alias: 'ziel' });

  protected readonly people = signal<TestPerson[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  /** Schlüssel der Person, deren Anmeldung gerade läuft; sperrt weitere Klicks (Doppelklick). */
  protected readonly loggingIn = signal<string | null>(null);

  async ngOnInit(): Promise<void> {
    try {
      this.people.set(await firstValueFrom(this.http.get<TestPerson[]>('/api/test/persons')));
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.loading.set(false);
    }
  }

  protected async login(person: TestPerson): Promise<void> {
    if (this.loggingIn()) return;
    this.loggingIn.set(person.loginKey);
    this.error.set(null);
    try {
      await this.auth.loginAsTestPerson(person.loginKey);
      await this.router.navigateByUrl(safeTarget(this.target()));
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.loggingIn.set(null);
    }
  }
}
