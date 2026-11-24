package ch.stvbero.platform.notification;

import ch.stvbero.platform.core.person.Person;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.UUID;

/**
 * E-mail (F-19, BEN-10): to persons with an e-mail address who did not switch e-mails off. Records the e-mail in
 * the transaction; {@link Deliveries} sends it after the commit.
 */
@ApplicationScoped
public class EmailChannel implements Channel {

    @Inject
    EmailTransport transport;

    @Inject
    Deliveries deliveries;

    @Inject
    NotificationSettingsService settings;

    @Override
    public void send(UUID notificationId, Message message, Instant instant) {
        if (!transport.available()) {
            return;
        }
        Person person = Person.findById(message.recipientId());
        if (person == null || !settings.emailOn(person)) {
            return;
        }
        Delivery.pending(notificationId, NotificationChannel.EMAIL, person.email.trim(),
                settings.subject(message), message.text(), DeliveryRules.path(message.eventId()), instant).persist();
        deliveries.afterCommit();
    }

    /** After the app; e-mail before push, so that the deliveries of a notification have a stable order. */
    @Override
    public int order() {
        return 10;
    }
}
