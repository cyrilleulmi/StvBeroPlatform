package ch.stvbero.platform.core.notification;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Zweiter Kanal nur in den Tests (BEN-11): zeigt, dass ein weiterer Kanal (später E-Mail oder Push) jede
 * Nachricht erhält, ohne dass sich die Auslöser ändern.
 */
@ApplicationScoped
public class TestChannel implements Channel {

    private final List<Message> kept = new CopyOnWriteArrayList<>();

    /** Über eine Methode, nicht das Feld: Der Test erhält einen Client-Proxy. */
    List<Message> kept() {
        return kept;
    }

    @Override
    public void send(Message message, Instant instant) {
        kept.add(message);
    }
}
