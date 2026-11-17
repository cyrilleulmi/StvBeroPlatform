import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type {
  SyncResult,
  TeamRole,
  Person,
  PersonSummary,
  Season,
  TeamDetail,
  TeamMembership,
} from '../../api/types';
import { AuthService } from '../../core/auth.service';
import { errorStatus, errorText } from '../../core/error-text';
import { teamRoleText, name } from '../../core/format';
import { TEXTS } from '../../core/texts';

interface Member {
  person: PersonSummary;
  teamRole: TeamRole;
}

const TEAM_ROLES: TeamRole[] = ['PLAYER', 'COACH'];

/**
 * Manage a team (admin, PTF-11): rename, add people with a team role (PTF-03, PTF-04),
 * change the team role, remove. `?saison=` selects the season; the archive is read-only (SAI-02).
 */
@Component({
  selector: 'app-team-admin',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  templateUrl: './team-admin.html',
  styleUrl: '../page.css',
  styles: `
    .team-link,
    form .button {
      justify-self: start;
    }
    .su-buttons {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
    }
    .member .row-title {
      min-width: 10em;
    }
    .member select {
      min-height: var(--touch);
      padding: 0 12px;
      border: 1.5px solid var(--border);
      border-radius: var(--radius-icon);
      background: var(--surface);
      color: var(--text);
      font: 600 14px/1.2 var(--font);
    }
  `,
})
export class TeamAdmin {
  private readonly http = inject(HttpClient);
  protected readonly auth = inject(AuthService);
  protected readonly texts = TEXTS;
  protected readonly name = name;
  protected readonly teamRole = teamRoleText;
  protected readonly teamRoles = TEAM_ROLES;

  /** Route `/verwaltung/teams/:teamId` */
  readonly teamId = input.required<string>();
  /** Query `?saison=`; without it the current season. */
  // eslint-disable-next-line @angular-eslint/no-input-rename -- query parameter `?saison=` is part of the browser URL and stays German (NF-40)
  readonly season = input<string | undefined>(undefined, { alias: 'saison' });

  protected readonly team = signal<TeamDetail | null>(null);
  protected readonly seasonData = signal<Season | null>(null);
  protected readonly people = signal<Person[]>([]);
  protected readonly loading = signal(true);
  /**
   * Which action is running: `rename`, `addMember`, `team-role:<personId>`,
   * `remove:<personId>`, `link`, `unlink` or `sync`. Only its button carries `aria-busy`, all carry `disabled`.
   */
  protected readonly runningAction = signal<string | null>(null);
  protected readonly saving = computed(() => this.runningAction() !== null);
  protected readonly error = signal<string | null>(null);
  protected readonly success = signal<string | null>(null);
  protected readonly readOnly = computed(() => this.seasonData()?.archived === true);

  protected readonly teamname = signal('');
  protected readonly teamnameError = signal<string | null>(null);
  protected readonly newPerson = signal('');
  protected readonly newTeamRole = signal<TeamRole>('PLAYER');
  protected readonly personError = signal<string | null>(null);
  protected readonly suTeamId = signal('');
  protected readonly suTeamIdError = signal<string | null>(null);

  /** Coaches and players separately (PTF-07). */
  protected readonly coaches = computed<Member[]>(() =>
    (this.team()?.coaches ?? []).map((person) => ({ person, teamRole: 'COACH' })),
  );
  protected readonly players = computed<Member[]>(() => {
    const t = this.team();
    if (!t) return [];
    return [...t.lineup.groups.flatMap((g) => g.players), ...t.lineup.noLine].map(
      (person) => ({
        person,
        teamRole: 'PLAYER',
      }),
    );
  });
  private readonly memberIds = computed(
    () => new Set([...this.coaches(), ...this.players()].map((m) => m.person.id)),
  );

  constructor() {
    effect(() => {
      const teamId = this.teamId();
      const seasonId = this.season();
      if (this.auth.canManage()) {
        untracked(() => void this.load(teamId, seasonId));
      }
    });
  }

  protected isMember(personId: string): boolean {
    return this.memberIds().has(personId);
  }

  private params(): Record<string, string> {
    const id = this.seasonData()?.id ?? this.season();
    return id ? { season: id } : {};
  }

  private async load(teamId: string, seasonId: string | undefined): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      const params: Record<string, string> = seasonId ? { season: seasonId } : {};
      const [seasons, team, people] = await Promise.all([
        firstValueFrom(this.http.get<Season[]>('/api/seasons')),
        firstValueFrom(this.http.get<TeamDetail>(`/api/teams/${teamId}`, { params })),
        firstValueFrom(this.http.get<Person[]>('/api/persons')),
      ]);
      this.seasonData.set(seasons.find((s) => (seasonId ? s.id === seasonId : s.current)) ?? null);
      this.team.set(team);
      this.teamname.set(team.name);
      this.suTeamId.set(team.suTeamId != null ? String(team.suTeamId) : '');
      this.people.set(people);
    } catch (e) {
      this.team.set(null);
      this.error.set(errorStatus(e) === 404 ? TEXTS.teams.notFound : errorText(e));
    } finally {
      this.loading.set(false);
    }
  }

  private async reload(): Promise<void> {
    const t = await firstValueFrom(
      this.http.get<TeamDetail>(`/api/teams/${this.teamId()}`, { params: this.params() }),
    );
    this.team.set(t);
  }

  private async run(key: string, action: () => Promise<string>): Promise<void> {
    if (this.saving()) return;
    this.success.set(null);
    this.error.set(null);
    this.runningAction.set(key);
    try {
      const message = await action();
      await this.reload();
      this.success.set(message);
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.runningAction.set(null);
    }
  }

  protected rename(domEvent: Event): void {
    domEvent.preventDefault();
    const newName = this.teamname().trim();
    if (!newName) {
      this.teamnameError.set(TEXTS.teamAdmin.nameRequired);
      return;
    }
    this.teamnameError.set(null);
    void this.run('rename', async () => {
      await firstValueFrom(
        this.http.put(
          `/api/teams/${this.teamId()}`,
          { name: newName },
          { params: this.params() },
        ),
      );
      return TEXTS.teamAdmin.renamed;
    });
  }

  protected addMember(domEvent: Event): void {
    domEvent.preventDefault();
    const personId = this.newPerson();
    if (!personId) {
      this.personError.set(TEXTS.teamAdmin.personRequired);
      return;
    }
    this.personError.set(null);
    const teamRole = this.newTeamRole();
    void this.run('addMember', async () => {
      const z = await firstValueFrom(
        this.http.post<TeamMembership>(
          `/api/teams/${this.teamId()}/members`,
          { personId, teamRole: teamRole },
          { params: this.params() },
        ),
      );
      this.newPerson.set('');
      this.newTeamRole.set('PLAYER');
      return TEXTS.teamAdmin.added(
        name(z.person),
        teamRoleText(z.teamRole),
        z.team.name,
      );
    });
  }

  protected changeTeamRole(m: Member, teamRole: TeamRole): void {
    void this.run(`team-role:${m.person.id}`, async () => {
      await firstValueFrom(
        this.http.put(
          `/api/teams/${this.teamId()}/members/${m.person.id}`,
          { teamRole: teamRole },
          { params: this.params() },
        ),
      );
      return TEXTS.teamAdmin.teamRoleChanged(name(m.person), teamRoleText(teamRole));
    });
  }

  protected remove(m: Member): void {
    void this.run(`remove:${m.person.id}`, async () => {
      await firstValueFrom(
        this.http.delete(`/api/teams/${this.teamId()}/members/${m.person.id}`, {
          params: this.params(),
        }),
      );
      return TEXTS.teamAdmin.removed(name(m.person));
    });
  }

  /** Link with swiss unihockey (SUH-02). */
  protected link(domEvent: Event): void {
    domEvent.preventDefault();
    const input = this.suTeamId().trim();
    if (!/^[1-9]\d{0,8}$/.test(input)) {
      this.suTeamIdError.set(TEXTS.swissUnihockey.fieldRequired);
      return;
    }
    this.suTeamIdError.set(null);
    const id = Number(input);
    void this.run('link', async () => {
      await firstValueFrom(
        this.http.put(`/api/teams/${this.teamId()}/swiss-unihockey`, { suTeamId: id }),
      );
      return TEXTS.swissUnihockey.linked(id);
    });
  }

  protected unlink(): void {
    this.suTeamIdError.set(null);
    void this.run('unlink', async () => {
      await firstValueFrom(
        this.http.put(`/api/teams/${this.teamId()}/swiss-unihockey`, { suTeamId: null }),
      );
      this.suTeamId.set('');
      return TEXTS.swissUnihockey.unlinked;
    });
  }

  /** Trigger a sync right away (SUH-03). On a failure the backend reports the reason (SUH-09). */
  protected sync(): void {
    void this.run('sync', async () => {
      const outcome = await firstValueFrom(
        this.http.post<SyncResult>(
          `/api/teams/${this.teamId()}/swiss-unihockey/sync`,
          null,
        ),
      );
      return TEXTS.swissUnihockey.synced(outcome);
    });
  }
}
