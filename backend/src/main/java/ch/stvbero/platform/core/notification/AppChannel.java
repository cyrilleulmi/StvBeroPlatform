package ch.stvbero.platform.core.notification;

import ch.stvbero.platform.core.person.Person;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.UUID;

/** Die Liste in der App (`/benachrichtigungen`). Gibt es immer (E-56). */
@ApplicationScoped
public class AppChannel implements Channel {

    @Override
    public void send(Message n, Instant instant) {
        Notification b = new Notification();
        b.id = UUID.randomUUID();
        b.type = n.type();
        b.recipient = Person.getEntityManager().getReference(Person.class, n.recipientId());
        b.text = n.text();
        b.eventId = n.eventId();
        b.createdAt = instant;
        b.persist();
    }
}
