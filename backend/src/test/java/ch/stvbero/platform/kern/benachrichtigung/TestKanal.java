package ch.stvbero.platform.kern.benachrichtigung;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Zweiter Kanal nur in den Tests (BEN-11): zeigt, dass ein weiterer Kanal (später E-Mail oder Push) jede
 * Nachricht erhält, ohne dass sich die Auslöser ändern.
 */
@ApplicationScoped
public class TestKanal implements Kanal {

    private final List<Nachricht> erhalten = new CopyOnWriteArrayList<>();

    /** Über eine Methode, nicht das Feld: Der Test erhält einen Client-Proxy. */
    List<Nachricht> erhalten() {
        return erhalten;
    }

    @Override
    public void senden(Nachricht nachricht, Instant zeitpunkt) {
        erhalten.add(nachricht);
    }
}
