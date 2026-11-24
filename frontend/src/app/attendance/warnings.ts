import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import type { Warning } from '../api/types';
import { Icon } from '../core/icon';
import { TEXTS } from '../core/texts';

/** German text of a warning (F-16). `calledUp`: call-up instead of responses. */
export function warningText(w: Warning, calledUp: boolean, withTeam: boolean): string {
  const t = TEXTS.warnings;
  const verb = calledUp ? t.calledUp : t.attending;
  let text: string;
  switch (w.type) {
    case 'GOALIES':
      text = t.goalies(w.count, verb);
      break;
    case 'FIELD_PLAYERS':
      text = t.fieldPlayers(w.count, verb);
      break;
    // Matches changed or no longer delivered by swiss unihockey belong to the match day, not to a team (SUH-12)
    case 'MATCH_CHANGED':
      return t.matchChanged(w.count);
    case 'MATCH_MISSING':
      return t.matchMissing(w.count);
    default:
      text = t.line(w.groupName ?? '', w.count);
  }
  return withTeam ? t.withTeam(w.team.name, text) : text;
}

/**
 * Warnings of an event or a call-up (F-16): each with an icon and a text in `accent-mustard`, not only a colour.
 * The list is a live region, so screen readers announce changes (e.g. while selecting the call-up). Without
 * warnings nothing is shown, but the live region stays.
 */
@Component({
  selector: 'app-warnings',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  template: `
    <div
      aria-live="polite"
      [attr.data-testid]="testId()"
      [attr.data-warning-count]="warnings().length"
    >
      @if (items().length > 0) {
        <section class="warnings" [attr.aria-labelledby]="testId() + '-title'">
          <h3 class="warnings-title" [id]="testId() + '-title'">{{ title() }}</h3>
          <ul class="warning-list" [attr.aria-labelledby]="testId() + '-title'">
            @for (w of items(); track w.key) {
              <li class="warning" data-testid="warning" [attr.data-warning]="w.type">
                <span class="warning-icon" aria-hidden="true"
                  ><app-icon name="triangle-alert" [size]="16"
                /></span>
                <span>{{ w.text }}</span>
              </li>
            }
          </ul>
        </section>
      }
    </div>
  `,
  styles: `
    .warnings {
      display: grid;
      gap: 6px;
      padding: 10px 12px;
      border-radius: var(--radius-icon);
      background: var(--mustard-soft);
      border: 1px solid var(--mustard);
      color: var(--text);
    }
    .warnings-title {
      margin: 0;
      font-size: 13px;
      font-weight: 800;
      color: var(--mustard);
    }
    .warning-list {
      list-style: none;
      margin: 0;
      padding: 0;
      display: grid;
      gap: 4px;
    }
    .warning {
      display: flex;
      align-items: flex-start;
      gap: 8px;
      font-size: 14px;
      font-weight: 600;
    }
    .warning-icon {
      color: var(--mustard);
      padding-top: 2px;
    }
  `,
})
export class Warnings {
  readonly warnings = input.required<Warning[]>();
  /** Call-up: «aufgeboten» instead of «angemeldet». */
  readonly calledUp = input(false);
  /** Event with several teams: the team name in front of each warning. */
  readonly withTeam = input(false);
  readonly testId = input.required<string>();
  readonly title = input<string>(TEXTS.warnings.title);

  protected readonly items = computed(() =>
    this.warnings().map((w, i) => ({
      key: `${w.team.id}-${w.type}-${w.groupId ?? i}`,
      type: w.type,
      text: warningText(w, this.calledUp(), this.withTeam()),
    })),
  );
}
