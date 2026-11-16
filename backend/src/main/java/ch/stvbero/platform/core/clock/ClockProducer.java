package ch.stvbero.platform.core.clock;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import java.time.Clock;

/** Stellt die injizierbare {@link Clock} bereit (Zeitzone Europe/Zurich). */
@ApplicationScoped
public class ClockProducer {

    @Produces
    @Singleton
    Clock clock(BackendClock clock) {
        return new ControllableClock(clock, BackendClock.ZONE);
    }
}
