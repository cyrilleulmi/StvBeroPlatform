import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { jahrVon, terminDatum, uhrzeit } from '../../anwesenheit/zeit';
import type { Benachrichtigung } from '../../api/typen';
import { AnmeldungService } from '../../kern/anmeldung.service';
import { BenachrichtigungenService } from '../../kern/benachrichtigungen.service';
import { fehlertext } from '../../kern/fehlertext';
import { Icon } from '../../kern/icon';
import { TEXTE } from '../../kern/texte';

/**
 * Eigene Benachrichtigungen, neueste zuerst (BEN). Ungelesene sind mit «Neu» markiert. Ein Klick öffnet den
 * Termin und markiert die Benachrichtigung als gelesen (BEN-12).
 */
@Component({
  selector: 'app-benachrichtigungen',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  template: `
    <h1>{{ texte.titel }}</h1>
    @if (fehler(); as meldung) {
      <p class="meldung-fehler" role="alert">{{ meldung }}</p>
    }
    @if (rueckmeldung(); as meldung) {
      <p class="meldung-erfolg" role="status">{{ meldung }}</p>
    }
    @if (ungelesene() > 0) {
      <div>
        <button type="button" class="button button-sekundaer" [disabled]="beschaeftigt()" (click)="alleGelesen()">
          {{ texte.alleGelesen }}
        </button>
      </div>
    }
    <section class="karte" aria-labelledby="benachrichtigungen-liste" [attr.aria-busy]="laedt() ? 'true' : null">
      <h2 id="benachrichtigungen-liste" class="visuell-versteckt">{{ texte.titel }}</h2>
      @if (laedt()) {
        <p class="leer">{{ allgemein.laden }}</p>
      } @else if (liste().length === 0) {
        <p class="leer">{{ texte.leer }}</p>
      } @else {
        <ul class="liste" [attr.aria-label]="texte.titel">
          @for (b of liste(); track b.id) {
            <li
              [attr.data-testid]="'benachrichtigung-' + b.id"
              [attr.data-gelesen]="b.read"
              [attr.data-art]="b.type"
            >
              @if (b.eventId) {
                <a class="zeile-link eintrag" [href]="'/termine/' + b.eventId" (click)="oeffnen($event, b)">
                  <span class="text">
                    @if (!b.read) {
                      <span class="chip" data-art="neu">{{ texte.neu }}</span>
                    }
                    <span class="zeile-titel">{{ b.text }}</span>
                    <span class="zeit">{{ zeitpunkt(b.createdAt) }}</span>
                  </span>
                  <app-icon name="chevron-right" [groesse]="20" />
                </a>
              } @else {
                <div class="zeile eintrag">
                  <span class="text">
                    @if (!b.read) {
                      <span class="chip" data-art="neu">{{ texte.neu }}</span>
                    }
                    <span class="zeile-titel">{{ b.text }}</span>
                    <span class="zeit">{{ zeitpunkt(b.createdAt) }}</span>
                  </span>
                  @if (!b.read) {
                    <button type="button" class="button button-klein button-sekundaer" (click)="markieren(b)">
                      {{ texte.alsGelesen }}
                    </button>
                  }
                </div>
              }
            </li>
          }
        </ul>
      }
    </section>
  `,
  styleUrls: ['../seite.css', './benachrichtigungen.css'],
})
export class Benachrichtigungen implements OnInit {
  private readonly service = inject(BenachrichtigungenService);
  private readonly router = inject(Router);
  private readonly anmeldung = inject(AnmeldungService);
  /** Jahr der Backend-Uhr, nicht des Browsers (testbarkeit.md). */
  private readonly jahr = computed(() => jahrVon(this.anmeldung.ich()?.now));
  protected readonly texte = TEXTE.benachrichtigungen;
  protected readonly allgemein = TEXTE.allgemein;
  protected readonly liste = signal<Benachrichtigung[]>([]);
  protected readonly laedt = signal(true);
  protected readonly beschaeftigt = signal(false);
  protected readonly fehler = signal<string | null>(null);
  protected readonly rueckmeldung = signal('');
  protected readonly ungelesene = computed(() => this.liste().filter((b) => !b.read).length);

  async ngOnInit(): Promise<void> {
    try {
      this.liste.set(await this.service.alle());
    } catch (e) {
      this.fehler.set(fehlertext(e));
    } finally {
      this.laedt.set(false);
    }
  }

  /** «Sa 15.11. 21:00» (mit Jahr, wenn nicht im laufenden Jahr). */
  protected zeitpunkt(iso: string): string {
    return `${terminDatum(iso, this.jahr())} ${uhrzeit(iso)}`;
  }

  /** Klick auf eine Benachrichtigung: als gelesen markieren, dann zum Termin (BEN-12). */
  protected async oeffnen(ereignis: MouseEvent, b: Benachrichtigung): Promise<void> {
    if (ereignis.button !== 0 || ereignis.ctrlKey || ereignis.metaKey || ereignis.shiftKey || ereignis.altKey) {
      return; // neuer Tab o.ä.: Browser machen lassen
    }
    ereignis.preventDefault();
    if (this.beschaeftigt()) return;
    this.beschaeftigt.set(true);
    try {
      if (!b.read) {
        await this.service.gelesen(b.id);
      }
      await this.router.navigate(['/termine', b.eventId]);
    } catch (e) {
      this.fehler.set(fehlertext(e));
    } finally {
      this.beschaeftigt.set(false);
    }
  }

  protected async markieren(b: Benachrichtigung): Promise<void> {
    await this.ausfuehren(async () => {
      await this.service.gelesen(b.id);
      this.alsGelesen([b.id]);
      this.rueckmeldung.set(this.texte.gelesenErledigt);
    });
  }

  protected async alleGelesen(): Promise<void> {
    await this.ausfuehren(async () => {
      const offen = this.liste().filter((b) => !b.read);
      for (const b of offen) {
        await this.service.gelesen(b.id);
      }
      this.alsGelesen(offen.map((b) => b.id));
      this.rueckmeldung.set(this.texte.alleGelesenErledigt);
    });
  }

  private alsGelesen(ids: string[]): void {
    this.liste.update((l) => l.map((b) => (ids.includes(b.id) ? { ...b, read: true } : b)));
  }

  private async ausfuehren(aktion: () => Promise<void>): Promise<void> {
    if (this.beschaeftigt()) return;
    this.beschaeftigt.set(true);
    this.fehler.set(null);
    this.rueckmeldung.set('');
    try {
      await aktion();
    } catch (e) {
      this.fehler.set(fehlertext(e));
    } finally {
      this.beschaeftigt.set(false);
    }
  }
}
