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
  EventDetail as EventData,
  EventSection,
  Overview as OverviewData,
} from '../api/types';
import { AuthService } from '../core/auth.service';
import { errorStatus, errorText } from '../core/error-text';
import { FeedbackService } from '../core/feedback.service';
import { TEXTS } from '../core/texts';
import { MyResponse } from './my-response';
import { MatchDay } from './match-day';
import { DriverListSection } from '../drivers/driver-list';
import { TimelineSection } from '../drivers/timeline';
import { EventLineupsSection } from './event-lineups';
import { OverviewSection } from './overview';
import { yearOf, eventDate, clockTime } from './time';

/** Sections of a match day if the variant names none; «Anmeldungen» first (VAR-07). */
const DEFAULT_SECTIONS: EventSection[] = [
  'RESPONSES',
  'MATCHES',
  'CALL_UP',
  'TIMELINE',
  'DRIVER_LIST',
  'LINEUPS',
  'ATTENDANCE',
  'DETAILS',
];

/**
 * Event (`/termine/:eventId`): details, status (cancelled with reason, rescheduled), for coaches the
 * link to edit (TER-04, TER-06), the own response and the overview by team and line (F-04, ANM).
 * For match days also call-up and matches (F-05, AUF), the timeline (TML), the driver list (F-20, DRV)
 * and the special line-ups (LIN-16). Which sections are shown and in which order is set by the event
 * variant of the team (VAR-07); the own response always stays on top.
 */
@Component({
  selector: 'app-event',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    MyResponse,
    MatchDay,
    TimelineSection,
    DriverListSection,
    EventLineupsSection,
    OverviewSection,
  ],
  template: `
    <div class="header">
      <a class="back" routerLink="/termine">{{ texts.events.allEvents }}</a>
      <h1>{{ event()?.title ?? texts.events.title }}</h1>
      @if (event(); as t) {
        <p class="secondary-text">{{ date(t.start) }} · {{ period() }} · {{ teams() }}</p>
        @if (t.variant; as v) {
          <p class="chips">
            <span class="tag-chip" data-testid="event-variant" [attr.data-tag]="v.color">{{
              v.name
            }}</span>
          </p>
        }
      }
    </div>
    @if (success(); as message) {
      <p class="message-success" role="status">{{ message }}</p>
    }
    @if (error(); as message) {
      <p class="message-error" role="alert">{{ message }}</p>
    }
    <div class="sections" [attr.aria-busy]="loading() ? 'true' : null">
      @if (loading()) {
        <p class="empty">{{ texts.common.loading }}</p>
      } @else if (event(); as t) {
        @if (t.status === 'CANCELLED') {
          <div class="message-error status" data-testid="event-status">
            <strong>{{ texts.events.cancelledWithReason(t.cancellationReason ?? '') }}</strong>
            <span>{{ texts.events.cancelledNotice }}</span>
          </div>
        } @else if (t.rescheduled) {
          <p class="message-notice" data-testid="event-status">
            {{ texts.events.rescheduledNotice }}
          </p>
        }
        @if (isMember()) {
          <section class="card" aria-labelledby="event-my-response">
            <h2 id="event-my-response" class="section-title">
              {{ texts.response.myResponse }}
            </h2>
            @if (t.myParticipation === 'OPTIONAL') {
              <p class="optional-note" data-testid="event-optional">
                <strong>{{ texts.participation.forYou }}.</strong>
                {{ texts.participation.forYouText }}
              </p>
            }
            <app-my-response
              [eventId]="t.id"
              [response]="t.myResponse"
              [name]="texts.response.myResponse"
              [locked]="lock()"
              [details]="true"
              [travel]="t.type === 'MATCH_DAY'"
              (changed)="reload()"
            />
          </section>
        }
        @for (section of sections(); track section) {
          @switch (section) {
            @case ('RESPONSES') {
              @if (overview(); as u) {
                <app-overview
                  [eventId]="t.id"
                  [data]="u"
                  [coachIn]="coachIn()"
                  [allTeams]="auth.canManage()"
                  [meId]="auth.me()?.id ?? null"
                  [responsesOpen]="!lock()"
                  (changed)="reload()"
                />
              }
            }
            @case ('MATCHES') {
              @if (t.type === 'MATCH_DAY') {
                <app-match-day
                  part="matches"
                  [event]="t"
                  [canEnterResult]="coachOrAdmin() && t.status !== 'CANCELLED'"
                  [canConfirm]="!!t.canEdit"
                  (changed)="reload()"
                />
              }
            }
            @case ('CALL_UP') {
              <!-- Only where the variant makes a call-up (VAR-06) -->
              @if (t.type === 'MATCH_DAY' && t.variant?.callUp) {
                <app-match-day part="callUp" [event]="t" [canCallUp]="coachOrAdmin()" />
              }
            }
            @case ('TIMELINE') {
              @if (t.type === 'MATCH_DAY') {
                <app-timeline
                  [eventId]="t.id"
                  [team]="t.teams[0]?.name ?? ''"
                  [version]="version()"
                  (changed)="reload()"
                />
              }
            }
            @case ('DRIVER_LIST') {
              @if (t.type === 'MATCH_DAY') {
                <app-driver-list
                  [eventId]="t.id"
                  [teamId]="t.teams[0]?.id ?? null"
                  [meId]="auth.me()?.id ?? null"
                  [canEditEvent]="!!t.canEdit"
                  [version]="version()"
                  (changed)="reload()"
                />
              }
            }
            @case ('LINEUPS') {
              @if (t.type === 'MATCH_DAY') {
                <app-event-lineups
                  [eventId]="t.id"
                  [teamId]="t.teams[0]?.id ?? null"
                  [canEdit]="coachOrAdmin()"
                  [version]="version()"
                />
              }
            }
            @case ('ATTENDANCE') {
              @if (t.myAttendance; as a) {
                <section
                  class="card my-attendance"
                  aria-labelledby="event-my-attendance"
                  data-testid="my-attendance"
                  [attr.data-attendance]="a"
                >
                  <h2 id="event-my-attendance" class="section-title">
                    {{ texts.attendanceCheck.yourAttendance }}
                  </h2>
                  <p>{{ texts.attendanceCheck.yourAttendanceText(texts.attendanceCheck.status[a]) }}</p>
                </section>
              }
              @if (attendanceSection(); as s) {
                <section
                  class="card attendance"
                  aria-labelledby="event-attendance"
                  data-testid="attendance-section"
                >
                  <h2 id="event-attendance" class="section-title">
                    {{ texts.attendanceCheck.section }}
                  </h2>
                  @if (s === 'open') {
                    <p
                      data-testid="attendance-recorded"
                      [attr.data-recorded]="t.attendanceRecorded === true"
                    >
                      {{
                        t.attendanceRecorded
                          ? texts.attendanceCheck.recorded
                          : texts.attendanceCheck.notRecorded
                      }}
                    </p>
                    <a
                      class="button button-primary button-small"
                      data-testid="attendance-record"
                      [routerLink]="['/termine', t.id, 'praesenz']"
                      >{{
                        t.attendanceRecorded
                          ? texts.attendanceCheck.correct
                          : texts.attendanceCheck.record
                      }}</a
                    >
                  } @else {
                    <p class="secondary-text">{{ texts.attendanceCheck.beforeStart }}</p>
                  }
                </section>
              }
            }
            @case ('DETAILS') {
              <section class="card" aria-labelledby="event-details">
                <h2 id="event-details" class="section-title">{{ texts.events.details }}</h2>
                <dl class="facts">
                  <dt>{{ texts.events.date }}</dt>
                  <dd>{{ date(t.start) }}</dd>
                  <dt>{{ texts.events.time }}</dt>
                  <dd>{{ period() }}</dd>
                  <dt>{{ texts.events.teams }}</dt>
                  <dd>{{ teams() }}</dd>
                  @if (t.location) {
                    <dt>{{ texts.events.location }}</dt>
                    <dd>
                      {{ t.location.name }}
                      @if (t.location.address) {
                        , {{ t.location.address }}
                      }
                      @if (t.location.mapsUrl) {
                        <br />
                        <a
                          [href]="t.location.mapsUrl"
                          target="_blank"
                          rel="noopener"
                          data-testid="venue-map"
                          >{{ texts.events.openMap }}</a
                        >
                      }
                    </dd>
                  }
                  @if (t.meetingPoint; as mp) {
                    <dt>{{ texts.events.meetingPoint }}</dt>
                    <dd data-testid="meeting-point">
                      {{ mp.time ? clockTime(mp.time) : texts.events.meetingPointTimeOpen }} ·
                      {{ mp.location }}
                    </dd>
                  }
                  <dt>{{ texts.events.responseDeadline }}</dt>
                  <dd data-testid="response-deadline">
                    {{
                      texts.events.responseDeadlineText(
                        date(t.responseDeadline),
                        clockTime(t.responseDeadline),
                        t.responseDeadlineHours ?? 24
                      )
                    }}
                  </dd>
                  @if (t.notes) {
                    <dt>{{ texts.events.notes }}</dt>
                    <dd class="notes">{{ t.notes }}</dd>
                  }
                  @if (t.seriesId) {
                    <dt>{{ texts.events.series }}</dt>
                    <dd>
                      @if (t.canEdit) {
                        <a [routerLink]="['/serien', t.seriesId]">{{ texts.events.viewSeries }}</a>
                      } @else {
                        {{ texts.events.partOfSeries }}
                      }
                    </dd>
                  }
                </dl>
                @if (t.canEdit) {
                  <a
                    class="button button-secondary button-small edit"
                    [routerLink]="['/termine', t.id, 'bearbeiten']"
                    >{{ texts.events.edit }}</a
                  >
                }
              </section>
            }
          }
        }
      }
    </div>
  `,
  styleUrl: './event.css',
})
export class EventPage {
  private readonly http = inject(HttpClient);
  protected readonly auth = inject(AuthService);
  private readonly feedback = inject(FeedbackService);
  protected readonly texts = TEXTS;
  protected readonly clockTime = clockTime;

  /** Route `/termine/:eventId` */
  readonly eventId = input.required<string>();

  protected readonly event = signal<EventData | null>(null);
  protected readonly overview = signal<OverviewData | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly success = signal<string | null>(null);
  /** Counts the reloads, so that the driver list loads again as well (DRV-04 follows the responses). */
  protected readonly version = signal(0);

  private readonly year = computed(() => yearOf(this.auth.me()?.now));

  protected readonly period = computed(() => {
    const t = this.event();
    return t ? TEXTS.events.period(clockTime(t.start), clockTime(t.end)) : '';
  });

  /** Sections in the order of the event variant (VAR-07); «Angaben» is always among them. */
  protected readonly sections = computed<EventSection[]>(() => {
    const sections = [...(this.event()?.variant?.sections ?? DEFAULT_SECTIONS)];
    if (!sections.includes('DETAILS')) sections.push('DETAILS');
    return sections;
  });

  /** Member of an involved team: can respond for themselves (ANM-01). */
  protected readonly isMember = computed(() =>
    (this.event()?.teams ?? []).some((t) => !!t.myTeamRole),
  );

  /** Teams of the event in which the logged-in person is coach (RR-06). */
  protected readonly coachIn = computed(() =>
    (this.event()?.teams ?? []).filter((t) => t.myTeamRole === 'COACH').map((t) => t.id),
  );

  /** Coach (of any team, E-69) or admin: call-up and results (RR-03). */
  protected readonly coachOrAdmin = computed(
    () => this.coachIn().length > 0 || this.auth.canManage(),
  );

  /**
   * Section «Präsenz» for coaches and admins (PRS-02): `open` from the start on, `later` with the check on
   * but before the start, otherwise not shown (check off, cancelled).
   */
  protected readonly attendanceSection = computed<'open' | 'later' | null>(() => {
    const t = this.event();
    if (!t || !this.coachOrAdmin() || !t.attendanceCheck || t.status === 'CANCELLED') return null;
    if (t.canRecordAttendance) return 'open';
    const now = this.auth.me()?.now;
    return now && Date.parse(now) < Date.parse(t.start) ? 'later' : null;
  });

  /** Explanation why responding is no longer possible (TER-06), otherwise `null`. */
  protected readonly lock = computed(() => {
    const t = this.event();
    if (!t) return null;
    if (t.status === 'CANCELLED') return TEXTS.response.cancelled;
    const now = this.auth.me()?.now;
    if (now && Date.parse(t.end) <= Date.parse(now)) return TEXTS.response.past;
    return null;
  });

  /** Teams of the event; the ones invited without obligation are named as such (TER-17). */
  protected readonly teams = computed(() => {
    const t = this.event();
    const optional = t?.optionalTeamIds ?? [];
    return TEXTS.events.joinTeams(
      (t?.teams ?? []).map((x) =>
        optional.includes(x.id) ? TEXTS.participation.optionalTeam(x.name) : x.name,
      ),
    );
  });

  constructor() {
    this.success.set(this.feedback.take());
    effect(() => {
      const id = this.eventId();
      untracked(() => void this.load(id));
    });
  }

  protected date(iso: string): string {
    return eventDate(iso, this.year());
  }

  /** After a response: event (own response, counters) and overview without reloading the page. */
  protected async reload(): Promise<void> {
    const id = this.eventId();
    try {
      const [event, overview] = await Promise.all([
        firstValueFrom(this.http.get<EventData>(`/api/events/${id}`)),
        firstValueFrom(this.http.get<OverviewData>(`/api/events/${id}/overview`)),
      ]);
      this.event.set(event);
      this.overview.set(overview);
      this.version.update((v) => v + 1);
    } catch (e) {
      this.error.set(errorText(e));
    }
  }

  private async load(id: string): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    this.event.set(null);
    this.overview.set(null);
    try {
      const [event, overview] = await Promise.all([
        firstValueFrom(this.http.get<EventData>(`/api/events/${id}`)),
        firstValueFrom(this.http.get<OverviewData>(`/api/events/${id}/overview`)),
      ]);
      this.event.set(event);
      this.overview.set(overview);
    } catch (e) {
      const status = errorStatus(e);
      this.error.set(
        status === 404 || status === 400 ? TEXTS.events.notFound : errorText(e),
      );
    } finally {
      this.loading.set(false);
    }
  }
}
