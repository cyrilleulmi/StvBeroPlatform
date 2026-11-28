import { clockTime } from '../core/time';

/** What a card or the event page needs of an event to show its pre-training (TER-29, TER-31). */
export interface WithPreTraining {
  start: string;
  end: string;
  preTraining?: { title: string; start: string } | null;
}

/** The pre-training and its practice as the designs show them (E-118). */
export interface PreTrainingView {
  title: string;
  /** Start of the pre-training, «20:00»: when to be there. */
  start: string;
  /** Start of the practice, «20:20». */
  practiceStart: string;
  /** End of the practice, «21:50». */
  end: string;
  /** Minutes from the start of the pre-training to the start of the practice. */
  leadMinutes: number;
  practiceMinutes: number;
  /** Width of the pre-training in the time bar in percent: to scale, but never too narrow to read. */
  share: number;
}

const MINUTE = 60_000;
const MIN_SHARE = 16;
const MAX_SHARE = 50;

/** `null` without a pre-training, or if it does not start before the practice (the API refuses that). */
export function preTrainingView(event: WithPreTraining): PreTrainingView | null {
  const pre = event.preTraining;
  if (!pre) return null;
  const from = Date.parse(pre.start);
  const start = Date.parse(event.start);
  const end = Date.parse(event.end);
  const leadMinutes = Math.round((start - from) / MINUTE);
  const practiceMinutes = Math.round((end - start) / MINUTE);
  if (!(leadMinutes > 0) || !(practiceMinutes > 0)) return null;
  const share = (leadMinutes / (leadMinutes + practiceMinutes)) * 100;
  return {
    title: pre.title,
    start: clockTime(pre.start),
    practiceStart: clockTime(event.start),
    end: clockTime(event.end),
    leadMinutes,
    practiceMinutes,
    share: Math.round(Math.min(MAX_SHARE, Math.max(MIN_SHARE, share))),
  };
}

/** «20 Min.», «1 Std.», «1 Std. 30 Min.» */
export function durationText(minutes: number): string {
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (hours === 0) return `${rest} Min.`;
  return rest === 0 ? `${hours} Std.` : `${hours} Std. ${rest} Min.`;
}

/** The design proposals (E-118), in the order of the switch bar. */
export const DESIGNS = ['a', 'b', 'c', 'd', 'e'] as const;
export type Design = (typeof DESIGNS)[number];

export function isDesign(value: string | null | undefined): value is Design {
  return (DESIGNS as readonly string[]).includes(value ?? '');
}

/** The proposal before or after this one; the ends wrap around. */
export function neighbour(design: Design, step: 1 | -1): Design {
  const i = DESIGNS.indexOf(design);
  return DESIGNS[(i + step + DESIGNS.length) % DESIGNS.length];
}
