package ch.stvbero.platform.core.driver;

import ch.stvbero.platform.core.clock.BackendClock;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.jboss.logging.Logger;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The stored route of a match day (DRV-05, DRV-07, DRV-08). The routing service is called outside of any
 * transaction, so that a slow service never holds a database lock; the result is stored in a short transaction
 * of its own. Two concurrent calculations both store, the last one wins.
 */
@ApplicationScoped
public class Routes {

    private static final Logger LOG = Logger.getLogger(Routes.class);

    @Inject
    EntityManager em;

    @Inject
    BackendClock clock;

    @Inject
    Routing routing;

    /** Route as stored. {@code places} identifies meeting point and venue it was calculated for. */
    public record Stored(String places, RouteStatus status, Integer distanceMeters, Integer durationSeconds,
                         Instant attemptedAt) {
    }

    /** Identifies the pair of places (DRV-08). */
    public static String places(Place from, Place to) {
        return from.key() + "\n" + to.key();
    }

    public Optional<Stored> stored(UUID eventId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        select places, status, distance_meters, duration_seconds, attempted_at
                        from route where event_id = ?""")
                .setParameter(1, eventId).getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Object[] row = rows.get(0);
        return Optional.of(new Stored((String) row[0], RouteStatus.valueOf((String) row[1]), (Integer) row[2],
                (Integer) row[3], instant(row[4])));
    }

    /**
     * The route for these places: the stored one, or calculated now if it never was, the places changed or the
     * service was unavailable a while ago (DRV-05, DRV-07, DRV-08). Not inside a transaction.
     */
    public Stored ensure(UUID eventId, Place from, Place to) {
        String places = places(from, to);
        Optional<Stored> stored = QuarkusTransaction.requiringNew().call(() -> stored(eventId));
        if (stored.isPresent() && !DriverRules.needsCalculation(stored.get().places(), stored.get().status(),
                stored.get().attemptedAt(), places, clock.now())) {
            return stored.get();
        }
        return calculate(eventId, from, to);
    }

    /** Asks the routing service and stores the result, also «not found» and «unavailable» (DRV-07). */
    public Stored calculate(UUID eventId, Place from, Place to) {
        Stored result;
        Instant now = clock.now();
        try {
            Optional<Routing.Result> route = routing.route(from, to);
            result = route.map(r -> new Stored(places(from, to), RouteStatus.CALCULATED, r.distanceMeters(),
                            r.durationSeconds(), now))
                    .orElseGet(() -> new Stored(places(from, to), RouteStatus.NOT_FOUND, null, null, now));
        } catch (RoutingUnavailable e) {
            LOG.warnf("Route for match day %s not calculated: %s", eventId, e.getMessage());
            result = new Stored(places(from, to), RouteStatus.UNAVAILABLE, null, null, now);
        }
        Stored toStore = result;
        QuarkusTransaction.requiringNew().run(() -> store(eventId, toStore));
        return result;
    }

    /** Stores kilometres entered by hand for these places (DRV-07). In the caller's transaction. */
    public void manual(UUID eventId, Place from, Place to, int distanceMeters) {
        store(eventId, new Stored(places(from, to), RouteStatus.MANUAL, distanceMeters, null, clock.now()));
    }

    private void store(UUID eventId, Stored route) {
        em.createNativeQuery("""
                        insert into route (event_id, places, status, distance_meters, duration_seconds, attempted_at)
                        select ?, ?, ?, cast(? as integer), cast(? as integer), ? where exists (select 1 from event where id = ?)
                        on conflict (event_id) do update
                        set places = excluded.places, status = excluded.status, distance_meters = excluded.distance_meters,
                            duration_seconds = excluded.duration_seconds, attempted_at = excluded.attempted_at""")
                .setParameter(1, eventId).setParameter(2, route.places()).setParameter(3, route.status().name())
                .setParameter(4, route.distanceMeters()).setParameter(5, route.durationSeconds())
                .setParameter(6, route.attemptedAt()).setParameter(7, eventId)
                .executeUpdate();
    }

    static Instant instant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        return ((Timestamp) value).toInstant();
    }
}
