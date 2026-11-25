import type { Driving } from '../api/types';
import { TEXTS } from '../core/texts';

/** State line of «Fahren» in the profile (DRV-18): «Kann fahren · 5 Plätze» or «Fährt nicht». */
export function drivingSummary(driving: Driving): string {
  return driving.canDrive ? TEXTS.driving.stateYes(driving.carSeats) : TEXTS.driving.stateNo;
}
