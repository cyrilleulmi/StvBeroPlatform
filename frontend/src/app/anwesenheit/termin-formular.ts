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
  Spiel,
  SpielSchreiben,
  Team,
  Termin,
  TerminSchreiben,
  TerminTyp,
  Treffpunkt,
} from '../api/typen';
import { AnmeldungService } from '../kern/anmeldung.service';
import { feldfehler, fehlerregel, fehlerstatus, fehlertext } from '../kern/fehlertext';
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
  mapsUrl: string;
  hinweise: string;
  antwortfrist: string;
  treffpunktZeit: string;
  treffpunktOrt: string;
}

/** Ein Spiel im Formular eines Spieltags (AUF-14). `id` leer: neu, wird beim Speichern angelegt. */
interface SpielZeile {
  schluessel: number;
  id: string | null;
  quelle: Spiel['quelle'];
  gegner: string;
  zeit: string;
  heim: boolean;
  resultat: string | null;
  /** Importiert und von swiss unihockey nicht mehr geliefert: darf entfernt werden (SUH-06). */
  nichtMehrBeiSu: boolean;
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
  mapsUrl: '',
  hinweise: '',
  antwortfrist: '24',
  treffpunktZeit: '',
  treffpunktOrt: '',
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
  'ort.mapsUrl': 'mapsUrl',
  'treffpunkt.zeit': 'treffpunktZeit',
  'treffpunkt.ort': 'treffpunktOrt',
};

/**
 * Termin erfassen (`/termine/neu`, `?art=serie` für eine Serie), Termin bearbeiten und absagen
 * (`/termine/:terminId/bearbeiten`) und Serie bearbeiten (`/serien/:serieId/bearbeiten`).
 * TER-01, TER-04 bis TER-06, TER-08, TER-10, TER-11. Spieltage mit Spielen, Treffpunkt und
 * Google-Maps-Link (AUF-10 bis AUF-16).
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
  protected readonly spiele = signal<SpielZeile[]>([]);
  /** Feldfehler der Spiele, Schlüssel `<schluessel>-gegner` bzw. `<schluessel>-zeit`. */
  protected readonly spielFehler = signal<Record<string, string>>({});
  private naechsterSchluessel = 1;

  protected readonly spieltag = computed(() => !this.alsSerie() && this.werte().typ === 'SPIELTAG');

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

  /**
   * Termintypen zur Auswahl. Neue Einzeltermine: alle drei (AUF-16). Ein bestehender Spieltag bleibt ein
   * Spieltag, Serien sind Trainings oder Anlässe.
   */
  protected readonly typen = computed<TerminTyp[]>(() => {
    if (this.termin()?.typ === 'SPIELTAG') return ['SPIELTAG'];
    if (this.modus() === 'neu' && !this.alsSerie()) return ['TRAINING', 'SPIELTAG', 'ANLASS'];
    return ['TRAINING', 'ANLASS'];
  });

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
    if (typ === 'SPIELTAG' && this.spiele().length === 0) {
      this.spielHinzufuegen();
    }
  }

  protected spielHinzufuegen(): void {
    const schluessel = this.naechsterSchluessel++;
    this.spiele.update((z) => [
      ...z,
      {
        schluessel,
        id: null,
        quelle: 'MANUELL',
        gegner: '',
        zeit: this.werte().beginn,
        heim: true,
        resultat: null,
        nichtMehrBeiSu: false,
      },
    ]);
  }

  protected spielEntfernen(schluessel: number): void {
    this.spiele.update((z) => z.filter((x) => x.schluessel !== schluessel));
  }

  protected spielSetzen<F extends 'gegner' | 'zeit' | 'heim'>(
    schluessel: number,
    feld: F,
    wert: SpielZeile[F],
  ): void {
    this.spiele.update((z) =>
      z.map((x) => (x.schluessel === schluessel ? { ...x, [feld]: wert } : x)),
    );
    const k = `${schluessel}-${feld}`;
    if (this.spielFehler()[k]) {
      this.spielFehler.update((f) => {
        const kopie = { ...f };
        delete kopie[k];
        return kopie;
      });
    }
  }

  protected spielFehlerId(schluessel: number, feld: 'gegner' | 'zeit'): string | null {
    return this.spielFehler()[`${schluessel}-${feld}`]
      ? `spiel-${schluessel}-${feld}-fehler`
      : null;
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
            const spielFehler = await this.spieleSpeichern(t, null);
            this.rueckmeldung.setzen(spielFehler ?? TEXTE.terminFormular.erfasst);
            await this.router.navigate(['/termine', t.id]);
          }
          break;
        case 'termin': {
          const vorher = this.termin()!;
          const t = await firstValueFrom(
            this.http.put<Termin>(`/api/termine/${vorher.id}`, this.terminDaten()),
          );
          const verschoben = Date.parse(t.beginn) !== Date.parse(vorher.beginn);
          const spielFehler = await this.spieleSpeichern(t, vorher);
          this.rueckmeldung.setzen(
            spielFehler ??
              (verschoben ? TEXTE.terminFormular.verschoben : TEXTE.terminFormular.gespeichert),
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
      ort: this.ort(vorher?.ort ?? null, true),
      hinweise: w.hinweise.trim() || null,
      antwortfristStunden: Number(w.antwortfrist),
      treffpunkt: this.spieltag() ? this.treffpunkt() : (vorher?.treffpunkt ?? null),
    };
  }

  /** Treffpunkt eines Spieltags am Tag des Spieltags (AUF-12); beide Felder leer: keiner. */
  private treffpunkt(): Treffpunkt | null {
    const w = this.werte();
    const ort = w.treffpunktOrt.trim();
    if (!w.treffpunktZeit || !ort) return null;
    return { zeit: zurich(w.datum, w.treffpunktZeit), ort };
  }

  /**
   * Spiele eines Spieltags nach dem Speichern des Termins: neue anlegen, geänderte speichern, entfernte
   * löschen (AUF-14, AUF-16). Importierte Spiele bleiben unberührt, ausser swiss unihockey liefert sie
   * nicht mehr: dann dürfen sie entfernt werden (SUH-06). Liefert eine Meldung, falls etwas
   * nicht ging; der Termin selbst ist dann schon gespeichert.
   */
  private async spieleSpeichern(t: Termin, vorher: Termin | null): Promise<string | null> {
    if (t.typ !== 'SPIELTAG') return null;
    const datum = this.werte().datum;
    const zeilen = this.spiele();
    const bisher = new Map((vorher?.spiele ?? []).map((sp) => [sp.id, sp]));
    try {
      for (const sp of bisher.values()) {
        const loeschbar = sp.quelle === 'MANUELL' || sp.beiSuNichtMehrVorhanden;
        if (loeschbar && !zeilen.some((z) => z.id === sp.id)) {
          await firstValueFrom(this.http.delete<void>(`/api/termine/${t.id}/spiele/${sp.id}`));
        }
      }
      for (const z of zeilen) {
        if (z.quelle !== 'MANUELL') continue;
        const daten: SpielSchreiben = {
          gegner: z.gegner.trim(),
          anspielzeit: zurich(datum, z.zeit),
          heim: z.heim,
          resultat: z.resultat,
        };
        const alt = z.id ? bisher.get(z.id) : undefined;
        if (!alt) {
          await firstValueFrom(this.http.post<Spiel>(`/api/termine/${t.id}/spiele`, daten));
        } else if (
          alt.gegner !== daten.gegner ||
          alt.heim !== daten.heim ||
          Date.parse(alt.anspielzeit) !== Date.parse(daten.anspielzeit)
        ) {
          await firstValueFrom(this.http.put<Spiel>(`/api/termine/${t.id}/spiele/${z.id}`, daten));
        }
      }
      return null;
    } catch (e) {
      return TEXTE.terminFormular.spieleFehler(fehlertext(e));
    }
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
      ort: this.ort(this.serie()?.ort ?? null, false),
      antwortfristStunden: Number(w.antwortfrist),
    };
  }

  /**
   * Ort aus dem Namen und (bei Einzelterminen) dem Google-Maps-Link (AUF-10, AUF-11). Adresse und Koordinaten
   * eines bestehenden Orts bleiben, solange der Name gleich ist; bei Serien auch der Link.
   */
  private ort(bisher: Ort | null | undefined, mitLink: boolean): Ort | null {
    const name = this.werte().ort.trim();
    if (!name) return null;
    const basis: Ort = bisher && bisher.name === name ? { ...bisher } : { name };
    if (mitLink) {
      basis.mapsUrl = this.werte().mapsUrl.trim() || null;
    }
    return basis;
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
    if (!this.alsSerie() && w.mapsUrl.trim()) {
      if (!w.ort.trim()) f.ort = pflicht('den Ort');
      if (!/^https:\/\/\S+$/.test(w.mapsUrl.trim())) f.mapsUrl = TEXTE.terminFormular.mapsUrlFormat;
    }
    const sf: Record<string, string> = {};
    if (this.spieltag()) {
      const zeit = !!w.treffpunktZeit;
      const ort = !!w.treffpunktOrt.trim();
      if (zeit && !ort) f.treffpunktOrt = TEXTE.terminFormular.treffpunktBeide;
      if (!zeit && ort) f.treffpunktZeit = TEXTE.terminFormular.treffpunktBeide;
      for (const z of this.spiele()) {
        if (z.quelle !== 'MANUELL') continue;
        if (!z.gegner.trim()) sf[`${z.schluessel}-gegner`] = pflicht('den Gegner');
        if (!z.zeit) sf[`${z.schluessel}-zeit`] = pflicht('eine Zeit');
      }
    }
    this.spielFehler.set(sf);
    const frist = Number(w.antwortfrist);
    if (w.antwortfrist.trim() === '' || !Number.isInteger(frist))
      f.antwortfrist = pflicht('eine ganze Zahl');
    else if (frist < 0) f.antwortfrist = TEXTE.terminFormular.antwortfristMindestens;
    else if (frist > 336) f.antwortfrist = TEXTE.terminFormular.antwortfristHoechstens;
    this.feldFehler.set(f);
    const erstes = Object.keys(f)[0];
    if (erstes) {
      document.getElementById(`termin-${erstes}`)?.focus();
      return false;
    }
    const erstesSpiel = Object.keys(sf)[0];
    if (erstesSpiel) {
      document.getElementById(`spiel-${erstesSpiel}`)?.focus();
      return false;
    }
    return true;
  }

  private fehlerAnzeigen(e: unknown): void {
    const felder = feldfehler(e);
    const zugeordnet: Partial<Record<Feld, string>> = {};
    // Spieltag mit Spielen aus swiss unihockey: nur am selben Tag verschiebbar, die Meldung gehört zum Datum (SUH-05).
    const datumsregel = fehlerregel(e) === 'SUH-05';
    for (const [feld, meldung] of Object.entries(felder)) {
      const ziel =
        datumsregel && feld === 'beginn'
          ? 'datum'
          : (API_FELDER[feld] ?? (feld.startsWith('ort.') ? 'ort' : undefined));
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
    this.spiele.set([]);
    this.spielFehler.set({});
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
          mapsUrl: t.ort?.mapsUrl ?? '',
          hinweise: t.hinweise ?? '',
          antwortfrist: String(t.antwortfristStunden ?? 24),
          treffpunktZeit: t.treffpunkt ? eingabe(t.treffpunkt.zeit).zeit : '',
          treffpunktOrt: t.treffpunkt?.ort ?? '',
        });
        this.spiele.set(
          (t.spiele ?? []).map((sp) => ({
            schluessel: this.naechsterSchluessel++,
            id: sp.id,
            quelle: sp.quelle,
            gegner: sp.gegner,
            zeit: eingabe(sp.anspielzeit).zeit,
            heim: sp.heim,
            resultat: sp.resultat ?? null,
            nichtMehrBeiSu: sp.quelle !== 'MANUELL' && sp.beiSuNichtMehrVorhanden,
          })),
        );
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
