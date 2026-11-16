package ch.stvbero.platform.core.clock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/** A {@link Clock} that asks the {@link BackendClock} for the time on every call. */
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
