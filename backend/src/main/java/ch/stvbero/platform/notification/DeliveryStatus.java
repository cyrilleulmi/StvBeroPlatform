package ch.stvbero.platform.notification;

/** State of one e-mail or push (docs/states.md, delivery). */
public enum DeliveryStatus {
    /** Not delivered yet; the next attempt is due at {@code nextAttemptAt}. */
    PENDING,
    /** Handed over to the mail server or the push service. */
    SENT,
    /** Given up after the last attempt. */
    FAILED,
    /** Dropped without a further attempt: the device was removed or the push service reports it as expired. */
    DISCARDED
}
