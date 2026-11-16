package ch.stvbero.platform.core.notification;

import ch.stvbero.platform.core.person.Person;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.UUID;

/** The list in the app ({@code /benachrichtigungen}). Always present (E-56). */
@ApplicationScoped
public class AppChannel implements Channel {

    @Override
    public void send(Message message, Instant instant) {
        Notification notification = new Notification();
        notification.id = UUID.randomUUID();
        notification.type = message.type();
        notification.recipient = Person.getEntityManager().getReference(Person.class, message.recipientId());
        notification.text = message.text();
        notification.eventId = message.eventId();
        notification.createdAt = instant;
        notification.persist();
    }
}
