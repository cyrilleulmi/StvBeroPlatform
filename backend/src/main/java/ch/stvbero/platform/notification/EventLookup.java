package ch.stvbero.platform.notification;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Extension point: what the notifications need to know about the event a message leads to, for the subject of
 * e-mails and pushes. The notifications know no events, so the module that owns them implements this.
 */
public interface EventLookup {

    Optional<EventRef> find(UUID eventId);

    record EventRef(String title, Instant start) {
    }
}
