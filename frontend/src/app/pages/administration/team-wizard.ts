import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { components } from '../../api/schema';
import type { Person, PersonSummary, Season, Team, TeamDetail, TeamRole } from '../../api/types';
import { SeriesNew } from '../../attendance/series-new';
import { AuthService } from '../../core/auth.service';
import { errorText, fieldErrorsOf } from '../../core/error-text';
import { name, teamRoleText } from '../../core/format';
import { Icon } from '../../core/icon';
import { TEXTS } from '../../core/texts';
import { EventVariantsSection } from './event-variants';
import { MatchDaySettingsForm } from './match-day-settings-form';
import { SuConnectionSection } from './su-connection';

type Overview = components['schemas']['SeriesOverview'];

/** The steps in order; their keys are the browser address (`?schritt=`, German, NF-40). */
const STEPS = ['name', 'personen', 'swissunihockey', 'trainings', 'spieltage', 'linien', 'fertig'] as const;
type Step = (typeof STEPS)[number];
const TEAM_ROLES: TeamRole[] = ['PLAYER', 'COACH'];

/**
 * Assistant «Neues Team» (`/verwaltung/teams/neu`, PTF-13): leads a coach through what a team needs, one step at a
 * time. The first step creates the team; every other step can be skipped and changed later on the team page. Every
 * step has its address (`?team=<id>&schritt=<key>`), so one can leave and come back.
 */
@Component({
  selector: 'app-team-wizard',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon, SeriesNew, MatchDaySettingsForm, SuConnectionSection, EventVariantsSection],
  template: `
    <div class="header">
      <a class="back" routerLink="/verwaltung/teams">{{ texts.teamAdmin.title }}</a>
      <h1>{{ w.title }}</h1>
      @if (detail(); as t) {
        <p class="secondary-text" data-testid="wizard-team">{{ t.name }}</p>
      } @else {
        <p class="secondary-text">{{ w.intro }}</p>
      }
    </div>
    @if (!auth.canManage()) {
      <p class="message-error" role="alert">{{ texts.common.noPermission }}</p>
    } @else {
      <nav class="steps-nav" [attr.aria-label]="w.stepsLabel">
        <ol class="steps" data-testid="wizard-steps">
          @for (s of steps; track s; let i = $index) {
            <li [attr.data-state]="s === step() ? 'current' : i < index() ? 'done' : 'todo'">
              @if (detail()) {
                <a
                  [routerLink]="[]"
                  [queryParams]="{ team: detail()!.id, schritt: s, saison: seasonParam() }"
                  [attr.aria-current]="s === step() ? 'step' : null"
                  [attr.data-testid]="'wizard-step-' + s"
                >
                  <span class="number" aria-hidden="true">
                    @if (i < index()) {
                      <app-icon name="check" [size]="14" />
                    } @else {
                      {{ i + 1 }}
                    }
                  </span>
                  <span class="label">{{ w.steps[s] }}</span>
                </a>
              } @else {
                <span class="step-off" [attr.aria-current]="s === step() ? 'step' : null" [attr.data-testid]="'wizard-step-' + s">
                  <span class="number" aria-hidden="true">{{ i + 1 }}</span>
                  <span class="label">{{ w.steps[s] }}</span>
                </span>
              }
            </li>
          }
        </ol>
        <p class="progress secondary-text" data-testid="wizard-progress">{{ w.progress(index() + 1, steps.length) }}</p>
      </nav>

      @if (message(); as m) {
        <p class="message-success" role="status">{{ m }}</p>
      }
      @if (error(); as m) {
        <p class="message-error" role="alert">{{ m }}</p>
      }

      <section class="card step" aria-labelledby="wizard-step-title" [attr.data-testid]="'wizard-' + step()">
        @switch (step()) {
          @case ('name') {
            <h2 id="wizard-step-title" class="section-title">{{ w.nameTitle }}</h2>
            <p class="secondary-text">{{ w.nameText }}</p>
            <form class="form" (submit)="saveName($event)" novalidate [attr.aria-label]="w.nameTitle">
              <div class="field">
                <label for="wizard-name">{{ w.name }}</label>
                <input
                  id="wizard-name"
                  name="wizard-name"
                  type="text"
                  maxlength="60"
                  autocomplete="off"
                  [value]="teamName()"
                  (input)="teamName.set($any($event.target).value)"
                  [attr.aria-invalid]="nameError() ? 'true' : null"
                  [attr.aria-describedby]="nameError() ? 'wizard-name-error' : null"
                />
                @if (nameError(); as m) {
                  <p class="field-error" id="wizard-name-error">{{ m }}</p>
                }
              </div>
              @if (!detail()) {
                <div class="field">
                  <label for="wizard-season">{{ w.season }}</label>
                  <select id="wizard-season" name="wizard-season" (change)="chosenSeason.set($any($event.target).value)">
                    @for (s of openSeasons(); track s.id) {
                      <option [value]="s.id" [selected]="s.id === season()?.id">{{ s.name }}</option>
                    }
                  </select>
                </div>
              }
              <button
                type="submit"
                class="button button-primary"
                data-testid="wizard-create"
                [disabled]="busy()"
                [attr.aria-busy]="busy() ? 'true' : null"
              >
                {{ detail() ? w.rename : w.create }}
              </button>
            </form>
          }
          @case ('personen') {
            <h2 id="wizard-step-title" class="section-title">{{ w.peopleTitle }}</h2>
            <p class="secondary-text">{{ w.peopleText }}</p>
            <div class="people">
              @for (block of [{ title: w.coaches, list: coaches(), id: 'coaches' }, { title: w.players, list: players(), id: 'players' }]; track block.id) {
                <div class="people-block" [attr.data-testid]="'wizard-' + block.id">
                  <h3>{{ block.title }} <span class="count">{{ block.list.length }}</span></h3>
                  @if (block.list.length === 0) {
                    <p class="empty">{{ w.nobody }}</p>
                  } @else {
                    <ul class="chips" [attr.aria-label]="block.title">
                      @for (p of block.list; track p.id) {
                        <li class="person">{{ name(p) }}</li>
                      }
                    </ul>
                  }
                </div>
              }
            </div>
            <form class="form add" (submit)="addMember($event)" novalidate [attr.aria-label]="texts.teamAdmin.addMemberTitle">
              <div class="field-row">
                <div class="field">
                  <label for="wizard-person">{{ texts.teamAdmin.person }}</label>
                  <select
                    id="wizard-person"
                    name="wizard-person"
                    (change)="newPerson.set($any($event.target).value)"
                    [attr.aria-invalid]="personError() ? 'true' : null"
                    [attr.aria-describedby]="personError() ? 'wizard-person-error' : null"
                  >
                    <option value="" [selected]="newPerson() === ''">{{ texts.teamAdmin.selectPerson }}</option>
                    @for (p of candidates(); track p.id) {
                      <option [value]="p.id" [selected]="newPerson() === p.id">{{ name(p) }}</option>
                    }
                  </select>
                  @if (personError(); as m) {
                    <p class="field-error" id="wizard-person-error">{{ m }}</p>
                  }
                </div>
                <div class="field">
                  <label for="wizard-role">{{ texts.teamAdmin.teamRole }}</label>
                  <select id="wizard-role" name="wizard-role" (change)="newRole.set($any($event.target).value)">
                    @for (r of roles; track r) {
                      <option [value]="r" [selected]="r === newRole()">{{ roleText(r) }}</option>
                    }
                  </select>
                </div>
              </div>
              <div class="buttons">
                <button
                  type="submit"
                  class="button button-secondary button-small"
                  data-testid="wizard-add-member"
                  [disabled]="busy()"
                  [attr.aria-busy]="busy() ? 'true' : null"
                >
                  {{ texts.teamAdmin.addMember }}
                </button>
                <span class="secondary-text">{{ w.peopleMissing }}</span>
                <a routerLink="/verwaltung/personen/neu" class="summary-link">{{ w.newPerson }}</a>
              </div>
            </form>
          }
          @case ('swissunihockey') {
            <h2 id="wizard-step-title" class="section-title">{{ w.suTitle }}</h2>
            <p class="secondary-text">{{ w.suText }}</p>
            @if (detail(); as t) {
              <app-su-connection [teamId]="t.id" [teamName]="t.name" (changed)="reloadTeam()" />
            }
          }
          @case ('trainings') {
            <h2 id="wizard-step-title" class="section-title">{{ w.trainingsTitle }}</h2>
            <p class="secondary-text">{{ w.trainingsText }}</p>
            @if (series().length === 0) {
              <p class="empty">{{ w.trainingsNone }}</p>
            } @else {
              <ul class="list" [attr.aria-label]="w.trainingsTitle" data-testid="wizard-series">
                @for (o of series(); track o.series.id) {
                  <li class="row">
                    {{ o.series.title }} · {{ day(o.series.weekday) }} {{ o.series.startTime }}–{{ o.series.endTime }}
                    <span class="secondary-text">· {{ texts.seriesCard.upcoming(o.upcoming) }}</span>
                  </li>
                }
              </ul>
            }
            @if (detail(); as t) {
              <app-series-new [teamId]="t.id" [season]="season()" [label]="texts.rework.newSeries" (created)="reloadSeries()" />
            }
          }
          @case ('spieltage') {
            <h2 id="wizard-step-title" class="section-title">{{ w.matchDaysTitle }}</h2>
            <p class="secondary-text">{{ w.matchDaysText }}</p>
            @if (detail(); as t) {
              <app-match-day-settings [teamId]="t.id" [settings]="t.matchDaySettings" (saved)="reloadTeam()" />
              <details class="more" data-testid="wizard-variants">
                <summary>{{ w.variantsTitle }}</summary>
                <p class="secondary-text">{{ w.variantsText }}</p>
                <app-event-variants [teamId]="t.id" />
              </details>
            }
          }
          @case ('linien') {
            <h2 id="wizard-step-title" class="section-title">{{ w.linesTitle }}</h2>
            <p class="secondary-text">{{ w.linesText }}</p>
            @if (players().length === 0) {
              <p class="message-notice">{{ w.linesNeedPlayers }}</p>
            } @else {
              <p data-testid="wizard-lines-state">{{ w.linesState(lined(), players().length) }}</p>
            }
            @if (detail(); as t) {
              <a class="button button-secondary button-small open-lines" [routerLink]="['/teams', t.id, 'linien']" data-testid="wizard-lines">
                {{ w.linesOpen }}
              </a>
            }
          }
          @case ('fertig') {
            <h2 id="wizard-step-title" class="section-title">{{ w.doneTitle }}</h2>
            <p class="secondary-text">{{ w.doneText }}</p>
            <ul class="list checklist" data-testid="wizard-checklist">
              @for (c of checklist(); track c.step) {
                <li class="row check" [attr.data-done]="c.done" [attr.data-testid]="'wizard-check-' + c.step">
                  <span class="mark" [attr.data-done]="c.done" aria-hidden="true">
                    <app-icon [name]="c.done ? 'check' : 'circle-dashed'" [size]="16" />
                  </span>
                  <span class="check-text">{{ c.text }}</span>
                  <span class="visually-hidden">{{ c.done ? w.done : w.open }}</span>
                  <a [routerLink]="[]" [queryParams]="{ team: detail()?.id, schritt: c.step, saison: seasonParam() }" class="summary-link">{{ w.edit }}</a>
                </li>
              }
            </ul>
            @if (detail(); as t) {
              <div class="buttons">
                <a class="button button-primary" [routerLink]="['/teams', t.id]" data-testid="wizard-to-team">{{ w.toTeam }}</a>
                <a class="button button-secondary" routerLink="/termine/ueberarbeiten" [queryParams]="{ team: t.id }">{{ w.toRework }}</a>
                <a class="button button-secondary" [routerLink]="['/verwaltung/teams', t.id]">{{ w.toAdmin }}</a>
              </div>
            }
          }
        }
      </section>

      @if (detail() && step() !== 'name' && step() !== 'fertig') {
        <div class="wizard-nav">
          <a class="button button-secondary" [routerLink]="[]" [queryParams]="params(previous())" data-testid="wizard-back">{{ w.back }}</a>
          <a class="button button-primary" [routerLink]="[]" [queryParams]="params(following())" data-testid="wizard-next">{{ w.next }}</a>
        </div>
      } @else if (detail() && step() === 'name') {
        <div class="wizard-nav">
          <span></span>
          <a class="button button-primary" [routerLink]="[]" [queryParams]="params('personen')" data-testid="wizard-next">{{ w.next }}</a>
        </div>
      }
    }
  `,
  styleUrl: '../page.css',
  styles: `
    .steps-nav {
      display: grid;
      gap: 6px;
    }
    .steps {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
    }
    .steps a,
    .step-off {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      min-height: var(--touch);
      padding: 4px 12px 4px 6px;
      border-radius: var(--radius-pill);
      border: 1.5px solid var(--border);
      background: var(--surface);
      color: var(--text);
      font-weight: 600;
      font-size: 14px;
      text-decoration: none;
    }
    .step-off {
      color: var(--muted);
    }
    .number {
      display: inline-grid;
      place-items: center;
      width: 26px;
      height: 26px;
      border-radius: 50%;
      background: var(--tag-grey-soft);
      font-weight: 800;
      font-size: 13px;
    }
    li[data-state='done'] .number {
      background: var(--tag-green-soft);
      color: var(--tag-green);
    }
    li[data-state='current'] a,
    li[data-state='current'] .step-off {
      border-color: var(--primary);
      background: var(--primary-soft);
      color: var(--text);
    }
    li[data-state='current'] .number {
      background: var(--primary);
      color: var(--on-primary);
    }
    .progress {
      margin: 0;
    }
    .step {
      display: grid;
      gap: 12px;
      min-width: 0;
    }
    .step > p,
    h3 {
      margin: 0;
    }
    .people {
      display: grid;
      gap: 12px;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 260px), 1fr));
    }
    .people-block {
      display: grid;
      gap: 8px;
      align-content: start;
    }
    h3 {
      font: 700 15px/1.3 var(--font);
    }
    .chips {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
    }
    .person {
      padding: 6px 12px;
      border-radius: var(--radius-pill);
      border: 1.5px solid var(--control-border);
      font-weight: 600;
      font-size: 14px;
    }
    .form {
      display: grid;
      gap: 12px;
    }
    .form > .button {
      justify-self: start;
    }
    .add {
      padding-top: 12px;
      border-top: 1px solid var(--border);
    }
    .buttons {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 8px 14px;
    }
    .more summary {
      cursor: pointer;
      font-weight: 700;
      min-height: var(--touch);
      display: list-item;
      list-style: revert;
      padding-top: 10px;
    }
    .more {
      display: grid;
      gap: 10px;
    }
    .open-lines {
      justify-self: start;
    }
    .check {
      display: flex;
      align-items: center;
      gap: 10px;
    }
    .check-text {
      flex: 1 1 auto;
      min-width: 0;
    }
    .mark {
      display: inline-grid;
      place-items: center;
      width: 26px;
      height: 26px;
      border-radius: 50%;
      background: var(--tag-grey-soft);
      color: var(--muted);
      flex: none;
    }
    .mark[data-done='true'] {
      background: var(--tag-green-soft);
      color: var(--tag-green);
    }
    .wizard-nav {
      display: flex;
      justify-content: space-between;
      gap: 12px;
    }
  `,
})
export class TeamWizard {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);
  protected readonly texts = TEXTS;
  protected readonly w = TEXTS.teamWizard;
  protected readonly steps = STEPS;
  protected readonly roles = TEAM_ROLES;
  protected readonly name = name;
  protected readonly roleText = teamRoleText;

  /** Query `?team=<id>`: the team that is being set up. */
  readonly team = input<string | undefined>(undefined);
  /** Query `?schritt=<key>`. */
  readonly schritt = input<string | undefined>(undefined);
  /** Query `?saison=<id>`; without it the current season. */
  readonly saison = input<string | undefined>(undefined);

  protected readonly seasons = signal<Season[]>([]);
  protected readonly persons = signal<Person[]>([]);
  protected readonly detail = signal<TeamDetail | null>(null);
  protected readonly series = signal<Overview[]>([]);
  protected readonly busy = signal(false);
  protected readonly message = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly teamName = signal('');
  protected readonly nameError = signal<string | null>(null);
  protected readonly chosenSeason = signal<string | null>(null);
  protected readonly newPerson = signal('');
  protected readonly newRole = signal<TeamRole>('PLAYER');
  protected readonly personError = signal<string | null>(null);

  protected readonly step = computed<Step>(() => {
    if (!this.team()) return 'name';
    const s = this.schritt();
    return (STEPS as readonly string[]).includes(s ?? '') ? (s as Step) : 'personen';
  });
  protected readonly index = computed(() => STEPS.indexOf(this.step()));
  protected readonly openSeasons = computed(() => this.seasons().filter((s) => !s.archived));
  protected readonly season = computed(() => {
    const id = this.saison() ?? this.chosenSeason();
    return this.seasons().find((s) => (id ? s.id === id : s.current)) ?? null;
  });
  /** `?saison=` only for another season than the current one. */
  protected readonly seasonParam = computed(() => {
    const s = this.season();
    return s && !s.current ? s.id : null;
  });

  protected readonly coaches = computed<PersonSummary[]>(() => this.detail()?.coaches ?? []);
  protected readonly players = computed<PersonSummary[]>(() => {
    const d = this.detail();
    if (!d) return [];
    const seen = new Map<string, PersonSummary>();
    for (const g of d.lineup.groups) for (const p of g.players) seen.set(p.id, p);
    for (const p of d.lineup.noLine) seen.set(p.id, p);
    const coachIds = new Set(this.coaches().map((c) => c.id));
    return [...seen.values()].filter((p) => !coachIds.has(p.id));
  });
  /** Players in a line (any group). */
  protected readonly lined = computed(() => {
    const d = this.detail();
    if (!d) return 0;
    const ids = new Set<string>();
    for (const g of d.lineup.groups) for (const p of g.players) ids.add(p.id);
    return ids.size;
  });
  protected readonly candidates = computed(() => {
    const members = new Set([...this.coaches(), ...this.players()].map((p) => p.id));
    return this.persons().filter((p) => !members.has(p.id));
  });
  protected readonly checklist = computed(() => {
    const d = this.detail();
    const settings = d?.matchDaySettings;
    return [
      { step: 'personen', done: this.coaches().length > 0 && this.players().length > 0, text: this.w.checkPeople(this.coaches().length, this.players().length) },
      { step: 'swissunihockey', done: !!d?.suTeamId, text: this.w.checkSu(!!d?.suTeamId) },
      { step: 'trainings', done: this.series().length > 0, text: this.w.checkTrainings(this.series().length) },
      { step: 'spieltage', done: true, text: this.w.checkMatchDays(settings?.matchDurationMinutes ?? 50, settings?.arrivalLeadMinutes ?? 70) },
      { step: 'linien', done: this.players().length > 0 && this.lined() === this.players().length, text: this.w.checkLines(this.lined(), this.players().length) },
    ];
  });

  constructor() {
    effect(() => {
      if (this.auth.canManage()) untracked(() => void this.loadBase());
    });
    effect(() => {
      const id = this.team();
      untracked(() => {
        if (id) void this.loadTeam(id);
        else this.detail.set(null);
      });
    });
    // A new step starts without the message of the last one
    effect(() => {
      this.step();
      untracked(() => {
        this.message.set(null);
        this.error.set(null);
      });
    });
  }

  protected day(d: string): string {
    return TEXTS.eventForm.weekdays[d as keyof typeof TEXTS.eventForm.weekdays];
  }

  protected previous(): Step {
    return STEPS[Math.max(0, this.index() - 1)];
  }

  protected following(): Step {
    return STEPS[Math.min(STEPS.length - 1, this.index() + 1)];
  }

  protected params(step: Step): Record<string, string | null> {
    return { team: this.detail()?.id ?? null, schritt: step, saison: this.seasonParam() };
  }

  private seasonQuery(): Record<string, string> {
    const s = this.season();
    return s ? { season: s.id } : {};
  }

  private async loadBase(): Promise<void> {
    try {
      const [seasons, persons] = await Promise.all([
        firstValueFrom(this.http.get<Season[]>('/api/seasons')),
        firstValueFrom(this.http.get<Person[]>('/api/persons')),
      ]);
      this.seasons.set(seasons);
      this.persons.set(persons);
    } catch (e) {
      this.error.set(errorText(e));
    }
  }

  private async loadTeam(id: string): Promise<void> {
    try {
      const params: Record<string, string> = this.saison() ? { season: this.saison()! } : {};
      const [team, series] = await Promise.all([
        firstValueFrom(this.http.get<TeamDetail>(`/api/teams/${id}`, { params })),
        firstValueFrom(this.http.get<Overview[]>('/api/series', { params: { team: id, ...params } })),
      ]);
      this.detail.set(team);
      this.series.set(series);
      if (!this.teamName()) this.teamName.set(team.name);
    } catch (e) {
      this.error.set(errorText(e));
    }
  }

  protected reloadTeam(): void {
    const id = this.team();
    if (id) void this.loadTeam(id);
  }

  protected reloadSeries(): void {
    this.reloadTeam();
  }

  /** Step 1: create the team, or rename it when coming back. */
  protected async saveName(domEvent: Event): Promise<void> {
    domEvent.preventDefault();
    if (this.busy()) return;
    const value = this.teamName().trim();
    if (!value) {
      this.nameError.set(TEXTS.teamAdmin.nameRequired);
      document.getElementById('wizard-name')?.focus();
      return;
    }
    this.nameError.set(null);
    this.busy.set(true);
    this.error.set(null);
    try {
      const existing = this.detail();
      if (existing) {
        await firstValueFrom(this.http.put(`/api/teams/${existing.id}`, { name: value }, { params: this.seasonQuery() }));
        this.message.set(this.w.renamed);
        this.reloadTeam();
      } else {
        const team = await firstValueFrom(this.http.post<Team>('/api/teams', { name: value }, { params: this.seasonQuery() }));
        await this.router.navigate([], {
          queryParams: { team: team.id, schritt: 'personen', saison: this.seasonParam() },
        });
        this.message.set(this.w.created(team.name));
      }
    } catch (e) {
      const field = fieldErrorsOf(e)['name'];
      if (field) this.nameError.set(field);
      else this.error.set(errorText(e));
    } finally {
      this.busy.set(false);
    }
  }

  /** Step 2: a coach or a player more (PTF-03). */
  protected async addMember(domEvent: Event): Promise<void> {
    domEvent.preventDefault();
    const team = this.detail();
    const personId = this.newPerson();
    if (!team || this.busy()) return;
    if (!personId) {
      this.personError.set(TEXTS.teamAdmin.personRequired);
      document.getElementById('wizard-person')?.focus();
      return;
    }
    this.personError.set(null);
    this.busy.set(true);
    this.error.set(null);
    try {
      await firstValueFrom(
        this.http.post(`/api/teams/${team.id}/members`, { personId, teamRole: this.newRole() }, { params: this.seasonQuery() }),
      );
      const person = this.persons().find((p) => p.id === personId);
      this.message.set(TEXTS.teamAdmin.added(person ? name(person) : '', teamRoleText(this.newRole()), team.name));
      this.newPerson.set('');
      // The next one is usually a player
      this.newRole.set('PLAYER');
      await this.loadTeam(team.id);
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.busy.set(false);
    }
  }
}
