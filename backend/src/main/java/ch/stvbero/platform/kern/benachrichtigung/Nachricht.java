package ch.stvbero.platform.kern.benachrichtigung;

import java.util.Objects;
import java.util.UUID;

/**
 * Eine Benachrichtigung an eine Person, unabhängig vom Kanal (BEN-11). Jeder {@link Kanal} erhält dieselbe
 * Nachricht.
 *
 * @param terminId Termin, zu dem die Benachrichtigung führt (BEN-12), oder {@code null}
 */
public record Nachricht(BenachrichtigungArt art, UUID empfaengerId, UUID terminId, String text) {

    public Nachricht {
        Objects.requireNonNull(art);
        Objects.requireNonNull(empfaengerId);
        Objects.requireNonNull(text);
    }
}
