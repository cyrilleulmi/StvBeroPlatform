import { Injectable, signal } from '@angular/core';

export type ThemeMode = 'light' | 'dune' | 'github-dark' | 'system';

const STORAGE_KEY = 'stv-theme';
/** Wert von `data-theme` je Wahl. «System» setzt keines und folgt `prefers-color-scheme`. */
const ATTRIBUTE: Record<ThemeMode, string | null> = {
  light: 'light',
  dune: 'dune',
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
  readonly mode = signal<ThemeMode>(this.saved());
  /** Die Werte aus index.html, damit Hell und System sie zurückbekommen. */
  private headColors: Map<HTMLMetaElement, string> | null = null;

  set(mode: ThemeMode): void {
    this.mode.set(mode);
    try {
      localStorage.setItem(STORAGE_KEY, mode);
    } catch {
      // Ohne Speicher gilt die Wahl nur bis zum Neuladen.
    }
    this.apply(mode);
  }

  apply(mode: ThemeMode = this.mode()): void {
    const html = document.documentElement;
    const attribute = ATTRIBUTE[mode];
    if (attribute) {
      html.setAttribute('data-theme', attribute);
    } else {
      html.removeAttribute('data-theme');
    }
    const ownColor = mode === 'dune' || mode === 'github-dark';
    this.setHeadColor(ownColor ? getComputedStyle(html).getPropertyValue('--bg') : null);
  }

  /** Farbe der Browser- bzw. Statusleiste: bei Düne und GitHub Dark deren Hintergrund. */
  private setHeadColor(color: string | null): void {
    this.headColors ??= new Map(
      Array.from(document.head.querySelectorAll<HTMLMetaElement>('meta[name="theme-color"]')).map(
        (meta) => [meta, meta.content],
      ),
    );
    for (const [meta, original] of this.headColors) {
      meta.content = color?.trim() || original;
    }
  }

  private saved(): ThemeMode {
    try {
      const value = localStorage.getItem(STORAGE_KEY);
      // Ein Wert, den es nicht mehr gibt (z.B. das frühere «dunkel»), fällt auf «System» zurück.
      if (value !== null && value in ATTRIBUTE) {
        return value as ThemeMode;
      }
    } catch {
      // nichts gespeichert
    }
    return 'system';
  }
}
