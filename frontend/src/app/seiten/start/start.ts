import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { Kachel, TerminKurz } from '../../api/typen';
import { MeineAntwort } from '../../anwesenheit/meine-antwort';
import { jahrVon, terminDatum, uhrzeit } from '../../anwesenheit/zeit';
import { AnmeldungService } from '../../kern/anmeldung.service';
import { fehlertext } from '../../kern/fehlertext';
import { Icon } from '../../kern/icon';
import { TEXTE } from '../../kern/texte';

/**
 * Start: Begrüssung, nächster eigener Termin mit Antwort-Buttons (F-04, ANM-08) und Kacheln aus
 * `GET /api/kacheln` (NF-11, NF-12, RR-05).
 */
@Component({
  selector: 'app-start',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon, MeineAntwort],
  templateUrl: './start.html',
  styleUrl: './start.css',
})
export class Start implements OnInit {
  private readonly http = inject(HttpClient);
  protected readonly anmeldung = inject(AnmeldungService);
  protected readonly texte = TEXTE;

  protected readonly kacheln = signal<Kachel[]>([]);
  protected readonly laedt = signal(true);
  protected readonly fehler = signal<string | null>(null);

  /** Nächster geplanter Termin der eigenen Teams; abgesagte überspringen. */
  protected readonly naechster = signal<TerminKurz | null>(null);
  protected readonly terminLaedt = signal(true);
  protected readonly terminFehler = signal<string | null>(null);

  private readonly jahr = computed(() => jahrVon(this.anmeldung.ich()?.jetzt));

  async ngOnInit(): Promise<void> {
    void this.terminLaden();
    try {
      this.kacheln.set(await firstValueFrom(this.http.get<Kachel[]>('/api/kacheln')));
    } catch (e) {
      this.fehler.set(fehlertext(e));
    } finally {
      this.laedt.set(false);
    }
  }

  /** «Training Di 17.11. 18:30–20:00 · Team 1 + Team 2» */
  protected beschreibung(t: TerminKurz): string {
    return `${terminDatum(t.beginn, this.jahr())} · ${TEXTE.termine.zeitraum(uhrzeit(t.beginn), uhrzeit(t.ende))} · ${TEXTE.termine.teamsVerbinden(t.teams.map((x) => x.name))}`;
  }

  protected kartenName(t: TerminKurz): string {
    return TEXTE.termine.karte(t.titel, terminDatum(t.beginn, this.jahr()), uhrzeit(t.beginn));
  }

  /** Nach einer Antwort: Zähler nachladen, ohne die Seite neu zu laden. */
  protected async nachladen(id: string): Promise<void> {
    try {
      this.naechster.set(await firstValueFrom(this.http.get<TerminKurz>(`/api/termine/${id}`)));
    } catch (e) {
      this.terminFehler.set(fehlertext(e));
    }
  }

  private async terminLaden(): Promise<void> {
    try {
      const termine = await firstValueFrom(this.http.get<TerminKurz[]>('/api/termine'));
      this.naechster.set(termine.find((t) => t.status !== 'ABGESAGT') ?? null);
    } catch (e) {
      this.terminFehler.set(fehlertext(e));
    } finally {
      this.terminLaedt.set(false);
    }
  }
}
