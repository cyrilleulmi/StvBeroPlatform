package ch.stvbero.platform.testsupport;

import ch.stvbero.platform.core.clock.BackendClock;
import ch.stvbero.platform.notification.Delivery;
import ch.stvbero.platform.notification.DeliveryStatus;
import ch.stvbero.platform.notification.Notification;
import ch.stvbero.platform.notification.NotificationChannel;
import ch.stvbero.platform.notification.NotificationType;
import ch.stvbero.platform.core.person.PersonSummary;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A notification with its e-mails and pushes, for the test support (F-19, criterion 8). The fields of
 * {@code Notification} plus {@code deliveries}.
 */
@Schema(name = "TestNotification")
public record TestNotification(UUID id, NotificationType type, PersonSummary recipient, String text, UUID eventId,
                               OffsetDateTime createdAt, boolean read, List<TestDelivery> deliveries) {

    /**
     * One e-mail or push as it went out (or would have gone out).
     *
     * @param address e-mail address or push endpoint
     * @param path    where the button or the tap leads in the app
     */
    @Schema(name = "TestDelivery")
    public record TestDelivery(NotificationChannel channel, String address, String subject, String text, String path,
                               DeliveryStatus status, int attempts, OffsetDateTime nextAttemptAt, String lastError,
                               OffsetDateTime sentAt) {

        static TestDelivery of(Delivery d) {
            return new TestDelivery(d.channel, d.address, d.subject, d.text, d.path, d.status, d.attempts,
                    time(d.nextAttemptAt), d.lastError, time(d.sentAt));
        }
    }

    static TestNotification of(Notification n, List<Delivery> deliveries) {
        return new TestNotification(n.id, n.type, PersonSummary.of(n.recipient), n.text, n.eventId, time(n.createdAt),
                n.read(), deliveries.stream().map(TestDelivery::of).toList());
    }

    private static OffsetDateTime time(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, BackendClock.ZONE);
    }
}
