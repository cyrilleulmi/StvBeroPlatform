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

/** Eine Gruppe im Screen: Linie, Goalies oder «Ohne Linie» (`id` = null). */
interface DisplayGroup {
  id: string | null;
  kind: Group['type'] | 'NO_LINE';
  name: string;
  players: PersonSummary[];
  /** Position unter den Linien (0-basiert), sonst -1. */
  index: number;
}

/** Wert im Auswahlfeld für «Ohne Linie». */
const NO_LINE = '';

/**
 * Linien eines Teams (LIN-07): Linien anlegen, umbenennen, sortieren, löschen und Spielerinnen per
 * Drag & Drop oder Auswahlfeld zuweisen. Beides speichert sofort. Ohne Drag & Drop bedienbar
 * (Handy, Tastatur, docs/testbarkeit.md Regel 7). Mitglieder ohne Coach-Funktion sehen die
 * Einteilung nur lesend.
 */
@Component({
  selector: 'app-linien',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon, NgTemplateOutlet],
  templateUrl: './lines.html',
  styleUrls: ['../page.css', './lines.css'],
})
export class Lines {
  private readonly http = inject(HttpClient);
  private readonly injector = inject(Injector);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly auth = inject(AuthService);
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
   * Laufende Aktion: `hinzufuegen`, `zuweisen:<personId>`, `oben:<id>`, `unten:<id>`,
   * `umbenennen:<id>`, `loeschen:<id>`. `aria-busy` trägt nur das auslösende Element, `disabled` alle.
   */
  protected readonly runningAction = signal<string | null>(null);
  protected readonly saving = computed(() => this.runningAction() !== null);

  /** Linie, die gerade umbenannt wird, und der eingegebene Name. */
  protected readonly renameId = signal<string | null>(null);
  protected readonly newName = signal('');
  protected readonly nameError = signal<string | null>(null);

  /** Linie, deren Löschen bestätigt werden soll. */
  protected readonly toDelete = signal<DisplayGroup | null>(null);
  private readonly deleteDialog = viewChild<ElementRef<HTMLDialogElement>>('deleteDialog');

  /** Drag & Drop: gezogene Spielerin und Gruppe unter dem Zeiger. */
  protected readonly dragged = signal<PersonSummary | null>(null);
  protected readonly dropTarget = signal<string | null>(null);

  protected readonly canEdit = computed(
    () => this.auth.isAdmin() || this.team()?.myTeamRole === 'COACH',
  );

  /** Linien nach Reihenfolge, dann Goalies, dann «Ohne Linie» (LIN-06, LIN-09). */
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

  /** Auswahl im Feld «Linie von …»: alle Gruppen in Anzeigereihenfolge, dann «Ohne Linie». */
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
    return g.id ? `gruppe-${g.id}` : 'gruppe-ohne-linie';
  }

  protected titleId(g: DisplayGroup): string {
    return g.id ? `gruppe-titel-${g.id}` : 'gruppe-titel-ohne-linie';
  }

  private async load(teamId: string): Promise<void> {
    this.loading.set(true);
    this.loadError.set(null);
    const me = this.auth.me();
    if (me && !me.admin && !me.memberships.some((z) => z.team.id === teamId)) {
      // Ohne Zugehörigkeit gar nicht erst anfragen; das Backend lehnt ab (403, RR-07).
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
   * Führt eine Änderung aus, lädt die Einteilung neu und meldet das Ergebnis. `fokus`: IDs der
   * Elemente, die danach den Fokus bekommen sollen; das erste, das es gibt und nicht gesperrt ist.
   * `beiFehler`: zeigt einen Fehler selbst an (z.B. beim Feld) statt oben auf der Seite.
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
      // Bei einem Fehler den tatsächlichen Stand zeigen (z.B. gleichzeitig geändert).
      await this.reload().catch(() => undefined);
    } finally {
      this.runningAction.set(null);
    }
    if (focus) {
      this.focusElement(typeof focus === 'string' ? [focus] : focus);
    }
    return ok;
  }

  /** Nach dem nächsten Rendern das erste vorhandene, nicht gesperrte Element fokussieren. */
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

  /** Spielerin einer Gruppe zuweisen; `gruppeId` leer = ohne Linie (LIN-02, LIN-06, LIN-07). */
  protected assign(person: PersonSummary, groupId: string, focus = true): void {
    const target = this.groups().find((g) => (g.id ?? NO_LINE) === groupId);
    const current = this.groups().find((g) => g.players.some((p) => p.id === person.id));
    if (!target || target === current) return;
    void this.run(
      `zuweisen:${person.id}`,
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
      focus ? `zuweisung-${person.id}` : undefined,
    );
  }

  /** Neue Linie am Ende mit dem nächsten freien Namen «Linie n» (LIN-07, LIN-09). */
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

  /** Linie eine Position nach oben oder unten (LIN-09). */
  protected move(g: DisplayGroup, direction: -1 | 1): void {
    const target = g.index + 1 + direction;
    if (!g.id || target < 1 || target > this.lines().length) return;
    // Landet die Linie am Rand, ist dieser Knopf danach gesperrt: dann den Gegenknopf fokussieren.
    const button = `${direction < 0 ? 'oben' : 'unten'}-${g.id}`;
    const otherButton = `${direction < 0 ? 'unten' : 'oben'}-${g.id}`;
    void this.run(
      `${direction < 0 ? 'oben' : 'unten'}:${g.id}`,
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
      () => this.host.nativeElement.querySelector<HTMLElement>(`#umbenennen-${g.id}`)?.focus(),
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
    // Fehler (z.B. Name schon vergeben, LIN-09) beim Namensfeld zeigen, Fokus bleibt im Feld.
    const ok = await this.run(
      `umbenennen:${g.id}`,
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

  /** Nach dem Schliessen des Dialogs (Abbrechen, Esc) den Fokus zurück auf «löschen». */
  protected dialogClosed(): void {
    const g = this.toDelete();
    this.toDelete.set(null);
    if (g?.id && this.lines().some((l) => l.id === g.id)) {
      this.host.nativeElement.querySelector<HTMLElement>(`#loeschen-${g.id}`)?.focus();
    }
  }

  /** Linie löschen; ihre Spielerinnen sind danach «Ohne Linie» (LIN-06). */
  protected delete(): void {
    const g = this.toDelete();
    if (!g?.id) return;
    this.deleteDialog()?.nativeElement.close();
    void this.run(`loeschen:${g.id}`, async () => {
      await firstValueFrom(this.http.delete(`/api/teams/${this.teamId()}/groups/${g.id}`));
      return TEXTS.lines.deleted(g.name);
    });
  }

  // ---- Drag & Drop (Ergänzung zum Auswahlfeld, LIN-07) ----

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
