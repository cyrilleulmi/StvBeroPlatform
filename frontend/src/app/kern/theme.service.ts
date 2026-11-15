import { Injectable, signal } from '@angular/core';

export type Modus = 'hell' | 'dunkel' | 'system';

const SCHLUESSEL = 'stv-theme';

/**
 * Hell/Dunkel/System (NF-06). Die Wahl bleibt im Browser gespeichert. «System» folgt
 * `prefers-color-scheme`. Das Inline-Skript in index.html wendet die Wahl vor dem Start an.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly modus = signal<Modus>(this.gespeichert());

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
    if (modus === 'hell') {
      html.setAttribute('data-theme', 'light');
    } else if (modus === 'dunkel') {
      html.setAttribute('data-theme', 'dark');
    } else {
      html.removeAttribute('data-theme');
    }
  }

  private gespeichert(): Modus {
    try {
      const wert = localStorage.getItem(SCHLUESSEL);
      if (wert === 'hell' || wert === 'dunkel' || wert === 'system') {
        return wert;
      }
    } catch {
      // nichts gespeichert
    }
    return 'system';
  }
}
