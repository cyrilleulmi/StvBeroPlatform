package ch.stvbero.platform.driver;

import ch.stvbero.platform.core.auth.CurrentUser;
import ch.stvbero.platform.core.clock.BackendClock;
import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.attendance.event.Event;
import ch.stvbero.platform.attendance.event.EventStatus;
import ch.stvbero.platform.attendance.event.EventType;
import ch.stvbero.platform.attendance.event.NotificationRules;
import ch.stvbero.platform.notification.Message;
import ch.stvbero.platform.notification.NotificationType;
import ch.stvbero.platform.notification.Notifications;
import ch.stvbero.platform.core.person.Person;
import ch.stvbero.platform.core.person.PersonSummary;
import ch.stvbero.platform.core.season.Season;
import ch.stvbero.platform.core.team.Team;
import ch.stvbero.platform.core.team.TeamMembership;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Driver plan of a team for a whole season (DRV-14, DRV-17): often one person plans the drivers of all match days
 * at once. Members of the team, coaches and admins see it; entering follows the rules of the driver list
 * (DRV-02): members themselves, coaches and admins also others.
 */
@ApplicationScoped
public class DriverPlanService {

    /** Routes calculated per view of the plan, so that a first view never asks the routing service too often. */
    static final int ROUTES_PER_VIEW = 12;

    @Inject
    EntityManager em;

    @Inject
    BackendClock clock;

    @Inject
    Routes routes;

    @Inject
    MatchDayRoutes matchDayRoutes;

    @Inject
    DriverListService drivers;

    @Inject
    DriverSwaps swaps;

    @Inject
    ExtraTrips extraTrips;

    @Inject
    DriverPlanHistory history;

    @Inject
    Notifications notifications;

    record Context(Team team, Season season, List<TeamMembership> members, boolean member,
                           boolean canManage, UUID caller, CurrentUser user) {
    }

    private record Car(UUID id, UUID eventId, UUID driver, int seats) {
    }

    /** The plan. Calculates missing routes first (DRV-05), outside of a transaction. */
    public DriverPlanDto get(CurrentUser user, UUID teamId, UUID seasonId) {
        Map<UUID, Place[]> toEnsure = QuarkusTransaction.requiringNew().call(() -> {
            Context c = context(user, teamId, seasonId);
            Map<UUID, Place[]> map = new LinkedHashMap<>();
            for (Event event : matchDays(c)) {
                if (map.size() < ROUTES_PER_VIEW && event.status != EventStatus.CANCELLED
                        && MatchDayRoutes.missing(event).isEmpty() && matchDayRoutes.current(event).isEmpty()) {
                    map.put(event.id, matchDayRoutes.places(event));
                }
            }
            return map;
        });
        toEnsure.forEach((id, places) -> routes.ensure(id, places[0], places[1]));
        return QuarkusTransaction.requiringNew().call(() -> dto(context(user, teamId, seasonId)));
    }

    /**
     * Enters one driver for several match days at once (DRV-14). Match days on which she already drives are left
     * as they are; every other rule of the driver list applies per match day (DRV-01, DRV-02), and if one match
     * day refuses, nothing is entered.
     */
    public DriverPlanDto plan(CurrentUser user, UUID teamId, UUID seasonId, DriverPlanDto.Input input) {
        QuarkusTransaction.requiringNew().run(() -> {
            Context c = context(user, teamId, seasonId);
            UUID driver = input.driverId() == null ? user.personId() : input.driverId();
            // Planning the season for others stays with coaches and admins (DRV-14)
            DriverRules.checkCanActFor(user.personId(), driver, c.member(), c.canManage());
            Map<UUID, Event> ofTeam = new HashMap<>();
            matchDays(c).forEach(event -> ofTeam.put(event.id, event));
            Set<UUID> wanted = new LinkedHashSet<>(input.eventIds());
            for (UUID eventId : wanted) {
                if (!ofTeam.containsKey(eventId)) {
                    throw DomainError.invalidField("eventIds",
                            "Ein gewählter Spieltag gehört nicht zu diesem Team und dieser Saison.");
                }
                IgnoredMatchDays.checkIncluded(ofTeam.get(eventId));
            }
            Set<UUID> alreadyDriving = new HashSet<>();
            cars(wanted).stream().filter(car -> car.driver().equals(driver))
                    .forEach(car -> alreadyDriving.add(car.eventId()));
            for (UUID eventId : wanted) {
                if (!alreadyDriving.contains(eventId)) {
                    drivers.addCar(user, eventId, new CarInput(driver, input.seats()));
                }
            }
        });
        return get(user, teamId, seasonId);
    }

    /** Asks for a swap or carries it out directly (DRV-25). */
    public DriverPlanDto swap(CurrentUser user, UUID teamId, UUID seasonId, DriverPlanDto.SwapInput input) {
        QuarkusTransaction.requiringNew().run(() -> {
            Context c = writableContext(user, teamId, seasonId);
            swaps.start(user, c.team().id, c.season().id, memberIds(c), matchDayIds(c), input);
        });
        return get(user, teamId, seasonId);
    }

    /** Answers an open swap request: {@code accept}, {@code decline} or {@code withdraw} (DRV-25). */
    public DriverPlanDto answerSwap(CurrentUser user, UUID teamId, UUID seasonId, UUID swapId, String answer) {
        QuarkusTransaction.requiringNew().run(() -> {
            Context c = writableContext(user, teamId, seasonId);
            switch (answer) {
                case "accept" -> swaps.accept(user, c.team().id, c.season().id, swapId);
                case "decline" -> swaps.decline(user, c.team().id, c.season().id, swapId);
                default -> swaps.withdraw(user, c.team().id, c.season().id, swapId);
            }
        });
        return get(user, teamId, seasonId);
    }

    /** Adds another trip (DRV-28). */
    public DriverPlanDto addTrip(CurrentUser user, UUID teamId, UUID seasonId, DriverPlanDto.TripInput input) {
        QuarkusTransaction.requiringNew().run(() -> {
            Context c = writableContext(user, teamId, seasonId);
            UUID driver = input.driverId() == null ? user.personId() : input.driverId();
            if (!memberIds(c).contains(driver)) {
                throw DomainError.invalidField("driverId", "Diese Person ist nicht im Team.");
            }
            extraTrips.add(user, c.team().id, c.season().id, driver, input);
        });
        return get(user, teamId, seasonId);
    }

    /** Removes another trip (DRV-28). */
    public DriverPlanDto removeTrip(CurrentUser user, UUID teamId, UUID seasonId, UUID tripId) {
        QuarkusTransaction.requiringNew().run(() -> {
            Context c = writableContext(user, teamId, seasonId);
            extraTrips.remove(user, c.team().id, c.season().id, tripId);
        });
        return get(user, teamId, seasonId);
    }

    /** Context for a change: the season must not be archived. */
    private Context writableContext(CurrentUser user, UUID teamId, UUID seasonId) {
        Context c = context(user, teamId, seasonId);
        c.season().checkWritable();
        return c;
    }

    private static Set<UUID> memberIds(Context c) {
        Set<UUID> ids = new HashSet<>();
        c.members().forEach(m -> ids.add(m.person.id));
        return ids;
    }

    private Set<UUID> matchDayIds(Context c) {
        Set<UUID> ids = new HashSet<>();
        matchDays(c).forEach(event -> ids.add(event.id));
        return ids;
    }

    private Context context(CurrentUser user, UUID teamId, UUID seasonId) {
        Team team = Team.<Team>findByIdOptional(teamId)
                .orElseThrow(() -> DomainError.notFound("Dieses Team gibt es nicht."));
        Season season = Season.orCurrent(seasonId);
        if (!team.belongsTo(season)) {
            throw DomainError.notFound("Dieses Team gibt es in der Saison " + season.name + " nicht.");
        }
        List<TeamMembership> members = em.createQuery("""
                        select m from TeamMembership m join fetch m.person
                        where m.team.id = :team and m.season.id = :season""", TeamMembership.class)
                .setParameter("team", team.id).setParameter("season", season.id).getResultList();
        boolean member = members.stream().anyMatch(m -> m.person.id.equals(user.personId()));
        if (!user.allTeams() && !member) {
            throw DomainError.forbidden();
        }
        return new Context(team, season, members, member, user.allTeams(), user.personId(), user);
    }

    List<Event> matchDays(Context c) {
        return em.createQuery("""
                        select e from Event e join e.teams t
                        where t.id = :team and e.season.id = :season and e.type = :type
                        order by e.start, e.id""", Event.class)
                .setParameter("team", c.team().id).setParameter("season", c.season().id)
                .setParameter("type", EventType.MATCH_DAY).getResultList();
    }

    private List<Car> cars(Set<UUID> eventIds) {
        if (eventIds.isEmpty()) {
            return List.of();
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "select id, event_id, driver_id, seats from car where event_id in (:ids)")
                .setParameter("ids", eventIds).getResultList();
        return rows.stream().map(r -> new Car((UUID) r[0], (UUID) r[1], (UUID) r[2], ((Number) r[3]).intValue()))
                .toList();
    }

    private DriverPlanDto dto(Context c) {
        Instant now = clock.now();
        List<Event> events = matchDays(c);
        Set<UUID> ids = new LinkedHashSet<>();
        events.forEach(event -> ids.add(event.id));
        Map<UUID, PersonSummary> persons = new HashMap<>();
        c.members().forEach(m -> persons.put(m.person.id, PersonSummary.of(m.person)));
        java.util.function.Function<UUID, PersonSummary> person = id -> persons.computeIfAbsent(id,
                key -> PersonSummary.of(Person.findById(key)));

        Map<UUID, List<Car>> carsPerEvent = new HashMap<>();
        cars(ids).forEach(car -> carsPerEvent.computeIfAbsent(car.eventId(), k -> new ArrayList<>()).add(car));
        Map<UUID, List<PersonSummary>> passengersPerCar = new HashMap<>();
        if (!ids.isEmpty()) {
            @SuppressWarnings("unchecked")
            List<Object[]> seats = em.createNativeQuery(
                            "select car_id, person_id from car_passenger where event_id in (:ids)")
                    .setParameter("ids", ids).getResultList();
            for (Object[] row : seats) {
                passengersPerCar.computeIfAbsent((UUID) row[0], k -> new ArrayList<>())
                        .add(person.apply((UUID) row[1]));
            }
        }

        Integer ofTeam = c.team().in(c.season()).map(ts -> ts.expectedTravellers).orElse(null);
        int memberCount = (int) c.members().stream().map(m -> m.person.id).distinct().count();
        Map<UUID, Integer> attending = attending(ids);

        List<DriverPlanDto.MatchDay> matchDays = new ArrayList<>();
        List<DriverPlanRules.Trip> trips = new ArrayList<>();
        boolean writable = !c.season().isArchived();
        for (Event event : events) {
            List<DriverRules.Missing> missing = MatchDayRoutes.missing(event);
            Optional<Routes.Stored> route = matchDayRoutes.current(event);
            Integer distance = route.map(Routes.Stored::distanceMeters).orElse(null);
            boolean cancelled = event.status == EventStatus.CANCELLED;
            boolean over = !event.end.isAfter(now);
            boolean ignored = event.ignoredInDriverPlan;
            Double creditKm = DriverRules.kilometres(DriverRules.credit(distance, event.status));
            List<DriverListDto.CarDto> cars = new ArrayList<>();
            int freeSeats = 0;
            for (Car car : carsPerEvent.getOrDefault(event.id, List.of())) {
                List<PersonSummary> passengers = passengersPerCar.getOrDefault(car.id(), List.of()).stream()
                        .sorted(PersonSummary.ORDER).toList();
                int free = Math.max(0, car.seats() - passengers.size());
                freeSeats += free;
                cars.add(new DriverListDto.CarDto(car.id(), person.apply(car.driver()), car.seats(), free,
                        passengers, creditKm));
                boolean counts = IgnoredMatchDays.counts(cancelled, ignored);
                trips.add(new DriverPlanRules.Trip(car.driver(), !counts, over, distance));
            }
            cars.sort(Comparator.comparing(DriverListDto.CarDto::driver, PersonSummary.ORDER));
            boolean changeable = writable && missing.isEmpty() && !cancelled && !ignored && (!over || c.canManage())
                    && (c.member() || c.canManage());
            matchDays.add(new DriverPlanDto.MatchDay(event.id, event.title,
                    event.start.atZone(BackendClock.ZONE).toOffsetDateTime(),
                    event.location == null ? null : event.location.name,
                    event.location == null ? null : event.location.address, cancelled, over, missing.isEmpty(),
                    missing, DriverRules.kilometres(distance), exactKilometres(DriverRules.credit(distance, event.status)),
                    DriverRules.minutes(route.map(Routes.Stored::effectiveDurationSeconds).orElse(null)), changeable, cars,
                    freeSeats, DriverDistributionRules.expected(event.expectedTravellers, ofTeam, memberCount),
                    event.expectedTravellers != null,
                    DriverDistributionRules.places(cars.stream().map(DriverListDto.CarDto::seats).toList()),
                    attending.getOrDefault(event.id, 0), noTrip(event), ignored));
        }

        LocalDate today = extraTrips.today();
        List<DriverPlanDto.ExtraTrip> others = new ArrayList<>();
        for (ExtraTrips.Stored trip : extraTrips.of(c.team().id, c.season().id)) {
            boolean tripOver = trip.over(today);
            trips.add(new DriverPlanRules.Trip(trip.driverId(), false, tripOver, trip.distanceMeters()));
            others.add(new DriverPlanDto.ExtraTrip(trip.id(), trip.date(), trip.title(), person.apply(trip.driverId()),
                    DriverRules.kilometres(trip.distanceMeters()),
                    DriverRules.kilometres(DriverRules.credit(trip.distanceMeters(), EventStatus.PLANNED)), tripOver,
                    writable && ExtraTrips.canRemove(c.user(), trip)));
        }
        Map<UUID, DriverPlanRules.Share> totals = DriverPlanRules.shares(trips);
        List<DriverPlanDto.Share> shares = c.members().stream().map(m -> m.person.id).distinct()
                .map(id -> {
                    DriverPlanRules.Share share = totals.getOrDefault(id, DriverPlanRules.Share.NONE);
                    return new DriverPlanDto.Share(person.apply(id), share.trips(), share.plannedTrips(),
                            DriverRules.kilometres(share.creditMeters()),
                            DriverRules.kilometres(share.plannedCreditMeters()));
                })
                .sorted(Comparator.comparing(DriverPlanDto.Share::person, PersonSummary.ORDER)).toList();
        List<PersonSummary> members = c.canManage()
                ? c.members().stream().map(m -> person.apply(m.person.id)).distinct().sorted(PersonSummary.ORDER).toList()
                : null;
        Map<UUID, Person> entities = new HashMap<>();
        c.members().forEach(m -> entities.put(m.person.id, m.person));
        List<DriverPlanDto.Driver> drivers = entities.values().stream()
                .map(p -> new DriverPlanDto.Driver(PersonSummary.of(p), p.canDrive, p.carSeats,
                        c.canManage() || p.id.equals(c.caller())))
                .sorted(Comparator.comparing(DriverPlanDto.Driver::person, PersonSummary.ORDER)).toList();
        DriverPlanDto.Totals sum = new DriverPlanDto.Totals(
                shares.stream().mapToInt(DriverPlanDto.Share::trips).sum(),
                shares.stream().mapToInt(DriverPlanDto.Share::plannedTrips).sum(),
                Math.round(shares.stream().mapToDouble(DriverPlanDto.Share::kilometres).sum() * 10) / 10.0,
                Math.round(shares.stream().mapToDouble(DriverPlanDto.Share::plannedKilometres).sum() * 10) / 10.0);
        return new DriverPlanDto(c.team().id, c.team().nameIn(c.season()), c.season().id, c.season().name,
                c.member() && writable, c.canManage() && writable, matchDays, shares, members, drivers, sum,
                swaps.visible(c.user(), c.team().id, c.season().id), others,
                history.of(c.team().id, c.season().id, ids),
                DriverDistributionRules.expected(null, ofTeam, memberCount), Math.max(1, memberCount), ofTeam != null);
    }

    /** The meeting point is the venue: a home match day without a trip (DRV-30). */
    private static boolean noTrip(Event event) {
        return event.location != null && DriverDistributionRules.samePlace(event.meetingPointLocation,
                event.location.name, event.location.address);
    }

    /** People who responded «dabei», per match day. */
    private Map<UUID, Integer> attending(Set<UUID> eventIds) {
        Map<UUID, Integer> counts = new HashMap<>();
        if (eventIds.isEmpty()) {
            return counts;
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "select event_id, count(*) from response where event_id in (:ids) and status = 'ATTENDING' "
                                + "group by event_id")
                .setParameter("ids", eventIds).getResultList();
        rows.forEach(row -> counts.put((UUID) row[0], ((Number) row[1]).intValue()));
        return counts;
    }

    /** People who responded «nicht dabei», per match day. */
    private Map<UUID, Set<UUID>> declined(Set<UUID> eventIds) {
        Map<UUID, Set<UUID>> result = new HashMap<>();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "select event_id, person_id from response where event_id in (:ids) and status = 'DECLINED'")
                .setParameter("ids", eventIds).getResultList();
        rows.forEach(row -> result.computeIfAbsent((UUID) row[0], k -> new HashSet<>()).add((UUID) row[1]));
        return result;
    }

    // ------------------------------------------------------------------ Simple planning (DRV-29, DRV-30)

    /**
     * Upcoming match days that need drivers (DRV-30): not cancelled, not over, meeting point and venue set, not at
     * home, not left out (DRV-35). By start.
     */
    private Map<UUID, Event> fillable(Context c) {
        Instant now = clock.now();
        Map<UUID, Event> byId = new LinkedHashMap<>();
        matchDays(c).stream()
                .filter(e -> e.status != EventStatus.CANCELLED && e.end.isAfter(now)
                        && MatchDayRoutes.missing(e).isEmpty() && !noTrip(e) && !e.ignoredInDriverPlan)
                .forEach(e -> byId.put(e.id, e));
        return byId;
    }

    /**
     * Proposes a fair distribution of the trips that are still missing (DRV-30): every upcoming match day gets
     * drivers until its cars have a place for everyone who is expected. Drivers who are entered stay and count.
     * Changes nothing. Coaches and admins.
     */
    public DriverPlanDto.Distribution previewDistribution(CurrentUser user, UUID teamId, UUID seasonId) {
        QuarkusTransaction.requiringNew().run(() -> managing(user, teamId, seasonId));
        get(user, teamId, seasonId); // calculates missing routes first, so that the kilometres count
        return QuarkusTransaction.requiringNew().call(() -> {
            Context c = managing(user, teamId, seasonId);
            Map<UUID, Event> byId = fillable(c);
            if (byId.isEmpty()) {
                return new DriverPlanDto.Distribution(List.of());
            }
            Map<UUID, Person> persons = new HashMap<>();
            c.members().forEach(m -> persons.put(m.person.id, m.person));
            DriverPlanDto plan = dto(c);
            Map<UUID, Set<UUID>> declined = declined(byId.keySet());
            List<DriverDistributionRules.Day> toFill = new ArrayList<>();
            for (DriverPlanDto.MatchDay day : plan.matchDays()) {
                if (!byId.containsKey(day.eventId())) {
                    continue;
                }
                Set<UUID> unavailable = new HashSet<>(declined.getOrDefault(day.eventId(), Set.of()));
                day.cars().forEach(car -> unavailable.add(car.driver().id()));
                toFill.add(new DriverDistributionRules.Day(day.eventId(), day.expected(), day.places(),
                        day.distanceKm() == null ? null : DriverRules.meters(day.distanceKm()), unavailable));
            }
            List<DriverPlanDto.ProposedTrip> trips = new ArrayList<>();
            for (DriverDistributionRules.Assignment a : DriverDistributionRules.distribute(toFill, candidates(plan))) {
                Person driver = persons.get(a.driver());
                trips.add(new DriverPlanDto.ProposedTrip(a.event(), PersonSummary.of(driver),
                        DriverProposalRules.freeSeats(driver.carSeats)));
            }
            List<UUID> order = new ArrayList<>(byId.keySet());
            trips.sort(Comparator.<DriverPlanDto.ProposedTrip>comparingInt(t -> order.indexOf(t.eventId()))
                    .thenComparing(DriverPlanDto.ProposedTrip::driver, PersonSummary.ORDER));
            return new DriverPlanDto.Distribution(trips);
        });
    }

    /**
     * Enters the trips of a distribution as they were proposed (DRV-30). Drivers who are entered stay; the new
     * ones stay too, a later distribution only adds. If one trip cannot be entered (the match day can no longer
     * be planned, the person cannot drive or already drives), nothing is entered. Each new driver gets one
     * message. Coaches and admins.
     */
    public DriverPlanDto distribute(CurrentUser user, UUID teamId, UUID seasonId,
                                    List<DriverPlanDto.TripEntry> trips) {
        QuarkusTransaction.requiringNew().run(() -> {
            Context c = managing(user, teamId, seasonId);
            if (trips.isEmpty()) {
                return;
            }
            Map<UUID, Event> byId = fillable(c);
            if (!byId.isEmpty()) {
                em.createNativeQuery("select id from event where id in (:ids) for update")
                        .setParameter("ids", byId.keySet()).getResultList();
            }
            Map<UUID, Person> persons = new HashMap<>();
            c.members().forEach(m -> persons.put(m.person.id, m.person));
            Map<UUID, Set<UUID>> driving = new HashMap<>();
            if (!byId.isEmpty()) {
                cars(byId.keySet()).forEach(car -> driving.computeIfAbsent(car.eventId(), k -> new HashSet<>())
                        .add(car.driver()));
            }
            Map<UUID, List<Event>> added = new LinkedHashMap<>();
            for (DriverPlanDto.TripEntry trip : trips) {
                Event event = byId.get(trip.eventId());
                if (event == null) {
                    throw DomainError.conflict("Spieltag nicht offen", DriverRules.DISTRIBUTION_OUTDATED, "DRV-30");
                }
                Person driver = persons.get(trip.driverId());
                if (driver == null || !driver.canDrive) {
                    throw DomainError.conflict("Kann nicht fahren", DriverRules.DISTRIBUTION_OUTDATED, "DRV-30");
                }
                if (!driving.computeIfAbsent(event.id, k -> new HashSet<>()).add(driver.id)) {
                    throw DomainError.conflict("Fährt schon", DriverRules.DISTRIBUTION_OUTDATED, "DRV-30");
                }
                DriverRules.checkSeats(trip.seats(), 0);
                drivers.enterCar(event, user.personId(), driver, trip.seats());
                added.computeIfAbsent(driver.id, k -> new ArrayList<>()).add(event);
            }
            List<Message> messages = new ArrayList<>();
            added.forEach((person, days) -> {
                if (!person.equals(user.personId())) {
                    List<Event> sorted = days.stream().sorted(Comparator.comparing(e -> e.start)).toList();
                    messages.add(new Message(NotificationType.DRIVER_LIST_CHANGED, person, sorted.get(0).id,
                            DriverRules.distributedText(sorted.stream()
                                    .map(e -> NotificationRules.label("", e.start).strip()).toList())));
                }
            });
            notifications.send(messages);
        });
        return get(user, teamId, seasonId);
    }

    /**
     * Removes every driver of the upcoming match days that are not cancelled nor left out (DRV-33, DRV-35); their
     * passengers lose their seats. Every driver and passenger affected gets one message. Coaches and admins.
     */
    public DriverPlanDto removeAll(CurrentUser user, UUID teamId, UUID seasonId) {
        QuarkusTransaction.requiringNew().run(() -> {
            Context c = managing(user, teamId, seasonId);
            Instant now = clock.now();
            Map<UUID, Event> byId = new LinkedHashMap<>();
            matchDays(c).stream()
                    .filter(e -> e.status != EventStatus.CANCELLED && e.end.isAfter(now) && !e.ignoredInDriverPlan)
                    .forEach(e -> byId.put(e.id, e));
            if (byId.isEmpty()) {
                return;
            }
            em.createNativeQuery("select id from event where id in (:ids) for update")
                    .setParameter("ids", byId.keySet()).getResultList();
            Map<UUID, List<String>> lostTrips = new LinkedHashMap<>();
            Map<UUID, List<String>> lostSeats = new LinkedHashMap<>();
            Map<UUID, UUID> firstEvent = new HashMap<>();
            List<Car> all = cars(byId.keySet()).stream()
                    .sorted(Comparator.comparing(car -> byId.get(car.eventId()).start)).toList();
            for (Car car : all) {
                Event event = byId.get(car.eventId());
                String day = NotificationRules.label("", event.start).strip();
                @SuppressWarnings("unchecked")
                List<UUID> passengers = em.createNativeQuery("select person_id from car_passenger where car_id = ?")
                        .setParameter(1, car.id()).getResultList();
                for (UUID passenger : passengers) {
                    lostSeats.computeIfAbsent(passenger, k -> new ArrayList<>()).add(day);
                    firstEvent.putIfAbsent(passenger, event.id);
                }
                lostTrips.computeIfAbsent(car.driver(), k -> new ArrayList<>()).add(day);
                firstEvent.putIfAbsent(car.driver(), event.id);
                drivers.dropCar(event, user.personId(), car.id(), Person.findById(car.driver()));
            }
            List<Message> messages = new ArrayList<>();
            lostTrips.forEach((person, days) -> {
                if (!person.equals(user.personId())) {
                    messages.add(new Message(NotificationType.DRIVER_LIST_CHANGED, person, firstEvent.get(person),
                            DriverRules.allTripsRemovedText(days)));
                }
            });
            lostSeats.forEach((person, days) -> {
                if (!person.equals(user.personId()) && !lostTrips.containsKey(person)) {
                    messages.add(new Message(NotificationType.DRIVER_LIST_CHANGED, person, firstEvent.get(person),
                            DriverRules.allSeatsRemovedText(days)));
                }
            });
            notifications.send(messages);
        });
        return get(user, teamId, seasonId);
    }

    /** Everyone who can drive (DRV-18) with her trips in the season, by name. */
    private static List<DriverDistributionRules.Driver> candidates(DriverPlanDto plan) {
        Map<UUID, DriverPlanDto.Share> shares = new HashMap<>();
        plan.shares().forEach(share -> shares.put(share.person().id(), share));
        return plan.drivers().stream().filter(DriverPlanDto.Driver::canDrive)
                .map(d -> {
                    DriverPlanDto.Share share = shares.get(d.person().id());
                    int trips = share == null ? 0 : share.trips() + share.plannedTrips();
                    double km = share == null ? 0 : share.kilometres() + share.plannedKilometres();
                    return new DriverDistributionRules.Driver(d.person().id(), d.carSeats(), trips,
                            Math.round(km * 1000));
                }).toList();
    }

    /**
     * The next driver in line takes a trip of a match day (DRV-31). Without {@code replaces} one more car is
     * entered (coaches and admins). With {@code replaces} that driver hands her car over, with its seats and
     * passengers: the driver herself, coaches and admins. The next in line is the member who can drive with the
     * fewest trips in the season who neither drives on that match day nor responded «nicht dabei».
     */
    public DriverPlanDto next(CurrentUser user, UUID teamId, UUID seasonId, UUID eventId, UUID replaces) {
        QuarkusTransaction.requiringNew().run(() -> {
            Context c = writableContext(user, teamId, seasonId);
            if (!c.canManage() && (replaces == null || !replaces.equals(user.personId()))) {
                throw DomainError.forbidden();
            }
            Set<UUID> matchDays = matchDayIds(c);
            if (!matchDays.contains(eventId)) {
                throw DomainError.notFound("Diesen Spieltag gibt es in diesem Team nicht.");
            }
            em.createNativeQuery("select id from event where id = ? for update").setParameter(1, eventId).getResultList();
            IgnoredMatchDays.checkIncluded(Event.findById(eventId));
            DriverPlanDto plan = dto(c);
            DriverPlanDto.MatchDay day = plan.matchDays().stream().filter(m -> m.eventId().equals(eventId))
                    .findFirst().orElseThrow();
            Set<UUID> unavailable = new HashSet<>(declined(Set.of(eventId)).getOrDefault(eventId, Set.of()));
            day.cars().forEach(car -> unavailable.add(car.driver().id()));
            if (replaces != null && day.cars().stream().noneMatch(car -> car.driver().id().equals(replaces))) {
                throw DomainError.conflict("Fährt nicht", "Diese Person fährt an diesem Spieltag nicht.", "DRV-31");
            }
            UUID next = DriverDistributionRules.next(candidates(plan), unavailable)
                    .orElseThrow(() -> DomainError.conflict("Niemand frei",
                            "Es ist niemand mehr frei, der fahren kann.", "DRV-31"));
            if (replaces != null) {
                swaps.start(user, c.team().id, c.season().id, memberIds(c), matchDays,
                        new DriverPlanDto.SwapInput(eventId, replaces, next, null, true));
            } else {
                Person driver = c.members().stream().map(m -> m.person).filter(p -> p.id.equals(next)).findFirst()
                        .orElseThrow();
                drivers.addCar(user, eventId, new CarInput(next, DriverProposalRules.freeSeats(driver.carSeats)));
            }
        });
        return get(user, teamId, seasonId);
    }

    /** Context for planning: coaches and admins, season not archived. */
    Context managing(CurrentUser user, UUID teamId, UUID seasonId) {
        Context c = writableContext(user, teamId, seasonId);
        if (!c.canManage()) {
            throw DomainError.forbidden();
        }
        return c;
    }

    /** Kilometres to the meter, so the page can add proposed trips and round once, like the totals (DRV-30). */
    private static Double exactKilometres(Integer meters) {
        return meters == null ? null : meters / 1000.0;
    }
}
