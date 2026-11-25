import type { EventType, Match, SeriesInput } from '../api/types';
import { TEXTS } from '../core/texts';

/** The values of the event form (`event-form.ts`), their start values and the names the API uses for them. */

export type Weekday = SeriesInput['weekday'];

export const WEEKDAYS: Weekday[] = [
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
  'SUNDAY',
];

export interface FormValues {
  type: EventType;
  title: string;
  date: string;
  start: string;
  end: string;
  weekday: Weekday;
  validFrom: string;
  validUntil: string;
  teamIds: string[];
  /** Teams invited without obligation («freiwillig», TER-15). */
  optionalTeamIds: string[];
  /** Chosen event variant; empty: the standard variant of the type (VAR-03). */
  variantId: string;
  location: string;
  mapsUrl: string;
  notes: string;
  responseDeadline: string;
  meetingPointTime: string;
  meetingPointLocation: string;
  attendanceCheck: boolean;
}

/** A match in the form of a match day (AUF-14). Empty `id`: new, created on saving. */
export interface MatchRow {
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

export type Field = keyof FormValues;

export const EMPTY: FormValues = {
  type: 'PRACTICE',
  title: TEXTS.events.type.PRACTICE,
  date: '',
  start: '18:30',
  end: '20:00',
  weekday: 'TUESDAY',
  validFrom: '',
  validUntil: '',
  teamIds: [],
  optionalTeamIds: [],
  variantId: '',
  location: '',
  mapsUrl: '',
  notes: '',
  responseDeadline: '24',
  meetingPointTime: '',
  meetingPointLocation: '',
  attendanceCheck: false,
};

/** Field names of the API mapped to the fields of the form. */
export const API_FIELDS: Record<string, Field> = {
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
