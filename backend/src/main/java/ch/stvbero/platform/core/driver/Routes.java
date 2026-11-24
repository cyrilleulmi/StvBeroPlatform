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

    /**
     * Route as stored. {@code places} identifies meeting point and venue it was calculated for. A driving time
     * overwritten by hand ({@code manualDurationSeconds}, DRV-21) wins over the calculated one.
     */
    public record Stored(String places, RouteStatus status, Integer distanceMeters, Integer durationSeconds,
                         Instant attemptedAt, RouteProvider provider, Integer manualDurationSeconds,
                         UUID manualDurationBy, Instant manualDurationAt) {

        Stored(String places, RouteStatus status, Integer distanceMeters, Integer durationSeconds, Instant attemptedAt,
               RouteProvider provider) {
            this(places, status, distanceMeters, durationSeconds, attemptedAt, provider, null, null, null);
        }

        /** The driving time in use: overwritten by hand, otherwise calculated (DRV-21). */
        public Integer effectiveDurationSeconds() {
            return manualDurationSeconds != null ? manualDurationSeconds : durationSeconds;
        }
    }

    /** Identifies the pair of places (DRV-08). */
    public static String places(Place from, Place to) {
        return from.key() + "\n" + to.key();
    }

    public Optional<Stored> stored(UUID eventId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        select places, status, distance_meters, duration_seconds, attempted_at, provider,
                               manual_duration_seconds, manual_duration_by, manual_duration_at
                        from route where event_id = ?""")
                .setParameter(1, eventId).getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Object[] row = rows.get(0);
        return Optional.of(new Stored((String) row[0], RouteStatus.valueOf((String) row[1]), (Integer) row[2],
                (Integer) row[3], instant(row[4]), row[5] == null ? null : RouteProvider.valueOf((String) row[5]),
                (Integer) row[6], (UUID) row[7], row[8] == null ? null : instant(row[8])));
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
        RouteProvider provider = routing.provider();
        try {
            Optional<Routing.Result> route = routing.route(from, to);
            result = route.map(r -> new Stored(places(from, to), RouteStatus.CALCULATED, r.distanceMeters(),
                            r.durationSeconds(), now, provider))
                    .orElseGet(() -> new Stored(places(from, to), RouteStatus.NOT_FOUND, null, null, now, provider));
        } catch (RoutingUnavailable e) {
            LOG.warnf("Route for match day %s not calculated: %s", eventId, e.getMessage());
            result = new Stored(places(from, to), RouteStatus.UNAVAILABLE, null, null, now, provider);
        }
        Stored toStore = result;
        QuarkusTransaction.requiringNew().run(() -> store(eventId, toStore));
        return result;
    }

    /** Stores kilometres entered by hand for these places (DRV-07). In the caller's transaction. */
    public void manual(UUID eventId, Place from, Place to, int distanceMeters) {
        store(eventId, new Stored(places(from, to), RouteStatus.MANUAL, distanceMeters, null, clock.now(), null));
    }

    /**
     * Overwrites the driving time by hand, or with {@code null} hands it back to the calculation (DRV-21), for the
     * places of the stored route. Without a stored route of these places, one marked «unavailable» is stored with
     * it. In the caller's transaction.
     */
    public void manualDuration(UUID eventId, Place from, Place to, Integer seconds, UUID by) {
        String places = places(from, to);
        Instant now = clock.now();
        int changed = em.createNativeQuery("""
                        update route set manual_duration_seconds = cast(? as integer), manual_duration_by = ?,
                               manual_duration_at = ?
                        where event_id = ? and places = ?""")
                .setParameter(1, seconds).setParameter(2, seconds == null ? null : by)
                .setParameter(3, seconds == null ? null : now).setParameter(4, eventId).setParameter(5, places)
                .executeUpdate();
        if (changed == 0 && seconds != null) {
            em.createNativeQuery("""
                            insert into route (event_id, places, status, attempted_at, manual_duration_seconds,
                                               manual_duration_by, manual_duration_at)
                            values (?, ?, 'UNAVAILABLE', ?, ?, ?, ?)
                            on conflict (event_id) do update
                            set places = excluded.places, status = excluded.status, distance_meters = null,
                                duration_seconds = null, provider = null, attempted_at = excluded.attempted_at,
                                manual_duration_seconds = excluded.manual_duration_seconds,
                                manual_duration_by = excluded.manual_duration_by,
                                manual_duration_at = excluded.manual_duration_at""")
                    .setParameter(1, eventId).setParameter(2, places).setParameter(3, now)
                    .setParameter(4, seconds).setParameter(5, by).setParameter(6, now)
                    .executeUpdate();
        }
    }

    private void store(UUID eventId, Stored route) {
        em.createNativeQuery("""
                        insert into route (event_id, places, status, distance_meters, duration_seconds, attempted_at,
                                           provider)
                        select ?, ?, ?, cast(? as integer), cast(? as integer), ?, ?
                        where exists (select 1 from event where id = ?)
                        on conflict (event_id) do update
                        set places = excluded.places, status = excluded.status, distance_meters = excluded.distance_meters,
                            duration_seconds = excluded.duration_seconds, attempted_at = excluded.attempted_at,
                            provider = excluded.provider,
                            manual_duration_seconds = case when route.places = excluded.places
                                then route.manual_duration_seconds end,
                            manual_duration_by = case when route.places = excluded.places
                                then route.manual_duration_by end,
                            manual_duration_at = case when route.places = excluded.places
                                then route.manual_duration_at end""")
                .setParameter(1, eventId).setParameter(2, route.places()).setParameter(3, route.status().name())
                .setParameter(4, route.distanceMeters()).setParameter(5, route.durationSeconds())
                .setParameter(6, route.attemptedAt())
                .setParameter(7, route.provider() == null ? null : route.provider().name())
                .setParameter(8, eventId)
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
