package ch.stvbero.platform.core.notification;

import java.util.Objects;
import java.util.UUID;

/**
 * Eine Benachrichtigung an eine Person, unabhängig vom Kanal (BEN-11). Jeder {@link Kanal} erhält dieselbe
 * Nachricht.
 *
 * @param terminId Termin, zu dem die Benachrichtigung führt (BEN-12), oder {@code null}
 */
public record Message(NotificationType type, UUID recipientId, UUID eventId, String text) {

    public Message {
        Objects.requireNonNull(type);
        Objects.requireNonNull(recipientId);
        Objects.requireNonNull(text);
    }
}
