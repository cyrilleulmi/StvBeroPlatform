package ch.stvbero.platform.kern.uhr;

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
public class Uhr {

    /** Zeitzone der Fachlogik. */
    public static final ZoneId ZONE = ZoneId.of("Europe/Zurich");

    private volatile Instant fest;

    public Instant jetzt() {
        Instant f = fest;
        return f != null ? f : Instant.now();
    }

    public OffsetDateTime jetztMitOffset() {
        return OffsetDateTime.ofInstant(jetzt(), ZONE);
    }

    public boolean istFest() {
        return fest != null;
    }

    public void festsetzen(Instant zeitpunkt) {
        this.fest = zeitpunkt;
    }

    public void loesen() {
        this.fest = null;
    }
}
