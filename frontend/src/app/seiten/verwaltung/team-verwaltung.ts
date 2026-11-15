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
  Funktion,
  Person,
  PersonKurz,
  Saison,
  TeamDetail,
  Teamzugehoerigkeit,
} from '../../api/typen';
import { AnmeldungService } from '../../kern/anmeldung.service';
import { fehlerstatus, fehlertext } from '../../kern/fehlertext';
import { funktionText, name } from '../../kern/format';
import { TEXTE } from '../../kern/texte';

interface Mitglied {
  person: PersonKurz;
  funktion: Funktion;
}

const FUNKTIONEN: Funktion[] = ['SPIELERIN', 'COACH'];

/**
 * Team verwalten (Admin, PTF-11): umbenennen, Personen mit Funktion aufnehmen (PTF-03, PTF-04),
 * Funktion ändern, entfernen. `?saison=` wählt die Saison; das Archiv ist nur lesend (SAI-02).
 */
@Component({
  selector: 'app-team-verwaltung',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  templateUrl: './team-verwaltung.html',
  styleUrl: '../seite.css',
  styles: `
    form .button {
      justify-self: start;
    }
    .mitglied .zeile-titel {
      min-width: 10em;
    }
    .mitglied select {
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
export class TeamVerwaltung {
  private readonly http = inject(HttpClient);
  protected readonly anmeldung = inject(AnmeldungService);
  protected readonly texte = TEXTE;
  protected readonly name = name;
  protected readonly funktion = funktionText;
  protected readonly funktionen = FUNKTIONEN;

  /** Route `/verwaltung/teams/:teamId` */
  readonly teamId = input.required<string>();
  /** Query `?saison=`; ohne Angabe die aktuelle Saison. */
  readonly saison = input<string | undefined>();

  protected readonly team = signal<TeamDetail | null>(null);
  protected readonly saisonDaten = signal<Saison | null>(null);
  protected readonly personen = signal<Person[]>([]);
  protected readonly laedt = signal(true);
  protected readonly speichert = signal(false);
  protected readonly fehler = signal<string | null>(null);
  protected readonly erfolg = signal<string | null>(null);
  protected readonly nurLesen = computed(() => this.saisonDaten()?.archiv === true);

  protected readonly teamname = signal('');
  protected readonly teamnameFehler = signal<string | null>(null);
  protected readonly neuePerson = signal('');
  protected readonly neueFunktion = signal<Funktion>('SPIELERIN');
  protected readonly personFehler = signal<string | null>(null);

  /** Coaches und Spielerinnen getrennt (PTF-07). */
  protected readonly coaches = computed<Mitglied[]>(() =>
    (this.team()?.coaches ?? []).map((person) => ({ person, funktion: 'COACH' })),
  );
  protected readonly spielerinnen = computed<Mitglied[]>(() => {
    const t = this.team();
    if (!t) return [];
    return [...t.einteilung.gruppen.flatMap((g) => g.spielerinnen), ...t.einteilung.ohneLinie].map(
      (person) => ({
        person,
        funktion: 'SPIELERIN',
      }),
    );
  });
  private readonly mitgliedIds = computed(
    () => new Set([...this.coaches(), ...this.spielerinnen()].map((m) => m.person.id)),
  );

  constructor() {
    effect(() => {
      const teamId = this.teamId();
      const saisonId = this.saison();
      if (this.anmeldung.istAdmin()) {
        untracked(() => void this.laden(teamId, saisonId));
      }
    });
  }

  protected istMitglied(personId: string): boolean {
    return this.mitgliedIds().has(personId);
  }

  private params(): Record<string, string> {
    const id = this.saisonDaten()?.id ?? this.saison();
    return id ? { saison: id } : {};
  }

  private async laden(teamId: string, saisonId: string | undefined): Promise<void> {
    this.laedt.set(true);
    this.fehler.set(null);
    try {
      const params: Record<string, string> = saisonId ? { saison: saisonId } : {};
      const [saisons, team, personen] = await Promise.all([
        firstValueFrom(this.http.get<Saison[]>('/api/saisons')),
        firstValueFrom(this.http.get<TeamDetail>(`/api/teams/${teamId}`, { params })),
        firstValueFrom(this.http.get<Person[]>('/api/personen')),
      ]);
      this.saisonDaten.set(saisons.find((s) => (saisonId ? s.id === saisonId : s.aktuell)) ?? null);
      this.team.set(team);
      this.teamname.set(team.name);
      this.personen.set(personen);
    } catch (e) {
      this.team.set(null);
      this.fehler.set(fehlerstatus(e) === 404 ? TEXTE.teams.nichtGefunden : fehlertext(e));
    } finally {
      this.laedt.set(false);
    }
  }

  private async neuLaden(): Promise<void> {
    const t = await firstValueFrom(
      this.http.get<TeamDetail>(`/api/teams/${this.teamId()}`, { params: this.params() }),
    );
    this.team.set(t);
  }

  private async ausfuehren(aktion: () => Promise<string>): Promise<void> {
    if (this.speichert()) return;
    this.erfolg.set(null);
    this.fehler.set(null);
    this.speichert.set(true);
    try {
      const meldung = await aktion();
      await this.neuLaden();
      this.erfolg.set(meldung);
    } catch (e) {
      this.fehler.set(fehlertext(e));
    } finally {
      this.speichert.set(false);
    }
  }

  protected umbenennen(ereignis: Event): void {
    ereignis.preventDefault();
    const neuerName = this.teamname().trim();
    if (!neuerName) {
      this.teamnameFehler.set(TEXTE.teamVerwaltung.namePflicht);
      return;
    }
    this.teamnameFehler.set(null);
    void this.ausfuehren(async () => {
      await firstValueFrom(this.http.put(`/api/teams/${this.teamId()}`, { name: neuerName }));
      return TEXTE.teamVerwaltung.umbenannt;
    });
  }

  protected aufnehmen(ereignis: Event): void {
    ereignis.preventDefault();
    const personId = this.neuePerson();
    if (!personId) {
      this.personFehler.set(TEXTE.teamVerwaltung.personPflicht);
      return;
    }
    this.personFehler.set(null);
    const funktion = this.neueFunktion();
    void this.ausfuehren(async () => {
      const z = await firstValueFrom(
        this.http.post<Teamzugehoerigkeit>(
          `/api/teams/${this.teamId()}/mitglieder`,
          { personId, funktion },
          { params: this.params() },
        ),
      );
      this.neuePerson.set('');
      this.neueFunktion.set('SPIELERIN');
      return TEXTE.teamVerwaltung.aufgenommen(
        name(z.person),
        funktionText(z.funktion),
        z.team.name,
      );
    });
  }

  protected funktionAendern(m: Mitglied, funktion: Funktion): void {
    void this.ausfuehren(async () => {
      await firstValueFrom(
        this.http.put(
          `/api/teams/${this.teamId()}/mitglieder/${m.person.id}`,
          { funktion },
          { params: this.params() },
        ),
      );
      return TEXTE.teamVerwaltung.funktionGeaendert(name(m.person), funktionText(funktion));
    });
  }

  protected entfernen(m: Mitglied): void {
    void this.ausfuehren(async () => {
      await firstValueFrom(
        this.http.delete(`/api/teams/${this.teamId()}/mitglieder/${m.person.id}`, {
          params: this.params(),
        }),
      );
      return TEXTE.teamVerwaltung.entfernt(name(m.person));
    });
  }
}
