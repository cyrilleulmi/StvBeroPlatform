import { Injectable, signal } from '@angular/core';

export type ThemeMode = 'light' | 'dune' | 'github-dark' | 'system';

const STORAGE_KEY = 'stv-theme';
/** Value of `data-theme` per choice. «System» sets none and follows `prefers-color-scheme`. */
const ATTRIBUTE: Record<ThemeMode, string | null> = {
  light: 'light',
  dune: 'dune',
  'github-dark': 'github-dark',
  system: null,
};

/**
 * Theme (NF-06, NF-08): Hell, Düne, GitHub Dark or System. The choice stays stored in the browser.
 * «System» shows Hell in a light environment and GitHub Dark in a dark one.
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
    const ownColor = mode === 'dune' || mode === 'github-dark';
    this.setHeadColor(ownColor ? getComputedStyle(html).getPropertyValue('--bg') : null);
  }

  /** Color of the browser or status bar: for Düne and GitHub Dark their background. */
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
