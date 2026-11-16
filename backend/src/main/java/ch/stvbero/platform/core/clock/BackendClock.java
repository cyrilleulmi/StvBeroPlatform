package ch.stvbero.platform.core.clock;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * The backend clock. "Now" always comes from here, never directly from {@code LocalDateTime.now()}.
 * <p>
 * In the prod profile it runs with the system time. In dev/test it can be fixed at a point in time
 * via {@code PUT /api/test/clock}. Because all components use the same injected {@link Clock}, that
 * point in time applies to the whole backend at once.
 */
@ApplicationScoped
public class BackendClock {

    /** Time zone of the domain logic. */
    public static final ZoneId ZONE = ZoneId.of("Europe/Zurich");

    private volatile Instant fixed;

    public Instant now() {
        Instant fixedAt = fixed;
        return fixedAt != null ? fixedAt : Instant.now();
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
