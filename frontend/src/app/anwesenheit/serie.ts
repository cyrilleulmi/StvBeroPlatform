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
import type { Ausnahme, Serie as SerieDaten } from '../api/typen';
import { AnmeldungService } from '../kern/anmeldung.service';
import { feldfehler, fehlerstatus, fehlertext } from '../kern/fehlertext';
import { datum } from '../kern/format';
import { RueckmeldungService } from '../kern/rueckmeldung.service';
import { TEXTE } from '../kern/texte';

type AusnahmeFeld = 'von' | 'bis' | 'grund';

/**
 * Serie (`/serien/:serieId`): Regel, Teams, Zeitraum und Ausnahmen (TER-05, TER-07). Coaches eines Teams der
 * Serie und Admins fügen Ausnahmen hinzu und kommen zu «Serie bearbeiten».
 */
@Component({
  selector: 'app-serie',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <div class="kopf">
      <a class="zurueck" routerLink="/termine">{{ texte.termine.alleTermine }}</a>
      <h1>{{ serie()?.titel ?? texte.termine.serie }}</h1>
      @if (serie(); as s) {
        <p class="nebentext">{{ regel() }}</p>
      }
    </div>
    @if (erfolg(); as meldung) {
      <p class="meldung-erfolg" role="status">{{ meldung }}</p>
    }
    @if (fehler(); as meldung) {
      <p class="meldung-fehler" role="alert">{{ meldung }}</p>
    }
    <div class="abschnitte" [attr.aria-busy]="laedt() ? 'true' : null">
      @if (laedt()) {
        <p class="leer">{{ texte.allgemein.laden }}</p>
      } @else if (serie(); as s) {
        <section class="karte" aria-labelledby="serie-angaben">
          <h2 id="serie-angaben" class="abschnitt-titel">{{ texte.termine.angaben }}</h2>
          <dl class="angaben">
            <dt>{{ texte.terminFormular.typ }}</dt>
            <dd>{{ texte.termine.typ[s.typ] }}</dd>
            <dt>{{ texte.terminFormular.wochentag }}</dt>
            <dd>{{ texte.terminFormular.wochentage[s.wochentag] }}</dd>
            <dt>{{ texte.termine.zeit }}</dt>
            <dd>{{ texte.termine.zeitraum(s.beginnZeit, s.endeZeit) }}</dd>
            <dt>{{ texte.serie.zeitraum }}</dt>
            <dd>{{ texte.serie.zeitraumText(datum(s.gueltigVon), datum(s.gueltigBis)) }}</dd>
            <dt>{{ texte.termine.teams }}</dt>
            <dd>{{ teams() }}</dd>
            @if (s.ort) {
              <dt>{{ texte.termine.ort }}</dt>
              <dd>{{ s.ort.name }}</dd>
            }
            <dt>{{ texte.termine.antwortfrist }}</dt>
            <dd>{{ s.antwortfristStunden ?? 24 }} Stunden vor Beginn</dd>
          </dl>
          @if (darfBearbeiten()) {
            <a
              class="button button-sekundaer button-klein"
              [routerLink]="['/serien', s.id, 'bearbeiten']"
              >{{ texte.serie.bearbeiten }}</a
            >
          }
        </section>

        <section class="karte" aria-labelledby="serie-ausnahmen" data-testid="ausnahmen">
          <h2 id="serie-ausnahmen" class="abschnitt-titel">
            {{ texte.serie.ausnahmen }} <span class="anzahl">{{ s.ausnahmen.length }}</span>
          </h2>
          @if (s.ausnahmen.length === 0) {
            <p class="leer">{{ texte.serie.keineAusnahmen }}</p>
          } @else {
            <ul class="liste" [attr.aria-label]="texte.serie.ausnahmen">
              @for (a of s.ausnahmen; track $index) {
                <li class="zeile">{{ ausnahmeText(a) }}</li>
              }
            </ul>
          }
          @if (darfBearbeiten()) {
            <form
              class="formular ausnahme"
              (submit)="ausnahmeAnlegen($event)"
              novalidate
              aria-labelledby="ausnahme-titel"
            >
              <h3 id="ausnahme-titel">{{ texte.serie.ausnahmeHinzufuegen }}</h3>
              <p class="nebentext" id="ausnahme-hinweis">{{ texte.serie.ausnahmeHinweis }}</p>
              <div class="feldreihe">
                @for (f of datumsfelder; track f.feld) {
                  <div class="feld">
                    <label [for]="'ausnahme-' + f.feld">{{ f.label }}</label>
                    <input
                      [id]="'ausnahme-' + f.feld"
                      type="date"
                      [value]="neu()[f.feld]"
                      (input)="setzen(f.feld, $any($event.target).value)"
                      [attr.aria-invalid]="feldFehler()[f.feld] ? 'true' : null"
                      [attr.aria-describedby]="
                        feldFehler()[f.feld] ? 'ausnahme-' + f.feld + '-fehler' : null
                      "
                    />
                    @if (feldFehler()[f.feld]; as m) {
                      <p class="feldfehler" [id]="'ausnahme-' + f.feld + '-fehler'">{{ m }}</p>
                    }
                  </div>
                }
              </div>
              <div class="feld">
                <label for="ausnahme-grund">{{ texte.serie.grund }}</label>
                <input
                  id="ausnahme-grund"
                  type="text"
                  maxlength="200"
                  autocomplete="off"
                  [value]="neu().grund"
                  (input)="setzen('grund', $any($event.target).value)"
                  [attr.aria-invalid]="feldFehler().grund ? 'true' : null"
                  [attr.aria-describedby]="
                    feldFehler().grund ? 'ausnahme-grund-fehler' : 'ausnahme-hinweis'
                  "
                />
                @if (feldFehler().grund; as m) {
                  <p class="feldfehler" id="ausnahme-grund-fehler">{{ m }}</p>
                }
              </div>
              <button
                type="submit"
                class="button button-primaer"
                [disabled]="speichert()"
                [attr.aria-busy]="speichert() ? 'true' : null"
              >
                {{ texte.serie.ausnahmeHinzufuegen }}
              </button>
            </form>
          }
        </section>
      }
    </div>
  `,
  styleUrl: './termin.css',
  styles: `
    .ausnahme {
      margin-top: 16px;
      padding-top: 16px;
      border-top: 1px solid var(--border);
    }
    .ausnahme h3,
    .ausnahme p {
      margin: 0;
    }
    .ausnahme .button {
      justify-self: start;
    }
  `,
})
export class Serie {
  private readonly http = inject(HttpClient);
  private readonly anmeldung = inject(AnmeldungService);
  private readonly rueckmeldung = inject(RueckmeldungService);
  protected readonly texte = TEXTE;
  protected readonly datum = datum;
  protected readonly datumsfelder: { feld: 'von' | 'bis'; label: string }[] = [
    { feld: 'von', label: TEXTE.serie.von },
    { feld: 'bis', label: TEXTE.serie.bis },
  ];

  /** Route `/serien/:serieId` */
  readonly serieId = input.required<string>();

  protected readonly serie = signal<SerieDaten | null>(null);
  protected readonly laedt = signal(true);
  protected readonly speichert = signal(false);
  protected readonly fehler = signal<string | null>(null);
  protected readonly erfolg = signal<string | null>(null);
  protected readonly feldFehler = signal<Partial<Record<AusnahmeFeld, string>>>({});
  protected readonly neu = signal<Record<AusnahmeFeld, string>>({ von: '', bis: '', grund: '' });

  protected readonly regel = computed(() => {
    const s = this.serie();
    return s
      ? TEXTE.serie.regel(TEXTE.terminFormular.wochentage[s.wochentag], s.beginnZeit, s.endeZeit)
      : '';
  });

  protected readonly teams = computed(() => {
    const s = this.serie();
    if (!s) return '';
    // Alle Teams der Serie mit Namen aus der API, auch die, in denen ich nicht bin (TER-11).
    return TEXTE.termine.teamsVerbinden(s.teams.map((t) => t.name));
  });

  /** Admins und Coaches eines Teams der Serie (TER-11, RR-06). */
  protected readonly darfBearbeiten = computed(() => {
    const s = this.serie();
    const ich = this.anmeldung.ich();
    if (!s || !ich) return false;
    return (
      ich.admin ||
      ich.zugehoerigkeiten.some((z) => z.funktion === 'COACH' && s.teamIds.includes(z.team.id))
    );
  });

  constructor() {
    this.erfolg.set(this.rueckmeldung.abholen());
    effect(() => {
      const id = this.serieId();
      untracked(() => void this.laden(id));
    });
  }

  protected ausnahmeText(a: Ausnahme): string {
    return TEXTE.serie.ausnahmeText(a.grund, datum(a.von), datum(a.bis));
  }

  protected setzen(feld: AusnahmeFeld, wert: string): void {
    this.neu.update((n) => ({ ...n, [feld]: wert }));
    if (this.feldFehler()[feld]) {
      this.feldFehler.update((f) => ({ ...f, [feld]: undefined }));
    }
  }

  async ausnahmeAnlegen(ereignis: Event): Promise<void> {
    ereignis.preventDefault();
    if (this.speichert()) return;
    const n = this.neu();
    const f: Partial<Record<AusnahmeFeld, string>> = {};
    if (!n.von) f.von = TEXTE.terminFormular.pflicht('ein Datum');
    if (!n.bis) f.bis = TEXTE.terminFormular.pflicht('ein Datum');
    else if (n.von && n.bis < n.von) f.bis = TEXTE.serie.bisVorVon;
    if (!n.grund.trim()) f.grund = TEXTE.terminFormular.grundPflicht;
    this.feldFehler.set(f);
    const erstes = Object.keys(f)[0];
    if (erstes) {
      document.getElementById(`ausnahme-${erstes}`)?.focus();
      return;
    }
    this.speichert.set(true);
    this.fehler.set(null);
    this.erfolg.set(null);
    try {
      const s = await firstValueFrom(
        this.http.post<SerieDaten>(`/api/serien/${this.serieId()}/ausnahmen`, {
          von: n.von,
          bis: n.bis,
          grund: n.grund.trim(),
        }),
      );
      this.serie.set(s);
      this.neu.set({ von: '', bis: '', grund: '' });
      this.erfolg.set(TEXTE.serie.ausnahmeAngelegt(n.grund.trim()));
    } catch (e) {
      const felder = feldfehler(e);
      if (Object.keys(felder).length > 0) {
        this.feldFehler.set(felder);
      } else {
        this.fehler.set(fehlertext(e));
      }
    } finally {
      this.speichert.set(false);
    }
  }

  private async laden(id: string): Promise<void> {
    this.laedt.set(true);
    this.fehler.set(null);
    this.serie.set(null);
    try {
      this.serie.set(await firstValueFrom(this.http.get<SerieDaten>(`/api/serien/${id}`)));
    } catch (e) {
      const status = fehlerstatus(e);
      this.fehler.set(status === 404 || status === 400 ? TEXTE.serie.nichtGefunden : fehlertext(e));
    } finally {
      this.laedt.set(false);
    }
  }
}
