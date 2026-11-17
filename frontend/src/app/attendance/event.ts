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
import type { EventDetail as EventData, Overview as OverviewData } from '../api/types';
import { AuthService } from '../core/auth.service';
import { errorStatus, errorText } from '../core/error-text';
import { FeedbackService } from '../core/feedback.service';
import { TEXTS } from '../core/texts';
import { MyResponse } from './my-response';
import { MatchDay } from './match-day';
import { OverviewSection } from './overview';
import { yearOf, eventDate, clockTime } from './time';

/**
 * Termin (`/termine/:terminId`): Angaben, Status (abgesagt mit Grund, verschoben), für Coaches der Link
 * zum Bearbeiten (TER-04, TER-06), die eigene Antwort und die Übersicht nach Team und Linie (F-04, ANM).
 * Bei Spieltagen zusätzlich Aufgebot und Spiele (F-05, AUF).
 */
@Component({
  selector: 'app-termin',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, MyResponse, MatchDay, OverviewSection],
  template: `
    <div class="kopf">
      <a class="zurueck" routerLink="/termine">{{ texts.termine.allEvents }}</a>
      <h1>{{ event()?.title ?? texts.termine.title }}</h1>
      @if (event(); as t) {
        <p class="nebentext">{{ date(t.start) }} · {{ period() }} · {{ teams() }}</p>
      }
    </div>
    @if (success(); as message) {
      <p class="meldung-erfolg" role="status">{{ message }}</p>
    }
    @if (error(); as message) {
      <p class="meldung-fehler" role="alert">{{ message }}</p>
    }
    <div class="abschnitte" [attr.aria-busy]="loading() ? 'true' : null">
      @if (loading()) {
        <p class="leer">{{ texts.common.loading }}</p>
      } @else if (event(); as t) {
        @if (t.status === 'CANCELLED') {
          <div class="meldung-fehler status" data-testid="termin-status">
            <strong>{{ texts.termine.cancelledWithReason(t.cancellationReason ?? '') }}</strong>
            <span>{{ texts.termine.cancelledNotice }}</span>
          </div>
        } @else if (t.rescheduled) {
          <p class="meldung-hinweis" data-testid="termin-status">
            {{ texts.termine.rescheduledNotice }}
          </p>
        }
        @if (isMember()) {
          <section class="karte" aria-labelledby="termin-meine-antwort">
            <h2 id="termin-meine-antwort" class="abschnitt-titel">
              {{ texts.response.myResponse }}
            </h2>
            <app-meine-antwort
              [eventId]="t.id"
              [response]="t.myResponse"
              [name]="texts.response.myResponse"
              [locked]="lock()"
              [details]="true"
              (changed)="reload()"
            />
          </section>
        }
        @if (t.type === 'MATCH_DAY') {
          <app-spieltag
            [event]="t"
            [canCallUp]="coachOrAdmin()"
            [canEnterResult]="coachOrAdmin() && t.status !== 'CANCELLED'"
            (changed)="reload()"
          />
        }
        <section class="karte" aria-labelledby="termin-angaben">
          <h2 id="termin-angaben" class="abschnitt-titel">{{ texts.termine.details }}</h2>
          <dl class="angaben">
            <dt>{{ texts.termine.date }}</dt>
            <dd>{{ date(t.start) }}</dd>
            <dt>{{ texts.termine.time }}</dt>
            <dd>{{ period() }}</dd>
            <dt>{{ texts.termine.teams }}</dt>
            <dd>{{ teams() }}</dd>
            @if (t.location) {
              <dt>{{ texts.termine.location }}</dt>
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
                    data-testid="spielort-karte"
                    >{{ texts.termine.openMap }}</a
                  >
                }
              </dd>
            }
            @if (t.meetingPoint; as tp) {
              <dt>{{ texts.termine.meetingPoint }}</dt>
              <dd data-testid="treffpunkt">{{ clockTime(tp.time) }} · {{ tp.location }}</dd>
            }
            <dt>{{ texts.termine.responseDeadline }}</dt>
            <dd data-testid="antwortfrist">
              {{
                texts.termine.responseDeadlineText(
                  date(t.responseDeadline),
                  clockTime(t.responseDeadline),
                  t.responseDeadlineHours ?? 24
                )
              }}
            </dd>
            @if (t.notes) {
              <dt>{{ texts.termine.notes }}</dt>
              <dd class="hinweise">{{ t.notes }}</dd>
            }
            @if (t.seriesId) {
              <dt>{{ texts.termine.series }}</dt>
              <dd>
                @if (t.canEdit) {
                  <a [routerLink]="['/serien', t.seriesId]">{{ texts.termine.viewSeries }}</a>
                } @else {
                  {{ texts.termine.partOfSeries }}
                }
              </dd>
            }
          </dl>
          @if (t.canEdit) {
            <a
              class="button button-sekundaer button-klein bearbeiten"
              [routerLink]="['/termine', t.id, 'edit']"
              >{{ texts.termine.edit }}</a
            >
          }
        </section>
        @if (overview(); as u) {
          <app-uebersicht
            [eventId]="t.id"
            [data]="u"
            [coachIn]="coachIn()"
            [admin]="auth.me()?.admin ?? false"
            [meId]="auth.me()?.id ?? null"
            [offen]="!lock()"
            (changed)="reload()"
          />
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

  /** Route `/termine/:terminId` */
  readonly eventId = input.required<string>();

  protected readonly event = signal<EventData | null>(null);
  protected readonly overview = signal<OverviewData | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly success = signal<string | null>(null);

  private readonly year = computed(() => yearOf(this.auth.me()?.now));

  protected readonly period = computed(() => {
    const t = this.event();
    return t ? TEXTS.termine.period(clockTime(t.start), clockTime(t.end)) : '';
  });

  /** Mitglied eines beteiligten Teams: kann für sich antworten (ANM-01). */
  protected readonly isMember = computed(() =>
    (this.event()?.teams ?? []).some((t) => !!t.myTeamRole),
  );

  /** Teams des Termins, in denen die angemeldete Person Coach ist (RR-06). */
  protected readonly coachIn = computed(() =>
    (this.event()?.teams ?? []).filter((t) => t.myTeamRole === 'COACH').map((t) => t.id),
  );

  /** Coach eines beteiligten Teams oder Admin: Aufgebot und Resultate (RR-03). */
  protected readonly coachOrAdmin = computed(
    () => this.coachIn().length > 0 || (this.auth.me()?.admin ?? false),
  );

  /** Erklärung, warum nicht mehr geantwortet werden kann (TER-06), sonst `null`. */
  protected readonly lock = computed(() => {
    const t = this.event();
    if (!t) return null;
    if (t.status === 'CANCELLED') return TEXTS.response.cancelled;
    const now = this.auth.me()?.now;
    if (now && Date.parse(t.end) <= Date.parse(now)) return TEXTS.response.past;
    return null;
  });

  protected readonly teams = computed(() =>
    TEXTS.termine.joinTeams((this.event()?.teams ?? []).map((t) => t.name)),
  );

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

  /** Nach einer Antwort: Termin (eigene Antwort, Zähler) und Übersicht ohne Neuladen der Seite. */
  protected async reload(): Promise<void> {
    const id = this.eventId();
    try {
      const [event, overview] = await Promise.all([
        firstValueFrom(this.http.get<EventData>(`/api/events/${id}`)),
        firstValueFrom(this.http.get<OverviewData>(`/api/events/${id}/overview`)),
      ]);
      this.event.set(event);
      this.overview.set(overview);
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
        status === 404 || status === 400 ? TEXTS.termine.notFound : errorText(e),
      );
    } finally {
      this.loading.set(false);
    }
  }
}
