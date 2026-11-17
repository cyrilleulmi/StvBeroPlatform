package ch.stvbero.platform.core.notification;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Push (F-19, BEN-10): one push per device on which the person allowed it (criterion 10). Records the pushes in
 * the transaction; {@link Deliveries} sends them after the commit.
 */
@ApplicationScoped
public class PushChannel implements Channel {

    @Inject
    PushTransport transport;

    @Inject
    Deliveries deliveries;

    @Inject
    NotificationSettingsService settings;

    @Override
    public void send(UUID notificationId, Message message, Instant instant) {
        if (!transport.available()) {
            return;
        }
        List<PushSubscription> devices = PushSubscription.list("person.id = ?1 order by createdAt, sequenceNumber",
                message.recipientId());
        if (devices.isEmpty()) {
            return;
        }
        String subject = settings.subject(message);
        String path = DeliveryRules.path(message.eventId());
        for (PushSubscription device : devices) {
            Delivery delivery = Delivery.pending(notificationId, NotificationChannel.PUSH, device.endpoint, subject,
                    message.text(), path, instant);
            delivery.pushSubscription = device;
            delivery.persist();
        }
        deliveries.afterCommit();
    }
}
