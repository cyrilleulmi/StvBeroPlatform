import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { firstValueFrom } from 'rxjs';
import type { SuClubTeam, SuConnection, SuLink, SyncResult } from '../../api/types';
import { eventDate, clockTime } from '../../attendance/time';
import { errorText, fieldErrorsOf } from '../../core/error-text';
import { TEXTS } from '../../core/texts';

/**
 * Links of a team to swiss unihockey (SUH-02, SUH-11): the linked teams with the state of their last sync
 * (SUH-13), linking through a dialog that offers the teams of the club, unlinking and syncing right away
 * (SUH-03). Coaches and admins (E-84).
 */
@Component({
  selector: 'app-su-connection',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card form" aria-labelledby="su-title" data-testid="swiss-unihockey">
      <h2 id="su-title">{{ s.title }}</h2>
      <p class="secondary-text">{{ s.explanation }}</p>
      @if (success(); as m) {
        <p class="message-success" role="status">{{ m }}</p>
      }
      @if (error(); as m) {
        <p class="message-error" role="alert">{{ m }}</p>
      }
      <div [attr.aria-busy]="loading() ? 'true' : null">
        @if (loading()) {
          <p class="empty">{{ texts.common.loading }}</p>
        } @else if (links().length === 0) {
          <p data-testid="su-status">{{ s.notLinked }}</p>
        } @else {
          <ul class="list" data-testid="su-links" [attr.aria-label]="s.links">
            @for (link of links(); track link.id) {
              <li
                class="row link"
                [attr.aria-label]="label(link)"
                [attr.data-testid]="'su-link-' + link.suTeamId"
                [attr.data-sync]="syncState(link)"
              >
                <span class="link-text">
                  <span class="row-title">{{ label(link) }}</span>
                  <span class="secondary-text" data-testid="su-link-sync">{{ syncText(link) }}</span>
                  @if (link.lastSyncOk === false && link.lastSyncError) {
                    <span class="sync-error">{{ link.lastSyncError }}</span>
                  }
                </span>
                <button
                  type="button"
                  class="button button-secondary button-small"
                  [attr.aria-label]="s.unlinkFor(label(link))"
                  [disabled]="busy()"
                  [attr.aria-busy]="running() === 'unlink:' + link.id ? 'true' : null"
                  (click)="unlink(link)"
                >
                  {{ s.unlink }}
                </button>
              </li>
            }
          </ul>
        }
      </div>
      <div class="su-buttons">
        <button
          type="button"
          class="button button-secondary button-small"
          [disabled]="busy() || loading()"
          (click)="open()"
        >
          {{ s.add }}
        </button>
        @if (links().length > 0) {
          <button
            type="button"
            class="button button-primary button-small"
            [disabled]="busy()"
            [attr.aria-busy]="running() === 'sync' ? 'true' : null"
            (click)="sync()"
          >
            {{ s.sync }}
          </button>
        }
      </div>
    </section>

    <dialog
      #dialog
      class="card dialog"
      aria-modal="true"
      aria-labelledby="su-dialog-title"
      aria-describedby="su-dialog-text"
      data-testid="su-dialog"
    >
      <form class="form" (submit)="link($event)" novalidate>
        <h2 id="su-dialog-title">{{ s.dialogTitle(teamName()) }}</h2>
        <p class="secondary-text" id="su-dialog-text">{{ s.dialogText }}</p>
        @if (clubError(); as m) {
          <p class="message-error" role="alert" data-testid="su-club-error">{{ m }}</p>
        }
        <div [attr.aria-busy]="clubLoading() ? 'true' : null">
          @if (clubLoading()) {
            <p class="empty">{{ s.listLoading }}</p>
          } @else if (clubTeams().length > 0 && !manual()) {
            <div class="field">
              <label for="su-club-team">{{ s.choose }}</label>
              <select
                #select
                id="su-club-team"
                (change)="chosen.set($any($event.target).value)"
                [attr.aria-invalid]="fieldError() ? 'true' : null"
                [attr.aria-describedby]="fieldError() ? 'su-field-error' : null"
              >
                <option value="" [selected]="chosen() === ''">{{ s.choosePlaceholder }}</option>
                @for (team of clubTeams(); track team.suTeamId) {
                  <option
                    [value]="team.suTeamId"
                    [disabled]="!!team.linkedTeamId"
                    [selected]="chosen() === '' + team.suTeamId"
                  >
                    {{ team.name
                    }}{{ team.linkedTeamId ? ' (' + s.alreadyLinked(team.linkedTeamName ?? '') + ')' : '' }}
                  </option>
                }
              </select>
            </div>
          }
        </div>
        @if (!clubLoading()) {
          @if (clubTeams().length > 0) {
            <label class="manual-switch">
              <input
                type="checkbox"
                [checked]="manual()"
                (change)="manual.set($any($event.target).checked)"
              />
              <span>{{ s.manual }}</span>
            </label>
          }
          @if (manual() || clubTeams().length === 0) {
            <div class="field">
              <label for="su-team-id">{{ s.field }}</label>
              <input
                #idField
                id="su-team-id"
                type="text"
                inputmode="numeric"
                maxlength="10"
                autocomplete="off"
                [value]="suTeamId()"
                (input)="suTeamId.set($any($event.target).value)"
                [attr.aria-invalid]="fieldError() ? 'true' : null"
                [attr.aria-describedby]="fieldError() ? 'su-team-id-help su-field-error' : 'su-team-id-help'"
              />
              <p class="secondary-text" id="su-team-id-help">{{ s.fieldHelp }}</p>
            </div>
          }
        }
        @if (fieldError(); as m) {
          <p class="field-error" id="su-field-error">{{ m }}</p>
        }
        <div class="dialog-buttons">
          <button
            type="submit"
            class="button button-primary button-small"
            [disabled]="busy() || clubLoading()"
            [attr.aria-busy]="running() === 'link' ? 'true' : null"
          >
            {{ s.link }}
          </button>
          <button
            type="button"
            class="button button-secondary button-small"
            [disabled]="busy()"
            (click)="close()"
          >
            {{ texts.response.cancel }}
          </button>
        </div>
      </form>
    </dialog>
  `,
  styles: `
    :host {
      display: contents;
    }
    .su-buttons,
    .dialog-buttons {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
    }
    .link {
      align-items: flex-start;
    }
    .link-text {
      display: grid;
      gap: 2px;
      flex: 1 1 200px;
      min-width: 0;
    }
    .link[data-sync='failed'] [data-testid='su-link-sync'],
    .sync-error {
      color: var(--coral);
      font-weight: 600;
      font-size: 13px;
    }
    .manual-switch {
      display: flex;
      align-items: center;
      gap: 10px;
      min-height: var(--touch);
      font-weight: 600;
    }
    .manual-switch input {
      width: 20px;
      height: 20px;
    }
    select {
      min-height: var(--touch);
      padding: 0 12px;
      border: 1.5px solid var(--border);
      border-radius: var(--radius-icon);
      background: var(--surface);
      color: var(--text);
      font: 600 15px/1.2 var(--font);
      max-width: 100%;
    }
    .dialog {
      width: min(460px, calc(100vw - 32px));
      color: var(--text);
    }
    .dialog:not([open]) {
      display: none;
    }
    .dialog h2,
    .dialog p {
      margin: 0;
    }
    .dialog::backdrop {
      background: color-mix(in srgb, var(--bg) 80%, transparent);
    }
  `,
})
export class SuConnectionSection {
  private readonly http = inject(HttpClient);
  protected readonly texts = TEXTS;
  protected readonly s = TEXTS.swissUnihockey;

  readonly teamId = input.required<string>();
  readonly teamName = input.required<string>();
  /** The matches or the links changed: the page shows the team again. */
  readonly changed = output<void>();

  protected readonly connection = signal<SuConnection | null>(null);
  protected readonly links = computed(() => this.connection()?.links ?? []);
  protected readonly loading = signal(true);
  /** Which action is running: `link`, `sync` or `unlink:<linkId>`. */
  protected readonly running = signal<string | null>(null);
  protected readonly busy = computed(() => this.running() !== null);
  protected readonly error = signal<string | null>(null);
  protected readonly success = signal<string | null>(null);

  protected readonly clubTeams = signal<SuClubTeam[]>([]);
  protected readonly clubLoading = signal(false);
  protected readonly clubError = signal<string | null>(null);
  protected readonly chosen = signal('');
  protected readonly manual = signal(false);
  protected readonly suTeamId = signal('');
  protected readonly fieldError = signal<string | null>(null);

  private readonly dialog = viewChild<ElementRef<HTMLDialogElement>>('dialog');

  constructor() {
    effect(() => {
      const id = this.teamId();
      untracked(() => void this.load(id));
    });
  }

  protected label(link: SuLink): string {
    return this.s.linkName(link.name, link.suTeamId);
  }

  protected syncState(link: SuLink): 'ok' | 'failed' | 'never' {
    if (link.lastSyncOk === false) return 'failed';
    return link.lastSyncAt ? 'ok' : 'never';
  }

  protected syncText(link: SuLink): string {
    if (!link.lastSyncAt) return this.s.neverSynced;
    const time = `${eventDate(link.lastSyncAt, null)} ${clockTime(link.lastSyncAt)}`;
    return link.lastSyncOk === false ? this.s.lastSyncFailed(time) : this.s.lastSync(time);
  }

  private async load(teamId: string): Promise<void> {
    this.loading.set(true);
    try {
      this.connection.set(
        await firstValueFrom(this.http.get<SuConnection>(`/api/teams/${teamId}/swiss-unihockey`)),
      );
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.loading.set(false);
    }
  }

  /** Opens the dialog and loads the teams of the club; if that fails, the ID can be typed (SUH-09). */
  protected async open(): Promise<void> {
    this.success.set(null);
    this.error.set(null);
    this.fieldError.set(null);
    this.clubError.set(null);
    this.chosen.set('');
    this.suTeamId.set('');
    this.manual.set(false);
    this.clubTeams.set([]);
    this.clubLoading.set(true);
    const d = this.dialog()?.nativeElement;
    if (d && !d.open) d.showModal();
    try {
      this.clubTeams.set(await firstValueFrom(this.http.get<SuClubTeam[]>('/api/swiss-unihockey/teams')));
    } catch (e) {
      this.clubError.set(errorText(e));
    } finally {
      this.clubLoading.set(false);
    }
  }

  protected close(): void {
    this.dialog()?.nativeElement.close();
  }

  protected async link(domEvent: Event): Promise<void> {
    domEvent.preventDefault();
    if (this.busy()) return;
    const byHand = this.manual() || this.clubTeams().length === 0;
    const input = byHand ? this.suTeamId().trim() : this.chosen();
    if (!/^[1-9]\d{0,8}$/.test(input)) {
      this.fieldError.set(this.s.fieldRequired);
      return;
    }
    const id = Number(input);
    const name = this.clubTeams().find((team) => team.suTeamId === id)?.name ?? null;
    this.fieldError.set(null);
    this.running.set('link');
    try {
      const connection = await firstValueFrom(
        this.http.post<SuConnection>(`/api/teams/${this.teamId()}/swiss-unihockey/links`, {
          suTeamId: id,
          name,
        }),
      );
      this.connection.set(connection);
      const link = connection.links.find((l) => l.suTeamId === id);
      const label = link ? this.label(link) : this.s.linkName(name, id);
      this.close();
      this.success.set(link?.lastSyncOk === false ? this.s.linkedButFailed(label) : this.s.linked(label));
      this.changed.emit();
    } catch (e) {
      this.fieldError.set(fieldErrorsOf(e)['suTeamId'] ?? errorText(e));
    } finally {
      this.running.set(null);
    }
  }

  protected async unlink(link: SuLink): Promise<void> {
    await this.run(`unlink:${link.id}`, async () => {
      await firstValueFrom(
        this.http.delete(`/api/teams/${this.teamId()}/swiss-unihockey/links/${link.id}`),
      );
      return this.s.unlinked;
    });
  }

  /** Trigger a sync right away (SUH-03). On a failure the backend reports the reason (SUH-09). */
  protected async sync(): Promise<void> {
    await this.run('sync', async () => {
      const outcome = await firstValueFrom(
        this.http.post<SyncResult>(`/api/teams/${this.teamId()}/swiss-unihockey/sync`, null),
      );
      return this.s.synced(outcome);
    });
  }

  private async run(key: string, action: () => Promise<string>): Promise<void> {
    if (this.busy()) return;
    this.success.set(null);
    this.error.set(null);
    this.running.set(key);
    let message: string | null = null;
    try {
      message = await action();
    } catch (e) {
      this.error.set(errorText(e));
    }
    // Also after a failure: the links show when the last sync failed (SUH-13)
    try {
      this.connection.set(
        await firstValueFrom(this.http.get<SuConnection>(`/api/teams/${this.teamId()}/swiss-unihockey`)),
      );
    } catch {
      // the list stays as it was
    }
    this.running.set(null);
    if (message) {
      this.success.set(message);
      this.changed.emit();
    }
  }
}
