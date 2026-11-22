import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type {
  Attendance,
  AttendanceInput,
  AttendanceStatus,
  EventDetail,
  PersonSummary,
} from '../api/types';
import { AuthService } from '../core/auth.service';
import { errorStatus, errorText } from '../core/error-text';
import { Icon } from '../core/icon';
import { TEXTS } from '../core/texts';
import { RESPONSE_ICON, responseKey } from './response';
import { clockTime, eventDate, yearOf } from './time';

const STATUSES: AttendanceStatus[] = ['PRESENT', 'ABSENT', 'EXCUSED'];

/** Value for `data-attendance` and test IDs: the status in lowercase, e.g. `excused`. */
export function attendanceKey(status: AttendanceStatus): string {
  return status.toLowerCase();
}

/**
 * Attendance check of an event (`/termine/:eventId/praesenz`, coaches and admins only): all players
 * of the involved teams by line with their response, each marked «anwesend», «abwesend» or
 * «entschuldigt». The preselection comes from the response; nothing is saved until «Präsenz
 * speichern» (PRS-02 to PRS-05).
 */
@Component({
  selector: 'app-attendance-check',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '(window:beforeunload)': 'warnUnsaved($event)' },
  imports: [RouterLink, Icon],
  template: `
    <div class="header">
      <a class="back" [routerLink]="['/termine', eventId()]">{{ t.toEvent }}</a>
      <h1>{{ t.title }}</h1>
      @if (event(); as ev) {
        <p class="secondary-text">
          {{ ev.title }} {{ date(ev.start) }} · {{ time(ev.start) }} · {{ teamNames() }}
        </p>
      }
    </div>
    @if (message(); as m) {
      <p class="message-success" role="status">{{ m }}</p>
    }
    @if (error(); as m) {
      <p class="message-error" role="alert">{{ m }}</p>
    }
    <div class="sections" [attr.aria-busy]="loading() ? 'true' : null">
      @if (loading()) {
        <p class="empty">{{ texts.common.loading }}</p>
      } @else if (attendance(); as a) {
        <form
          class="card selection"
          data-testid="attendance-list"
          aria-labelledby="attendance-list-title"
          (submit)="save($event)"
          novalidate
        >
          <h2 id="attendance-list-title" class="section-title">{{ t.list }}</h2>
          <p class="secondary-text">{{ t.hint }}</p>
          <p class="state" data-testid="attendance-state" [attr.data-recorded]="a.recorded">
            @if (a.recorded && a.recordedAt) {
              {{ t.recordedBy(recordedByName(a), date(a.recordedAt), time(a.recordedAt)) }}
            } @else {
              {{ t.notRecorded }}
            }
          </p>
          @for (block of a.teams; track block.team.id) {
            <section
              class="team-block"
              [attr.data-testid]="'attendance-team-' + block.team.id"
              [attr.aria-labelledby]="'attendance-team-title-' + block.team.id"
            >
              <h3 class="team-title" [id]="'attendance-team-title-' + block.team.id">
                {{ block.team.name }}
              </h3>
              @for (g of block.groups; track g.groupId ?? 'no-line') {
                @let groupKey =
                  g.groupId
                    ? 'attendance-group-' + g.groupId
                    : 'attendance-no-line-' + block.team.id;
                <section
                  class="group"
                  [attr.data-testid]="groupKey"
                  [attr.aria-labelledby]="groupKey + '-title'"
                >
                  <h4 class="group-title" [id]="groupKey + '-title'">{{ g.name }}</h4>
                  <ul class="people" [attr.aria-labelledby]="groupKey + '-title'">
                    @for (e of g.entries; track e.person.id) {
                      <li
                        class="person"
                        [attr.aria-label]="name(e.person)"
                        [attr.data-testid]="'attendance-person-' + e.person.id"
                        [attr.data-response]="e.response.status"
                        [attr.data-attendance]="choice(e.person.id)"
                      >
                        <div class="person-head">
                          <span class="person-name">{{ name(e.person) }}</span>
                          <span class="response">
                            <span
                              class="symbol"
                              [attr.data-kind]="key(e.response.status)"
                              aria-hidden="true"
                            >
                              <app-icon [name]="icon[e.response.status]" [size]="14" />
                            </span>
                            <span class="status-text">{{
                              texts.response.status[e.response.status]
                            }}</span>
                            @if (e.response.comment; as k) {
                              <span class="secondary-text comment">«{{ k }}»</span>
                            }
                          </span>
                        </div>
                        <div
                          class="options"
                          role="radiogroup"
                          [attr.aria-label]="t.of(name(e.person))"
                        >
                          @for (s of statuses; track s) {
                            <label class="option" [attr.data-kind]="attendanceKey(s)">
                              <input
                                type="radio"
                                [name]="
                                  'attendance-' +
                                  block.team.id +
                                  '-' +
                                  (g.groupId ?? 'no-line') +
                                  '-' +
                                  e.person.id
                                "
                                [value]="s"
                                [checked]="choice(e.person.id) === s"
                                [disabled]="saving()"
                                (change)="choose(e.person.id, s)"
                              />
                              <span>{{ t.status[s] }}</span>
                            </label>
                          }
                        </div>
                      </li>
                    }
                  </ul>
                </section>
              }
            </section>
          } @empty {
            <p class="empty">{{ t.noPlayers }}</p>
          }
          @if (persons().length > 0) {
            <div class="save-bar">
              <p class="count-text" data-testid="attendance-summary">{{ summary() }}</p>
              @if (dirty()) {
                <p class="secondary-text" data-testid="attendance-unsaved">{{ t.unsaved }}</p>
              }
              <button
                type="submit"
                class="button button-primary"
                data-testid="attendance-save"
                [disabled]="saving()"
                [attr.aria-busy]="saving() ? 'true' : null"
              >
                {{ t.save }}
              </button>
            </div>
          }
        </form>
      }
    </div>
  `,
  styleUrls: ['./overview.css', './call-up.css', './attendance-check.css'],
})
export class AttendanceCheckPage {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  protected readonly texts = TEXTS;
  protected readonly t = TEXTS.attendanceCheck;
  protected readonly icon = RESPONSE_ICON;
  protected readonly key = responseKey;
  protected readonly attendanceKey = attendanceKey;
  protected readonly statuses = STATUSES;
  protected readonly time = clockTime;

  /** Route `/termine/:eventId/praesenz` */
  readonly eventId = input.required<string>();

  protected readonly event = signal<EventDetail | null>(null);
  protected readonly attendance = signal<Attendance | null>(null);
  /** Current choice per person; a person in two teams appears twice but has one value. */
  protected readonly choices = signal<Record<string, AttendanceStatus>>({});
  private readonly saved = signal<Record<string, AttendanceStatus>>({});
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly message = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  private readonly year = computed(() => yearOf(this.auth.me()?.now));

  protected readonly teamNames = computed(() =>
    TEXTS.events.joinTeams((this.event()?.teams ?? []).map((t) => t.name)),
  );

  /** Every player once, in display order. */
  protected readonly persons = computed(() => [
    ...new Set(
      (this.attendance()?.teams ?? []).flatMap((b) =>
        b.groups.flatMap((g) => g.entries.map((e) => e.person.id)),
      ),
    ),
  ]);

  protected readonly dirty = computed(() => {
    const now = this.choices();
    const before = this.saved();
    return this.persons().some((id) => now[id] !== before[id]);
  });

  /** Closing or reloading the tab with unsaved choices asks first (the browser shows its own text). */
  protected warnUnsaved(domEvent: BeforeUnloadEvent): void {
    if (this.dirty()) domEvent.preventDefault();
  }

  protected readonly summary = computed(() => {
    const c = this.choices();
    const count = (s: AttendanceStatus) => this.persons().filter((id) => c[id] === s).length;
    return this.t.summary(count('PRESENT'), count('ABSENT'), count('EXCUSED'));
  });

  constructor() {
    effect(() => {
      const id = this.eventId();
      untracked(() => void this.load(id));
    });
  }

  protected date(iso: string): string {
    return eventDate(iso, this.year());
  }

  protected name(p: PersonSummary): string {
    return `${p.firstName} ${p.lastName}`;
  }

  protected recordedByName(a: Attendance): string {
    return a.recordedBy ? this.name(a.recordedBy) : '–';
  }

  protected choice(personId: string): AttendanceStatus | undefined {
    return this.choices()[personId];
  }

  protected choose(personId: string, status: AttendanceStatus): void {
    this.choices.update((c) => ({ ...c, [personId]: status }));
    this.message.set(null);
  }

  /** Save all players at once (PRS-03, PRS-04). */
  protected async save(domEvent: Event): Promise<void> {
    domEvent.preventDefault();
    if (this.saving()) return;
    const c = this.choices();
    const body: AttendanceInput = {
      entries: this.persons().map((id) => ({ personId: id, status: c[id] })),
    };
    this.saving.set(true);
    this.message.set(null);
    this.error.set(null);
    try {
      const updated = await firstValueFrom(
        this.http.put<Attendance>(`/api/events/${this.eventId()}/attendance`, body),
      );
      this.apply(updated);
      this.message.set(this.t.saved);
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.saving.set(false);
    }
  }

  private apply(a: Attendance): void {
    const values: Record<string, AttendanceStatus> = {};
    a.teams.forEach((b) =>
      b.groups.forEach((g) => g.entries.forEach((e) => (values[e.person.id] = e.status))),
    );
    this.attendance.set(a);
    this.choices.set({ ...values });
    this.saved.set(a.recorded ? { ...values } : {});
  }

  private async load(id: string): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    this.event.set(null);
    this.attendance.set(null);
    try {
      const event = await firstValueFrom(this.http.get<EventDetail>(`/api/events/${id}`));
      this.event.set(event);
      // Permission and state first from the event: so without them no 403 or 409 request is made.
      const coach = event.teams.some((t) => t.myTeamRole === 'COACH');
      if (!coach && !this.auth.canManage()) {
        this.error.set(TEXTS.common.noPermission);
        return;
      }
      if (!event.canRecordAttendance) {
        this.error.set(this.notAvailable(event));
        return;
      }
      this.apply(await firstValueFrom(this.http.get<Attendance>(`/api/events/${id}/attendance`)));
    } catch (e) {
      const status = errorStatus(e);
      if (status === 404 || status === 400) {
        this.error.set(TEXTS.events.notFound);
      } else {
        this.error.set(errorText(e));
      }
    } finally {
      this.loading.set(false);
    }
  }

  private notAvailable(event: EventDetail): string {
    const now = this.auth.me()?.now;
    if (
      event.attendanceCheck &&
      event.status !== 'CANCELLED' &&
      now &&
      Date.parse(now) < Date.parse(event.start)
    ) {
      return this.t.beforeStart;
    }
    return this.t.notAvailable;
  }
}
