import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import type {
  DriverList,
  DriverPlan,
  DriverPlanProposedTrip,
  DriverProposal,
} from '../../api/types';
import { AuthService } from '../../core/auth.service';
import { TEXTS } from '../../core/texts';
import { PlanStore } from './plan-store';
import { matchDay, person, plan, setting, share } from './plan.testing';

const T = TEXTS.simplePlan;

function setup(now = '2026-11-24T10:00:00Z', meId: string | null = 'cleo') {
  const me = signal(meId ? { id: meId, now } : null);
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      PlanStore,
      { provide: AuthService, useValue: { me } },
    ],
  });
  return { store: TestBed.inject(PlanStore), http: TestBed.inject(HttpTestingController), me };
}

const list = (eventId: string, over: Partial<DriverList> = {}): DriverList =>
  ({
    eventId,
    cars: [],
    withoutSeat: [],
    individual: [],
    needSeat: [],
    ...over,
  }) as unknown as DriverList;

const trip = (eventId: string, driverId: string, seats = 4): DriverPlanProposedTrip => ({
  eventId,
  driver: person(driverId),
  seats,
});

describe('PlanStore', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  describe('queue («Wer ist dran»)', () => {
    it('is empty without a plan', () => {
      expect(setup().store.queue()).toEqual([]);
    });

    it('adds done and planned trips and kilometres', () => {
      const { store } = setup();
      store.plan.set(
        plan({
          drivers: [setting('anna')],
          shares: [
            share('anna', { trips: 2, plannedTrips: 1, kilometres: 50, plannedKilometres: 25 }),
          ],
        }),
      );
      expect(store.queue()[0]).toMatchObject({
        trips: 3,
        done: 2,
        planned: 1,
        kilometres: 75,
        canDrive: true,
      });
    });

    it('puts those who can drive first, then fewer trips, then fewer kilometres', () => {
      const { store } = setup();
      store.plan.set(
        plan({
          drivers: [
            setting('a-no', { canDrive: false }),
            setting('b-many'),
            setting('c-far'),
            setting('d-near'),
            setting('e-none'),
          ],
          shares: [
            share('a-no'),
            share('b-many', { trips: 5 }),
            share('c-far', { trips: 2, kilometres: 90 }),
            share('d-near', { trips: 2, kilometres: 30 }),
          ],
        }),
      );
      expect(store.queue().map((q) => q.person.id)).toEqual([
        'e-none',
        'd-near',
        'c-far',
        'b-many',
        'a-no',
      ]);
    });

    it('keeps the order of the drivers on a complete tie', () => {
      const { store } = setup();
      store.plan.set(plan({ drivers: [setting('x'), setting('y'), setting('z')] }));
      expect(store.queue().map((q) => q.person.id)).toEqual(['x', 'y', 'z']);
    });

    it('counts a person without a share as 0 trips and ignores shares of people who are not drivers', () => {
      const { store } = setup();
      store.plan.set(plan({ drivers: [setting('anna')], shares: [share('ghost', { trips: 9 })] }));
      expect(store.queue()).toHaveLength(1);
      expect(store.tripsOf('anna')).toBe(0);
      expect(store.tripsOf('ghost')).toBe(0);
    });

    it('has a maxTrips of at least 1, so bars never divide by zero', () => {
      const { store } = setup();
      expect(store.maxTrips()).toBe(1);
      store.plan.set(
        plan({
          drivers: [setting('anna')],
          shares: [share('anna', { trips: 4, plannedTrips: 2 })],
        }),
      );
      expect(store.maxTrips()).toBe(6);
    });
  });

  describe('match days', () => {
    const days = [
      matchDay('past', { over: true }),
      matchDay('home', { noTrip: true }),
      matchDay('cancelled', { cancelled: true }),
      matchDay('next', { places: 4, expected: 8 }),
      matchDay('later', { places: 8, expected: 8 }),
    ];

    it('splits upcoming and past and finds the next match day that needs trips', () => {
      const { store } = setup();
      store.plan.set(plan({ matchDays: days }));
      expect(store.upcoming().map((m) => m.eventId)).toEqual([
        'home',
        'cancelled',
        'next',
        'later',
      ]);
      expect(store.past().map((m) => m.eventId)).toEqual(['past']);
      expect(store.nextMatchDay()?.eventId).toBe('next');
      expect(store.short().map((m) => m.eventId)).toEqual(['next']);
    });

    it('skips a match day left out of the driver list (DRV-35)', () => {
      const { store } = setup();
      const ignored = matchDay('ignored', { places: 0, expected: 8, ignored: true });
      store.plan.set(plan({ matchDays: [ignored, matchDay('later', { places: 4, expected: 8 })] }));
      expect(store.nextMatchDay()?.eventId).toBe('later');
      expect(store.short().map((m) => m.eventId)).toEqual(['later']);
      expect(store.skipped(ignored)).toBe(true);
      expect(store.missingPlaces(ignored)).toBe(0);
      expect(store.state(ignored)).toBe('none');
      expect(store.stateWithProposal(ignored)).toBe('none');
    });

    it('has no next match day if there are none', () => {
      const { store } = setup();
      expect(store.nextMatchDay()).toBeNull();
      expect(store.matchDays()).toEqual([]);
    });

    it('counts the missing places only where trips are needed', () => {
      const { store } = setup();
      expect(store.missingPlaces(matchDay('a', { expected: 8, places: 5 }))).toBe(3);
      expect(store.missingPlaces(matchDay('a', { expected: 8, places: 12 }))).toBe(0);
      expect(store.missingPlaces(matchDay('a', { expected: 8, places: 0, noTrip: true }))).toBe(0);
      expect(store.missingPlaces(matchDay('a', { expected: 8, places: 0, cancelled: true }))).toBe(
        0,
      );
      expect(store.missingPlaces(matchDay('a', { expected: 8, places: 0, over: true }))).toBe(0);
      expect(store.missingPlaces(matchDay('a', { expected: 8, places: 0, ready: false }))).toBe(0);
    });

    it('derives the state: enough at exactly the expected number, short below, none if no trip is needed', () => {
      const { store } = setup();
      expect(store.state(matchDay('a', { expected: 8, places: 8 }))).toBe('enough');
      expect(store.state(matchDay('a', { expected: 8, places: 7 }))).toBe('short');
      expect(store.state(matchDay('a', { noTrip: true }))).toBe('none');
      expect(store.state(matchDay('a', { cancelled: true }))).toBe('none');
      expect(store.state(matchDay('a', { ready: false }))).toBe('none');
    });

    it('shows a past match day with missing places as «short» (state) but without missing places', () => {
      const { store } = setup();
      const m = matchDay('a', { over: true, expected: 8, places: 3 });
      expect(store.state(m)).toBe('short');
      expect(store.missingPlaces(m)).toBe(0);
    });

    it('finds the upcoming trips of the signed-in person only', () => {
      const { store } = setup();
      const car = (id: string, driver: string) => ({
        id,
        driver: person(driver),
        seats: 4,
        freeSeats: 4,
        passengers: [],
      });
      store.plan.set(
        plan({
          matchDays: [
            matchDay('mine', { cars: [car('c1', 'cleo')] }),
            matchDay('other', { cars: [car('c2', 'anna')] }),
            matchDay('mine-cancelled', { cars: [car('c3', 'cleo')], cancelled: true }),
            matchDay('mine-over', { cars: [car('c4', 'cleo')], over: true }),
          ],
        }),
      );
      expect(store.myTrips().map((m) => m.eventId)).toEqual(['mine']);
    });

    it('has no trips of its own when nobody is signed in', () => {
      const { store } = setup('2026-11-24T10:00:00Z', null);
      store.plan.set(plan({ matchDays: [matchDay('x')] }));
      expect(store.meId()).toBeNull();
      expect(store.myTrips()).toEqual([]);
    });
  });

  describe('texts', () => {
    it('writes the day without the year in the current year and with it in other years', () => {
      const m = matchDay('a', { start: '2026-11-29T08:00:00Z' });
      expect(setup('2026-11-24T10:00:00Z').store.day(m)).toBe('So 29.11.');
    });

    it('writes the year if the backend clock is in another year', () => {
      TestBed.resetTestingModule();
      const m = matchDay('a', { start: '2026-11-29T08:00:00Z' });
      expect(setup('2025-11-24T10:00:00Z').store.day(m)).toBe('So 29.11.2026');
    });

    it('adds the clock time in Zurich', () => {
      const m = matchDay('a', { start: '2026-11-29T08:00:00Z' });
      expect(setup().store.dayTime(m)).toBe('So 29.11. 09:00');
    });

    it('joins venue and town, skipping what is missing', () => {
      const { store } = setup();
      expect(store.venue(matchDay('a', { venue: 'Halle', town: 'Sursee' }))).toBe('Halle, Sursee');
      expect(store.venue(matchDay('a', { venue: 'Halle' }))).toBe('Halle');
      expect(store.venue(matchDay('a', { town: 'Sursee' }))).toBe('Sursee');
      expect(store.venue(matchDay('a'))).toBe('');
    });

    it('shortens a name to first name and initial', () => {
      const { store } = setup();
      expect(store.shortName(person('p', 'Petra', 'Keller'))).toBe('Petra K.');
      expect(store.shortName(person('p', 'Petra', ''))).toBe('Petra');
    });
  });

  describe('usualSeats', () => {
    it('is the places of the car minus the driver', () => {
      const { store } = setup();
      store.plan.set(
        plan({ drivers: [setting('a', { carSeats: 5 }), setting('b', { carSeats: 2 })] }),
      );
      expect(store.usualSeats('a')).toBe(4);
      expect(store.usualSeats('b')).toBe(1);
    });

    it('stays between 1 and 8', () => {
      const { store } = setup();
      store.plan.set(
        plan({ drivers: [setting('a', { carSeats: 1 }), setting('b', { carSeats: 12 })] }),
      );
      expect(store.usualSeats('a')).toBe(1);
      expect(store.usualSeats('b')).toBe(8);
    });

    it('assumes a car with 5 places for unknown people and without a plan', () => {
      const { store } = setup();
      expect(store.usualSeats('nobody')).toBe(4);
      store.plan.set(plan());
      expect(store.usualSeats('nobody')).toBe(4);
    });
  });

  describe('proposal', () => {
    const base = () =>
      plan({
        matchDays: [
          matchDay('free', { places: 4, expected: 8 }),
          matchDay('cancelled', { cancelled: true }),
          matchDay('locked', { changeable: false }),
          matchDay('taken', {
            cars: [{ id: 'c', driver: person('anna'), seats: 4, freeSeats: 4, passengers: [] }],
          }),
          matchDay('past', { over: true }),
        ],
      });

    it('keeps only trips that still fit', () => {
      const { store } = setup();
      store.plan.set(base());
      store.proposal.set([
        trip('free', 'anna'),
        trip('cancelled', 'anna'),
        trip('locked', 'anna'),
        trip('taken', 'anna'), // anna drives there already
        trip('taken', 'berta'),
        trip('past', 'anna'),
        trip('unknown', 'anna'),
      ]);
      expect(store.proposed().map((t) => `${t.eventId}:${t.driver.id}`)).toEqual([
        'free:anna',
        'taken:berta',
      ]);
    });

    it('is empty without a proposal', () => {
      const { store } = setup();
      store.plan.set(base());
      expect(store.proposed()).toEqual([]);
    });

    it('adds seats plus the driver to the places of a match day', () => {
      const { store } = setup();
      store.plan.set(base());
      store.proposal.set([trip('free', 'anna', 3), trip('free', 'berta', 2)]);
      const free = store.matchDays()[0];
      expect(store.proposedOn('free')).toHaveLength(2);
      expect(store.placesWithProposal(free)).toBe(4 + (3 + 1) + (2 + 1));
      expect(store.stateWithProposal(free)).toBe('enough');
    });

    it('stays «short» if the proposal is not enough and «none» where no trip is needed', () => {
      const { store } = setup();
      store.plan.set(base());
      store.proposal.set([trip('free', 'anna', 1)]);
      expect(store.stateWithProposal(store.matchDays()[0])).toBe('short');
      expect(store.stateWithProposal(matchDay('x', { noTrip: true }))).toBe('none');
    });
  });

  describe('load', () => {
    it('loads the plan of a team, with the season as a parameter', async () => {
      const { store, http } = setup();
      const done = store.load('t1', 's7');
      expect(store.loading()).toBe(true);
      const req = http.expectOne((r) => r.url === '/api/teams/t1/driver-plan');
      expect(req.request.params.get('season')).toBe('s7');
      req.flush(plan());
      await done;
      expect(store.loading()).toBe(false);
      expect(store.error()).toBeNull();
      expect(store.plan()?.teamId).toBe('t1');
    });

    it('sends no season parameter for the current season', async () => {
      const { store, http } = setup();
      const done = store.load('t1', undefined);
      const req = http.expectOne('/api/teams/t1/driver-plan');
      expect(req.request.params.keys()).toEqual([]);
      req.flush(plan());
      await done;
    });

    it('says «not found» for 404 and drops the old plan', async () => {
      const { store, http } = setup();
      store.plan.set(plan());
      const done = store.load('t1', undefined);
      http
        .expectOne('/api/teams/t1/driver-plan')
        .flush({}, { status: 404, statusText: 'Not Found' });
      await done;
      expect(store.plan()).toBeNull();
      expect(store.error()).toBe(TEXTS.teams.notFound);
      expect(store.loading()).toBe(false);
    });

    it('shows the detail of other errors', async () => {
      const { store, http } = setup();
      const done = store.load('t1', undefined);
      http
        .expectOne('/api/teams/t1/driver-plan')
        .flush({ detail: 'Kaputt' }, { status: 500, statusText: 'x' });
      await done;
      expect(store.error()).toBe('Kaputt');
    });

    it('forgets opened driver lists and old errors', async () => {
      const { store, http } = setup();
      store.put(list('e1'));
      store.error.set('old');
      const done = store.load('t1', undefined);
      expect(store.lists()).toEqual({});
      expect(store.error()).toBeNull();
      http.expectOne('/api/teams/t1/driver-plan').flush(plan());
      await done;
    });
  });

  describe('driver lists', () => {
    it('puts and opens lists by event', async () => {
      const { store, http } = setup();
      store.put(list('e1'));
      const opened = store.open('e2');
      http.expectOne('/api/events/e2/driver-list').flush(list('e2'));
      await opened;
      expect(Object.keys(store.lists())).toEqual(['e1', 'e2']);
    });

    it('shows the error if a list cannot be opened', async () => {
      const { store, http } = setup();
      const opened = store.open('e2');
      http
        .expectOne('/api/events/e2/driver-list')
        .flush({ detail: 'Nein' }, { status: 409, statusText: 'x' });
      await opened;
      expect(store.error()).toBe('Nein');
      expect(store.lists()).toEqual({});
    });
  });

  describe('season actions', () => {
    async function loaded() {
      const ctx = setup();
      const done = ctx.store.load('t1', 's1');
      ctx.http
        .expectOne((r) => r.url === '/api/teams/t1/driver-plan')
        .flush(plan({ matchDays: [matchDay('free')] }));
      await done;
      return ctx;
    }

    it('shows a proposal without entering anything', async () => {
      const { store, http } = await loaded();
      const done = store.preview();
      expect(store.running()).toBe('distribute');
      expect(store.busy()).toBe(true);
      const req = http.expectOne((r) => r.url === '/api/teams/t1/driver-plan/distribute/preview');
      expect(req.request.method).toBe('POST');
      req.flush({ trips: [trip('free', 'anna')] });
      await done;
      expect(store.proposal()).toHaveLength(1);
      expect(store.running()).toBeNull();
    });

    it('says there is nothing to distribute for an empty proposal', async () => {
      const { store, http } = await loaded();
      store.proposal.set([trip('free', 'anna')]);
      const done = store.preview();
      http.expectOne((r) => r.url.endsWith('/distribute/preview')).flush({ trips: [] });
      await done;
      expect(store.proposal()).toBeNull();
      expect(store.success()).toBe(T.proposalNothing);
    });

    it('enters only the trips that still fit and clears the proposal', async () => {
      const { store, http } = await loaded();
      store.proposal.set([trip('free', 'anna', 3), trip('gone', 'berta', 4)]);
      const done = store.acceptProposal();
      const req = http.expectOne((r) => r.url === '/api/teams/t1/driver-plan/distribute');
      expect(req.request.body).toEqual({
        trips: [{ eventId: 'free', driverId: 'anna', seats: 3 }],
      });
      req.flush(plan({ matchDays: [] }));
      expect(await done).toBe(true);
      expect(store.proposal()).toBeNull();
      expect(store.success()).toBe(T.distributed);
      expect(store.running()).toBeNull();
    });

    it('keeps the proposal and shows the error if entering fails', async () => {
      const { store, http } = await loaded();
      store.proposal.set([trip('free', 'anna')]);
      const done = store.acceptProposal();
      http
        .expectOne((r) => r.url.endsWith('/distribute'))
        .flush({ detail: 'Zu spät' }, { status: 409, statusText: 'x' });
      expect(await done).toBe(false);
      expect(store.proposal()).toHaveLength(1);
      expect(store.error()).toBe('Zu spät');
      expect(store.success()).toBeNull();
      expect(store.running()).toBeNull();
    });

    it('discards a proposal with a message', async () => {
      const { store } = await loaded();
      store.proposal.set([trip('free', 'anna')]);
      store.error.set('x');
      store.discardProposal();
      expect(store.proposal()).toBeNull();
      expect(store.error()).toBeNull();
      expect(store.success()).toBe(T.proposalDiscarded);
    });

    it('ignores a second action while one is running', async () => {
      const { store, http } = await loaded();
      const first = store.removeAll();
      expect(await store.setExpected(5)).toBe(false);
      http.expectNone((r) => r.url.endsWith('/expected'));
      http.expectOne((r) => r.url === '/api/teams/t1/driver-plan/cars').flush(plan());
      expect(await first).toBe(true);
      expect(store.success()).toBe(T.removedAll);
    });

    it('loads opened match days again after a change of the plan', async () => {
      const { store, http } = await loaded();
      store.put(list('free'));
      const done = store.setExpected(6);
      const req = http.expectOne((r) => r.url === '/api/teams/t1/driver-plan/expected');
      expect(req.request.body).toEqual({ expected: 6 });
      expect(req.request.method).toBe('PUT');
      req.flush(plan());
      await Promise.resolve();
      await Promise.resolve();
      http.expectOne('/api/events/free/driver-list').flush(list('free', { defaultSeats: 4 }));
      expect(await done).toBe(true);
      expect(store.lists()['free']).toMatchObject({ defaultSeats: 4 });
    });

    it('asks the next in line to replace a person, with the right message', async () => {
      const { store, http } = await loaded();
      const m = store.matchDays()[0];
      const done = store.next(m, person('anna', 'Anna', 'Muster'));
      const req = http.expectOne((r) => r.url === '/api/teams/t1/driver-plan/match-days/free/next');
      expect(req.request.body).toEqual({ replaces: 'anna' });
      expect(store.running()).toBe('pass:anna:free');
      req.flush(plan());
      await done;
      expect(store.success()).toBe(T.passed('Anna Muster', store.day(m)));
    });

    it('adds one more car without a replacement', async () => {
      const { store, http } = await loaded();
      const m = store.matchDays()[0];
      const done = store.next(m, null);
      const req = http.expectOne((r) => r.url.endsWith('/match-days/free/next'));
      expect(req.request.body).toEqual({});
      req.flush(plan());
      await done;
      expect(store.success()).toBe(T.carAdded(store.day(m)));
    });

    it('hands a car over directly', async () => {
      const { store, http } = await loaded();
      const m = store.matchDays()[0];
      const done = store.handOver(m, person('anna'), person('berta'));
      const req = http.expectOne((r) => r.url === '/api/teams/t1/driver-plan/swaps');
      expect(req.request.body).toEqual({
        eventId: 'free',
        driverId: 'anna',
        partnerId: 'berta',
        direct: true,
      });
      req.flush(plan());
      await done;
    });
  });

  describe('match day actions (changeList)', () => {
    async function loaded() {
      const ctx = setup();
      const done = ctx.store.load('t1', undefined);
      ctx.http.expectOne('/api/teams/t1/driver-plan').flush(plan());
      await done;
      return ctx;
    }

    it('enters a driver, updates an opened list and loads the plan again', async () => {
      const { store, http } = await loaded();
      store.put(list('e1'));
      const done = store.enterDriver('e1', person('anna', 'Anna', 'Muster'), 4);
      const req = http.expectOne('/api/events/e1/driver-list/cars');
      expect(req.request.body).toEqual({ driverId: 'anna', seats: 4 });
      req.flush(list('e1', { defaultSeats: 7 }));
      await Promise.resolve();
      http.expectOne('/api/teams/t1/driver-plan').flush(plan());
      expect(await done).toBe(true);
      expect(store.lists()['e1']).toMatchObject({ defaultSeats: 7 });
      expect(store.success()).toBe(TEXTS.simplePlan.board.added('Anna Muster'));
    });

    it('does not add a list that was not opened', async () => {
      const { store, http } = await loaded();
      const done = store.enterDriver('e1', person('anna'), 4);
      http.expectOne('/api/events/e1/driver-list/cars').flush(list('e1'));
      await Promise.resolve();
      http.expectOne('/api/teams/t1/driver-plan').flush(plan());
      await done;
      expect(store.lists()).toEqual({});
    });

    it('words the success with the day if one is given', async () => {
      const { store, http } = await loaded();
      const done = store.enterDriver('e1', person('anna', 'Anna', 'Muster'), 4, 'So 29.11.');
      http.expectOne('/api/events/e1/driver-list/cars').flush(list('e1'));
      await Promise.resolve();
      http.expectOne('/api/teams/t1/driver-plan').flush(plan());
      await done;
      expect(store.success()).toBe('Anna Muster fährt am So 29.11.');
    });

    it('loads an opened list again after an error (e.g. the car is full)', async () => {
      const { store, http } = await loaded();
      store.put(list('e1'));
      const done = store.seat('e1', 'c1', person('anna'));
      const req = http.expectOne('/api/events/e1/driver-list/cars/c1/passengers/anna');
      expect(req.request.method).toBe('PUT');
      req.flush({ detail: 'Das Auto ist voll.' }, { status: 409, statusText: 'x' });
      await Promise.resolve();
      await Promise.resolve();
      http.expectOne('/api/events/e1/driver-list').flush(list('e1', { defaultSeats: 9 }));
      expect(await done).toBe(false);
      expect(store.error()).toBe('Das Auto ist voll.');
      expect(store.lists()['e1']).toMatchObject({ defaultSeats: 9 });
      expect(store.running()).toBeNull();
    });

    it('does not reload the plan on a match day page (no team loaded)', async () => {
      const { store, http } = setup();
      const done = store.setSeats('e1', 'c1', 3);
      const req = http.expectOne('/api/events/e1/driver-list/cars/c1');
      expect(req.request.body).toEqual({ seats: 3 });
      req.flush(list('e1'));
      expect(await done).toBe(true);
    });

    it('marks and unmarks travelling individually', async () => {
      const { store, http } = setup();
      const on = store.setIndividual('e1', person('anna', 'Anna', 'Muster'), true);
      const put = http.expectOne('/api/events/e1/driver-list/individual/anna');
      expect(put.request.method).toBe('PUT');
      put.flush(list('e1'));
      await on;
      expect(store.success()).toBe(TEXTS.individual.saved('Anna Muster'));

      const off = store.setIndividual('e1', person('anna', 'Anna', 'Muster'), false);
      const del = http.expectOne('/api/events/e1/driver-list/individual/anna');
      expect(del.request.method).toBe('DELETE');
      del.flush(list('e1'));
      await off;
      expect(store.success()).toBe(TEXTS.individual.removed('Anna Muster'));
    });

    it('takes a driver off', async () => {
      const { store, http } = setup();
      const done = store.dropDriver('e1', 'c1', person('anna', 'Anna', 'Muster'));
      const req = http.expectOne('/api/events/e1/driver-list/cars/c1');
      expect(req.request.method).toBe('DELETE');
      req.flush(list('e1'));
      await done;
      expect(store.success()).toBe(TEXTS.simplePlan.board.removed('Anna Muster'));
    });
  });

  describe('fill («Alle verteilen»)', () => {
    const proposal = (over: Partial<DriverProposal>): DriverProposal =>
      ({ eventId: 'e1', cars: [], withoutSeat: [], changes: false, ...over }) as DriverProposal;
    const car = (driver: string, over: Record<string, unknown> = {}) => ({
      driver: person(driver, driver, 'X'),
      seats: 4,
      existing: false,
      passengers: [],
      added: [],
      ...over,
    });

    it('applies only the cars that are new or get passengers and names the new drivers', async () => {
      const { store, http } = setup();
      const done = store.fill('e1');
      expect(store.running()).toBe('fill:e1');
      http.expectOne('/api/events/e1/driver-list/proposal').flush(
        proposal({
          cars: [
            car('old-unchanged', { existing: true }),
            car('old-filled', { existing: true, added: [person('p1')] }),
            car('fresh', { added: [person('p2'), person('p3')] }),
          ],
        }),
      );
      await Promise.resolve();
      await Promise.resolve();
      const apply = http.expectOne('/api/events/e1/driver-list/proposal/apply');
      expect(apply.request.body).toEqual({
        cars: [
          { driverId: 'old-filled', seats: 4, passengerIds: ['p1'] },
          { driverId: 'fresh', seats: 4, passengerIds: ['p2', 'p3'] },
        ],
      });
      apply.flush(list('e1', { defaultSeats: 3 }));
      expect(await done).toBe(true);
      expect(store.lists()['e1']).toMatchObject({ defaultSeats: 3 });
      expect(store.success()).toBe(T.filledWith('fresh X'));
    });

    it('says nothing was to do if no car changes', async () => {
      const { store, http } = setup();
      const done = store.fill('e1');
      http
        .expectOne('/api/events/e1/driver-list/proposal')
        .flush(proposal({ cars: [car('a', { existing: true })] }));
      expect(await done).toBe(true);
      expect(store.success()).toBe(T.filledNothing);
    });

    it('warns if somebody still has no seat', async () => {
      const { store, http } = setup();
      const done = store.fill('e1');
      http
        .expectOne('/api/events/e1/driver-list/proposal')
        .flush(proposal({ cars: [], withoutSeat: [person('p1'), person('p2')] }));
      await done;
      expect(store.success()).toBe(T.filledShort(2));
    });

    it('reports an error and returns false', async () => {
      const { store, http } = setup();
      const done = store.fill('e1');
      http
        .expectOne('/api/events/e1/driver-list/proposal')
        .flush({}, { status: 403, statusText: 'x' });
      expect(await done).toBe(false);
      expect(store.error()).toBe(TEXTS.common.noPermission);
      expect(store.running()).toBeNull();
    });
  });

  describe('setDriving', () => {
    it('saves the setting and loads the plan again', async () => {
      const { store, http } = setup();
      const l = store.load('t1', undefined);
      http.expectOne('/api/teams/t1/driver-plan').flush(plan());
      await l;
      const done = store.setDriving(person('anna'), true, 5);
      const put = http.expectOne('/api/persons/anna/driving');
      expect(put.request.body).toEqual({ canDrive: true, carSeats: 5 });
      put.flush(null);
      await Promise.resolve();
      http
        .expectOne('/api/teams/t1/driver-plan')
        .flush(plan({ teamName: 'Neu' } as Partial<DriverPlan>));
      await done;
      expect(store.plan()?.teamName).toBe('Neu');
      expect(store.running()).toBeNull();
    });

    it('ignores the call while busy', async () => {
      const { store, http } = setup();
      store.running.set('something');
      await store.setDriving(person('anna'), true, 5);
      http.expectNone('/api/persons/anna/driving');
    });
  });
});
