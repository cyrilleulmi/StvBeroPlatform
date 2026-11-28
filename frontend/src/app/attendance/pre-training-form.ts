import type { EventInput, PreTraining, SeriesInput, SeriesPreTraining } from '../api/types';
import { TEXTS } from '../core/texts';
import { inputParts, zurich } from '../core/time';

/**
 * The pre-training («Vortraining», TER-29 to TER-32) in the forms of events and series: the form
 * values, the suggestion for the start, the checks and the conversion from and to the API.
 */

/** The three form values of a pre-training. */
export interface PreTrainingValues {
  preTraining: boolean;
  preTrainingTitle: string;
  preTrainingTime: string;
}

/** Everything the helpers need from a form: the type, the start of the practice and the values. */
export interface PreTrainingForm extends PreTrainingValues {
  type: string;
  /** Date of a single event; empty for a series. */
  date: string;
  /** Start of the practice («HH:mm»). */
  start: string;
}

export const NO_PRE_TRAINING: PreTrainingValues = {
  preTraining: false,
  preTrainingTitle: '',
  preTrainingTime: '',
};

export const PRE_TRAINING_MINUTES = 15;
export const PRE_TRAINING_TITLE_MAX = 60;

/** Start 15 minutes before the practice, never before 00:00; empty if the practice has no valid start. */
export function suggestedTime(start: string): string {
  const m = /^(\d{1,2}):(\d{2})$/.exec(start);
  if (!m) return '';
  const minutes = Math.max(0, Number(m[1]) * 60 + Number(m[2]) - PRE_TRAINING_MINUTES);
  const two = (n: number) => String(n).padStart(2, '0');
  return `${two(Math.floor(minutes / 60))}:${two(minutes % 60)}`;
}

/** Whether the form carries a pre-training: only for practices with the switch on. */
function active(w: PreTrainingForm): boolean {
  return w.type === 'PRACTICE' && w.preTraining;
}

/** Field errors of the pre-training, keyed like the fields of the form. Empty if there is none or it is off. */
export function preTrainingErrors(
  w: PreTrainingForm,
): { preTrainingTitle?: string; preTrainingTime?: string } {
  if (!active(w)) return {};
  const errors: { preTrainingTitle?: string; preTrainingTime?: string } = {};
  if (!w.preTrainingTitle.trim()) errors.preTrainingTitle = TEXTS.preTrainingForm.titleRequired;
  if (!w.preTrainingTime) errors.preTrainingTime = TEXTS.preTrainingForm.timeRequired;
  else if (w.start && w.preTrainingTime >= w.start) {
    errors.preTrainingTime = TEXTS.preTrainingForm.beforePractice;
  }
  return errors;
}

/** `preTraining` of an event: moment on the date of the event, or null. */
export function eventPreTraining(w: PreTrainingForm): PreTraining | null {
  if (!active(w)) return null;
  return { title: w.preTrainingTitle.trim(), start: zurich(w.date, w.preTrainingTime) };
}

/** `preTraining` of a series: time of day, or null. */
export function seriesPreTraining(w: PreTrainingForm): SeriesPreTraining | null {
  if (!active(w)) return null;
  return { title: w.preTrainingTitle.trim(), startTime: w.preTrainingTime };
}

/** Form values of an event (or event summary) as the API delivers it. */
export function valuesOfEvent(t: Pick<EventInput, 'preTraining'>): PreTrainingValues {
  const p = t.preTraining;
  return p
    ? { preTraining: true, preTrainingTitle: p.title, preTrainingTime: inputParts(p.start).time }
    : NO_PRE_TRAINING;
}

/** Form values of a series. */
export function valuesOfSeries(s: Pick<SeriesInput, 'preTraining'>): PreTrainingValues {
  const p = s.preTraining;
  return p
    ? { preTraining: true, preTrainingTitle: p.title, preTrainingTime: p.startTime }
    : NO_PRE_TRAINING;
}
