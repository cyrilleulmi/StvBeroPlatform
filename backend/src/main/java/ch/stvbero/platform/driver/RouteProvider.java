package ch.stvbero.platform.driver;

/**
 * The service that calculated a route (DRV-22). None of them takes day and time of departure into account: the
 * driving time is the one without traffic.
 */
public enum RouteProvider {
    GOOGLE_MAPS, OPEN_ROUTE_SERVICE, OPEN_STREET_MAP,
    /** The simulator of the profiles {@code dev} and {@code test} (DRV-06). */
    SIMULATOR
}
