package ch.stvbero.platform.notification;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Second channel used only in tests (BEN-11): shows that a further channel (later e-mail or push) receives
 * every message without the triggers changing.
 */
@ApplicationScoped
public class TestChannel implements Channel {

    private final List<Message> kept = new CopyOnWriteArrayList<>();

    /** Through a method, not the field: the test receives a client proxy. */
    List<Message> kept() {
        return kept;
    }

    @Override
    public void send(UUID notificationId, Message message, Instant instant) {
        kept.add(message);
    }
}
