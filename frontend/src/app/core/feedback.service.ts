import { Injectable, signal } from '@angular/core';

/**
 * Success message that survives a page change (e.g. «Person erfasst» after saving).
 * The target page shows it with `role="status"` and takes it with `take()`.
 */
@Injectable({ providedIn: 'root' })
export class FeedbackService {
  private readonly message = signal<string | null>(null);

  set(text: string): void {
    this.message.set(text);
  }

  take(): string | null {
    const m = this.message();
    this.message.set(null);
    return m;
  }
}
