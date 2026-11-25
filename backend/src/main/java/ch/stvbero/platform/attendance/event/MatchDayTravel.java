package ch.stvbero.platform.attendance.event;

import ch.stvbero.platform.core.auth.CurrentUser;

import java.util.Set;
import java.util.UUID;

/**
 * Extension point: how people get to a match day. Events and responses know no driver list, so the module that
 * plans the trips implements this. Without one nobody travels on her own and no departure is suggested.
 */
public interface MatchDayTravel {

    /** No module plans the trips. */
    MatchDayTravel NONE = new MatchDayTravel() {
        @Override
        public void individualFromResponse(CurrentUser user, Event event, UUID personId, boolean attending,
                                           Boolean wanted) {
            // nothing to mark
        }

        @Override
        public Set<UUID> individual(UUID eventId) {
            return Set.of();
        }

        @Override
        public void autoDeparture(UUID eventId) {
            // no suggestion
        }
    };

    /**
     * The mark that comes with a response (ANM-13): «dabei» with «Ich reise individuell an» sets it, any other
     * response removes it. {@code wanted == null} leaves a «dabei» as it is.
     */
    void individualFromResponse(CurrentUser user, Event event, UUID personId, boolean attending, Boolean wanted);

    /** People of the match day who travel on their own (ANM-13). */
    Set<UUID> individual(UUID eventId);

    /**
     * Automatic departure (TML-09): while nobody has entered a meeting time by hand, the meeting time of a match day
     * is the suggested departure. Runs outside of a transaction and never fails.
     */
    void autoDeparture(UUID eventId);
}
