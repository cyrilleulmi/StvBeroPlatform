import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import type { PersonSummary, TeamRole } from '../../api/types';
import { name, teamRoleText } from '../../core/format';
import { Icon } from '../../core/icon';
import { TEXTS } from '../../core/texts';

export interface Member {
  person: PersonSummary;
  teamRole: TeamRole;
}

const TEAM_ROLES: TeamRole[] = ['PLAYER', 'COACH'];

/**
 * Coaches and players of a team (PTF-07) on `/verwaltung/teams/:teamId`, one row per person (E-110): the name, the
 * team role as a select and an icon button to take the person out of the team. In the archive only name and role.
 * The page decides who may do what and runs the requests.
 */
@Component({
  selector: 'app-team-members',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  template: `
    @for (block of blocks(); track block.id) {
      <section
        class="card"
        [attr.aria-labelledby]="'team-' + block.id + '-title'"
        [attr.data-testid]="'team-' + block.id"
      >
        <h2 [id]="'team-' + block.id + '-title'" class="section-title">
          {{ block.title }} <span class="count">{{ block.list.length }}</span>
        </h2>
        @if (block.list.length === 0) {
          <p class="empty">{{ block.empty }}</p>
        } @else {
          <ul class="list" [attr.aria-label]="block.title">
            @for (m of block.list; track m.person.id) {
              <li class="member" [attr.aria-label]="name(m.person)" [attr.data-testid]="'member-' + m.person.id">
                <span class="member-name">{{ name(m.person) }}</span>
                @if (readOnly()) {
                  <span class="chip" [attr.data-kind]="m.teamRole === 'COACH' ? 'coach' : null">{{
                    teamRole(m.teamRole)
                  }}</span>
                } @else {
                  <select
                    [attr.aria-label]="texts.teamAdmin.teamRoleOf(name(m.person))"
                    [disabled]="saving()"
                    (change)="teamRoleChange.emit({ member: m, teamRole: $any($event.target).value })"
                  >
                    @for (f of teamRoles; track f) {
                      <option [value]="f" [selected]="f === m.teamRole">{{ teamRole(f) }}</option>
                    }
                  </select>
                  @if (canRemove()) {
                    <button
                      type="button"
                      class="remove"
                      [attr.aria-label]="texts.teamAdmin.remove(name(m.person))"
                      [title]="texts.teamAdmin.remove(name(m.person))"
                      [disabled]="saving()"
                      [attr.aria-busy]="runningAction() === 'remove:' + m.person.id ? 'true' : null"
                      (click)="remove.emit(m)"
                    >
                      <app-icon name="user-minus" [size]="20" />
                    </button>
                  }
                }
              </li>
            }
          </ul>
        }
      </section>
    }
  `,
  styles: `
    :host {
      display: grid;
      gap: var(--section-gap);
      min-width: 0;
    }
    .section-title {
      margin-bottom: 4px;
    }
    .member {
      display: flex;
      align-items: center;
      gap: 8px;
      min-height: 56px;
      padding: 6px 0;
    }
    .member-name {
      flex: 1 1 auto;
      min-width: 0;
      overflow-wrap: anywhere;
      font-size: 16px;
      font-weight: 600;
    }
    select {
      flex: none;
      min-height: var(--touch);
      max-width: 9.5em;
      padding: 0 10px;
      border: 1.5px solid var(--control-border);
      border-radius: var(--radius-control);
      background: var(--surface);
      color: var(--text);
      font: 600 14px/1.2 var(--font);
    }
    /* Icon button (design «Shape»): no frame at rest, muted; the name is in aria-label and title. */
    .remove {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      flex: none;
      width: var(--touch);
      height: var(--touch);
      padding: 0;
      border: 1.5px solid transparent;
      border-radius: var(--radius-control);
      background: transparent;
      color: var(--muted);
      cursor: pointer;
    }
    .remove:disabled {
      opacity: 0.6;
      cursor: default;
    }
    @media (hover: hover) {
      .remove:not(:disabled):hover {
        border-color: var(--coral);
        background: var(--coral-soft);
        color: var(--coral);
      }
    }
  `,
})
export class TeamMembers {
  protected readonly texts = TEXTS;
  protected readonly name = name;
  protected readonly teamRole = teamRoleText;
  protected readonly teamRoles = TEAM_ROLES;

  readonly coaches = input.required<Member[]>();
  readonly players = input.required<Member[]>();
  /** Archive: only names and roles. */
  readonly readOnly = input(false);
  /** Whether the remove buttons are shown at all. */
  readonly canRemove = input(false);
  readonly saving = input(false);
  /** The running action of the page, e.g. `remove:<personId>`: only that button carries `aria-busy`. */
  readonly runningAction = input<string | null>(null);

  readonly teamRoleChange = output<{ member: Member; teamRole: TeamRole }>();
  readonly remove = output<Member>();

  /** Coaches and players separately (PTF-07). */
  protected readonly blocks = computed(() => [
    { title: TEXTS.teams.coaches, id: 'coaches', list: this.coaches(), empty: TEXTS.teams.noCoaches },
    { title: TEXTS.teams.players, id: 'players', list: this.players(), empty: TEXTS.teams.noPlayers },
  ]);
}
