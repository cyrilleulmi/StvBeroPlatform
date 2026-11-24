import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, firstValueFrom } from 'rxjs';
import type {
  DriverList,
  DriverPlan,
  DriverPlanDistribution,
  DriverPlanMatchDay,
  DriverPlanProposedTrip,
  DriverProposal,
  DriverSetting,
  PersonSummary,
} from '../../api/types';
import { clockTime, eventDate, yearOf } from '../../core/time';
import { AuthService } from '../../core/auth.service';
import { errorStatus, errorText } from '../../core/error-text';
import { name } from '../../core/format';
import { TEXTS } from '../../core/texts';

/** A member who can drive, with her trips in the season: the order of «Wer ist dran» (DRV-30, DRV-31). */
export interface QueueEntry {
  person: PersonSummary;
  /** Trips that are over plus planned ones. */
  trips: number;
  done: number;
  planned: number;
  kilometres: number;
  canDrive: boolean;
  carSeats: number;
  canChange: boolean;
}

/**
 * State and actions of the simple driver list (DRV-29 to DRV-33): the plan of a team for a season and the driver
 * lists of the match days that are opened. The page provides it; its parts inject it. On the match day
 * (`/termine/:id`) only the driver list of that match day is loaded, without a plan.
 */
@Injectable()
export class PlanStore {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly t = TEXTS.simplePlan;

  private team = '';
  private season: string | undefined;

  readonly plan = signal<DriverPlan | null>(null);
  readonly loading = signal(true);
  /** Which action is running, e.g. `distribute` or `seat:<personId>`. */
  readonly running = signal<string | null>(null);
  readonly busy = computed(() => this.running() !== null);
  readonly error = signal<string | null>(null);
  readonly success = signal<string | null>(null);
  /** Driver lists of opened match days, by event. */
  readonly lists = signal<Record<string, DriverList>>({});
  /** «Fair verteilen» shows its proposal first; nothing is entered until it is accepted (DRV-30). */
  readonly proposal = signal<DriverPlanProposedTrip[] | null>(null);

  readonly meId = computed(() => this.auth.me()?.id ?? null);
  private readonly year = computed(() => yearOf(this.auth.me()?.now));

  readonly canManage = computed(() => this.plan()?.canManage === true);
  readonly matchDays = computed(() => this.plan()?.matchDays ?? []);
  readonly upcoming = computed(() => this.matchDays().filter((m) => !m.over));
  readonly past = computed(() => this.matchDays().filter((m) => m.over));
  /** The next match day people have to drive to. */
  readonly nextMatchDay = computed(() => this.upcoming().find((m) => !m.cancelled && !m.noTrip) ?? null);
  /** Upcoming match days that still lack places. */
  readonly short = computed(() => this.upcoming().filter((m) => this.missingPlaces(m) > 0));

  /** Everyone of the team with her trips, the one who is next in line first. */
  readonly queue = computed<QueueEntry[]>(() => {
    const plan = this.plan();
    if (!plan) return [];
    const shares = new Map(plan.shares.map((s) => [s.person.id, s]));
    return plan.drivers
      .map((d: DriverSetting) => {
        const s = shares.get(d.person.id);
        const done = s?.trips ?? 0;
        const planned = s?.plannedTrips ?? 0;
        return {
          person: d.person,
          trips: done + planned,
          done,
          planned,
          kilometres: (s?.kilometres ?? 0) + (s?.plannedKilometres ?? 0),
          canDrive: d.canDrive,
          carSeats: d.carSeats,
          canChange: d.canChange,
        };
      })
      .sort(
        (a, b) =>
          Number(b.canDrive) - Number(a.canDrive) || a.trips - b.trips || a.kilometres - b.kilometres || 0,
      );
  });
  readonly maxTrips = computed(() => Math.max(1, ...this.queue().map((q) => q.trips)));

  /**
   * The proposed trips that still fit: the match day can be planned and the person does not drive there yet. A
   * change made while the proposal is shown keeps the rest of it.
   */
  readonly proposed = computed<DriverPlanProposedTrip[]>(() => {
    const trips = this.proposal() ?? [];
    const days = new Map(this.upcoming().map((m) => [m.eventId, m]));
    return trips.filter((t) => {
      const m = days.get(t.eventId);
      return !!m && !m.cancelled && m.changeable && !m.cars.some((car) => car.driver.id === t.driver.id);
    });
  });

  /** Match days on which the signed-in person drives, upcoming ones. */
  readonly myTrips = computed(() => {
    const me = this.meId();
    return this.upcoming().filter((m) => !m.cancelled && m.cars.some((car) => car.driver.id === me));
  });

  // ------------------------------------------------------------------ Texts and numbers

  /** «So 29.11.» */
  day(m: DriverPlanMatchDay): string {
    return eventDate(m.start, this.year());
  }

  /** «So 29.11. 09:00» */
  dayTime(m: DriverPlanMatchDay): string {
    return `${this.day(m)} ${clockTime(m.start)}`;
  }

  venue(m: DriverPlanMatchDay): string {
    return [m.venue, m.town].filter(Boolean).join(', ');
  }

  /** Places that are missing for everyone who is expected; 0 for home, cancelled and past match days. */
  missingPlaces(m: DriverPlanMatchDay): number {
    if (m.noTrip || m.cancelled || m.over || !m.ready) return 0;
    return Math.max(0, m.expected - m.places);
  }

  /** `enough`, `short`, `none` (home, cancelled, not ready) for colours and test attributes. */
  state(m: DriverPlanMatchDay): 'enough' | 'short' | 'none' {
    if (m.noTrip || m.cancelled || !m.ready) return 'none';
    return m.places >= m.expected ? 'enough' : 'short';
  }

  /** «Petra K.»: first name and the initial of the last name, for narrow places. */
  shortName(p: PersonSummary): string {
    return p.lastName ? `${p.firstName} ${p.lastName.charAt(0)}.` : p.firstName;
  }

  tripsOf(personId: string): number {
    return this.queue().find((q) => q.person.id === personId)?.trips ?? 0;
  }

  /** Seats for passengers a person usually offers: her places minus herself (DRV-18). */
  usualSeats(personId: string): number {
    const places = this.plan()?.drivers.find((d) => d.person.id === personId)?.carSeats ?? 5;
    return Math.max(1, Math.min(8, places - 1));
  }

  /** Proposed trips of a match day. */
  proposedOn(eventId: string): DriverPlanProposedTrip[] {
    return this.proposed().filter((t) => t.eventId === eventId);
  }

  /** Places of a match day with the proposal: the cars plus the proposed ones with their drivers. */
  placesWithProposal(m: DriverPlanMatchDay): number {
    return m.places + this.proposedOn(m.eventId).reduce((sum, t) => sum + t.seats + 1, 0);
  }

  /** `enough` or `short` with the proposal, for the foot of the grid. */
  stateWithProposal(m: DriverPlanMatchDay): 'enough' | 'short' | 'none' {
    if (m.noTrip || m.cancelled || !m.ready) return 'none';
    return this.placesWithProposal(m) >= m.expected ? 'enough' : 'short';
  }

  // ------------------------------------------------------------------ Loading

  async load(team: string, season: string | undefined): Promise<void> {
    this.team = team;
    this.season = season;
    this.loading.set(true);
    this.error.set(null);
    this.lists.set({});
    try {
      this.plan.set(await firstValueFrom(this.http.get<DriverPlan>(this.url(''), { params: this.params() })));
    } catch (e) {
      this.plan.set(null);
      this.error.set(errorStatus(e) === 404 ? TEXTS.teams.notFound : errorText(e));
    } finally {
      this.loading.set(false);
    }
  }

  /** Shows a driver list that was loaded elsewhere, e.g. by the section on the match day. */
  put(list: DriverList): void {
    this.lists.update((all) => ({ ...all, [list.eventId]: list }));
  }

  /** Loads the plan again, if this store shows one (not on the match day). */
  private async reloadPlan(): Promise<void> {
    if (!this.team) return;
    this.plan.set(await firstValueFrom(this.http.get<DriverPlan>(this.url(''), { params: this.params() })));
  }

  /** Loads the driver list of a match day (cars with passengers, who needs a seat). */
  async open(eventId: string): Promise<void> {
    try {
      const list = await firstValueFrom(this.http.get<DriverList>(this.listUrl(eventId, '')));
      this.lists.update((all) => ({ ...all, [eventId]: list }));
    } catch (e) {
      this.error.set(errorText(e));
    }
  }

  private url(path: string): string {
    return `/api/teams/${this.team}/driver-plan${path}`;
  }

  private listUrl(eventId: string, path: string): string {
    return `/api/events/${eventId}/driver-list${path}`;
  }

  private params(): Record<string, string> {
    return this.season ? { season: this.season } : {};
  }

  /** Runs a change of the plan; the answer is the plan. Opened match days are loaded again. */
  private async change(key: string, request: Observable<DriverPlan>, success?: string): Promise<boolean> {
    if (this.busy()) return false;
    this.running.set(key);
    this.error.set(null);
    this.success.set(null);
    try {
      this.plan.set(await firstValueFrom(request));
      await Promise.all(Object.keys(this.lists()).map((id) => this.open(id)));
      if (success) this.success.set(success);
      return true;
    } catch (e) {
      this.error.set(errorText(e));
      return false;
    } finally {
      this.running.set(null);
    }
  }

  /** Runs a change of one match day; the answer is its driver list. The plan is loaded again. */
  private async changeList(
    key: string,
    eventId: string,
    request: Observable<DriverList>,
    success?: string,
  ): Promise<boolean> {
    if (this.busy()) return false;
    this.running.set(key);
    this.error.set(null);
    this.success.set(null);
    try {
      const list = await firstValueFrom(request);
      if (this.lists()[eventId]) this.lists.update((all) => ({ ...all, [eventId]: list }));
      await this.reloadPlan();
      if (success) this.success.set(success);
      return true;
    } catch (e) {
      this.error.set(errorText(e));
      // The list may have changed meanwhile (e.g. car full): show the current state
      if (this.lists()[eventId]) await this.open(eventId);
      return false;
    } finally {
      this.running.set(null);
    }
  }

  // ------------------------------------------------------------------ Season (DRV-29 to DRV-31, DRV-33)

  /** «Fair verteilen»: asks for a proposal and shows it; nothing is entered yet (DRV-30). */
  async preview(): Promise<void> {
    if (this.busy()) return;
    this.running.set('distribute');
    this.error.set(null);
    this.success.set(null);
    try {
      const d = await firstValueFrom(
        this.http.post<DriverPlanDistribution>(this.url('/distribute/preview'), null, { params: this.params() }),
      );
      if (d.trips.length === 0) {
        this.proposal.set(null);
        this.success.set(this.t.proposalNothing);
      } else {
        this.proposal.set(d.trips);
      }
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.running.set(null);
    }
  }

  /** Enters the proposal as it is shown; the trips then stay (DRV-30). */
  async acceptProposal(): Promise<boolean> {
    const trips = this.proposed().map((t) => ({ eventId: t.eventId, driverId: t.driver.id, seats: t.seats }));
    const done = await this.change(
      'accept',
      this.http.post<DriverPlan>(this.url('/distribute'), { trips }, { params: this.params() }),
      this.t.distributed,
    );
    if (done) this.proposal.set(null);
    return done;
  }

  discardProposal(): void {
    this.proposal.set(null);
    this.error.set(null);
    this.success.set(this.t.proposalDiscarded);
  }

  /** «Alle entfernen»: every driver of the upcoming match days (DRV-33). */
  async removeAll(): Promise<boolean> {
    const done = await this.change(
      'remove-all',
      this.http.delete<DriverPlan>(this.url('/cars'), { params: this.params() }),
      this.t.removedAll,
    );
    if (done) this.proposal.set(null);
    return done;
  }

  /** People expected per match day of the team; `null`: everyone. */
  setExpected(expected: number | null): Promise<boolean> {
    return this.change(
      'expected',
      this.http.put<DriverPlan>(this.url('/expected'), { expected }, { params: this.params() }),
    );
  }

  /** People expected on one match day; `null`: the number of the team. */
  setMatchDayExpected(eventId: string, expected: number | null): Promise<boolean> {
    return this.change(
      'expected:' + eventId,
      this.http.put<DriverPlan>(this.url(`/match-days/${eventId}/expected`), { expected }, { params: this.params() }),
    );
  }

  /** The next in line drives: one more car, or instead of `replaces` who cannot drive. */
  next(m: DriverPlanMatchDay, replaces: PersonSummary | null): Promise<boolean> {
    return this.change(
      (replaces ? 'pass:' + replaces.id : 'next') + ':' + m.eventId,
      this.http.post<DriverPlan>(
        this.url(`/match-days/${m.eventId}/next`),
        replaces ? { replaces: replaces.id } : {},
        { params: this.params() },
      ),
      replaces ? this.t.passed(name(replaces), this.day(m)) : this.t.carAdded(this.day(m)),
    );
  }

  /** Enters a person as driver of a match day with her usual seats. */
  addDriver(m: DriverPlanMatchDay, person: PersonSummary): Promise<boolean> {
    return this.enterDriver(m.eventId, person, this.usualSeats(person.id), this.day(m));
  }

  /** Enters a person as driver of a match day (DRV-02, DRV-32). */
  enterDriver(eventId: string, person: PersonSummary, seats: number, day?: string): Promise<boolean> {
    return this.changeList(
      'add:' + person.id + ':' + eventId,
      eventId,
      this.http.post<DriverList>(this.listUrl(eventId, '/cars'), { driverId: person.id, seats }),
      day ? this.t.driverAdded(name(person), day) : this.t.board.added(name(person)),
    );
  }

  removeCar(m: DriverPlanMatchDay, carId: string, driver: PersonSummary): Promise<boolean> {
    return this.dropDriver(m.eventId, carId, driver, this.day(m));
  }

  /** Takes a driver off a match day; her passengers lose their seats (DRV-02, DRV-11). */
  dropDriver(eventId: string, carId: string, driver: PersonSummary, day?: string): Promise<boolean> {
    return this.changeList(
      'remove:' + carId,
      eventId,
      this.http.delete<DriverList>(this.listUrl(eventId, `/cars/${carId}`)),
      day ? this.t.driverRemoved(name(driver), day) : this.t.board.removed(name(driver)),
    );
  }

  /** Travelling on one's own, from the cars of a match day (DRV-20). */
  setIndividual(eventId: string, person: PersonSummary, individual: boolean): Promise<boolean> {
    const url = this.listUrl(eventId, `/individual/${person.id}`);
    return this.changeList(
      'individual:' + person.id,
      eventId,
      individual ? this.http.put<DriverList>(url, {}) : this.http.delete<DriverList>(url),
      individual ? TEXTS.individual.saved(name(person)) : TEXTS.individual.removed(name(person)),
    );
  }

  /** `to` takes over the car of `from` with its seats and passengers (direct takeover, DRV-25). */
  handOver(m: DriverPlanMatchDay, from: PersonSummary, to: PersonSummary): Promise<boolean> {
    return this.change(
      'handover:' + from.id + ':' + m.eventId,
      this.http.post<DriverPlan>(
        this.url('/swaps'),
        { eventId: m.eventId, driverId: from.id, partnerId: to.id, direct: true },
        { params: this.params() },
      ),
      this.t.handedOver(name(from), name(to), this.day(m)),
    );
  }

  /** Can drive and the places of the car (DRV-18). */
  async setDriving(person: PersonSummary, canDrive: boolean, carSeats: number): Promise<void> {
    if (this.busy()) return;
    this.running.set('driving:' + person.id);
    this.error.set(null);
    this.success.set(null);
    try {
      await firstValueFrom(this.http.put(`/api/persons/${person.id}/driving`, { canDrive, carSeats }));
      this.plan.set(await firstValueFrom(this.http.get<DriverPlan>(this.url(''), { params: this.params() })));
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.running.set(null);
    }
  }

  // ------------------------------------------------------------------ Match day (DRV-02, DRV-03, DRV-19)

  /** Puts a person into a car, or moves her there. */
  seat(eventId: string, carId: string, person: PersonSummary): Promise<boolean> {
    return this.changeList(
      'seat:' + person.id,
      eventId,
      this.http.put<DriverList>(this.listUrl(eventId, `/cars/${carId}/passengers/${person.id}`), null),
    );
  }

  unseat(eventId: string, carId: string, person: PersonSummary): Promise<boolean> {
    return this.changeList(
      'seat:' + person.id,
      eventId,
      this.http.delete<DriverList>(this.listUrl(eventId, `/cars/${carId}/passengers/${person.id}`)),
    );
  }

  setSeats(eventId: string, carId: string, seats: number): Promise<boolean> {
    return this.changeList(
      'seats:' + carId,
      eventId,
      this.http.put<DriverList>(this.listUrl(eventId, `/cars/${carId}`), { seats }),
    );
  }

  /**
   * «Alle verteilen»: everyone who needs a seat gets one. Cars stay and are filled first; if they are not enough,
   * the next in line bring their cars (the proposal of DRV-19, entered right away).
   */
  async fill(eventId: string): Promise<boolean> {
    if (this.busy()) return false;
    this.running.set('fill:' + eventId);
    this.error.set(null);
    this.success.set(null);
    try {
      const p = await firstValueFrom(this.http.post<DriverProposal>(this.listUrl(eventId, '/proposal'), null));
      const cars = p.cars
        .filter((c) => !c.existing || c.added.length > 0)
        .map((c) => ({ driverId: c.driver.id, seats: c.seats, passengerIds: c.added.map((x) => x.id) }));
      if (cars.length > 0) {
        const list = await firstValueFrom(this.http.post<DriverList>(this.listUrl(eventId, '/proposal/apply'), { cars }));
        this.lists.update((all) => ({ ...all, [eventId]: list }));
        await this.reloadPlan();
      }
      const added = p.cars.filter((c) => !c.existing).map((c) => name(c.driver));
      this.success.set(
        p.withoutSeat.length > 0
          ? this.t.filledShort(p.withoutSeat.length)
          : cars.length === 0
            ? this.t.filledNothing
            : added.length > 0
              ? this.t.filledWith(added.join(', '))
              : this.t.filled,
      );
      return true;
    } catch (e) {
      this.error.set(errorText(e));
      return false;
    } finally {
      this.running.set(null);
    }
  }
}
