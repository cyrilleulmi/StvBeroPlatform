import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { Saison, SaisonAnlegen } from '../../api/typen';
import { AnmeldungService } from '../../kern/anmeldung.service';
import { feldfehler, fehlertext } from '../../kern/fehlertext';
import { datum, saisonStatus } from '../../kern/format';
import { TEXTE } from '../../kern/texte';

type Feld = 'name' | 'von' | 'bis';

/** ISO-Datum um Tage bzw. Jahre verschieben, ohne Zeitzonen. */
function verschieben(iso: string, tage: number, jahre = 0): string {
  const [j, m, t] = iso.split('-').map(Number);
  const d = new Date(Date.UTC(j + jahre, m - 1, t + tage));
  return d.toISOString().slice(0, 10);
}

/**
 * Saisons (Admin, SAI-06): Liste mit Status, neue Saison mit Übernahme der Vorsaison anlegen (SAI-04),
 * aktuelle Saison wechseln (SAI-03), Archiv ansehen (SAI-02).
 */
@Component({
  selector: 'app-saisons',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  templateUrl: './saisons.html',
  styleUrl: '../seite.css',
  styles: `
    .saison {
      display: grid;
      gap: 6px;
      padding: 12px 0;
    }
    .saison-kopf {
      display: flex;
      align-items: center;
      gap: 10px;
    }
    .aktionen {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 8px 16px;
    }
    .aktionen a {
      display: inline-flex;
      align-items: center;
      min-height: var(--touch);
      font-weight: 700;
    }
    form .button {
      justify-self: start;
    }
  `,
})
export class Saisons implements OnInit {
  private readonly http = inject(HttpClient);
  protected readonly anmeldung = inject(AnmeldungService);
  protected readonly texte = TEXTE;
  protected readonly datum = datum;
  protected readonly status = saisonStatus;

  protected readonly saisons = signal<Saison[]>([]);
  protected readonly laedt = signal(true);
  /**
   * Welche Aktion gerade läuft: `anlegen` oder `aktivieren:<saisonId>`.
   * `aria-busy` trägt nur deren Schaltfläche, `disabled` alle.
   */
  protected readonly laufendeAktion = signal<string | null>(null);
  protected readonly speichert = computed(() => this.laufendeAktion() !== null);
  protected readonly fehler = signal<string | null>(null);
  protected readonly erfolg = signal<string | null>(null);
  protected readonly feldFehler = signal<Record<string, string>>({});

  protected readonly neu = signal<{
    name: string;
    von: string;
    bis: string;
    uebernehmenAus: string;
  }>({
    name: '',
    von: '',
    bis: '',
    uebernehmenAus: '',
  });

  protected readonly felder: { feld: Feld; label: string; typ: string }[] = [
    { feld: 'name', label: TEXTE.saisons.name, typ: 'text' },
    { feld: 'von', label: TEXTE.saisons.von, typ: 'date' },
    { feld: 'bis', label: TEXTE.saisons.bis, typ: 'date' },
  ];

  async ngOnInit(): Promise<void> {
    if (this.anmeldung.istAdmin()) {
      await this.laden(true);
    }
  }

  protected setzen(feld: Feld | 'uebernehmenAus', wert: string): void {
    this.neu.update((n) => ({ ...n, [feld]: wert }));
  }

  private async laden(vorschlag: boolean): Promise<void> {
    this.laedt.set(true);
    try {
      const saisons = await firstValueFrom(this.http.get<Saison[]>('/api/seasons'));
      this.saisons.set(saisons);
      if (vorschlag) {
        this.vorschlagen(saisons);
      }
    } catch (e) {
      this.fehler.set(fehlertext(e));
    } finally {
      this.laedt.set(false);
    }
  }

  /** Vorschlag für die nächste Saison: ein Jahr nach der neuesten, Übernahme aus der aktuellen. */
  private vorschlagen(saisons: Saison[]): void {
    const neueste = saisons[0];
    const aktuelle = saisons.find((s) => s.current);
    if (!neueste) {
      this.neu.set({ name: '', von: '', bis: '', uebernehmenAus: '' });
      return;
    }
    const von = verschieben(neueste.endDate, 1);
    const bis = verschieben(von, -1, 1);
    const jahr = Number(von.slice(0, 4));
    this.neu.set({
      name: `${jahr}/${String((jahr + 1) % 100).padStart(2, '0')}`,
      von,
      bis,
      uebernehmenAus: aktuelle?.id ?? neueste.id,
    });
  }

  protected async anlegen(ereignis: Event): Promise<void> {
    ereignis.preventDefault();
    if (this.speichert()) return;
    this.erfolg.set(null);
    this.fehler.set(null);
    const n = this.neu();
    const fehler: Record<string, string> = {};
    if (!n.name.trim()) fehler['name'] = TEXTE.saisons.namePflicht;
    if (!n.von) fehler['von'] = TEXTE.saisons.datumPflicht;
    if (!n.bis) fehler['bis'] = TEXTE.saisons.datumPflicht;
    this.feldFehler.set(fehler);
    if (Object.keys(fehler).length > 0) return;

    const eingabe: SaisonAnlegen = {
      name: n.name.trim(),
      startDate: n.von,
      endDate: n.bis,
      copyFrom: n.uebernehmenAus || null,
    };
    this.laufendeAktion.set('anlegen');
    try {
      const saison = await firstValueFrom(this.http.post<Saison>('/api/seasons', eingabe));
      await this.laden(true);
      this.erfolg.set(TEXTE.saisons.angelegt(saison.name));
    } catch (e) {
      this.feldFehler.set(feldfehler(e, { startDate: 'von', endDate: 'bis' }));
      this.fehler.set(fehlertext(e));
    } finally {
      this.laufendeAktion.set(null);
    }
  }

  protected async aktivieren(s: Saison): Promise<void> {
    if (this.speichert()) return;
    this.erfolg.set(null);
    this.fehler.set(null);
    this.laufendeAktion.set(`aktivieren:${s.id}`);
    try {
      await firstValueFrom(this.http.post<void>(`/api/seasons/${s.id}/activate`, null));
      await Promise.all([this.laden(false), this.anmeldung.laden()]);
      this.erfolg.set(TEXTE.saisons.aktiviert(s.name));
    } catch (e) {
      this.fehler.set(fehlertext(e));
    } finally {
      this.laufendeAktion.set(null);
    }
  }
}
