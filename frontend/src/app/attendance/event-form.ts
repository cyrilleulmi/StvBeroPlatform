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
type FormMode = 'neu' | 'termin' | 'serie';

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
}

/** Ein Spiel im Formular eines Spieltags (AUF-14). `id` leer: neu, wird beim Speichern angelegt. */
interface MatchRow {
  key: number;
  id: string | null;
  source: Match['source'];
  opponent: string;
  time: string;
  home: boolean;
  result: string | null;
  /** Importiert und von swiss unihockey nicht mehr geliefert: darf entfernt werden (SUH-06). */
  missingAtSu: boolean;
}

type Field = keyof FormValues;

const EMPTY: FormValues = {
  type: 'PRACTICE',
  title: TEXTS.termine.type.PRACTICE,
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
};

/** Feldnamen der API auf die Felder des Formulars. */
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
 * Termin erfassen (`/termine/neu`, `?art=serie` für eine Serie), Termin bearbeiten und absagen
 * (`/termine/:terminId/bearbeiten`) und Serie bearbeiten (`/serien/:serieId/bearbeiten`).
 * TER-01, TER-04 bis TER-06, TER-08, TER-10, TER-11. Spieltage mit Spielen, Treffpunkt und
 * Google-Maps-Link (AUF-10 bis AUF-16).
 */
@Component({
  selector: 'app-termin-formular',
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

  /** Route `/termine/:terminId/bearbeiten` */
  readonly eventId = input<string | undefined>();
  /** Route `/serien/:serieId/bearbeiten` */
  readonly seriesId = input<string | undefined>();
  /** Query `?art=serie` auf `/termine/neu` */
  readonly kind = input<string | undefined>(undefined, { alias: 'art' });

  protected readonly mode = computed<FormMode>(() =>
    this.eventId() ? 'termin' : this.seriesId() ? 'serie' : 'neu',
  );
  /** Felder einer Serie (Wochentag, Zeitraum) statt eines einzelnen Datums. */
  protected readonly asSeries = computed(
    () => this.mode() === 'serie' || (this.mode() === 'neu' && this.kind() === 'serie'),
  );

  protected readonly values = signal<FormValues>({ ...EMPTY });
  protected readonly event = signal<EventDetail | null>(null);
  protected readonly series = signal<Series | null>(null);
  /** Teams zur Auswahl: eigene Teams, für Admins alle der aktuellen Saison. */
  protected readonly teams = signal<Team[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly fieldErrors = signal<Partial<Record<Field | 'reason', string>>>({});
  /** `speichern` oder `absagen`: aria-busy nur dort, `disabled` für alle. */
  protected readonly runningAction = signal<string | null>(null);
  protected readonly saving = computed(() => this.runningAction() !== null);
  protected readonly reason = signal('');
  protected readonly matches = signal<MatchRow[]>([]);
  /** Feldfehler der Spiele, Schlüssel `<schluessel>-gegner` bzw. `<schluessel>-zeit`. */
  protected readonly matchErrors = signal<Record<string, string>>({});
  private nextKey = 1;

  protected readonly matchDay = computed(() => !this.asSeries() && this.values().type === 'MATCH_DAY');

  private readonly cancelDialog = viewChild<ElementRef<HTMLDialogElement>>('cancelDialog');
  private readonly reasonField = viewChild<ElementRef<HTMLInputElement>>('reasonField');

  /** Wer erfassen und bearbeiten darf: Coaches (in irgendeinem Team) und Admins (TER-11). */
  protected readonly canCreate = computed(() => {
    const me = this.auth.me();
    return !!me && (me.admin || me.memberships.some((z) => z.teamRole === 'COACH'));
  });

  /** Ob das Formular gezeigt wird. Sonst steht dort ein Hinweis (`hinweis()`). */
  protected readonly editable = computed(() => {
    switch (this.mode()) {
      case 'neu':
        return this.canCreate();
      case 'termin':
        return this.event()?.canEdit === true;
      case 'serie':
        return this.canEditSeries();
    }
  });

  protected readonly notice = computed(() => {
    if (this.loading() || this.error()) return null;
    if (this.mode() === 'termin' && this.event() && !this.event()!.canEdit) {
      const t = this.event()!;
      const coach = t.teams.some((x) => x.myTeamRole === 'COACH') || this.auth.isAdmin();
      return coach ? TEXTS.eventForm.notEditable : TEXTS.common.noPermission;
    }
    return this.editable() ? null : TEXTS.common.noPermission;
  });

  protected readonly title = computed(() => {
    switch (this.mode()) {
      case 'neu':
        return TEXTS.eventForm.newTitle;
      case 'termin':
        return TEXTS.eventForm.editTitle;
      case 'serie':
        return TEXTS.eventForm.editSeriesTitle;
    }
  });

  /**
   * Termintypen zur Auswahl. Neue Einzeltermine: alle drei (AUF-16). Ein bestehender Spieltag bleibt ein
   * Spieltag, Serien sind Trainings oder Anlässe.
   */
  protected readonly types = computed<EventType[]>(() => {
    if (this.event()?.type === 'MATCH_DAY') return ['MATCH_DAY'];
    if (this.mode() === 'neu' && !this.asSeries()) return ['PRACTICE', 'MATCH_DAY', 'OTHER'];
    return ['PRACTICE', 'OTHER'];
  });

  /** Teams der Serie bzw. des Termins, in denen die Person nicht Mitglied ist. Sie bleiben erhalten. */
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
    const defaultTitle = Object.values(TEXTS.termine.type) as string[];
    this.set('type', type);
    if (!old.title || defaultTitle.includes(old.title)) {
      this.set('title', TEXTS.termine.type[type]);
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
      ? `spiel-${key}-${field}-fehler`
      : null;
  }

  protected toggleTeam(teamId: string, an: boolean): void {
    const ids = this.values().teamIds.filter((id) => id !== teamId);
    this.set('teamIds', an ? [...ids, teamId] : ids);
  }

  protected selectKind(kind: 'einzel' | 'serie'): void {
    this.fieldErrors.set({});
    void this.router.navigate(['/termine/neu'], {
      queryParams: { art: kind === 'serie' ? 'serie' : null },
      replaceUrl: true,
    });
  }

  protected errorId(field: Field | 'reason'): string | null {
    return this.fieldErrors()[field] ? `termin-${field}-fehler` : null;
  }

  async save(domEvent: Event): Promise<void> {
    domEvent.preventDefault();
    if (this.saving()) return;
    if (!this.validate()) return;
    this.runningAction.set('save');
    this.error.set(null);
    try {
      switch (this.mode()) {
        case 'neu':
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
        case 'termin': {
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
        case 'serie': {
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
    };
  }

  /** Treffpunkt eines Spieltags am Tag des Spieltags (AUF-12); beide Felder leer: keiner. */
  private meetingPoint(): MeetingPoint | null {
    const w = this.values();
    const location = w.meetingPointLocation.trim();
    if (!w.meetingPointTime || !location) return null;
    return { time: zurich(w.date, w.meetingPointTime), location: location };
  }

  /**
   * Spiele eines Spieltags nach dem Speichern des Termins: neue anlegen, geänderte speichern, entfernte
   * löschen (AUF-14, AUF-16). Importierte Spiele bleiben unberührt, ausser swiss unihockey liefert sie
   * nicht mehr: dann dürfen sie entfernt werden (SUH-06). Liefert eine Meldung, falls etwas
   * nicht ging; der Termin selbst ist dann schon gespeichert.
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
    };
  }

  /**
   * Ort aus dem Namen und (bei Einzelterminen) dem Google-Maps-Link (AUF-10, AUF-11). Adresse und Koordinaten
   * eines bestehenden Orts bleiben, solange der Name gleich ist; bei Serien auch der Link.
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

  /** Pflichtfelder im Browser prüfen; die übrigen Regeln prüft das Backend. */
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
    const sf: Record<string, string> = {};
    if (this.matchDay()) {
      const time = !!w.meetingPointTime;
      const location = !!w.meetingPointLocation.trim();
      if (time && !location) f.meetingPointLocation = TEXTS.eventForm.meetingPointBoth;
      if (!time && location) f.meetingPointTime = TEXTS.eventForm.meetingPointBoth;
      for (const z of this.matches()) {
        if (z.source !== 'MANUAL') continue;
        if (!z.opponent.trim()) sf[`${z.key}-gegner`] = required('den Gegner');
        if (!z.time) sf[`${z.key}-zeit`] = required('eine Zeit');
      }
    }
    this.matchErrors.set(sf);
    const deadline = Number(w.responseDeadline);
    if (w.responseDeadline.trim() === '' || !Number.isInteger(deadline))
      f.responseDeadline = required('eine ganze Zahl');
    else if (deadline < 0) f.responseDeadline = TEXTS.eventForm.responseDeadlineMin;
    else if (deadline > 336) f.responseDeadline = TEXTS.eventForm.responseDeadlineMax;
    this.fieldErrors.set(f);
    const first = Object.keys(f)[0];
    if (first) {
      document.getElementById(`termin-${first}`)?.focus();
      return false;
    }
    const firstMatch = Object.keys(sf)[0];
    if (firstMatch) {
      document.getElementById(`spiel-${firstMatch}`)?.focus();
      return false;
    }
    return true;
  }

  private showErrors(e: unknown): void {
    const fields = fieldErrorsOf(e);
    const assigned: Partial<Record<Field, string>> = {};
    // Spieltag mit Spielen aus swiss unihockey: nur am selben Tag verschiebbar, die Meldung gehört zum Datum (SUH-05).
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
      document.getElementById(`termin-${Object.keys(assigned)[0]}`)?.focus();
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
          meetingPointTime: t.meetingPoint ? inputParts(t.meetingPoint.time).time : '',
          meetingPointLocation: t.meetingPoint?.location ?? '',
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
        });
      } else {
        // Neu: vorgewählt ist das Team, in dem die Person Coach ist, wenn es genau eines gibt.
        const coach = (this.auth.me()?.memberships ?? []).filter(
          (z) => z.teamRole === 'COACH',
        );
        this.values.set({ ...EMPTY, teamIds: coach.length === 1 ? [coach[0].team.id] : [] });
      }
    } catch (e) {
      const status = errorStatus(e);
      if (status === 404 || status === 400) {
        this.error.set(eventId ? TEXTS.termine.notFound : TEXTS.series.notFound);
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
    const teams = me?.admin ? await firstValueFrom(this.http.get<Team[]>('/api/teams')) : own;
    this.teams.set([...teams].sort((a, b) => a.name.localeCompare(b.name, 'de-CH')));
  }

  /** Teams eines bestehenden Termins, die nicht zu den eigenen gehören, bleiben sichtbar und gewählt. */
  private addTeams(teams: Team[]): void {
    const known = new Set(this.teams().map((t) => t.id));
    const added = teams.filter((t) => !known.has(t.id));
    if (added.length > 0) {
      this.teams.set(
        [...this.teams(), ...added].sort((a, b) => a.name.localeCompare(b.name, 'de-CH')),
      );
    }
  }

  /** Serie: bearbeiten dürfen Admins und Coaches eines Teams der Serie (TER-11, RR-06). */
  private canEditSeries(): boolean {
    const s = this.series();
    const me = this.auth.me();
    if (!s || !me) return false;
    return (
      me.admin ||
      me.memberships.some((z) => z.teamRole === 'COACH' && s.teamIds.includes(z.team.id))
    );
  }
}
