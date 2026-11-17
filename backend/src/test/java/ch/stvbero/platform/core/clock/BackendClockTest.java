package ch.stvbero.platform.core.clock;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendClockTest {

    @Test
    void fixedTimeAppliesToInjectedClock() {
        BackendClock backendClock = new BackendClock();
        Clock clock = new ClockProducer().clock(backendClock);
        Instant instant = Instant.parse("2026-11-16T05:00:00Z");

        backendClock.fixAt(instant);

        assertTrue(backendClock.isFixed());
        assertEquals(instant, clock.instant());
        assertEquals(ZoneId.of("Europe/Zurich"), clock.getZone());
        assertEquals("2026-11-16T06:00+01:00", backendClock.nowWithOffset().toString());
    }

    @Test
    void releasedClockRunsWithSystemTime() {
        BackendClock backendClock = new BackendClock();
        backendClock.fixAt(Instant.parse("2000-01-01T00:00:00Z"));

        backendClock.release();

        assertFalse(backendClock.isFixed());
        Instant before = Instant.now();
        Instant now = backendClock.now();
        assertTrue(!now.isBefore(before.minusSeconds(1)) && !now.isAfter(Instant.now().plusSeconds(1)));
    }

    @Test
    void otherZoneStaysControllable() {
        BackendClock backendClock = new BackendClock();
        Clock utc = new ClockProducer().clock(backendClock).withZone(ZoneId.of("UTC"));
        Instant instant = Instant.parse("2026-12-24T18:00:00Z");

        backendClock.fixAt(instant);

        assertEquals(instant, utc.instant());
        assertEquals(ZoneId.of("UTC"), utc.getZone());
    }
}
