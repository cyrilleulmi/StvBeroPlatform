package ch.stvbero.platform.core.driver;

/** The routing service is not configured, not reachable or answered unexpectedly (DRV-07). */
public class RoutingUnavailable extends RuntimeException {

    public RoutingUnavailable(String message) {
        super(message);
    }

    public RoutingUnavailable(String message, Throwable cause) {
        super(message, cause);
    }
}
