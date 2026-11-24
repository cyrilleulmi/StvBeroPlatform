package ch.stvbero.platform.driver;

/** State of the route of a match day (DRV-05, DRV-07). */
public enum RouteStatus {
    /** Calculated by the routing service. */
    CALCULATED,
    /** The routing service found no route for these places. */
    NOT_FOUND,
    /** The routing service was not available at the last attempt. */
    UNAVAILABLE,
    /** Kilometres entered by hand by a coach or admin. */
    MANUAL
}
