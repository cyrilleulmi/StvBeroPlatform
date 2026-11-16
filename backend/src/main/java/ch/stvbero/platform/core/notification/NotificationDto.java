package ch.stvbero.platform.core.notification;

import ch.stvbero.platform.core.person.PersonSummary;
import ch.stvbero.platform.core.clock.BackendClock;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Benachrichtigung für die Liste in der App. {@code terminId} führt zum Termin (BEN-12). */
@Schema(name = "Notification")
public record NotificationDto(UUID id, NotificationType type, PersonSummary recipient, String text, UUID eventId,
                                  OffsetDateTime createdAt, boolean read) {

    static NotificationDto of(Notification b) {
        return new NotificationDto(b.id, b.type, PersonSummary.of(b.recipient), b.text, b.eventId,
                OffsetDateTime.ofInstant(b.createdAt, BackendClock.ZONE), b.read());
    }
}
