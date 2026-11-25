package ch.stvbero.platform.attendance.event;

import ch.stvbero.platform.core.auth.CurrentUser;
import ch.stvbero.platform.core.clock.BackendClock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.UUID;

/**
 * Pin an event to the start page of the members of its teams, and take the pin away again (TER-26 to TER-28, E-114).
 * Whoever may edit the event may pin it: coaches, admins, people with a special permission for it (RR-09). A pin
 * belongs to one event, not to its series. Pinning notifies nobody.
 */
@ApplicationScoped
public class EventPins {

    @Inject
    BackendClock clock;

    @Inject
    EventService events;

    @Transactional
    public EventDto pin(CurrentUser user, UUID eventId) {
        Event event = changeable(user, eventId);
        if (!event.isPinned()) {
            event.pinnedAt = clock.now();
            event.pinnedBy = user.personId();
        }
        return events.get(user, eventId);
    }

    @Transactional
    public EventDto unpin(CurrentUser user, UUID eventId) {
        Event event = changeable(user, eventId);
        event.pinnedAt = null;
        event.pinnedBy = null;
        return events.get(user, eventId);
    }

    /** 404 unknown, 403 without the right to edit (TER-27), 409 cancelled, over or in an archived season. */
    private Event changeable(CurrentUser user, UUID eventId) {
        Event event = EventService.find(eventId);
        EventRules.checkCanCreate(user.allTeams(), EventService.teamRoles(user, event.teams, event.season));
        EventRules.checkPinnable(event.status, event.end, clock.now());
        event.season.checkWritable();
        return event;
    }
}
