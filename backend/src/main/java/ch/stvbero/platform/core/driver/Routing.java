package ch.stvbero.platform.core.driver;

import java.util.Optional;

/**
 * Calculates the route by car between two places (DRV-06): the real service ({@link LiveRouting}: Google Maps,
 * OpenRouteService or OpenStreetMap, DRV-15) or, while the app simulates, the {@link RoutingSimulator}. Inject
 * this interface; {@link RoutingSwitch} picks.
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

    /** Which service calculates (DRV-22). */
    RouteProvider provider();
}
