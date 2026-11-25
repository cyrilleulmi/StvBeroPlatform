package ch.stvbero.platform.attendance.event;

import ch.stvbero.platform.notification.EventLookup;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

/** Tells the notifications the title and start of an event, for the subject of e-mails and pushes (BEN-10). */
@ApplicationScoped
public class NotifiedEvents implements EventLookup {

    @Override
    public Optional<EventRef> find(UUID eventId) {
        Event event = Event.findById(eventId);
        return Optional.ofNullable(event).map(e -> new EventRef(e.title, e.start));
    }
}
