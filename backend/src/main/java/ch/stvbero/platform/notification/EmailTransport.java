package ch.stvbero.platform.notification;

/**
 * Hands e-mails to the outside (F-19). In {@code prod} over SMTP ({@link SmtpEmailTransport}); in {@code dev} and
 * {@code test} the {@link DeliverySimulator} replaces it, so nothing leaves the app (criterion 8).
 */
public interface EmailTransport {

    /** Whether e-mails can be sent at all; without configuration the channel is off (criterion 9). */
    boolean available();

    /** Sends the e-mail; throws if that did not work, then it is tried again later. */
    void send(DeliveryRules.Email email);
}
