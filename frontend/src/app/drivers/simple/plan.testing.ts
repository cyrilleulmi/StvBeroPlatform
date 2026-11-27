/** Builders for unit tests of the simple driver list (not part of the app build, see tsconfig.app.json). */
import type {
  DriverPlan,
  DriverPlanMatchDay,
  DriverSetting,
  DriverShare,
  PersonSummary,
} from '../../api/types';

export const person = (id: string, firstName = id, lastName = ''): PersonSummary => ({
  id,
  firstName,
  lastName,
});

export const setting = (id: string, over: Partial<DriverSetting> = {}): DriverSetting => ({
  person: person(id, id[0].toUpperCase() + id.slice(1), 'Muster'),
  canDrive: true,
  carSeats: 5,
  canChange: false,
  ...over,
});

export const share = (id: string, over: Partial<DriverShare> = {}): DriverShare => ({
  person: person(id),
  trips: 0,
  plannedTrips: 0,
  kilometres: 0,
  plannedKilometres: 0,
  ...over,
});

export const matchDay = (
  eventId: string,
  over: Partial<DriverPlanMatchDay> = {},
): DriverPlanMatchDay => ({
  eventId,
  title: 'Spieltag',
  start: '2026-11-29T08:00:00Z',
  cancelled: false,
  over: false,
  ready: true,
  missing: [],
  changeable: true,
  cars: [],
  freeSeats: 0,
  expected: 8,
  expectedManual: false,
  places: 8,
  attending: 8,
  noTrip: false,
  ignored: false,
  ...over,
});

export const plan = (over: Partial<DriverPlan> = {}): DriverPlan =>
  ({
    teamId: 't1',
    teamName: 'Damen',
    seasonId: 's1',
    seasonName: '2026/27',
    canJoin: false,
    canManage: false,
    matchDays: [],
    shares: [],
    drivers: [],
    totals: {},
    swaps: [],
    extraTrips: [],
    history: [],
    expected: 8,
    expectedDefault: 8,
    expectedManual: false,
    ...over,
  }) as DriverPlan;
