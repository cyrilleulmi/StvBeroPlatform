package ch.stvbero.platform.driver;

import ch.stvbero.platform.attendance.event.Event;
import ch.stvbero.platform.core.error.DomainError;

/**
 * Match days left out of the driver list of the season (DRV-35): they keep their cars, but count no trips, and
 * nobody is distributed to them. Without a database, so that unit tests can check them.
 */
public final class IgnoredMatchDays {

    static final String TEXT = "Dieser Spieltag ist in der Fahrerliste ausgelassen.";

    private IgnoredMatchDays() {
    }

    /** Refuses to enter drivers on a match day that is left out (409). */
    public static void checkIncluded(Event event) {
        if (event.ignoredInDriverPlan) {
            throw DomainError.conflict("Spieltag ausgelassen", TEXT, "DRV-35");
        }
    }

    /** A trip of the match day counts in the season plan: not cancelled (DRV-10) and not left out. */
    public static boolean counts(boolean cancelled, boolean ignored) {
        return !cancelled && !ignored;
    }
}
