package ch.stvbero.platform.core.notification;

import java.time.Instant;
import java.util.UUID;

/**
 * A way in which notifications reach people (BEN-11). The list in the app ({@link AppChannel}, E-56) always
 * exists; e-mail ({@link EmailChannel}) and push ({@link PushChannel}) come on top (BEN-10, E-71).
 * {@link Notifications} passes every message to all channels, the triggers do not know them.
 */
public interface Channel {

    /**
     * Delivers the message. Runs in the transaction of the triggering change; if that is rolled back, the
     * message counts as not sent. Channels that talk to the outside only record the delivery here and send it
     * after the commit, so that a failure never blocks the change.
     *
     * @param notificationId ID of the notification in the app, shared by all channels
     */
    void send(UUID notificationId, Message message, Instant instant);

    /** Order in which the channels receive a message. The app comes first: the other channels refer to it. */
    default int order() {
        return 100;
    }
}
