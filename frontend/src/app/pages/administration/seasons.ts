import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { Season, NewSeason } from '../../api/types';
import { AuthService } from '../../core/auth.service';
import { fieldErrorsOf, errorText } from '../../core/error-text';
import { date, seasonStatus } from '../../core/format';
import { TEXTS } from '../../core/texts';

type Field = 'name' | 'from' | 'until';

/** Shift an ISO date by days or years, without time zones. */
function shiftDate(iso: string, days: number, years = 0): string {
  const [y, m, day] = iso.split('-').map(Number);
  const d = new Date(Date.UTC(y + years, m - 1, day + days));
  return d.toISOString().slice(0, 10);
}

/**
 * Seasons (admin, SAI-06): list with status, create a new season with carry-over from the previous
 * season (SAI-04), switch the current season (SAI-03), view the archive (SAI-02).
 */
@Component({
  selector: 'app-seasons',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  templateUrl: './seasons.html',
  styleUrl: '../page.css',
  styles: `
    .season {
      display: grid;
      gap: 6px;
      padding: 12px 0;
    }
    .season-header {
      display: flex;
      align-items: center;
      gap: 10px;
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 8px 16px;
    }
    .actions a {
      display: inline-flex;
      align-items: center;
      min-height: var(--touch);
      font-weight: 700;
    }
    form .button {
      justify-self: start;
    }
  `,
})
export class Seasons implements OnInit {
  private readonly http = inject(HttpClient);
  protected readonly auth = inject(AuthService);
  protected readonly texts = TEXTS;
  protected readonly date = date;
  protected readonly status = seasonStatus;

  protected readonly seasons = signal<Season[]>([]);
  protected readonly loading = signal(true);
  /**
   * Which action is running: `create` or `activate:<seasonId>`.
   * Only its button carries `aria-busy`, all carry `disabled`.
   */
  protected readonly runningAction = signal<string | null>(null);
  protected readonly saving = computed(() => this.runningAction() !== null);
  protected readonly error = signal<string | null>(null);
  protected readonly success = signal<string | null>(null);
  protected readonly fieldErrors = signal<Record<string, string>>({});

  protected readonly newSeason = signal<{
    name: string;
    from: string;
    until: string;
    carryOverFrom: string;
  }>({
    name: '',
    from: '',
    until: '',
    carryOverFrom: '',
  });

  protected readonly fields: { field: Field; label: string; type: string }[] = [
    { field: 'name', label: TEXTS.seasons.name, type: 'text' },
    { field: 'from', label: TEXTS.seasons.from, type: 'date' },
    { field: 'until', label: TEXTS.seasons.until, type: 'date' },
  ];

  async ngOnInit(): Promise<void> {
    if (this.auth.isAdmin()) {
      await this.load(true);
    }
  }

  protected set(field: Field | 'carryOverFrom', value: string): void {
    this.newSeason.update((n) => ({ ...n, [field]: value }));
  }

  private async load(suggestion: boolean): Promise<void> {
    this.loading.set(true);
    try {
      const seasons = await firstValueFrom(this.http.get<Season[]>('/api/seasons'));
      this.seasons.set(seasons);
      if (suggestion) {
        this.suggest(seasons);
      }
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.loading.set(false);
    }
  }

  /** Suggestion for the next season: one year after the newest, carry-over from the current one. */
  private suggest(seasons: Season[]): void {
    const newest = seasons[0];
    const current = seasons.find((s) => s.current);
    if (!newest) {
      this.newSeason.set({ name: '', from: '', until: '', carryOverFrom: '' });
      return;
    }
    const from = shiftDate(newest.endDate, 1);
    const until = shiftDate(from, -1, 1);
    const year = Number(from.slice(0, 4));
    this.newSeason.set({
      name: `${year}/${String((year + 1) % 100).padStart(2, '0')}`,
      from,
      until,
      carryOverFrom: current?.id ?? newest.id,
    });
  }

  protected async create(domEvent: Event): Promise<void> {
    domEvent.preventDefault();
    if (this.saving()) return;
    this.success.set(null);
    this.error.set(null);
    const n = this.newSeason();
    const error: Record<string, string> = {};
    if (!n.name.trim()) error['name'] = TEXTS.seasons.nameRequired;
    if (!n.from) error['from'] = TEXTS.seasons.dateRequired;
    if (!n.until) error['until'] = TEXTS.seasons.dateRequired;
    this.fieldErrors.set(error);
    if (Object.keys(error).length > 0) return;

    const input: NewSeason = {
      name: n.name.trim(),
      startDate: n.from,
      endDate: n.until,
      copyFrom: n.carryOverFrom || null,
    };
    this.runningAction.set('create');
    try {
      const season = await firstValueFrom(this.http.post<Season>('/api/seasons', input));
      await this.load(true);
      this.success.set(TEXTS.seasons.created(season.name));
    } catch (e) {
      this.fieldErrors.set(fieldErrorsOf(e, { startDate: 'from', endDate: 'until' }));
      this.error.set(errorText(e));
    } finally {
      this.runningAction.set(null);
    }
  }

  protected async activate(s: Season): Promise<void> {
    if (this.saving()) return;
    this.success.set(null);
    this.error.set(null);
    this.runningAction.set(`activate:${s.id}`);
    try {
      await firstValueFrom(this.http.post<void>(`/api/seasons/${s.id}/activate`, null));
      await Promise.all([this.load(false), this.auth.load()]);
      this.success.set(TEXTS.seasons.activated(s.name));
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.runningAction.set(null);
    }
  }
}
