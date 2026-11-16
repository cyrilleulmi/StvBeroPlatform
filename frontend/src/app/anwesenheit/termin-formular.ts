import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type {
  Ort,
  Serie,
  SerieSchreiben,
  Team,
  Termin,
  TerminSchreiben,
  TerminTyp,
} from '../api/typen';
import { AnmeldungService } from '../kern/anmeldung.service';
import { feldfehler, fehlerstatus, fehlertext } from '../kern/fehlertext';
import { RueckmeldungService } from '../kern/rueckmeldung.service';
import { TEXTE } from '../kern/texte';
import { eingabe, zurich } from './zeit';

type Wochentag = SerieSchreiben['wochentag'];
type Modus = 'neu' | 'termin' | 'serie';

const WOCHENTAGE: Wochentag[] = [
  'MONTAG',
  'DIENSTAG',
  'MITTWOCH',
  'DONNERSTAG',
  'FREITAG',
  'SAMSTAG',
  'SONNTAG',
];

interface Eingabe {
  typ: TerminTyp;
  titel: string;
  datum: string;
  beginn: string;
  ende: string;
  wochentag: Wochentag;
  gueltigVon: string;
  gueltigBis: string;
  teamIds: string[];
  ort: string;
  hinweise: string;
  antwortfrist: string;
}

type Feld = keyof Eingabe;

const LEER: Eingabe = {
  typ: 'TRAINING',
  titel: TEXTE.termine.typ.TRAINING,
  datum: '',
  beginn: '18:30',
  ende: '20:00',
  wochentag: 'DIENSTAG',
  gueltigVon: '',
  gueltigBis: '',
  teamIds: [],
  ort: '',
  hinweise: '',
  antwortfrist: '24',
};

/** Feldnamen der API auf die Felder des Formulars. */
const API_FELDER: Record<string, Feld> = {
  beginn: 'beginn',
  ende: 'ende',
  teamIds: 'teamIds',
  titel: 'titel',
  beginnZeit: 'beginn',
  endeZeit: 'ende',
  gueltigVon: 'gueltigVon',
  gueltigBis: 'gueltigBis',
  antwortfristStunden: 'antwortfrist',
  hinweise: 'hinweise',
  'ort.name': 'ort',
};

/**
 * Termin erfassen (`/termine/neu`, `?art=serie` für eine Serie), Termin bearbeiten und absagen
 * (`/termine/:terminId/bearbeiten`) und Serie bearbeiten (`/serien/:serieId/bearbeiten`).
 * TER-01, TER-04 bis TER-06, TER-08, TER-10, TER-11.
 */
@Component({
  selector: 'app-termin-formular',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  templateUrl: './termin-formular.html',
  styleUrl: './termin-formular.css',
})
export class TerminFormular {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly anmeldung = inject(AnmeldungService);
  private readonly rueckmeldung = inject(RueckmeldungService);
  protected readonly texte = TEXTE;
  protected readonly wochentage = WOCHENTAGE;
  protected readonly zeitfelder: { feld: 'beginn' | 'ende'; label: string }[] = [
    { feld: 'beginn', label: TEXTE.terminFormular.beginn },
    { feld: 'ende', label: TEXTE.terminFormular.ende },
  ];
  protected readonly zeitraumfelder: { feld: 'gueltigVon' | 'gueltigBis'; label: string }[] = [
    { feld: 'gueltigVon', label: TEXTE.terminFormular.gueltigVon },
    { feld: 'gueltigBis', label: TEXTE.terminFormular.gueltigBis },
  ];

  /** Route `/termine/:terminId/bearbeiten` */
  readonly terminId = input<string | undefined>();
  /** Route `/serien/:serieId/bearbeiten` */
  readonly serieId = input<string | undefined>();
  /** Query `?art=serie` auf `/termine/neu` */
  readonly art = input<string | undefined>();

  protected readonly modus = computed<Modus>(() =>
    this.terminId() ? 'termin' : this.serieId() ? 'serie' : 'neu',
  );
  /** Felder einer Serie (Wochentag, Zeitraum) statt eines einzelnen Datums. */
  protected readonly alsSerie = computed(
    () => this.modus() === 'serie' || (this.modus() === 'neu' && this.art() === 'serie'),
  );

  protected readonly werte = signal<Eingabe>({ ...LEER });
  protected readonly termin = signal<Termin | null>(null);
  protected readonly serie = signal<Serie | null>(null);
  /** Teams zur Auswahl: eigene Teams, für Admins alle der aktuellen Saison. */
  protected readonly teams = signal<Team[]>([]);
  protected readonly laedt = signal(true);
  protected readonly fehler = signal<string | null>(null);
  protected readonly feldFehler = signal<Partial<Record<Feld | 'grund', string>>>({});
  /** `speichern` oder `absagen`: aria-busy nur dort, `disabled` für alle. */
  protected readonly laufendeAktion = signal<string | null>(null);
  protected readonly speichert = computed(() => this.laufendeAktion() !== null);
  protected readonly grund = signal('');

  private readonly absagenDialog = viewChild<ElementRef<HTMLDialogElement>>('absagenDialog');
  private readonly grundFeld = viewChild<ElementRef<HTMLInputElement>>('grundFeld');

  /** Wer erfassen und bearbeiten darf: Coaches (in irgendeinem Team) und Admins (TER-11). */
  protected readonly darfErfassen = computed(() => {
    const ich = this.anmeldung.ich();
    return !!ich && (ich.admin || ich.zugehoerigkeiten.some((z) => z.funktion === 'COACH'));
  });

  /** Ob das Formular gezeigt wird. Sonst steht dort ein Hinweis (`hinweis()`). */
  protected readonly bearbeitbar = computed(() => {
    switch (this.modus()) {
      case 'neu':
        return this.darfErfassen();
      case 'termin':
        return this.termin()?.darfBearbeiten === true;
      case 'serie':
        return this.darfSerieBearbeiten();
    }
  });

  protected readonly hinweis = computed(() => {
    if (this.laedt() || this.fehler()) return null;
    if (this.modus() === 'termin' && this.termin() && !this.termin()!.darfBearbeiten) {
      const t = this.termin()!;
      const coach = t.teams.some((x) => x.meineFunktion === 'COACH') || this.anmeldung.istAdmin();
      return coach ? TEXTE.terminFormular.nichtAenderbar : TEXTE.allgemein.keineBerechtigung;
    }
    return this.bearbeitbar() ? null : TEXTE.allgemein.keineBerechtigung;
  });

  protected readonly titel = computed(() => {
    switch (this.modus()) {
      case 'neu':
        return TEXTE.terminFormular.neuTitel;
      case 'termin':
        return TEXTE.terminFormular.bearbeitenTitel;
      case 'serie':
        return TEXTE.terminFormular.serieBearbeitenTitel;
    }
  });

  /** Termintypen zur Auswahl. Spieltage kommen mit F-05; ein bestehender bleibt wählbar. */
  protected readonly typen = computed<TerminTyp[]>(() =>
    this.termin()?.typ === 'SPIELTAG' ? ['SPIELTAG'] : ['TRAINING', 'ANLASS'],
  );

  /** Teams der Serie bzw. des Termins, in denen die Person nicht Mitglied ist. Sie bleiben erhalten. */
  protected readonly weitereTeams = computed(() => {
    const sichtbar = new Set(this.teams().map((t) => t.id));
    return this.werte().teamIds.filter((id) => !sichtbar.has(id)).length;
  });

  protected readonly zurueck = computed(() => {
    const t = this.terminId();
    const s = this.serieId();
    return t ? ['/termine', t] : s ? ['/serien', s] : ['/termine'];
  });

  constructor() {
    effect(() => {
      const terminId = this.terminId();
      const serieId = this.serieId();
      untracked(() => void this.laden(terminId, serieId));
    });
  }

  protected setzen<F extends Feld>(feld: F, wert: Eingabe[F]): void {
    this.werte.update((w) => ({ ...w, [feld]: wert }));
    if (this.feldFehler()[feld]) {
      this.feldFehler.update((f) => ({ ...f, [feld]: undefined }));
    }
  }

  protected typWaehlen(typ: TerminTyp): void {
    const alt = this.werte();
    const standardtitel = Object.values(TEXTE.termine.typ) as string[];
    this.setzen('typ', typ);
    if (!alt.titel || standardtitel.includes(alt.titel)) {
      this.setzen('titel', TEXTE.termine.typ[typ]);
    }
  }

  protected teamUmschalten(teamId: string, an: boolean): void {
    const ids = this.werte().teamIds.filter((id) => id !== teamId);
    this.setzen('teamIds', an ? [...ids, teamId] : ids);
  }

  protected artWaehlen(art: 'einzel' | 'serie'): void {
    this.feldFehler.set({});
    void this.router.navigate(['/termine/neu'], {
      queryParams: { art: art === 'serie' ? 'serie' : null },
      replaceUrl: true,
    });
  }

  protected fehlerId(feld: Feld | 'grund'): string | null {
    return this.feldFehler()[feld] ? `termin-${feld}-fehler` : null;
  }

  async speichern(ereignis: Event): Promise<void> {
    ereignis.preventDefault();
    if (this.speichert()) return;
    if (!this.pruefen()) return;
    this.laufendeAktion.set('speichern');
    this.fehler.set(null);
    try {
      switch (this.modus()) {
        case 'neu':
          if (this.alsSerie()) {
            const s = await firstValueFrom(this.http.post<Serie>('/api/serien', this.serieDaten()));
            this.rueckmeldung.setzen(TEXTE.terminFormular.serieAngelegt);
            await this.router.navigate(['/serien', s.id]);
          } else {
            const t = await firstValueFrom(
              this.http.post<Termin>('/api/termine', this.terminDaten()),
            );
            this.rueckmeldung.setzen(TEXTE.terminFormular.erfasst);
            await this.router.navigate(['/termine', t.id]);
          }
          break;
        case 'termin': {
          const vorher = this.termin()!;
          const t = await firstValueFrom(
            this.http.put<Termin>(`/api/termine/${vorher.id}`, this.terminDaten()),
          );
          const verschoben = Date.parse(t.beginn) !== Date.parse(vorher.beginn);
          this.rueckmeldung.setzen(
            verschoben ? TEXTE.terminFormular.verschoben : TEXTE.terminFormular.gespeichert,
          );
          await this.router.navigate(['/termine', t.id]);
          break;
        }
        case 'serie': {
          const s = await firstValueFrom(
            this.http.put<Serie>(`/api/serien/${this.serie()!.id}`, this.serieDaten()),
          );
          this.rueckmeldung.setzen(TEXTE.terminFormular.gespeichert);
          await this.router.navigate(['/serien', s.id]);
          break;
        }
      }
    } catch (e) {
      this.fehlerAnzeigen(e);
    } finally {
      this.laufendeAktion.set(null);
    }
  }

  protected absagenOeffnen(): void {
    this.grund.set('');
    this.feldFehler.update((f) => ({ ...f, grund: undefined }));
    this.absagenDialog()?.nativeElement.showModal();
  }

  protected absagenSchliessen(): void {
    this.absagenDialog()?.nativeElement.close();
  }

  async absagen(ereignis: Event): Promise<void> {
    ereignis.preventDefault();
    if (this.speichert()) return;
    const grund = this.grund().trim();
    if (!grund) {
      this.feldFehler.update((f) => ({ ...f, grund: TEXTE.terminFormular.grundPflicht }));
      this.grundFeld()?.nativeElement.focus();
      return;
    }
    this.laufendeAktion.set('absagen');
    try {
      const id = this.termin()!.id;
      await firstValueFrom(this.http.post<Termin>(`/api/termine/${id}/absagen`, { grund }));
      this.absagenSchliessen();
      this.rueckmeldung.setzen(TEXTE.terminFormular.abgesagt);
      await this.router.navigate(['/termine', id]);
    } catch (e) {
      this.absagenSchliessen();
      this.fehlerAnzeigen(e);
    } finally {
      this.laufendeAktion.set(null);
    }
  }

  private terminDaten(): TerminSchreiben {
    const w = this.werte();
    const vorher = this.termin();
    return {
      typ: w.typ,
      titel: w.titel.trim(),
      beginn: zurich(w.datum, w.beginn),
      ende: zurich(w.datum, w.ende),
      teamIds: w.teamIds,
      ort: this.ort(vorher?.ort ?? null),
      hinweise: w.hinweise.trim() || null,
      antwortfristStunden: Number(w.antwortfrist),
      treffpunkt: vorher?.treffpunkt ?? null,
    };
  }

  private serieDaten(): SerieSchreiben {
    const w = this.werte();
    return {
      typ: w.typ === 'ANLASS' ? 'ANLASS' : 'TRAINING',
      titel: w.titel.trim(),
      teamIds: w.teamIds,
      wochentag: w.wochentag,
      beginnZeit: w.beginn,
      endeZeit: w.ende,
      gueltigVon: w.gueltigVon,
      gueltigBis: w.gueltigBis,
      ort: this.ort(this.serie()?.ort ?? null),
      antwortfristStunden: Number(w.antwortfrist),
    };
  }

  /** Ort aus dem Namen; Adresse und Karten-Link eines bestehenden Orts bleiben, solange der Name gleich ist. */
  private ort(bisher: Ort | null | undefined): Ort | null {
    const name = this.werte().ort.trim();
    if (!name) return null;
    return bisher && bisher.name === name ? bisher : { name };
  }

  /** Pflichtfelder im Browser prüfen; die übrigen Regeln prüft das Backend. */
  private pruefen(): boolean {
    const w = this.werte();
    const f: Partial<Record<Feld, string>> = {};
    const pflicht = TEXTE.terminFormular.pflicht;
    if (!w.titel.trim()) f.titel = pflicht('einen Titel');
    if (this.alsSerie()) {
      if (!w.gueltigVon) f.gueltigVon = pflicht('ein Datum');
      if (!w.gueltigBis) f.gueltigBis = pflicht('ein Datum');
    } else if (!w.datum) {
      f.datum = pflicht('ein Datum');
    }
    if (!w.beginn) f.beginn = pflicht('eine Zeit');
    if (!w.ende) f.ende = pflicht('eine Zeit');
    else if (w.beginn && w.ende <= w.beginn) f.ende = TEXTE.terminFormular.endeNachBeginn;
    if (w.teamIds.length === 0) f.teamIds = TEXTE.terminFormular.teamPflicht;
    const frist = Number(w.antwortfrist);
    if (w.antwortfrist.trim() === '' || !Number.isInteger(frist)) f.antwortfrist = pflicht('eine ganze Zahl');
    else if (frist < 0) f.antwortfrist = TEXTE.terminFormular.antwortfristMindestens;
    else if (frist > 336) f.antwortfrist = TEXTE.terminFormular.antwortfristHoechstens;
    this.feldFehler.set(f);
    const erstes = Object.keys(f)[0];
    if (erstes) {
      document.getElementById(`termin-${erstes}`)?.focus();
      return false;
    }
    return true;
  }

  private fehlerAnzeigen(e: unknown): void {
    const felder = feldfehler(e);
    const zugeordnet: Partial<Record<Feld, string>> = {};
    for (const [feld, meldung] of Object.entries(felder)) {
      const ziel = API_FELDER[feld];
      if (ziel) zugeordnet[ziel] = meldung;
    }
    if (Object.keys(zugeordnet).length > 0) {
      this.feldFehler.set(zugeordnet);
      document.getElementById(`termin-${Object.keys(zugeordnet)[0]}`)?.focus();
    } else {
      this.fehler.set(fehlertext(e));
    }
  }

  private async laden(terminId: string | undefined, serieId: string | undefined): Promise<void> {
    this.laedt.set(true);
    this.fehler.set(null);
    this.feldFehler.set({});
    this.termin.set(null);
    this.serie.set(null);
    try {
      await this.teamsLaden();
      if (terminId) {
        const t = await firstValueFrom(this.http.get<Termin>(`/api/termine/${terminId}`));
        this.termin.set(t);
        this.teamsErgaenzen(t.teams);
        const b = eingabe(t.beginn);
        this.werte.set({
          ...LEER,
          typ: t.typ,
          titel: t.titel,
          datum: b.datum,
          beginn: b.zeit,
          ende: eingabe(t.ende).zeit,
          teamIds: t.teams.map((x) => x.id),
          ort: t.ort?.name ?? '',
          hinweise: t.hinweise ?? '',
          antwortfrist: String(t.antwortfristStunden ?? 24),
        });
      } else if (serieId) {
        const s = await firstValueFrom(this.http.get<Serie>(`/api/serien/${serieId}`));
        this.serie.set(s);
        this.werte.set({
          ...LEER,
          typ: s.typ,
          titel: s.titel,
          wochentag: s.wochentag,
          beginn: s.beginnZeit,
          ende: s.endeZeit,
          gueltigVon: s.gueltigVon,
          gueltigBis: s.gueltigBis,
          teamIds: s.teamIds,
          ort: s.ort?.name ?? '',
          antwortfrist: String(s.antwortfristStunden ?? 24),
        });
      } else {
        // Neu: vorgewählt ist das Team, in dem die Person Coach ist, wenn es genau eines gibt.
        const coach = (this.anmeldung.ich()?.zugehoerigkeiten ?? []).filter(
          (z) => z.funktion === 'COACH',
        );
        this.werte.set({ ...LEER, teamIds: coach.length === 1 ? [coach[0].team.id] : [] });
      }
    } catch (e) {
      const status = fehlerstatus(e);
      if (status === 404 || status === 400) {
        this.fehler.set(terminId ? TEXTE.termine.nichtGefunden : TEXTE.serie.nichtGefunden);
      } else {
        this.fehler.set(fehlertext(e));
      }
    } finally {
      this.laedt.set(false);
    }
  }

  private async teamsLaden(): Promise<void> {
    const ich = this.anmeldung.ich();
    const eigene = (ich?.zugehoerigkeiten ?? []).map((z) => z.team);
    const teams = ich?.admin ? await firstValueFrom(this.http.get<Team[]>('/api/teams')) : eigene;
    this.teams.set([...teams].sort((a, b) => a.name.localeCompare(b.name, 'de-CH')));
  }

  /** Teams eines bestehenden Termins, die nicht zu den eigenen gehören, bleiben sichtbar und gewählt. */
  private teamsErgaenzen(teams: Team[]): void {
    const bekannt = new Set(this.teams().map((t) => t.id));
    const neu = teams.filter((t) => !bekannt.has(t.id));
    if (neu.length > 0) {
      this.teams.set(
        [...this.teams(), ...neu].sort((a, b) => a.name.localeCompare(b.name, 'de-CH')),
      );
    }
  }

  /** Serie: bearbeiten dürfen Admins und Coaches eines Teams der Serie (TER-11, RR-06). */
  private darfSerieBearbeiten(): boolean {
    const s = this.serie();
    const ich = this.anmeldung.ich();
    if (!s || !ich) return false;
    return (
      ich.admin ||
      ich.zugehoerigkeiten.some((z) => z.funktion === 'COACH' && s.teamIds.includes(z.team.id))
    );
  }
}
