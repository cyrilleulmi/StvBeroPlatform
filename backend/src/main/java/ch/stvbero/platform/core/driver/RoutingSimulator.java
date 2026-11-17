package ch.stvbero.platform.core.driver;

import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simulates the routing service in the profiles {@code dev} and {@code test} (DRV-06, docs/test-data.md,
 * simulated routing). Fixed values per pair of places, matched by the label of the places (meeting point text,
 * venue name), ignoring case. After the reset it knows the routes of match days 04 and 05; every other pair has
 * no route. The test support endpoint {@code PUT /api/test/routing} adds pairs or simulates an outage.
 */
@ApplicationScoped
@IfBuildProperty(name = "stv.test-support", stringValue = "true")
public class RoutingSimulator implements Routing {

    /** Meeting point and venue of match day 04 (docs/test-data.md). */
    public static final String SEED_FROM_04 = "Parkplatz Sporthalle Nord";
    public static final String SEED_TO_04 = "Sporthalle Seefeld";
    public static final Result SEED_ROUTE_04 = new Result(23_400, 26 * 60);
    /** Meeting point and venue of match day 05. */
    public static final String SEED_FROM_05 = "Sporthalle Nord";
    public static final String SEED_TO_05 = "Turnhalle Weid";
    public static final Result SEED_ROUTE_05 = new Result(12_600, 16 * 60);

    private final Map<String, Result> routes = new ConcurrentHashMap<>();
    private final AtomicInteger calls = new AtomicInteger();
    private volatile boolean unavailable;

    public RoutingSimulator() {
        reset();
    }

    /** State after the reset: the routes of match days 04 and 05, available, no calls counted. */
    public final void reset() {
        routes.clear();
        routes.put(key(SEED_FROM_04, SEED_TO_04), SEED_ROUTE_04);
        routes.put(key(SEED_FROM_05, SEED_TO_05), SEED_ROUTE_05);
        unavailable = false;
        calls.set(0);
    }

    /** Sets the route for a pair of places; {@code null} removes it (then there is no route). */
    public void set(String from, String to, Result result) {
        if (result == null) {
            routes.remove(key(from, to));
        } else {
            routes.put(key(from, to), result);
        }
    }

    public void unavailable(boolean unavailable) {
        this.unavailable = unavailable;
    }

    /** Number of calculations since the reset; shows that a route is calculated only once (DRV-05). */
    public int calls() {
        return calls.get();
    }

    @Override
    public Optional<Result> route(Place from, Place to) {
        calls.incrementAndGet();
        if (unavailable) {
            throw new RoutingUnavailable("Routing simulator: outage");
        }
        return Optional.ofNullable(routes.get(key(from.label(), to.label())));
    }

    private static String key(String from, String to) {
        return from.strip().toLowerCase(Locale.ROOT) + "\n" + to.strip().toLowerCase(Locale.ROOT);
    }
}
