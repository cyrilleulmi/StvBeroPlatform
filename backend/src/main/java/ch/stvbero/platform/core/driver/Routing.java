package ch.stvbero.platform.core.driver;

import java.util.Optional;

/**
 * Calculates the route by car between two places (DRV-06). In profile {@code prod} OpenRouteService
 * ({@link OpenRouteServiceRouting}), in {@code dev} and {@code test} the {@link RoutingSimulator}.
 */
public interface Routing {

    /** Distance and driving time of a route. */
    record Result(int distanceMeters, int durationSeconds) {
    }

    /**
     * The route by car from {@code from} to {@code to}.
     *
     * @return empty if there is no route for these places (place not found, no road)
     * @throws RoutingUnavailable if the service is not configured, does not answer or answers unexpectedly
     */
    Optional<Result> route(Place from, Place to);
}
