package ch.stvbero.platform.core.driver;

import ch.stvbero.platform.core.auth.CurrentUser;
import ch.stvbero.platform.core.clock.BackendClock;
import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.person.Person;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Other trips (DRV-28): trips for something that is not an event in the app, credited like a match day (the
 * return trip). Members add their own, coaches and admins also for others; the driver, whoever added it, coaches
 * and admins remove it.
 */
@ApplicationScoped
public class ExtraTrips {

    public static final String DISTANCE_TEXT = "Bitte eine Zahl von 0.1 bis 2000 eingeben.";

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    @Inject
    EntityManager em;

    @Inject
    BackendClock clock;

    @Inject
    DriverPlanHistory history;

    /** A stored trip. */
    public record Stored(UUID id, UUID teamId, UUID seasonId, UUID driverId, LocalDate date, String title,
                         int distanceMeters, UUID createdBy) {

        /** The date has passed: the trip counts as driven, otherwise as planned. */
        public boolean over(LocalDate today) {
            return date.isBefore(today);
        }
    }

    /** Adds a trip. In the caller's transaction; team membership of the driver is checked by the caller. */
    public void add(CurrentUser user, UUID teamId, UUID seasonId, UUID driverId, DriverPlanDto.TripInput input) {
        if (!driverId.equals(user.personId()) && !user.allTeams()) {
            throw DomainError.forbidden();
        }
        String title = input.title().trim();
        int meters = DriverRules.meters(input.distanceKm());
        em.createNativeQuery("""
                        insert into extra_trip (id, team_id, season_id, driver_id, trip_date, title, distance_meters,
                                                created_by, created_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                .setParameter(1, UUID.randomUUID()).setParameter(2, teamId).setParameter(3, seasonId)
                .setParameter(4, driverId).setParameter(5, Date.valueOf(input.date())).setParameter(6, title)
                .setParameter(7, meters).setParameter(8, user.personId()).setParameter(9, clock.now())
                .executeUpdate();
        history.ofTeam(teamId, seasonId, DriverPlanHistory.Kind.TRIP_ADDED, user.personId(),
                DriverRules.historyTripAdded(name(driverId), title, DAY.format(input.date()),
                        DriverRules.kilometres(meters)));
    }

    /** Removes a trip of the team. In the caller's transaction. */
    public void remove(CurrentUser user, UUID teamId, UUID seasonId, UUID tripId) {
        Stored trip = of(teamId, seasonId).stream().filter(t -> t.id().equals(tripId)).findFirst()
                .orElseThrow(() -> DomainError.notFound("Diese Fahrt gibt es nicht."));
        if (!canRemove(user, trip)) {
            throw DomainError.forbidden();
        }
        em.createNativeQuery("delete from extra_trip where id = ?").setParameter(1, tripId).executeUpdate();
        history.ofTeam(teamId, seasonId, DriverPlanHistory.Kind.TRIP_REMOVED, user.personId(),
                DriverRules.historyTripRemoved(name(trip.driverId()), trip.title(), DAY.format(trip.date())));
    }

    public static boolean canRemove(CurrentUser user, Stored trip) {
        return user.allTeams() || trip.driverId().equals(user.personId())
                || user.personId().equals(trip.createdBy());
    }

    /** Trips of a team in a season, by date. */
    public List<Stored> of(UUID teamId, UUID seasonId) {
        return query("team_id = ? and season_id = ?", teamId, seasonId);
    }

    /** Trips of all teams in a season (for «Fahrten», DRV-09). */
    public List<Stored> ofSeason(UUID seasonId) {
        return query("season_id = ?", seasonId, null);
    }

    private List<Stored> query(String where, UUID first, UUID second) {
        var query = em.createNativeQuery("""
                select id, team_id, season_id, driver_id, trip_date, title, distance_meters, created_by
                from extra_trip where %s order by trip_date, title, id""".formatted(where))
                .setParameter(1, first);
        if (second != null) {
            query.setParameter(2, second);
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return rows.stream().map(r -> new Stored((UUID) r[0], (UUID) r[1], (UUID) r[2], (UUID) r[3],
                date(r[4]), (String) r[5], ((Number) r[6]).intValue(), (UUID) r[7])).toList();
    }

    private static LocalDate date(Object value) {
        return value instanceof LocalDate local ? local : ((Date) value).toLocalDate();
    }

    /** Today in the time zone of the club. */
    public LocalDate today() {
        return clock.now().atZone(BackendClock.ZONE).toLocalDate();
    }

    private static String name(UUID personId) {
        Person person = Person.findById(personId);
        return person == null ? "" : person.firstName + " " + person.lastName;
    }
}
