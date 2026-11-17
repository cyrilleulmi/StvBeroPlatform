import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { Group, PersonSummary, TeamDetail } from '../../api/types';
import { AuthService } from '../../core/auth.service';
import { errorStatus, errorText } from '../../core/error-text';
import { name } from '../../core/format';
import { Icon } from '../../core/icon';
import { TEXTS } from '../../core/texts';

/** A group on the screen: line, goalies or «Ohne Linie» (`id` = null). */
interface DisplayGroup {
  id: string | null;
  kind: Group['type'] | 'NO_LINE';
  name: string;
  players: PersonSummary[];
  /** Position among the lines (0-based), otherwise -1. */
  index: number;
}

/** Value in the select field for «Ohne Linie». */
const NO_LINE = '';

/**
 * Lines of a team (LIN-07): create, rename, sort and delete lines and assign players by
 * drag & drop or select field. Both save immediately. Usable without drag & drop
 * (phone, keyboard, docs/testability.md rule 7). Members without the coach team role see the
 * lineup read-only.
 */
@Component({
  selector: 'app-lines',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon, NgTemplateOutlet],
  templateUrl: './lines.html',
  styleUrls: ['../page.css', './lines.css'],
})
export class Lines {
  private readonly http = inject(HttpClient);
  private readonly injector = inject(Injector);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  protected readonly auth = inject(AuthService);
  protected readonly texts = TEXTS;
  protected readonly name = name;
  protected readonly noLineValue = NO_LINE;

  /** Route `/teams/:teamId/linien` */
  readonly teamId = input.required<string>();

  protected readonly team = signal<TeamDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly loadError = signal<string | null>(null);
  protected readonly success = signal<string | null>(null);
  /**
   * Running action: `add`, `assign:<personId>`, `up:<id>`, `down:<id>`,
   * `rename:<id>`, `delete:<id>`. Only the triggering element carries `aria-busy`, all carry `disabled`.
   */
  protected readonly runningAction = signal<string | null>(null);
  protected readonly saving = computed(() => this.runningAction() !== null);

  /** Line that is being renamed, and the entered name. */
  protected readonly renameId = signal<string | null>(null);
  protected readonly newName = signal('');
  protected readonly nameError = signal<string | null>(null);

  /** Line whose deletion is to be confirmed. */
  protected readonly toDelete = signal<DisplayGroup | null>(null);
  private readonly deleteDialog = viewChild<ElementRef<HTMLDialogElement>>('deleteDialog');

  /** Drag & drop: dragged player and group under the pointer. */
  protected readonly dragged = signal<PersonSummary | null>(null);
  protected readonly dropTarget = signal<string | null>(null);

  /** Coaches of any team and admins (LIN-07, RR-03, E-69). */
  protected readonly canEdit = computed(
    () => this.auth.canManage() || this.team()?.myTeamRole === 'COACH',
  );

  /** Lines in order, then goalies, then «Ohne Linie» (LIN-06, LIN-09). */
  protected readonly groups = computed<DisplayGroup[]>(() => {
    const t = this.team();
    if (!t) return [];
    let line = 0;
    const groups: DisplayGroup[] = t.lineup.groups.map((g) => ({
      id: g.id,
      kind: g.type,
      name: g.name,
      players: g.players,
      index: g.type === 'LINE' ? line++ : -1,
    }));
    groups.push({
      id: null,
      kind: 'NO_LINE',
      name: TEXTS.lines.noLine,
      players: t.lineup.noLine,
      index: -1,
    });
    return groups;
  });
  protected readonly lines = computed(() => this.groups().filter((g) => g.kind === 'LINE'));
  protected readonly others = computed(() => this.groups().filter((g) => g.kind !== 'LINE'));

  /** Options in the field «Linie von …»: all groups in display order, then «Ohne Linie». */
  protected readonly options = computed(() =>
    this.groups().map((g) => ({ value: g.id ?? NO_LINE, name: g.name })),
  );

  constructor() {
    effect(() => {
      const teamId = this.teamId();
      untracked(() => void this.load(teamId));
    });
  }

  protected testId(g: DisplayGroup): string {
    return g.id ? `group-${g.id}` : 'group-no-line';
  }

  protected titleId(g: DisplayGroup): string {
    return g.id ? `group-title-${g.id}` : 'group-title-no-line';
  }

  private async load(teamId: string): Promise<void> {
    this.loading.set(true);
    this.loadError.set(null);
    const me = this.auth.me();
    if (me && !this.auth.canManage() && !me.memberships.some((z) => z.team.id === teamId)) {
      // Without membership do not even ask; the backend refuses (403, RR-07).
      this.team.set(null);
      this.loadError.set(TEXTS.common.noPermission);
      this.loading.set(false);
      return;
    }
    try {
      this.team.set(await firstValueFrom(this.http.get<TeamDetail>(`/api/teams/${teamId}`)));
    } catch (e) {
      this.team.set(null);
      this.loadError.set(errorStatus(e) === 404 ? TEXTS.teams.notFound : errorText(e));
    } finally {
      this.loading.set(false);
    }
  }

  private async reload(): Promise<void> {
    this.team.set(await firstValueFrom(this.http.get<TeamDetail>(`/api/teams/${this.teamId()}`)));
  }

  /**
   * Runs a change, reloads the lineup and reports the result. `focus`: IDs of the elements that
   * should get the focus afterwards; the first one that exists and is not disabled.
   * `onError`: shows an error itself (e.g. at the field) instead of at the top of the page.
   */
  private async run(
    key: string,
    action: () => Promise<string>,
    focus?: string | string[],
    onError?: (message: string) => void,
  ): Promise<boolean> {
    if (this.saving()) return false;
    this.success.set(null);
    this.error.set(null);
    this.runningAction.set(key);
    let ok = false;
    try {
      const message = await action();
      await this.reload();
      this.success.set(message);
      ok = true;
    } catch (e) {
      if (onError) onError(errorText(e));
      else this.error.set(errorText(e));
      // On an error show the actual state (e.g. changed at the same time).
      await this.reload().catch(() => undefined);
    } finally {
      this.runningAction.set(null);
    }
    if (focus) {
      this.focusElement(typeof focus === 'string' ? [focus] : focus);
    }
    return ok;
  }

  /** After the next render focus the first existing element that is not disabled. */
  private focusElement(ids: string[]): void {
    afterNextRender(
      () => {
        for (const id of ids) {
          const el = this.host.nativeElement.querySelector<HTMLElement>(`#${id}`);
          if (el && !(el as HTMLButtonElement).disabled) {
            el.focus();
            return;
          }
        }
      },
      { injector: this.injector },
    );
  }

  /** Assign a player to a group; empty `groupId` = no line (LIN-02, LIN-06, LIN-07). */
  protected assign(person: PersonSummary, groupId: string, focus = true): void {
    const target = this.groups().find((g) => (g.id ?? NO_LINE) === groupId);
    const current = this.groups().find((g) => g.players.some((p) => p.id === person.id));
    if (!target || target === current) return;
    void this.run(
      `assign:${person.id}`,
      async () => {
        await firstValueFrom(
          this.http.put(`/api/teams/${this.teamId()}/assignments/${person.id}`, {
            groupId: target.id,
          }),
        );
        return target.id
          ? TEXTS.lines.assigned(name(person), target.name)
          : TEXTS.lines.noLineSet(name(person));
      },
      focus ? `assignment-${person.id}` : undefined,
    );
  }

  /** New line at the end with the next free name «Linie n» (LIN-07, LIN-09). */
  protected add(): void {
    const names = new Set(this.groups().map((g) => g.name.toLowerCase()));
    let nr = this.lines().length + 1;
    while (names.has(TEXTS.lines.newLine(nr).toLowerCase())) nr++;
    const name = TEXTS.lines.newLine(nr);
    void this.run('add', async () => {
      await firstValueFrom(this.http.post(`/api/teams/${this.teamId()}/groups`, { name }));
      return TEXTS.lines.added(name);
    });
  }

  /** Move a line one position up or down (LIN-09). */
  protected move(g: DisplayGroup, direction: -1 | 1): void {
    const target = g.index + 1 + direction;
    if (!g.id || target < 1 || target > this.lines().length) return;
    // If the line ends up at the edge, this button is disabled afterwards: then focus the opposite button.
    const button = `${direction < 0 ? 'up' : 'down'}-${g.id}`;
    const otherButton = `${direction < 0 ? 'down' : 'up'}-${g.id}`;
    void this.run(
      `${direction < 0 ? 'up' : 'down'}:${g.id}`,
      async () => {
        await firstValueFrom(
          this.http.put(`/api/teams/${this.teamId()}/groups/${g.id}`, {
            name: g.name,
            sortOrder: target,
          }),
        );
        return TEXTS.lines.moved(g.name);
      },
      [button, otherButton],
    );
  }

  protected startRename(g: DisplayGroup): void {
    this.renameId.set(g.id);
    this.newName.set(g.name);
    this.nameError.set(null);
    afterNextRender(
      () => this.host.nativeElement.querySelector<HTMLInputElement>(`#name-${g.id}`)?.select(),
      { injector: this.injector },
    );
  }

  protected cancelRename(g: DisplayGroup): void {
    this.renameId.set(null);
    this.nameError.set(null);
    afterNextRender(
      () => this.host.nativeElement.querySelector<HTMLElement>(`#rename-${g.id}`)?.focus(),
      { injector: this.injector },
    );
  }

  protected async rename(domEvent: Event, g: DisplayGroup): Promise<void> {
    domEvent.preventDefault();
    const newValue = this.newName().trim();
    if (!newValue) {
      this.nameError.set(TEXTS.lines.nameRequired);
      return;
    }
    this.nameError.set(null);
    if (newValue === g.name) {
      this.cancelRename(g);
      return;
    }
    // Show errors (e.g. name already taken, LIN-09) at the name field, the focus stays in the field.
    const ok = await this.run(
      `rename:${g.id}`,
      async () => {
        await firstValueFrom(
          this.http.put(`/api/teams/${this.teamId()}/groups/${g.id}`, { name: newValue }),
        );
        return TEXTS.lines.renamed(newValue);
      },
      undefined,
      (message) => {
        this.nameError.set(message);
        this.focusElement([`name-${g.id}`]);
      },
    );
    if (ok) {
      this.cancelRename(g);
    }
  }

  protected askDelete(g: DisplayGroup): void {
    this.toDelete.set(g);
    this.deleteDialog()?.nativeElement.showModal();
  }

  protected cancelDelete(): void {
    this.deleteDialog()?.nativeElement.close();
  }

  /** After closing the dialog (cancel, Esc) put the focus back on «löschen». */
  protected dialogClosed(): void {
    const g = this.toDelete();
    this.toDelete.set(null);
    if (g?.id && this.lines().some((l) => l.id === g.id)) {
      this.host.nativeElement.querySelector<HTMLElement>(`#delete-${g.id}`)?.focus();
    }
  }

  /** Delete a line; its players are «Ohne Linie» afterwards (LIN-06). */
  protected delete(): void {
    const g = this.toDelete();
    if (!g?.id) return;
    this.deleteDialog()?.nativeElement.close();
    void this.run(`delete:${g.id}`, async () => {
      await firstValueFrom(this.http.delete(`/api/teams/${this.teamId()}/groups/${g.id}`));
      return TEXTS.lines.deleted(g.name);
    });
  }

  // ---- Drag & drop (in addition to the select field, LIN-07) ----

  protected dragStart(domEvent: DragEvent, person: PersonSummary): void {
    if (!this.canEdit() || this.saving()) {
      domEvent.preventDefault();
      return;
    }
    this.dragged.set(person);
    domEvent.dataTransfer?.setData('text/plain', person.id);
    if (domEvent.dataTransfer) domEvent.dataTransfer.effectAllowed = 'move';
  }

  protected dragEnd(): void {
    this.dragged.set(null);
    this.dropTarget.set(null);
  }

  protected over(domEvent: DragEvent, g: DisplayGroup): void {
    if (!this.dragged()) return;
    domEvent.preventDefault();
    if (domEvent.dataTransfer) domEvent.dataTransfer.dropEffect = 'move';
    this.dropTarget.set(g.id ?? NO_LINE);
  }

  protected leave(domEvent: DragEvent, g: DisplayGroup): void {
    const next = domEvent.relatedTarget as Node | null;
    if (next && (domEvent.currentTarget as HTMLElement).contains(next)) return;
    if (this.dropTarget() === (g.id ?? NO_LINE)) this.dropTarget.set(null);
  }

  protected drop(domEvent: DragEvent, g: DisplayGroup): void {
    const person = this.dragged();
    domEvent.preventDefault();
    this.dragEnd();
    if (person) {
      this.assign(person, g.id ?? NO_LINE, false);
    }
  }
}
