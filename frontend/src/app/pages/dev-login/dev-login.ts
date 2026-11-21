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

/** A person in a group, with what the other groups say about her. */
interface Entry {
  person: TestPerson;
  coach: boolean;
  /** Names of her other teams, e.g. «auch in Team 2». */
  alsoIn: string[];
}

/** People shown together: the admins, one team, or the people without a team. */
interface Group {
  /** `admins`, `none` or the ID of the team: part of the test IDs and anchors. */
  key: string;
  title: string;
  entries: Entry[];
}

/**
 * Test mode login (dev/test only): the people of the data set grouped by team, coaches first and marked, one
 * click logs in. People in several teams appear in each of them. Also shows which data set is loaded and
 * switches between the club data and the test data (docs/test-data.md).
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

  protected readonly people = signal<TestPerson[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly success = signal<string | null>(null);
  /** Key of the person whose login is running; blocks further clicks (double click). */
  protected readonly loggingIn = signal<string | null>(null);
  protected readonly search = signal('');
  protected readonly dataSet = signal<DataSetState | null>(null);
  /** Data set that is being loaded right now. */
  protected readonly switching = signal<DataSet | null>(null);
  private poll: ReturnType<typeof setTimeout> | null = null;

  /** Admins first, then the teams by name with their coaches on top, then people without a team. */
  protected readonly groups = computed<Group[]>(() => {
    const people = this.people();
    const groups: Group[] = [];
    const admins = people.filter((p) => p.admin);
    if (admins.length > 0) {
      groups.push({
        key: 'admins',
        title: this.t.admins,
        entries: admins.map((person) => ({ person, coach: false, alsoIn: person.teams.map((m) => m.teamName) })),
      });
    }
    const teams = new Map<string, Group>();
    for (const person of people) {
      for (const membership of person.teams) {
        let group = teams.get(membership.teamId);
        if (!group) {
          group = { key: membership.teamId, title: membership.teamName, entries: [] };
          teams.set(membership.teamId, group);
        }
        group.entries.push({
          person,
          coach: membership.teamRole === 'COACH',
          alsoIn: person.teams.filter((m) => m.teamId !== membership.teamId).map((m) => m.teamName),
        });
      }
    }
    const byTitle = [...teams.values()].sort((a, b) => a.title.localeCompare(b.title, 'de'));
    for (const group of byTitle) {
      // Coaches on top; the API delivers the people by last name
      group.entries = [...group.entries.filter((e) => e.coach), ...group.entries.filter((e) => !e.coach)];
      groups.push(group);
    }
    const without = people.filter((p) => !p.admin && p.teams.length === 0);
    if (without.length > 0) {
      groups.push({
        key: 'none',
        title: this.t.noTeam,
        entries: without.map((person) => ({ person, coach: false, alsoIn: [] })),
      });
    }
    return groups;
  });

  /** The groups with the people that match the search (name, key or team). */
  protected readonly shown = computed<Group[]>(() => {
    const term = this.search().trim().toLowerCase();
    if (!term) return this.groups();
    return this.groups()
      .map((group) =>
        group.title.toLowerCase().includes(term)
          ? group
          : {
              ...group,
              entries: group.entries.filter((e) =>
                `${e.person.firstName} ${e.person.lastName} ${e.person.loginKey}`.toLowerCase().includes(term),
              ),
            },
      )
      .filter((group) => group.entries.length > 0);
  });

  protected readonly dataSets: DataSet[] = ['CLUB', 'TEST'];

  /** Per person the first group she appears in: there her entry carries the plain test ID. */
  private readonly firstGroups = computed(() => {
    const first = new Map<string, string>();
    for (const group of this.groups()) {
      for (const entry of group.entries) {
        if (!first.has(entry.person.id)) first.set(entry.person.id, group.key);
      }
    }
    return first;
  });

  protected firstGroupOf(person: TestPerson): string | undefined {
    return this.firstGroups().get(person.id);
  }

  /** Scrolls to a group and moves the focus to its heading. */
  protected jump(key: string): void {
    const heading = document.getElementById('gruppe-titel-' + key);
    if (!heading) return;
    heading.setAttribute('tabindex', '-1');
    heading.scrollIntoView({ block: 'start' });
    heading.focus({ preventScroll: true });
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
      this.search.set('');
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
