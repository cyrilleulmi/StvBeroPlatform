import { Injectable, signal } from '@angular/core';

/**
 * Erfolgsmeldung, die einen Seitenwechsel überdauert (z.B. «Person erfasst» nach dem Speichern).
 * Die Zielseite zeigt sie mit `role="status"` an und nimmt sie mit `abholen()` entgegen.
 */
@Injectable({ providedIn: 'root' })
export class RueckmeldungService {
  private readonly meldung = signal<string | null>(null);

  setzen(text: string): void {
    this.meldung.set(text);
  }

  abholen(): string | null {
    const m = this.meldung();
    this.meldung.set(null);
    return m;
  }
}
