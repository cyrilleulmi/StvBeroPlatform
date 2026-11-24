package ch.stvbero.platform.driver;

import ch.stvbero.platform.attendance.event.Event;
import ch.stvbero.platform.attendance.event.MatchDayTravel;
import ch.stvbero.platform.core.auth.CurrentUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Set;
import java.util.UUID;

/** The driver list and the timeline answer the events and responses how people travel to a match day. */
@ApplicationScoped
public class DriverTravel implements MatchDayTravel {

    @Inject
    DriverListService drivers;

    @Inject
    TimelineService timeline;

    @Override
    public void individualFromResponse(CurrentUser user, Event event, UUID personId, boolean attending, Boolean wanted) {
        drivers.individualFromResponse(user, event, personId, attending, wanted);
    }

    @Override
    public Set<UUID> individual(UUID eventId) {
        return drivers.individual(eventId);
    }

    @Override
    public void autoDeparture(UUID eventId) {
        timeline.autoDeparture(eventId);
    }
}
