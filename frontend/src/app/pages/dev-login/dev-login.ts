import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { TestPerson } from '../../api/types';
import { AuthService, safeTarget } from '../../core/auth.service';
import { errorText } from '../../core/error-text';
import { Icon } from '../../core/icon';
import { TEXTS } from '../../core/texts';

/** Test mode login: all seed people, one click logs in (dev/test only). */
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

  /** Where to go after the login (`?ziel=`). */
  // eslint-disable-next-line @angular-eslint/no-input-rename -- query parameter `?ziel=` is part of the browser URL and stays German (NF-40)
  readonly target = input<string>(undefined, { alias: 'ziel' });

  protected readonly people = signal<TestPerson[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  /** Key of the person whose login is running; blocks further clicks (double click). */
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
