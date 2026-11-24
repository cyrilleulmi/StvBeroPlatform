package ch.stvbero.platform.notification;

/**
 * Hands pushes to the push service of the browser (F-19, Web Push). In {@code prod} {@link WebPushTransport}; in
 * {@code dev} and {@code test} the {@link DeliverySimulator} replaces it, so nothing leaves the app (criterion 8).
 */
public interface PushTransport {

    /** Whether push can be sent at all; without VAPID keys the channel is off (criterion 9). */
    boolean available();

    /** Public VAPID key (base64url) the browser needs to register; {@code null} if not available. */
    String publicKey();

    /** Device a push goes to. */
    record Target(String endpoint, String p256dh, String auth) {
    }

    enum Result {
        SENT,
        /** The push service reports the subscription as gone; it is removed (criterion 7). */
        EXPIRED
    }

    /** Sends the push; throws if that did not work, then it is tried again later. */
    Result send(Target target, String payload);
}
