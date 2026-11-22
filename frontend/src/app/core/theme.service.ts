import { Injectable, signal } from '@angular/core';

export type ThemeMode = 'light' | 'dune' | 'slate' | 'anthracite' | 'night-blue' | 'system';

const STORAGE_KEY = 'stv-theme';
/** Value of `data-theme` per choice. «System» sets none and follows `prefers-color-scheme`. */
const ATTRIBUTE: Record<ThemeMode, string | null> = {
  light: 'light',
  dune: 'dune',
  slate: 'slate',
  anthracite: 'anthracite',
  'night-blue': 'night-blue',
  system: null,
};

/**
 * Theme (NF-06, NF-08): Hell, Düne, one of three dark themes (Schiefer, Anthrazit, Nachtblau) or System. The choice stays stored in the browser.
 * «System» shows Hell in a light environment and Schiefer in a dark one. A stored «github-dark» from
 * before the Sporthalle PoC falls back to «System».
 * The inline script in index.html applies the choice before the start.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly mode = signal<ThemeMode>(this.stored());
  /** The values from index.html, so that Hell and System get them back. */
  private headColors: Map<HTMLMetaElement, string> | null = null;

  set(mode: ThemeMode): void {
    this.mode.set(mode);
    try {
      localStorage.setItem(STORAGE_KEY, mode);
    } catch {
      // Without storage the choice only lasts until the next reload.
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
    const ownColor = mode !== 'light' && mode !== 'system';
    this.setHeadColor(ownColor ? getComputedStyle(html).getPropertyValue('--bg') : null);
  }

  /** Color of the browser or status bar: for Düne and the dark themes their background. */
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

  private stored(): ThemeMode {
    try {
      const value = localStorage.getItem(STORAGE_KEY);
      // A value that no longer exists (e.g. the former German «hell», «duene» or «dunkel») falls
      // back to «System».
      if (value !== null && Object.prototype.hasOwnProperty.call(ATTRIBUTE, value)) {
        return value as ThemeMode;
      }
    } catch {
      // nothing stored
    }
    return 'system';
  }
}
