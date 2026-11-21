import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type {
  EventLocation,
  Series,
  SeriesInput,
  Match,
  MatchInput,
  Team,
  EventDetail,
  EventInput,
  EventType,
  MeetingPoint,
} from '../api/types';
import { AuthService } from '../core/auth.service';
import { fieldErrorsOf, errorRule, errorStatus, errorText } from '../core/error-text';
import { FeedbackService } from '../core/feedback.service';
import { TEXTS } from '../core/texts';
import { inputParts, zurich } from './time';

type Weekday = SeriesInput['weekday'];
type FormMode = 'new' | 'event' | 'series';

const WEEKDAYS: Weekday[] = [
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
  'SUNDAY',
];

interface FormValues {
  type: EventType;
  title: string;
  date: string;
  start: string;
  end: string;
  weekday: Weekday;
  validFrom: string;
  validUntil: string;
  teamIds: string[];
  location: string;
  mapsUrl: string;
  notes: string;
  responseDeadline: string;
  meetingPointTime: string;
  meetingPointLocation: string;
  attendanceCheck: boolean;
}

/** A match in the form of a match day (AUF-14). Empty `id`: new, created on saving. */
interface MatchRow {
  key: number;
  id: string | null;
  source: Match['source'];
  opponent: string;
  time: string;
  home: boolean;
  result: string | null;
  /** Imported and no longer delivered by swiss unihockey: may be removed (SUH-06). */
  missingAtSu: boolean;
}

type Field = keyof FormValues;

const EMPTY: FormValues = {
  type: 'PRACTICE',
  title: TEXTS.events.type.PRACTICE,
  date: '',
  start: '18:30',
  end: '20:00',
  weekday: 'TUESDAY',
  validFrom: '',
  validUntil: '',
  teamIds: [],
  location: '',
  mapsUrl: '',
  notes: '',
  responseDeadline: '24',
  meetingPointTime: '',
  meetingPointLocation: '',
  attendanceCheck: false,
};

/** Field names of the API mapped to the fields of the form. */
const API_FIELDS: Record<string, Field> = {
  start: 'start',
  end: 'end',
  teamIds: 'teamIds',
  title: 'title',
  startTime: 'start',
  endTime: 'end',
  validFrom: 'validFrom',
  validUntil: 'validUntil',
  responseDeadlineHours: 'responseDeadline',
  notes: 'notes',
  'location.name': 'location',
  'location.mapsUrl': 'mapsUrl',
  'meetingPoint.time': 'meetingPointTime',
  'meetingPoint.location': 'meetingPointLocation',
};

/**
 * Create an event (`/termine/neu`, `?art=serie` for a series), edit and cancel an event
 * (`/termine/:eventId/bearbeiten`) and edit a series (`/serien/:seriesId/bearbeiten`).
 * TER-01, TER-04 to TER-06, TER-08, TER-10, TER-11. Match days with matches, meeting point and
 * Google Maps link (AUF-10 to AUF-16).
 */
@Component({
  selector: 'app-event-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  templateUrl: './event-form.html',
  styleUrl: './event-form.css',
})
export class EventForm {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly feedback = inject(FeedbackService);
  protected readonly texts = TEXTS;
  protected readonly weekdays = WEEKDAYS;
  protected readonly timeFields: { field: 'start' | 'end'; label: string }[] = [
    { field: 'start', label: TEXTS.eventForm.start },
    { field: 'end', label: TEXTS.eventForm.end },
  ];
  protected readonly periodFields: { field: 'validFrom' | 'validUntil'; label: string }[] = [
    { field: 'validFrom', label: TEXTS.eventForm.validFrom },
    { field: 'validUntil', label: TEXTS.eventForm.validUntil },
  ];

  /** Route `/termine/:eventId/bearbeiten` */
  readonly eventId = input<string | undefined>();
  /** Route `/serien/:seriesId/bearbeiten` */
  readonly seriesId = input<string | undefined>();
  /** Query `?art=serie` on `/termine/neu` */
  // eslint-disable-next-line @angular-eslint/no-input-rename -- query parameter `?art=` is part of the browser URL and stays German (NF-40)
  readonly kind = input<string | undefined>(undefined, { alias: 'art' });

  protected readonly mode = computed<FormMode>(() =>
    this.eventId() ? 'event' : this.seriesId() ? 'series' : 'new',
  );
  /** Fields of a series (weekday, period) instead of a single date. */
  protected readonly asSeries = computed(
    () => this.mode() === 'series' || (this.mode() === 'new' && this.kind() === 'serie'),
  );

  protected readonly values = signal<FormValues>({ ...EMPTY });
  protected readonly event = signal<EventDetail | null>(null);
  protected readonly series = signal<Series | null>(null);
  /** Teams to choose from: for coaches and admins all of the current season (E-69). */
  protected readonly teams = signal<Team[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly fieldErrors = signal<Partial<Record<Field | 'reason', string>>>({});
  /** `save` or `cancelEvent`: aria-busy only there, `disabled` for all. */
  protected readonly runningAction = signal<string | null>(null);
  protected readonly saving = computed(() => this.runningAction() !== null);
  protected readonly reason = signal('');
  protected readonly matches = signal<MatchRow[]>([]);
  /** Field errors of the matches, key `<key>-opponent` or `<key>-time`. */
  protected readonly matchErrors = signal<Record<string, string>>({});
  private nextKey = 1;

  protected readonly matchDay = computed(() => !this.asSeries() && this.values().type === 'MATCH_DAY');

  private readonly cancelDialog = viewChild<ElementRef<HTMLDialogElement>>('cancelDialog');
  private readonly attendanceOffDialog = viewChild<ElementRef<HTMLDialogElement>>('attendanceOffDialog');
  private readonly reasonField = viewChild<ElementRef<HTMLInputElement>>('reasonField');

  /** Who may create and edit: coaches (in any team) and admins (TER-11). */
  protected readonly canCreate = computed(() => this.auth.canManage());

  /** Whether the form is shown. Otherwise a notice is shown there (`notice()`). */
  protected readonly editable = computed(() => {
    switch (this.mode()) {
      case 'new':
        return this.canCreate();
      case 'event':
        return this.event()?.canEdit === true;
      case 'series':
        return this.canEditSeries();
    }
  });

  protected readonly notice = computed(() => {
    if (this.loading() || this.error()) return null;
    if (this.mode() === 'event' && this.event() && !this.event()!.canEdit) {
      const t = this.event()!;
      const coach = t.teams.some((x) => x.myTeamRole === 'COACH') || this.auth.canManage();
      return coach ? TEXTS.eventForm.notEditable : TEXTS.common.noPermission;
    }
    return this.editable() ? null : TEXTS.common.noPermission;
  });

  protected readonly title = computed(() => {
    switch (this.mode()) {
      case 'new':
        return TEXTS.eventForm.newTitle;
      case 'event':
        return TEXTS.eventForm.editTitle;
      case 'series':
        return TEXTS.eventForm.editSeriesTitle;
    }
  });

  /**
   * Event types to choose from. New single events: all three (AUF-16). An existing match day stays a
   * match day, series are practices or other events.
   */
  protected readonly types = computed<EventType[]>(() => {
    if (this.event()?.type === 'MATCH_DAY') return ['MATCH_DAY'];
    if (this.mode() === 'new' && !this.asSeries()) return ['PRACTICE', 'MATCH_DAY', 'OTHER'];
    return ['PRACTICE', 'OTHER'];
  });

  /** Teams of the series or event in which the person is not a member. They are kept. */
  protected readonly moreTeams = computed(() => {
    const visible = new Set(this.teams().map((t) => t.id));
    return this.values().teamIds.filter((id) => !visible.has(id)).length;
  });

  protected readonly back = computed(() => {
    const t = this.eventId();
    const s = this.seriesId();
    return t ? ['/termine', t] : s ? ['/serien', s] : ['/termine'];
  });

  constructor() {
    effect(() => {
      const eventId = this.eventId();
      const seriesId = this.seriesId();
      untracked(() => void this.load(eventId, seriesId));
    });
  }

  protected set<F extends Field>(field: F, value: FormValues[F]): void {
    this.values.update((w) => ({ ...w, [field]: value }));
    if (this.fieldErrors()[field]) {
      this.fieldErrors.update((f) => ({ ...f, [field]: undefined }));
    }
  }

  protected selectType(type: EventType): void {
    const old = this.values();
    const defaultTitle = Object.values(TEXTS.events.type) as string[];
    this.set('type', type);
    if (!old.title || defaultTitle.includes(old.title)) {
      this.set('title', TEXTS.events.type[type]);
    }
    if (type === 'MATCH_DAY' && this.matches().length === 0) {
      this.addMatch();
    }
  }

  protected addMatch(): void {
    const key = this.nextKey++;
    this.matches.update((z) => [
      ...z,
      {
        key,
        id: null,
        source: 'MANUAL',
        opponent: '',
        time: this.values().start,
        home: true,
        result: null,
        missingAtSu: false,
      },
    ]);
  }

  protected removeMatch(key: number): void {
    this.matches.update((z) => z.filter((x) => x.key !== key));
  }

  protected setMatch<F extends 'opponent' | 'time' | 'home'>(
    key: number,
    field: F,
    value: MatchRow[F],
  ): void {
    this.matches.update((z) =>
      z.map((x) => (x.key === key ? { ...x, [field]: value } : x)),
    );
    const k = `${key}-${field}`;
    if (this.matchErrors()[k]) {
      this.matchErrors.update((f) => {
        const copy = { ...f };
        delete copy[k];
        return copy;
      });
    }
  }

  protected matchErrorId(key: number, field: 'opponent' | 'time'): string | null {
    return this.matchErrors()[`${key}-${field}`]
      ? `match-${key}-${field}-error`
      : null;
  }

  protected toggleTeam(teamId: string, checked: boolean): void {
    const ids = this.values().teamIds.filter((id) => id !== teamId);
    this.set('teamIds', checked ? [...ids, teamId] : ids);
  }

  protected selectKind(kind: 'single' | 'series'): void {
    this.fieldErrors.set({});
    void this.router.navigate(['/termine/neu'], {
      queryParams: { art: kind === 'series' ? 'serie' : null },
      replaceUrl: true,
    });
  }

  protected errorId(field: Field | 'reason'): string | null {
    return this.fieldErrors()[field] ? `event-${field}-error` : null;
  }

  async save(domEvent: Event): Promise<void> {
    domEvent.preventDefault();
    if (this.saving()) return;
    if (!this.validate()) return;
    // Switching off an event with recorded attendance asks first; the data is kept (PRS-08).
    const before = this.event();
    if (
      this.mode() === 'event' &&
      before?.attendanceCheck &&
      before.attendanceRecorded &&
      !this.values().attendanceCheck
    ) {
      this.attendanceOffDialog()?.nativeElement.showModal();
      return;
    }
    await this.submit();
  }

  protected closeAttendanceOff(): void {
    this.attendanceOffDialog()?.nativeElement.close();
  }

  protected async confirmAttendanceOff(): Promise<void> {
    if (this.saving()) return;
    await this.submit();
    this.closeAttendanceOff();
  }

  private async submit(): Promise<void> {
    this.runningAction.set('save');
    this.error.set(null);
    try {
      switch (this.mode()) {
        case 'new':
          if (this.asSeries()) {
            const s = await firstValueFrom(this.http.post<Series>('/api/series', this.seriesData()));
            this.feedback.set(TEXTS.eventForm.seriesCreated);
            await this.router.navigate(['/serien', s.id]);
          } else {
            const t = await firstValueFrom(
              this.http.post<EventDetail>('/api/events', this.eventData()),
            );
            const matchErrors = await this.saveMatches(t, null);
            this.feedback.set(matchErrors ?? TEXTS.eventForm.created);
            await this.router.navigate(['/termine', t.id]);
          }
          break;
        case 'event': {
          const before = this.event()!;
          const t = await firstValueFrom(
            this.http.put<EventDetail>(`/api/events/${before.id}`, this.eventData()),
          );
          const rescheduled = Date.parse(t.start) !== Date.parse(before.start);
          const matchErrors = await this.saveMatches(t, before);
          this.feedback.set(
            matchErrors ??
              (rescheduled ? TEXTS.eventForm.rescheduled : TEXTS.eventForm.saved),
          );
          await this.router.navigate(['/termine', t.id]);
          break;
        }
        case 'series': {
          const s = await firstValueFrom(
            this.http.put<Series>(`/api/series/${this.series()!.id}`, this.seriesData()),
          );
          this.feedback.set(TEXTS.eventForm.saved);
          await this.router.navigate(['/serien', s.id]);
          break;
        }
      }
    } catch (e) {
      this.showErrors(e);
    } finally {
      this.runningAction.set(null);
    }
  }

  protected openCancel(): void {
    this.reason.set('');
    this.fieldErrors.update((f) => ({ ...f, reason: undefined }));
    this.cancelDialog()?.nativeElement.showModal();
  }

  protected closeCancel(): void {
    this.cancelDialog()?.nativeElement.close();
  }

  async cancelEvent(domEvent: Event): Promise<void> {
    domEvent.preventDefault();
    if (this.saving()) return;
    const reason = this.reason().trim();
    if (!reason) {
      this.fieldErrors.update((f) => ({ ...f, reason: TEXTS.eventForm.reasonRequired }));
      this.reasonField()?.nativeElement.focus();
      return;
    }
    this.runningAction.set('cancelEvent');
    try {
      const id = this.event()!.id;
      await firstValueFrom(this.http.post<EventDetail>(`/api/events/${id}/cancel`, { reason: reason }));
      this.closeCancel();
      this.feedback.set(TEXTS.eventForm.cancelled);
      await this.router.navigate(['/termine', id]);
    } catch (e) {
      this.closeCancel();
      this.showErrors(e);
    } finally {
      this.runningAction.set(null);
    }
  }

  private eventData(): EventInput {
    const w = this.values();
    const before = this.event();
    return {
      type: w.type,
      title: w.title.trim(),
      start: zurich(w.date, w.start),
      end: zurich(w.date, w.end),
      teamIds: w.teamIds,
      location: this.location(before?.location ?? null, true),
      notes: w.notes.trim() || null,
      responseDeadlineHours: Number(w.responseDeadline),
      meetingPoint: this.matchDay() ? this.meetingPoint() : (before?.meetingPoint ?? null),
      attendanceCheck: w.attendanceCheck,
    };
  }

  /**
   * Meeting point of a match day on the day of the match day (AUF-12); without a place: none. The time may
   * stay open, the timeline then suggests a departure (TML-04).
   */
  private meetingPoint(): MeetingPoint | null {
    const w = this.values();
    const location = w.meetingPointLocation.trim();
    if (!location) return null;
    return { time: w.meetingPointTime ? zurich(w.date, w.meetingPointTime) : null, location: location };
  }

  /**
   * Matches of a match day after saving the event: create new ones, save changed ones, delete removed
   * ones (AUF-14, AUF-16). Imported matches stay untouched, unless swiss unihockey no longer delivers
   * them: then they may be removed (SUH-06). Returns a message if something failed; the event
   * itself is already saved then.
   */
  private async saveMatches(t: EventDetail, before: EventDetail | null): Promise<string | null> {
    if (t.type !== 'MATCH_DAY') return null;
    const date = this.values().date;
    const rows = this.matches();
    const previous = new Map((before?.matches ?? []).map((m) => [m.id, m]));
    try {
      for (const m of previous.values()) {
        const deletable = m.source === 'MANUAL' || m.missingAtSu;
        if (deletable && !rows.some((z) => z.id === m.id)) {
          await firstValueFrom(this.http.delete<void>(`/api/events/${t.id}/matches/${m.id}`));
        }
      }
      for (const z of rows) {
        if (z.source !== 'MANUAL') continue;
        const data: MatchInput = {
          opponent: z.opponent.trim(),
          startTime: zurich(date, z.time),
          home: z.home,
          result: z.result,
        };
        const old = z.id ? previous.get(z.id) : undefined;
        if (!old) {
          await firstValueFrom(this.http.post<Match>(`/api/events/${t.id}/matches`, data));
        } else if (
          old.opponent !== data.opponent ||
          old.home !== data.home ||
          Date.parse(old.startTime) !== Date.parse(data.startTime)
        ) {
          await firstValueFrom(this.http.put<Match>(`/api/events/${t.id}/matches/${z.id}`, data));
        }
      }
      return null;
    } catch (e) {
      return TEXTS.eventForm.matchesError(errorText(e));
    }
  }

  private seriesData(): SeriesInput {
    const w = this.values();
    return {
      type: w.type === 'OTHER' ? 'OTHER' : 'PRACTICE',
      title: w.title.trim(),
      teamIds: w.teamIds,
      weekday: w.weekday,
      startTime: w.start,
      endTime: w.end,
      validFrom: w.validFrom,
      validUntil: w.validUntil,
      location: this.location(this.series()?.location ?? null, false),
      responseDeadlineHours: Number(w.responseDeadline),
      attendanceCheck: w.attendanceCheck,
    };
  }

  /**
   * Location from the name and (for single events) the Google Maps link (AUF-10, AUF-11). Address and
   * coordinates of an existing location stay as long as the name is the same; for series also the link.
   */
  private location(previous: EventLocation | null | undefined, withLink: boolean): EventLocation | null {
    const name = this.values().location.trim();
    if (!name) return null;
    const base: EventLocation = previous && previous.name === name ? { ...previous } : { name };
    if (withLink) {
      base.mapsUrl = this.values().mapsUrl.trim() || null;
    }
    return base;
  }

  /** Check required fields in the browser; the backend checks the other rules. */
  private validate(): boolean {
    const w = this.values();
    const f: Partial<Record<Field, string>> = {};
    const required = TEXTS.eventForm.required;
    if (!w.title.trim()) f.title = required('einen Titel');
    if (this.asSeries()) {
      if (!w.validFrom) f.validFrom = required('ein Datum');
      if (!w.validUntil) f.validUntil = required('ein Datum');
    } else if (!w.date) {
      f.date = required('ein Datum');
    }
    if (!w.start) f.start = required('eine Zeit');
    if (!w.end) f.end = required('eine Zeit');
    else if (w.start && w.end <= w.start) f.end = TEXTS.eventForm.endAfterStart;
    if (w.teamIds.length === 0) f.teamIds = TEXTS.eventForm.teamRequired;
    if (!this.asSeries() && w.mapsUrl.trim()) {
      if (!w.location.trim()) f.location = required('den Ort');
      if (!/^https:\/\/\S+$/.test(w.mapsUrl.trim())) f.mapsUrl = TEXTS.eventForm.mapsUrlFormat;
    }
    const matchFieldErrors: Record<string, string> = {};
    if (this.matchDay()) {
      const time = !!w.meetingPointTime;
      const location = !!w.meetingPointLocation.trim();
      if (time && !location) f.meetingPointLocation = TEXTS.eventForm.meetingPointBoth;
      for (const z of this.matches()) {
        if (z.source !== 'MANUAL') continue;
        if (!z.opponent.trim()) matchFieldErrors[`${z.key}-opponent`] = required('den Gegner');
        if (!z.time) matchFieldErrors[`${z.key}-time`] = required('eine Zeit');
      }
    }
    this.matchErrors.set(matchFieldErrors);
    const deadline = Number(w.responseDeadline);
    if (w.responseDeadline.trim() === '' || !Number.isInteger(deadline))
      f.responseDeadline = required('eine ganze Zahl');
    else if (deadline < 0) f.responseDeadline = TEXTS.eventForm.responseDeadlineMin;
    else if (deadline > 336) f.responseDeadline = TEXTS.eventForm.responseDeadlineMax;
    this.fieldErrors.set(f);
    const first = Object.keys(f)[0];
    if (first) {
      document.getElementById(`event-${first}`)?.focus();
      return false;
    }
    const firstMatch = Object.keys(matchFieldErrors)[0];
    if (firstMatch) {
      document.getElementById(`match-${firstMatch}`)?.focus();
      return false;
    }
    return true;
  }

  private showErrors(e: unknown): void {
    const fields = fieldErrorsOf(e);
    const assigned: Partial<Record<Field, string>> = {};
    // Match day with matches from swiss unihockey: can only be moved within the same day, the message belongs to the date (SUH-05).
    const dateRule = errorRule(e) === 'SUH-05';
    for (const [field, message] of Object.entries(fields)) {
      const target =
        dateRule && field === 'start'
          ? 'date'
          : (API_FIELDS[field] ?? (field.startsWith('location.') ? 'location' : undefined));
      if (target) assigned[target] = message;
    }
    if (Object.keys(assigned).length > 0) {
      this.fieldErrors.set(assigned);
      document.getElementById(`event-${Object.keys(assigned)[0]}`)?.focus();
    } else {
      this.error.set(errorText(e));
    }
  }

  private async load(eventId: string | undefined, seriesId: string | undefined): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    this.fieldErrors.set({});
    this.event.set(null);
    this.series.set(null);
    this.matches.set([]);
    this.matchErrors.set({});
    try {
      await this.loadTeams();
      if (eventId) {
        const t = await firstValueFrom(this.http.get<EventDetail>(`/api/events/${eventId}`));
        this.event.set(t);
        this.addTeams(t.teams);
        const b = inputParts(t.start);
        this.values.set({
          ...EMPTY,
          type: t.type,
          title: t.title,
          date: b.date,
          start: b.time,
          end: inputParts(t.end).time,
          teamIds: t.teams.map((x) => x.id),
          location: t.location?.name ?? '',
          mapsUrl: t.location?.mapsUrl ?? '',
          notes: t.notes ?? '',
          responseDeadline: String(t.responseDeadlineHours ?? 24),
          meetingPointTime: t.meetingPoint?.time ? inputParts(t.meetingPoint.time).time : '',
          meetingPointLocation: t.meetingPoint?.location ?? '',
          attendanceCheck: t.attendanceCheck ?? false,
        });
        this.matches.set(
          (t.matches ?? []).map((m) => ({
            key: this.nextKey++,
            id: m.id,
            source: m.source,
            opponent: m.opponent,
            time: inputParts(m.startTime).time,
            home: m.home,
            result: m.result ?? null,
            missingAtSu: m.source !== 'MANUAL' && m.missingAtSu,
          })),
        );
      } else if (seriesId) {
        const s = await firstValueFrom(this.http.get<Series>(`/api/series/${seriesId}`));
        this.series.set(s);
        this.values.set({
          ...EMPTY,
          type: s.type,
          title: s.title,
          weekday: s.weekday,
          start: s.startTime,
          end: s.endTime,
          validFrom: s.validFrom,
          validUntil: s.validUntil,
          teamIds: s.teamIds,
          location: s.location?.name ?? '',
          responseDeadline: String(s.responseDeadlineHours ?? 24),
          attendanceCheck: s.attendanceCheck ?? false,
        });
      } else {
        // New: the team in which the person is coach is preselected if there is exactly one.
        const coach = (this.auth.me()?.memberships ?? []).filter(
          (z) => z.teamRole === 'COACH',
        );
        this.values.set({ ...EMPTY, teamIds: coach.length === 1 ? [coach[0].team.id] : [] });
      }
    } catch (e) {
      const status = errorStatus(e);
      if (status === 404 || status === 400) {
        this.error.set(eventId ? TEXTS.events.notFound : TEXTS.series.notFound);
      } else {
        this.error.set(errorText(e));
      }
    } finally {
      this.loading.set(false);
    }
  }

  private async loadTeams(): Promise<void> {
    const me = this.auth.me();
    const own = (me?.memberships ?? []).map((z) => z.team);
    const teams = this.auth.canManage()
      ? await firstValueFrom(this.http.get<Team[]>('/api/teams'))
      : own;
    this.teams.set([...teams].sort((a, b) => a.name.localeCompare(b.name, 'de-CH')));
  }

  /** Teams of an existing event that are not among the own ones stay visible and selected. */
  private addTeams(teams: Team[]): void {
    const known = new Set(this.teams().map((t) => t.id));
    const added = teams.filter((t) => !known.has(t.id));
    if (added.length > 0) {
      this.teams.set(
        [...this.teams(), ...added].sort((a, b) => a.name.localeCompare(b.name, 'de-CH')),
      );
    }
  }

  /** Series: admins and coaches (of any team, E-69) may edit (TER-11, RR-03). */
  private canEditSeries(): boolean {
    return !!this.series() && this.auth.canManage();
  }
}
