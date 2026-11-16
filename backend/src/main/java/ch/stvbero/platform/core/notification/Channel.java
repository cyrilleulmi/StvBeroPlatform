package ch.stvbero.platform.core.notification;

import java.time.Instant;

/**
 * A way in which notifications reach people (BEN-11). Today there is the list in the app
 * ({@link AppChannel}, E-56). Email or push (BEN-10, open) are added as further implementations
 * without changing the triggers: {@link Notifications} passes every message to all channels.
 */
public interface Channel {

    /**
     * Delivers the message. Runs in the transaction of the triggering change; if that is rolled back, the
     * message counts as not sent.
     */
    void send(Message message, Instant instant);
}
