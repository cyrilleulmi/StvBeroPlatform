import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  OnInit,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { DataSet, DataSetState, TestPerson } from '../../api/types';
import { AuthService, safeTarget } from '../../core/auth.service';
import { errorText } from '../../core/error-text';
import { Icon } from '../../core/icon';
import { TEXTS } from '../../core/texts';
import { quickPersonas, rolesLine, searchPeople } from './quick-personas';

/**
 * Test mode login (dev/test only, E-112): on top the people most often needed for trying things out, chosen by role
 * so that it works in both data sets; below them everybody once in one list by name, with a search (`?suche=`). One
 * tap logs in. Small at the bottom: which data set is loaded, and the switch between the club data and the test
 * data (docs/test-data.md).
 */
@Component({
  selector: 'app-dev-login',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  templateUrl: './dev-login.html',
  styleUrl: './dev-login.css',
})
export class DevLogin implements OnInit, OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly texts = TEXTS;
  protected readonly t = TEXTS.devLogin;

  /** Where to go after the login (`?ziel=`). */
  // eslint-disable-next-line @angular-eslint/no-input-rename -- query parameter `?ziel=` is part of the browser URL and stays German (NF-40)
  readonly target = input<string>(undefined, { alias: 'ziel' });

  /** Query `?suche=`: filters the list by name while typing. */
  // eslint-disable-next-line @angular-eslint/no-input-rename -- query parameter `?suche=` is part of the browser URL and stays German (NF-40)
  readonly searchQuery = input<string | undefined>(undefined, { alias: 'suche' });

  protected readonly people = signal<TestPerson[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly success = signal<string | null>(null);
  /** Key of the person whose login is running; blocks further clicks (double click). */
  protected readonly loggingIn = signal<string | null>(null);
  protected readonly dataSet = signal<DataSetState | null>(null);
  /** Data set that is being loaded right now. */
  protected readonly switching = signal<DataSet | null>(null);
  private poll: ReturnType<typeof setTimeout> | null = null;

  protected readonly search = computed(() => this.searchQuery() ?? '');
  protected readonly quick = computed(() => quickPersonas(this.people()));
  protected readonly shown = computed(() => searchPeople(this.people(), this.search()));
  protected readonly dataSets: DataSet[] = ['CLUB', 'TEST'];

  protected roles(person: TestPerson): string {
    return rolesLine(person);
  }

  /** `data-role` of an entry: admin, coach (in any team) or player. */
  protected role(person: TestPerson): string {
    if (person.admin) return 'admin';
    return person.teams.some((m) => m.teamRole === 'COACH') ? 'coach' : 'player';
  }

  protected setSearch(text: string): void {
    void this.router.navigate([], {
      queryParams: { suche: text.trim() ? text : null },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  async ngOnInit(): Promise<void> {
    await Promise.all([this.loadPeople(), this.loadDataSet()]);
  }

  ngOnDestroy(): void {
    if (this.poll) clearTimeout(this.poll);
  }

  private async loadPeople(): Promise<void> {
    try {
      this.people.set(await firstValueFrom(this.http.get<TestPerson[]>('/api/test/persons')));
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.loading.set(false);
    }
  }

  /** While the club data is still loading from swiss unihockey, ask again every two seconds. */
  private async loadDataSet(): Promise<void> {
    if (this.poll) clearTimeout(this.poll);
    try {
      const state = await firstValueFrom(this.http.get<DataSetState>('/api/test/dataset'));
      this.dataSet.set(state);
      if (state.loading) {
        this.poll = setTimeout(() => void this.loadDataSet(), 2000);
      }
    } catch {
      // The display of the data set is an extra; the login works without it
    }
  }

  /** Empties the database and loads the other data set. */
  protected async load(dataSet: DataSet): Promise<void> {
    if (this.switching() || this.loggingIn()) return;
    this.switching.set(dataSet);
    this.error.set(null);
    this.success.set(null);
    try {
      await firstValueFrom(this.http.post('/api/test/reset', null, { params: { dataset: dataSet } }));
      this.setSearch('');
      await Promise.all([this.loadPeople(), this.loadDataSet()]);
      this.success.set(this.t.loaded[dataSet]);
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.switching.set(null);
    }
  }

  protected async login(person: TestPerson): Promise<void> {
    if (this.loggingIn() || this.switching()) return;
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
