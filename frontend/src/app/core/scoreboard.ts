import { afterNextRender, ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { TEXTS } from './texts';

export interface ResponseCounts {
  attending: number;
  declined: number;
  maybe: number;
  pending: number;
}

type Key = keyof ResponseCounts;
const KEYS: Key[] = ['attending', 'declined', 'maybe', 'pending'];

/**
 * The four response counts as the columns of a scoreboard: number above its word. The words come from
 * `TEXTS.overview`, so the text stays «8 dabei · 3 nicht dabei · 1 vielleicht · 2 offen» for screen
 * readers and tests; the separators are only hidden visually. The host is placed inside an element with
 * the class `scoreboard` (styles.css).
 */
@Component({
  selector: 'app-scoreboard',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { style: 'display: contents', '[class.live]': 'live()' },
  template: `
    @for (c of cells(); track c.key + ':' + c.count; let last = $last) {
      <span class="score" [attr.data-counter]="c.key"
        ><b class="score-number">{{ c.count }}</b><span class="score-label">{{ c.label }}</span></span
      >
      @if (!last) {
        <span class="separator"> · </span>
      }
    }
  `,
})
export class Scoreboard {
  readonly counts = input.required<ResponseCounts>();

  /**
   * Set after the first render. Only then does a changed count enter with motion (styles.css,
   * emil-design-eng): the digit cell is tracked by key and count, so a new count is a new element.
   */
  protected readonly live = signal(false);

  constructor() {
    afterNextRender(() => this.live.set(true));
  }

  protected readonly cells = computed(() =>
    KEYS.map((key) => {
      const count = this.counts()[key];
      const text = TEXTS.overview[key](count);
      // Keep the leading space: it separates number and word in the text content.
      return { key, count, label: text.slice(String(count).length) };
    }),
  );
}
