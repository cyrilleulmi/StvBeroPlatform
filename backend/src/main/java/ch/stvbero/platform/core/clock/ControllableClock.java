package ch.stvbero.platform.core.clock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/** {@link Clock}, die ihre Zeit bei jedem Aufruf von der {@link Uhr} holt. */
final class ControllableClock extends Clock {

    private final BackendClock clock;
    private final ZoneId zone;

    ControllableClock(BackendClock clock, ZoneId zone) {
        this.clock = clock;
        this.zone = zone;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId otherZone) {
        return new ControllableClock(clock, otherZone);
    }

    @Override
    public Instant instant() {
        return clock.now();
    }
}
