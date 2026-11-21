package ch.stvbero.platform.core.driver;

import ch.stvbero.platform.core.auth.CurrentUser;
import ch.stvbero.platform.core.clock.BackendClock;
import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.event.Event;
import ch.stvbero.platform.core.event.EventStatus;
import ch.stvbero.platform.core.event.EventType;
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

    private record Context(Team team, Season season, List<TeamMembership> members, boolean member,
                           boolean canManage, UUID caller) {
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
            Set<UUID> ofTeam = new HashSet<>();
            matchDays(c).forEach(event -> ofTeam.add(event.id));
            Set<UUID> wanted = new LinkedHashSet<>(input.eventIds());
            for (UUID eventId : wanted) {
                if (!ofTeam.contains(eventId)) {
                    throw DomainError.invalidField("eventIds",
                            "Ein gewählter Spieltag gehört nicht zu diesem Team und dieser Saison.");
                }
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
        return new Context(team, season, members, member, user.allTeams(), user.personId());
    }

    private List<Event> matchDays(Context c) {
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

        List<DriverPlanDto.MatchDay> matchDays = new ArrayList<>();
        List<DriverPlanRules.Trip> trips = new ArrayList<>();
        boolean writable = !c.season().isArchived();
        for (Event event : events) {
            List<DriverRules.Missing> missing = MatchDayRoutes.missing(event);
            Optional<Routes.Stored> route = matchDayRoutes.current(event);
            Integer distance = route.map(Routes.Stored::distanceMeters).orElse(null);
            boolean cancelled = event.status == EventStatus.CANCELLED;
            boolean over = !event.end.isAfter(now);
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
                trips.add(new DriverPlanRules.Trip(car.driver(), cancelled, over, distance));
            }
            cars.sort(Comparator.comparing(DriverListDto.CarDto::driver, PersonSummary.ORDER));
            boolean changeable = writable && missing.isEmpty() && !cancelled && (!over || c.canManage())
                    && (c.member() || c.canManage());
            matchDays.add(new DriverPlanDto.MatchDay(event.id, event.title,
                    event.start.atZone(BackendClock.ZONE).toOffsetDateTime(),
                    event.location == null ? null : event.location.name,
                    event.location == null ? null : event.location.address, cancelled, over, missing.isEmpty(),
                    missing, DriverRules.kilometres(distance),
                    DriverRules.minutes(route.map(Routes.Stored::durationSeconds).orElse(null)), changeable, cars,
                    freeSeats));
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
        return new DriverPlanDto(c.team().id, c.team().nameIn(c.season()), c.season().id, c.season().name,
                c.member() && writable, c.canManage() && writable, matchDays, shares, members, drivers);
    }
}
