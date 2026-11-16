package ch.stvbero.platform.core.notification;

import java.time.Instant;

/**
 * Ein Weg, auf dem Benachrichtigungen ankommen (BEN-11). Heute gibt es die Liste in der App
 * ({@link AppKanal}, E-56). E-Mail oder Push (BEN-10, offen) kommen als weitere Implementierung dazu,
 * ohne dass sich die Auslöser ändern: {@link Benachrichtigungen} gibt jede Nachricht an alle Kanäle.
 */
public interface Channel {

    /**
     * Nachricht zustellen. Läuft in der Transaktion der auslösenden Änderung; wird sie zurückgerollt, gilt
     * die Nachricht als nicht versandt.
     */
    void send(Message message, Instant instant);
}
