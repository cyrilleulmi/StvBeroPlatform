package ch.stvbero.platform.core.clock;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Die Backend-Uhr. «Jetzt» kommt immer von hier, nie direkt aus {@code LocalDateTime.now()}.
 * <p>
 * Im Profil prod läuft sie mit der Systemzeit. In dev/test lässt sie sich über
 * {@code PUT /api/test/uhr} auf einen festen Zeitpunkt setzen. Weil alle Komponenten dieselbe
 * injizierte {@link Clock} verwenden, gilt der Zeitpunkt sofort im ganzen Backend.
 */
@ApplicationScoped
public class BackendClock {

    /** Zeitzone der Fachlogik. */
    public static final ZoneId ZONE = ZoneId.of("Europe/Zurich");

    private volatile Instant fixed;

    public Instant now() {
        Instant f = fixed;
        return f != null ? f : Instant.now();
    }

    public OffsetDateTime nowWithOffset() {
        return OffsetDateTime.ofInstant(now(), ZONE);
    }

    public boolean isFixed() {
        return fixed != null;
    }

    public void fixAt(Instant instant) {
        this.fixed = instant;
    }

    public void release() {
        this.fixed = null;
    }
}
