import { Injectable, signal } from '@angular/core';

export type Modus = 'hell' | 'duene' | 'github-dark' | 'system';

const SCHLUESSEL = 'stv-theme';
/** Wert von `data-theme` je Wahl. «System» setzt keines und folgt `prefers-color-scheme`. */
const ATTRIBUT: Record<Modus, string | null> = {
  hell: 'light',
  duene: 'duene',
  'github-dark': 'github-dark',
  system: null,
};

/**
 * Darstellung (NF-06, NF-08): Hell, Düne, GitHub Dark oder System. Die Wahl bleibt im Browser
 * gespeichert. «System» zeigt im Hellen Hell und im Dunkeln GitHub Dark.
 * Das Inline-Skript in index.html wendet die Wahl vor dem Start an.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly modus = signal<Modus>(this.gespeichert());
  /** Die Werte aus index.html, damit Hell und System sie zurückbekommen. */
  private kopfFarben: Map<HTMLMetaElement, string> | null = null;

  setzen(modus: Modus): void {
    this.modus.set(modus);
    try {
      localStorage.setItem(SCHLUESSEL, modus);
    } catch {
      // Ohne Speicher gilt die Wahl nur bis zum Neuladen.
    }
    this.anwenden(modus);
  }

  anwenden(modus: Modus = this.modus()): void {
    const html = document.documentElement;
    const attribut = ATTRIBUT[modus];
    if (attribut) {
      html.setAttribute('data-theme', attribut);
    } else {
      html.removeAttribute('data-theme');
    }
    const eigeneFarbe = modus === 'duene' || modus === 'github-dark';
    this.kopfFarbeSetzen(eigeneFarbe ? getComputedStyle(html).getPropertyValue('--bg') : null);
  }

  /** Farbe der Browser- bzw. Statusleiste: bei Düne und GitHub Dark deren Hintergrund. */
  private kopfFarbeSetzen(farbe: string | null): void {
    this.kopfFarben ??= new Map(
      Array.from(document.head.querySelectorAll<HTMLMetaElement>('meta[name="theme-color"]')).map(
        (meta) => [meta, meta.content],
      ),
    );
    for (const [meta, urspruenglich] of this.kopfFarben) {
      meta.content = farbe?.trim() || urspruenglich;
    }
  }

  private gespeichert(): Modus {
    try {
      const wert = localStorage.getItem(SCHLUESSEL);
      // Ein Wert, den es nicht mehr gibt (z.B. das frühere «dunkel»), fällt auf «System» zurück.
      if (wert !== null && wert in ATTRIBUT) {
        return wert as Modus;
      }
    } catch {
      // nichts gespeichert
    }
    return 'system';
  }
}
