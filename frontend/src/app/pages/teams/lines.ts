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
import type { Gruppe, PersonKurz, TeamDetail } from '../../api/types';
import { AnmeldungService } from '../../core/auth.service';
import { fehlerstatus, fehlertext } from '../../core/error-text';
import { name } from '../../core/format';
import { Icon } from '../../core/icon';
import { TEXTE } from '../../core/texts';

/** Eine Gruppe im Screen: Linie, Goalies oder «Ohne Linie» (`id` = null). */
interface Anzeigegruppe {
  id: string | null;
  art: Gruppe['type'] | 'NO_LINE';
  name: string;
  spielerinnen: PersonKurz[];
  /** Position unter den Linien (0-basiert), sonst -1. */
  index: number;
}

/** Wert im Auswahlfeld für «Ohne Linie». */
const OHNE_LINIE = '';

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
export class Linien {
  private readonly http = inject(HttpClient);
  private readonly injector = inject(Injector);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly anmeldung = inject(AnmeldungService);
  protected readonly texte = TEXTE;
  protected readonly name = name;
  protected readonly ohneLinieWert = OHNE_LINIE;

  /** Route `/teams/:teamId/linien` */
  readonly teamId = input.required<string>();

  protected readonly team = signal<TeamDetail | null>(null);
  protected readonly laedt = signal(true);
  protected readonly fehler = signal<string | null>(null);
  protected readonly ladefehler = signal<string | null>(null);
  protected readonly erfolg = signal<string | null>(null);
  /**
   * Laufende Aktion: `hinzufuegen`, `zuweisen:<personId>`, `oben:<id>`, `unten:<id>`,
   * `umbenennen:<id>`, `loeschen:<id>`. `aria-busy` trägt nur das auslösende Element, `disabled` alle.
   */
  protected readonly laufendeAktion = signal<string | null>(null);
  protected readonly speichert = computed(() => this.laufendeAktion() !== null);

  /** Linie, die gerade umbenannt wird, und der eingegebene Name. */
  protected readonly umbenennenId = signal<string | null>(null);
  protected readonly neuerName = signal('');
  protected readonly nameFehler = signal<string | null>(null);

  /** Linie, deren Löschen bestätigt werden soll. */
  protected readonly zuLoeschen = signal<Anzeigegruppe | null>(null);
  private readonly loeschenDialog = viewChild<ElementRef<HTMLDialogElement>>('loeschenDialog');

  /** Drag & Drop: gezogene Spielerin und Gruppe unter dem Zeiger. */
  protected readonly gezogen = signal<PersonKurz | null>(null);
  protected readonly dropZiel = signal<string | null>(null);

  protected readonly darfBearbeiten = computed(
    () => this.anmeldung.istAdmin() || this.team()?.myTeamRole === 'COACH',
  );

  /** Linien nach Reihenfolge, dann Goalies, dann «Ohne Linie» (LIN-06, LIN-09). */
  protected readonly gruppen = computed<Anzeigegruppe[]>(() => {
    const t = this.team();
    if (!t) return [];
    let linie = 0;
    const gruppen: Anzeigegruppe[] = t.lineup.groups.map((g) => ({
      id: g.id,
      art: g.type,
      name: g.name,
      spielerinnen: g.players,
      index: g.type === 'LINE' ? linie++ : -1,
    }));
    gruppen.push({
      id: null,
      art: 'NO_LINE',
      name: TEXTE.linien.ohneLinie,
      spielerinnen: t.lineup.noLine,
      index: -1,
    });
    return gruppen;
  });
  protected readonly linien = computed(() => this.gruppen().filter((g) => g.art === 'LINE'));
  protected readonly uebrige = computed(() => this.gruppen().filter((g) => g.art !== 'LINE'));

  /** Auswahl im Feld «Linie von …»: alle Gruppen in Anzeigereihenfolge, dann «Ohne Linie». */
  protected readonly optionen = computed(() =>
    this.gruppen().map((g) => ({ wert: g.id ?? OHNE_LINIE, name: g.name })),
  );

  constructor() {
    effect(() => {
      const teamId = this.teamId();
      untracked(() => void this.laden(teamId));
    });
  }

  protected testId(g: Anzeigegruppe): string {
    return g.id ? `gruppe-${g.id}` : 'gruppe-ohne-linie';
  }

  protected titelId(g: Anzeigegruppe): string {
    return g.id ? `gruppe-titel-${g.id}` : 'gruppe-titel-ohne-linie';
  }

  private async laden(teamId: string): Promise<void> {
    this.laedt.set(true);
    this.ladefehler.set(null);
    const ich = this.anmeldung.ich();
    if (ich && !ich.admin && !ich.memberships.some((z) => z.team.id === teamId)) {
      // Ohne Zugehörigkeit gar nicht erst anfragen; das Backend lehnt ab (403, RR-07).
      this.team.set(null);
      this.ladefehler.set(TEXTE.allgemein.keineBerechtigung);
      this.laedt.set(false);
      return;
    }
    try {
      this.team.set(await firstValueFrom(this.http.get<TeamDetail>(`/api/teams/${teamId}`)));
    } catch (e) {
      this.team.set(null);
      this.ladefehler.set(fehlerstatus(e) === 404 ? TEXTE.teams.nichtGefunden : fehlertext(e));
    } finally {
      this.laedt.set(false);
    }
  }

  private async neuLaden(): Promise<void> {
    this.team.set(await firstValueFrom(this.http.get<TeamDetail>(`/api/teams/${this.teamId()}`)));
  }

  /**
   * Führt eine Änderung aus, lädt die Einteilung neu und meldet das Ergebnis. `fokus`: IDs der
   * Elemente, die danach den Fokus bekommen sollen; das erste, das es gibt und nicht gesperrt ist.
   * `beiFehler`: zeigt einen Fehler selbst an (z.B. beim Feld) statt oben auf der Seite.
   */
  private async ausfuehren(
    schluessel: string,
    aktion: () => Promise<string>,
    fokus?: string | string[],
    beiFehler?: (meldung: string) => void,
  ): Promise<boolean> {
    if (this.speichert()) return false;
    this.erfolg.set(null);
    this.fehler.set(null);
    this.laufendeAktion.set(schluessel);
    let ok = false;
    try {
      const meldung = await aktion();
      await this.neuLaden();
      this.erfolg.set(meldung);
      ok = true;
    } catch (e) {
      if (beiFehler) beiFehler(fehlertext(e));
      else this.fehler.set(fehlertext(e));
      // Bei einem Fehler den tatsächlichen Stand zeigen (z.B. gleichzeitig geändert).
      await this.neuLaden().catch(() => undefined);
    } finally {
      this.laufendeAktion.set(null);
    }
    if (fokus) {
      this.fokussieren(typeof fokus === 'string' ? [fokus] : fokus);
    }
    return ok;
  }

  /** Nach dem nächsten Rendern das erste vorhandene, nicht gesperrte Element fokussieren. */
  private fokussieren(ids: string[]): void {
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
  protected zuweisen(person: PersonKurz, gruppeId: string, fokus = true): void {
    const ziel = this.gruppen().find((g) => (g.id ?? OHNE_LINIE) === gruppeId);
    const aktuell = this.gruppen().find((g) => g.spielerinnen.some((p) => p.id === person.id));
    if (!ziel || ziel === aktuell) return;
    void this.ausfuehren(
      `zuweisen:${person.id}`,
      async () => {
        await firstValueFrom(
          this.http.put(`/api/teams/${this.teamId()}/assignments/${person.id}`, {
            groupId: ziel.id,
          }),
        );
        return ziel.id
          ? TEXTE.linien.zugewiesen(name(person), ziel.name)
          : TEXTE.linien.ohneLinieGesetzt(name(person));
      },
      fokus ? `zuweisung-${person.id}` : undefined,
    );
  }

  /** Neue Linie am Ende mit dem nächsten freien Namen «Linie n» (LIN-07, LIN-09). */
  protected hinzufuegen(): void {
    const namen = new Set(this.gruppen().map((g) => g.name.toLowerCase()));
    let nr = this.linien().length + 1;
    while (namen.has(TEXTE.linien.neueLinie(nr).toLowerCase())) nr++;
    const neu = TEXTE.linien.neueLinie(nr);
    void this.ausfuehren('hinzufuegen', async () => {
      await firstValueFrom(this.http.post(`/api/teams/${this.teamId()}/groups`, { name: neu }));
      return TEXTE.linien.hinzugefuegt(neu);
    });
  }

  /** Linie eine Position nach oben oder unten (LIN-09). */
  protected verschieben(g: Anzeigegruppe, richtung: -1 | 1): void {
    const ziel = g.index + 1 + richtung;
    if (!g.id || ziel < 1 || ziel > this.linien().length) return;
    // Landet die Linie am Rand, ist dieser Knopf danach gesperrt: dann den Gegenknopf fokussieren.
    const knopf = `${richtung < 0 ? 'oben' : 'unten'}-${g.id}`;
    const gegenknopf = `${richtung < 0 ? 'unten' : 'oben'}-${g.id}`;
    void this.ausfuehren(
      `${richtung < 0 ? 'oben' : 'unten'}:${g.id}`,
      async () => {
        await firstValueFrom(
          this.http.put(`/api/teams/${this.teamId()}/groups/${g.id}`, {
            name: g.name,
            sortOrder: ziel,
          }),
        );
        return TEXTE.linien.verschoben(g.name);
      },
      [knopf, gegenknopf],
    );
  }

  protected umbenennenStarten(g: Anzeigegruppe): void {
    this.umbenennenId.set(g.id);
    this.neuerName.set(g.name);
    this.nameFehler.set(null);
    afterNextRender(
      () => this.host.nativeElement.querySelector<HTMLInputElement>(`#name-${g.id}`)?.select(),
      { injector: this.injector },
    );
  }

  protected umbenennenAbbrechen(g: Anzeigegruppe): void {
    this.umbenennenId.set(null);
    this.nameFehler.set(null);
    afterNextRender(
      () => this.host.nativeElement.querySelector<HTMLElement>(`#umbenennen-${g.id}`)?.focus(),
      { injector: this.injector },
    );
  }

  protected async umbenennen(ereignis: Event, g: Anzeigegruppe): Promise<void> {
    ereignis.preventDefault();
    const neu = this.neuerName().trim();
    if (!neu) {
      this.nameFehler.set(TEXTE.linien.namePflicht);
      return;
    }
    this.nameFehler.set(null);
    if (neu === g.name) {
      this.umbenennenAbbrechen(g);
      return;
    }
    // Fehler (z.B. Name schon vergeben, LIN-09) beim Namensfeld zeigen, Fokus bleibt im Feld.
    const ok = await this.ausfuehren(
      `umbenennen:${g.id}`,
      async () => {
        await firstValueFrom(
          this.http.put(`/api/teams/${this.teamId()}/groups/${g.id}`, { name: neu }),
        );
        return TEXTE.linien.umbenannt(neu);
      },
      undefined,
      (meldung) => {
        this.nameFehler.set(meldung);
        this.fokussieren([`name-${g.id}`]);
      },
    );
    if (ok) {
      this.umbenennenAbbrechen(g);
    }
  }

  protected loeschenFragen(g: Anzeigegruppe): void {
    this.zuLoeschen.set(g);
    this.loeschenDialog()?.nativeElement.showModal();
  }

  protected loeschenAbbrechen(): void {
    this.loeschenDialog()?.nativeElement.close();
  }

  /** Nach dem Schliessen des Dialogs (Abbrechen, Esc) den Fokus zurück auf «löschen». */
  protected dialogGeschlossen(): void {
    const g = this.zuLoeschen();
    this.zuLoeschen.set(null);
    if (g?.id && this.linien().some((l) => l.id === g.id)) {
      this.host.nativeElement.querySelector<HTMLElement>(`#loeschen-${g.id}`)?.focus();
    }
  }

  /** Linie löschen; ihre Spielerinnen sind danach «Ohne Linie» (LIN-06). */
  protected loeschen(): void {
    const g = this.zuLoeschen();
    if (!g?.id) return;
    this.loeschenDialog()?.nativeElement.close();
    void this.ausfuehren(`loeschen:${g.id}`, async () => {
      await firstValueFrom(this.http.delete(`/api/teams/${this.teamId()}/groups/${g.id}`));
      return TEXTE.linien.geloescht(g.name);
    });
  }

  // ---- Drag & Drop (Ergänzung zum Auswahlfeld, LIN-07) ----

  protected ziehenStart(ereignis: DragEvent, person: PersonKurz): void {
    if (!this.darfBearbeiten() || this.speichert()) {
      ereignis.preventDefault();
      return;
    }
    this.gezogen.set(person);
    ereignis.dataTransfer?.setData('text/plain', person.id);
    if (ereignis.dataTransfer) ereignis.dataTransfer.effectAllowed = 'move';
  }

  protected ziehenEnde(): void {
    this.gezogen.set(null);
    this.dropZiel.set(null);
  }

  protected ueber(ereignis: DragEvent, g: Anzeigegruppe): void {
    if (!this.gezogen()) return;
    ereignis.preventDefault();
    if (ereignis.dataTransfer) ereignis.dataTransfer.dropEffect = 'move';
    this.dropZiel.set(g.id ?? OHNE_LINIE);
  }

  protected verlassen(ereignis: DragEvent, g: Anzeigegruppe): void {
    const naechstes = ereignis.relatedTarget as Node | null;
    if (naechstes && (ereignis.currentTarget as HTMLElement).contains(naechstes)) return;
    if (this.dropZiel() === (g.id ?? OHNE_LINIE)) this.dropZiel.set(null);
  }

  protected ablegen(ereignis: DragEvent, g: Anzeigegruppe): void {
    const person = this.gezogen();
    ereignis.preventDefault();
    this.ziehenEnde();
    if (person) {
      this.zuweisen(person, g.id ?? OHNE_LINIE, false);
    }
  }
}
