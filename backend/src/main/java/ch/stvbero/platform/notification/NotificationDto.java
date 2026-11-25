package ch.stvbero.platform.notification;

import ch.stvbero.platform.core.person.PersonSummary;
import ch.stvbero.platform.core.clock.BackendClock;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Notification for the list in the app. {@code eventId} leads to the event (BEN-12). */
@Schema(name = "Notification")
public record NotificationDto(UUID id, NotificationType type, PersonSummary recipient, String text, UUID eventId,
                                  OffsetDateTime createdAt, boolean read) {

    public static NotificationDto of(Notification n) {
        return new NotificationDto(n.id, n.type, PersonSummary.of(n.recipient), n.text, n.eventId,
                OffsetDateTime.ofInstant(n.createdAt, BackendClock.ZONE), n.read());
    }
}
