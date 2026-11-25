import { Injectable, signal } from '@angular/core';

export type ThemeMode = 'light' | 'dark' | 'system';

const STORAGE_KEY = 'stv-theme';
/** Value of `data-theme` per choice. «System» sets none and follows `prefers-color-scheme`. */
const ATTRIBUTE: Record<ThemeMode, string | null> = {
  light: 'light',
  dark: 'dark',
  system: null,
};

/**
 * Theme (NF-06, NF-08): Hell, Dunkel or System. The choice stays stored in the browser.
 * Without a choice the app is Hell, also on a device in dark mode (E-100): Dunkel, or «System» that follows the
 * device, has to be chosen in the profile. A stored theme that no longer exists («dune», «github-dark», E-90)
 * falls back to Hell. The inline script in index.html applies the choice before the start.
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
    // Hell and Dunkel colour the browser bar themselves; «System» keeps the two values of index.html
    const ownColor = mode !== 'system';
    this.setHeadColor(ownColor ? getComputedStyle(html).getPropertyValue('--bg') : null);
  }

  /** Color of the browser or status bar: the background of Hell or Dunkel. */
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
      // back to Hell.
      if (value !== null && Object.prototype.hasOwnProperty.call(ATTRIBUTE, value)) {
        return value as ThemeMode;
      }
    } catch {
      // nothing stored
    }
    return 'light';
  }
}
