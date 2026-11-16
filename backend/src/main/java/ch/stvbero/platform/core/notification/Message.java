package ch.stvbero.platform.core.notification;

import java.util.Objects;
import java.util.UUID;

/**
 * A notification to one person, independent of the channel (BEN-11). Every {@link Channel} receives the same
 * message.
 *
 * @param eventId event the notification leads to (BEN-12), or {@code null}
 */
public record Message(NotificationType type, UUID recipientId, UUID eventId, String text) {

    public Message {
        Objects.requireNonNull(type);
        Objects.requireNonNull(recipientId);
        Objects.requireNonNull(text);
    }
}
