import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { DriverSummary } from '../api/types';
import { errorText } from '../core/error-text';
import { name } from '../core/format';
import { TEXTS } from '../core/texts';
import { km } from './driver-list';

/**
 * Trips per season (`/fahrten`, DRV-09): coaches and admins see every person who drove, with trips and credited
 * kilometres; everyone else sees only herself. `?saison=<id>` shows another season.
 */
@Component({
  selector: 'app-trips',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <div class="header">
      <a class="back" routerLink="/profil">{{ t.toProfile }}</a>
      <h1 class="page-title">{{ t.title }}</h1>
      @if (data(); as d) {
        <p class="secondary-text" data-testid="trips-season">{{ t.season(d.seasonName) }}</p>
      }
    </div>
    @if (error(); as m) {
      <p class="message-error" role="alert">{{ m }}</p>
    }
    <div class="sections" [attr.aria-busy]="loading() ? 'true' : null">
      @if (loading()) {
        <p class="empty">{{ texts.common.loading }}</p>
      } @else if (data(); as d) {
        <section class="card" aria-labelledby="trips-title" data-testid="trips">
          <h2 id="trips-title" class="section-title">{{ d.everyone ? t.tableLabel : t.mine }}</h2>
          <p class="secondary-text">{{ t.hint }}</p>
          @if (d.rows.length === 0) {
            <p class="empty">{{ t.nobody }}</p>
          } @else if (d.everyone) {
            <div class="scroll" tabindex="0" role="region" [attr.aria-label]="t.tableLabel">
              <table [attr.aria-label]="t.tableLabel">
                <thead>
                  <tr>
                    <th scope="col">{{ t.name }}</th>
                    <th scope="col" class="number">{{ t.trips }}</th>
                    <th scope="col" class="number">{{ t.kilometres }}</th>
                  </tr>
                </thead>
                <tbody>
                  @for (r of d.rows; track r.person.id) {
                    <tr [attr.data-testid]="'trips-person-' + r.person.id">
                      <th scope="row">{{ name(r.person) }}</th>
                      <td class="number" data-column="trips">{{ r.trips }}</td>
                      <td class="number" data-column="kilometres">
                        {{ t.km(km(r.kilometres)) }}
                        @if (r.tripsWithoutDistance > 0) {
                          <span class="secondary-text note">{{ t.withoutDistance(r.tripsWithoutDistance) }}</span>
                        }
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          } @else {
            @for (r of d.rows; track r.person.id) {
              <dl class="facts" [attr.data-testid]="'trips-person-' + r.person.id">
                <dt>{{ t.trips }}</dt>
                <dd data-column="trips">{{ r.trips }}</dd>
                <dt>{{ t.kilometres }}</dt>
                <dd data-column="kilometres">
                  {{ t.km(km(r.kilometres)) }}
                  @if (r.tripsWithoutDistance > 0) {
                    <span class="secondary-text note">{{ t.withoutDistance(r.tripsWithoutDistance) }}</span>
                  }
                </dd>
              </dl>
            }
          }
        </section>
      }
    </div>
  `,
  styles: `
    :host {
      display: grid;
      gap: 16px;
      min-width: 0;
    }
    .sections {
      display: grid;
      gap: 16px;
      min-width: 0;
    }
    .card {
      display: grid;
      gap: 10px;
      min-width: 0;
    }
    .card p {
      margin: 0;
    }
    .scroll {
      overflow-x: auto;
      min-width: 0;
    }
    table {
      width: 100%;
      border-collapse: collapse;
    }
    th,
    td {
      text-align: left;
      padding: 10px 8px;
      border-bottom: 1px solid var(--border);
      vertical-align: top;
    }
    thead th {
      font-size: 13px;
      color: var(--muted);
    }
    .number {
      text-align: right;
      white-space: nowrap;
    }
    .note {
      display: block;
      white-space: normal;
    }
    .facts {
      display: grid;
      grid-template-columns: auto 1fr;
      gap: 6px 16px;
      margin: 0;
    }
    .facts dt {
      color: var(--muted);
    }
    .facts dd {
      margin: 0;
      font-weight: 700;
    }
  `,
})
export class TripsPage {
  private readonly http = inject(HttpClient);
  protected readonly texts = TEXTS;
  protected readonly t = TEXTS.trips;
  protected readonly name = name;
  protected readonly km = km;

  /** Query parameter `?saison=<id>`; without it the current season. */
  readonly saison = input<string | undefined>(undefined);

  protected readonly data = signal<DriverSummary | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  constructor() {
    effect(() => {
      const season = this.saison();
      untracked(() => void this.load(season));
    });
  }

  private async load(season: string | undefined): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      const query = season ? `?season=${encodeURIComponent(season)}` : '';
      this.data.set(await firstValueFrom(this.http.get<DriverSummary>(`/api/driver-summary${query}`)));
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.loading.set(false);
    }
  }
}
