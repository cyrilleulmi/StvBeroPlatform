package ch.stvbero.platform.core.driver;

import ch.stvbero.platform.core.auth.CurrentUser;
import ch.stvbero.platform.core.clock.BackendClock;
import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.event.CallUp;
import ch.stvbero.platform.core.event.Event;
import ch.stvbero.platform.core.event.EventRules;
import ch.stvbero.platform.core.event.EventStatus;
import ch.stvbero.platform.core.event.NotificationRules;
import ch.stvbero.platform.core.notification.Message;
import ch.stvbero.platform.core.notification.NotificationType;
import ch.stvbero.platform.core.notification.Notifications;
import ch.stvbero.platform.core.person.Person;
import ch.stvbero.platform.core.person.PersonSummary;
import ch.stvbero.platform.core.season.Season;
import ch.stvbero.platform.core.team.TeamMembership;
import ch.stvbero.platform.core.team.TeamRole;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

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
 * Driver list of a match day (DRV): cars with driver and seats, passengers, who has no seat, the route with the
 * credits and the summary per season. Members of the teams enter themselves; coaches (of any team, E-69) and admins
 * also others (RR-03). Enforced here in the backend (RR-07).
 * <p>
 * Every change locks the match day ({@code select … for update}), so that two concurrent requests never put more
 * passengers into a car than it has seats, or a person into two cars.
 */
@ApplicationScoped
public class DriverListService {

    @Inject
    EntityManager em;

    @Inject
    BackendClock clock;

    @Inject
    Routes routes;

    @Inject
    Notifications notifications;

    /** Match day with its members and the caller's position. */
    private record Context(Event event, List<TeamMembership> members, boolean member, boolean canManage,
                           List<DriverRules.Missing> missing) {

        boolean isMember(UUID person) {
            return members.stream().anyMatch(m -> m.person.id.equals(person));
        }
    }

    private record Car(UUID id, UUID driver, int seats) {
    }

    // ------------------------------------------------------------------ Read

    /**
     * The driver list. Calculates the route first if needed (DRV-05, DRV-08), outside of a transaction; a
     * cancelled match day keeps its route but is not calculated again (DRV-10).
     */
    public DriverListDto get(CurrentUser user, UUID eventId) {
        Optional<Place[]> places = QuarkusTransaction.requiringNew().call(() -> {
            Context c = context(user, eventId);
            return c.missing().isEmpty() && c.event().status != EventStatus.CANCELLED
                    ? Optional.of(places(c.event())) : Optional.<Place[]>empty();
        });
        places.ifPresent(p -> routes.ensure(eventId, p[0], p[1]));
        return QuarkusTransaction.requiringNew().call(() -> dto(user, context(user, eventId)));
    }

    /** Trips and credited kilometres per person in a season (DRV-09). */
    public DriverSummaryDto summary(CurrentUser user, UUID seasonId) {
        Season season = QuarkusTransaction.requiringNew().call(() -> seasonId == null
                ? Season.currentOrFail()
                : Season.<Season>findByIdOptional(seasonId)
                        .orElseThrow(() -> DomainError.notFound("Diese Saison gibt es nicht.")));
        // Routes of match days with cars that were never calculated or whose places changed (DRV-08)
        Map<UUID, Place[]> toEnsure = QuarkusTransaction.requiringNew().call(() -> {
            Map<UUID, Place[]> map = new LinkedHashMap<>();
            for (Event event : eventsWithCars(season.id)) {
                if (event.status != EventStatus.CANCELLED && missing(event).isEmpty()) {
                    map.put(event.id, places(event));
                }
            }
            return map;
        });
        toEnsure.forEach((id, p) -> routes.ensure(id, p[0], p[1]));
        return QuarkusTransaction.requiringNew().call(() -> summaryDto(user, season));
    }

    // ------------------------------------------------------------------ Cars

    /** Enter a car (DRV-02). A driver who sat in another car leaves that seat. */
    @Transactional
    public void addCar(CurrentUser user, UUID eventId, CarInput input) {
        Context c = writable(user, eventId);
        UUID driver = input.driverId() == null ? user.personId() : input.driverId();
        DriverRules.checkCanActFor(user.personId(), driver, c.member(), c.canManage());
        Person person = person(driver, "driverId");
        DriverRules.checkMember(c.isMember(driver), "driverId");
        DriverRules.checkNotDriving(carOf(eventId, driver).isPresent(), name(person));
        DriverRules.checkSeats(input.seats(), 0);
        List<Message> messages = new ArrayList<>();
        String label = label(c.event());
        Optional<Car> seat = seatOf(eventId, driver);
        if (seat.isPresent()) {
            deletePassenger(seat.get().id(), driver);
            notify(messages, user, seat.get().driver(), DriverRules.passengerLeftText(name(person), label), eventId);
        }
        em.createNativeQuery("insert into car (id, event_id, driver_id, seats, created_at) values (?, ?, ?, ?, ?)")
                .setParameter(1, UUID.randomUUID()).setParameter(2, eventId).setParameter(3, driver)
                .setParameter(4, input.seats()).setParameter(5, clock.now())
                .executeUpdate();
        notify(messages, user, driver, DriverRules.carAddedText(label, input.seats()), eventId);
        notifications.send(messages);
    }

    /** Change the number of seats; never fewer than passengers (DRV-02, DRV-03). */
    @Transactional
    public void changeSeats(CurrentUser user, UUID eventId, UUID carId, SeatsInput input) {
        Context c = writable(user, eventId);
        Car car = car(eventId, carId);
        DriverRules.checkCanActFor(user.personId(), car.driver(), c.member(), c.canManage());
        DriverRules.checkSeats(input.seats(), passengers(carId).size());
        if (input.seats() == car.seats()) {
            return;
        }
        em.createNativeQuery("update car set seats = ? where id = ?")
                .setParameter(1, input.seats()).setParameter(2, carId).executeUpdate();
        List<Message> messages = new ArrayList<>();
        notify(messages, user, car.driver(), DriverRules.seatsChangedText(label(c.event()), input.seats()), eventId);
        notifications.send(messages);
    }

    /** Remove a car; its passengers lose their seat and are notified (DRV-02, DRV-11). */
    @Transactional
    public void removeCar(CurrentUser user, UUID eventId, UUID carId) {
        Context c = writable(user, eventId);
        Car car = car(eventId, carId);
        DriverRules.checkCanActFor(user.personId(), car.driver(), c.member(), c.canManage());
        List<UUID> passengers = passengers(carId);
        em.createNativeQuery("delete from car where id = ?").setParameter(1, carId).executeUpdate();
        String label = label(c.event());
        String driver = name(Person.findById(car.driver()));
        List<Message> messages = new ArrayList<>();
        passengers.forEach(p -> notify(messages, user, p, DriverRules.carRemovedText(driver, label), eventId));
        notify(messages, user, car.driver(), DriverRules.ownCarRemovedText(label), eventId);
        notifications.send(messages);
    }

    // ------------------------------------------------------------------ Passengers

    /**
     * Put a person into a car (DRV-03): for oneself every member, for others coaches and admins. Someone who sits
     * in another car moves. Already in this car: nothing changes.
     */
    @Transactional
    public void addPassenger(CurrentUser user, UUID eventId, UUID carId, UUID personId) {
        Context c = writable(user, eventId);
        Car car = car(eventId, carId);
        DriverRules.checkCanActFor(user.personId(), personId, c.member(), c.canManage());
        Person person = person(personId, "personId");
        DriverRules.checkMember(c.isMember(personId), "personId");
        DriverRules.checkNotDriver(carOf(eventId, personId).isPresent());
        Optional<Car> previous = seatOf(eventId, personId);
        if (previous.isPresent() && previous.get().id().equals(carId)) {
            return;
        }
        DriverRules.checkFreeSeat(car.seats(), passengers(carId).size());
        String label = label(c.event());
        String passenger = name(person);
        List<Message> messages = new ArrayList<>();
        if (previous.isPresent()) {
            deletePassenger(previous.get().id(), personId);
            notify(messages, user, previous.get().driver(), DriverRules.passengerLeftText(passenger, label), eventId);
        }
        em.createNativeQuery("insert into car_passenger (car_id, event_id, person_id, added_at) values (?, ?, ?, ?)")
                .setParameter(1, carId).setParameter(2, eventId).setParameter(3, personId).setParameter(4, clock.now())
                .executeUpdate();
        notify(messages, user, car.driver(), DriverRules.passengerJoinedText(passenger, label), eventId);
        notify(messages, user, personId,
                DriverRules.seatAssignedText(name(Person.findById(car.driver())), label), eventId);
        notifications.send(messages);
    }

    /** Take a person out of a car (DRV-03). */
    @Transactional
    public void removePassenger(CurrentUser user, UUID eventId, UUID carId, UUID personId) {
        Context c = writable(user, eventId);
        Car car = car(eventId, carId);
        DriverRules.checkCanActFor(user.personId(), personId, c.member(), c.canManage());
        if (!passengers(carId).contains(personId)) {
            throw DomainError.notFound(DriverRules.NOT_IN_CAR);
        }
        deletePassenger(carId, personId);
        String label = label(c.event());
        List<Message> messages = new ArrayList<>();
        notify(messages, user, car.driver(),
                DriverRules.passengerLeftText(name(Person.findById(personId)), label), eventId);
        notify(messages, user, personId,
                DriverRules.seatRemovedText(name(Person.findById(car.driver())), label), eventId);
        notifications.send(messages);
    }

    // ------------------------------------------------------------------ Route

    /** Kilometres by hand, coaches and admins (DRV-07). Applies to the current meeting point and venue. */
    @Transactional
    public void setDistance(CurrentUser user, UUID eventId, RouteInput input) {
        Context c = writable(user, eventId);
        if (!c.canManage()) {
            throw DomainError.forbidden();
        }
        int meters = DriverRules.meters(input.distanceKm());
        Place[] places = places(c.event());
        routes.manual(eventId, places[0], places[1], meters);
    }

    /** Ask the routing service again, coaches and admins (DRV-07). Replaces kilometres entered by hand. */
    public void recalculate(CurrentUser user, UUID eventId) {
        Place[] places = QuarkusTransaction.requiringNew().call(() -> {
            Context c = context(user, eventId);
            if (!c.canManage()) {
                throw DomainError.forbidden();
            }
            DriverRules.checkChangeable(c.missing(), c.event().status, c.event().end, clock.now(), true);
            c.event().season.checkWritable();
            return places(c.event());
        });
        routes.calculate(eventId, places[0], places[1]);
    }

    // ------------------------------------------------------------------ Helpers

    private Context context(CurrentUser user, UUID eventId) {
        Event event = Event.<Event>findByIdOptional(eventId)
                .orElseThrow(() -> DomainError.notFound("Diesen Termin gibt es nicht."));
        List<TeamMembership> members = members(event);
        Set<TeamRole> mine = new HashSet<>();
        members.stream().filter(m -> m.person.id.equals(user.personId())).forEach(m -> mine.add(m.teamRole));
        EventRules.checkCanView(user.allTeams(), mine);
        DriverRules.checkMatchDay(event.type);
        return new Context(event, members, !mine.isEmpty(), EventRules.canEdit(user.allTeams(), mine),
                missing(event));
    }

    /** Context for a change: locks the match day and checks that the list can be changed. */
    private Context writable(CurrentUser user, UUID eventId) {
        Context c = context(user, eventId);
        em.createNativeQuery("select id from event where id = ? for update").setParameter(1, eventId).getResultList();
        DriverRules.checkChangeable(c.missing(), c.event().status, c.event().end, clock.now(), c.canManage());
        c.event().season.checkWritable();
        return c;
    }

    private static List<DriverRules.Missing> missing(Event event) {
        return DriverRules.missing(event.meetingPointLocation != null && !event.meetingPointLocation.isBlank(),
                event.location != null && event.location.name != null);
    }

    /** Meeting point and venue as places of the route (DRV-12). */
    private static Place[] places(Event event) {
        return new Place[] {Places.meetingPoint(event.meetingPointLocation), Places.venue(event.location.name,
                event.location.address, event.location.mapsUrl, event.location.latitude, event.location.longitude)};
    }

    /** Members (any team role) of the participating teams in the season of the match day, with person. */
    private List<TeamMembership> members(Event event) {
        List<UUID> teams = event.teams.stream().map(team -> team.id).toList();
        if (teams.isEmpty()) {
            return List.of();
        }
        return em.createQuery("""
                        select z from TeamMembership z join fetch z.person
                        where z.season.id = :season and z.team.id in :teams""", TeamMembership.class)
                .setParameter("season", event.season.id).setParameter("teams", teams).getResultList();
    }

    private List<Car> cars(UUID eventId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("select id, driver_id, seats from car where event_id = ?")
                .setParameter(1, eventId).getResultList();
        return rows.stream().map(r -> new Car((UUID) r[0], (UUID) r[1], ((Number) r[2]).intValue())).toList();
    }

    private Car car(UUID eventId, UUID carId) {
        return cars(eventId).stream().filter(car -> car.id().equals(carId)).findFirst()
                .orElseThrow(() -> DomainError.notFound(DriverRules.CAR_NOT_FOUND));
    }

    private Optional<Car> carOf(UUID eventId, UUID driver) {
        return cars(eventId).stream().filter(car -> car.driver().equals(driver)).findFirst();
    }

    /** The car in which the person sits as a passenger. */
    private Optional<Car> seatOf(UUID eventId, UUID personId) {
        @SuppressWarnings("unchecked")
        List<UUID> rows = em.createNativeQuery("select car_id from car_passenger where event_id = ? and person_id = ?")
                .setParameter(1, eventId).setParameter(2, personId).getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return cars(eventId).stream().filter(car -> car.id().equals(rows.get(0))).findFirst();
    }

    private List<UUID> passengers(UUID carId) {
        @SuppressWarnings("unchecked")
        List<UUID> rows = em.createNativeQuery("select person_id from car_passenger where car_id = ?")
                .setParameter(1, carId).getResultList();
        return rows;
    }

    private void deletePassenger(UUID carId, UUID personId) {
        em.createNativeQuery("delete from car_passenger where car_id = ? and person_id = ?")
                .setParameter(1, carId).setParameter(2, personId).executeUpdate();
    }

    private static Person person(UUID id, String field) {
        return Person.<Person>findByIdOptional(id)
                .orElseThrow(() -> DomainError.notFound("Diese Person gibt es nicht."));
    }

    private static String name(Person person) {
        return person.firstName + " " + person.lastName;
    }

    /** «Spieltag So 22.11.» */
    private static String label(Event event) {
        return NotificationRules.label(event.title, event.start);
    }

    /** A notification to a person affected by the change, never to the one who made it (DRV-11). */
    private static void notify(List<Message> messages, CurrentUser user, UUID recipient, String text, UUID eventId) {
        if (!recipient.equals(user.personId())) {
            messages.add(new Message(NotificationType.DRIVER_LIST_CHANGED, recipient, eventId, text));
        }
    }

    private DriverListDto dto(CurrentUser user, Context c) {
        Event event = c.event();
        Map<UUID, PersonSummary> persons = new HashMap<>();
        c.members().forEach(m -> persons.put(m.person.id, PersonSummary.of(m.person)));
        java.util.function.Function<UUID, PersonSummary> person = id -> persons.computeIfAbsent(id,
                key -> PersonSummary.of(Person.findById(key)));

        boolean cancelled = event.status == EventStatus.CANCELLED;
        Integer distance = null;
        DriverListDto.RouteDto route = null;
        if (c.missing().isEmpty()) {
            Place[] places = places(event);
            Optional<Routes.Stored> stored = routes.stored(event.id)
                    .filter(s -> s.places().equals(Routes.places(places[0], places[1])));
            if (stored.isPresent()) {
                Routes.Stored s = stored.get();
                distance = s.distanceMeters();
                route = new DriverListDto.RouteDto(s.status(), places[0].label(), places[1].label(),
                        DriverRules.kilometres(s.distanceMeters()), DriverRules.minutes(s.durationSeconds()),
                        DriverRules.kilometres(DriverRules.credit(s.distanceMeters(), event.status)));
            }
        }
        Double creditKm = DriverRules.kilometres(DriverRules.credit(distance, event.status));

        Map<UUID, List<PersonSummary>> passengersPerCar = new HashMap<>();
        Set<UUID> seated = new HashSet<>();
        @SuppressWarnings("unchecked")
        List<Object[]> seats = em.createNativeQuery("select car_id, person_id from car_passenger where event_id = ?")
                .setParameter(1, event.id).getResultList();
        for (Object[] row : seats) {
            passengersPerCar.computeIfAbsent((UUID) row[0], k -> new ArrayList<>()).add(person.apply((UUID) row[1]));
            seated.add((UUID) row[1]);
        }
        List<DriverListDto.CarDto> cars = new ArrayList<>();
        for (Car car : cars(event.id)) {
            seated.add(car.driver());
            List<PersonSummary> passengers = passengersPerCar.getOrDefault(car.id(), List.of()).stream()
                    .sorted(PersonSummary.ORDER).toList();
            cars.add(new DriverListDto.CarDto(car.id(), person.apply(car.driver()), car.seats(),
                    Math.max(0, car.seats() - passengers.size()), passengers, creditKm));
        }
        cars.sort(Comparator.comparing(DriverListDto.CarDto::driver, PersonSummary.ORDER));

        CallUp callUp = CallUp.findById(event.id);
        boolean published = callUp != null && callUp.publishedAt != null;
        Set<UUID> players = new LinkedHashSet<>();
        c.members().stream().filter(m -> m.teamRole == TeamRole.PLAYER).forEach(m -> players.add(m.person.id));
        @SuppressWarnings("unchecked")
        List<UUID> attending = em.createNativeQuery(
                        "select person_id from response where event_id = ? and status = 'ATTENDING'")
                .setParameter(1, event.id).getResultList();
        List<UUID> attendingPlayers = attending.stream().filter(players::contains).toList();
        List<PersonSummary> withoutSeat = DriverRules.withoutSeat(published,
                        published ? callUp.personIds : Set.of(), attendingPlayers, seated)
                .stream().map(person).sorted(PersonSummary.ORDER).toList();

        boolean changeable = c.missing().isEmpty() && !cancelled && !event.season.isArchived()
                && (event.end.isAfter(clock.now()) || c.canManage());
        List<PersonSummary> members = c.canManage()
                ? c.members().stream().map(m -> person.apply(m.person.id)).distinct().sorted(PersonSummary.ORDER).toList()
                : null;
        return new DriverListDto(event.id, c.missing().isEmpty(), c.missing(), route, cancelled,
                changeable && c.member(), changeable && c.canManage(), published, cars, withoutSeat, members);
    }

    /** Match days of the season with at least one car. */
    private List<Event> eventsWithCars(UUID seasonId) {
        @SuppressWarnings("unchecked")
        List<UUID> ids = em.createNativeQuery("""
                        select distinct e.id from event e join car c on c.event_id = e.id where e.season_id = ?""")
                .setParameter(1, seasonId).getResultList();
        return ids.isEmpty() ? List.of() : Event.list("id in ?1", ids);
    }

    private DriverSummaryDto summaryDto(CurrentUser user, Season season) {
        Map<UUID, Event> events = new HashMap<>();
        eventsWithCars(season.id).forEach(e -> events.put(e.id, e));
        List<DriverRules.Trip> trips = new ArrayList<>();
        for (Event event : events.values()) {
            Integer distance = null;
            if (missing(event).isEmpty()) {
                Place[] places = places(event);
                distance = routes.stored(event.id)
                        .filter(s -> s.places().equals(Routes.places(places[0], places[1])))
                        .map(Routes.Stored::distanceMeters).orElse(null);
            }
            for (Car car : cars(event.id)) {
                trips.add(new DriverRules.Trip(event.id, car.driver(), event.status, distance));
            }
        }
        Map<UUID, DriverRules.Total> totals = DriverRules.totals(trips);
        boolean everyone = user.allTeams();
        Set<UUID> shown = new LinkedHashSet<>(everyone ? totals.keySet() : Set.of(user.personId()));
        List<DriverSummaryDto.Row> rows = shown.stream()
                .map(id -> {
                    DriverRules.Total total = totals.getOrDefault(id, new DriverRules.Total(0, 0, 0));
                    return new DriverSummaryDto.Row(PersonSummary.of(Person.findById(id)), total.trips(),
                            DriverRules.kilometres(total.creditMeters()), total.tripsWithoutDistance());
                })
                .sorted(Comparator.comparing(DriverSummaryDto.Row::person, PersonSummary.ORDER))
                .toList();
        return new DriverSummaryDto(season.id, season.name, everyone, rows);
    }
}
