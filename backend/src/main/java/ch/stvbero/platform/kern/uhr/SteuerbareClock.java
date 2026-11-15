package ch.stvbero.platform.kern.uhr;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/** {@link Clock}, die ihre Zeit bei jedem Aufruf von der {@link Uhr} holt. */
final class SteuerbareClock extends Clock {

    private final Uhr uhr;
    private final ZoneId zone;

    SteuerbareClock(Uhr uhr, ZoneId zone) {
        this.uhr = uhr;
        this.zone = zone;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId andereZone) {
        return new SteuerbareClock(uhr, andereZone);
    }

    @Override
    public Instant instant() {
        return uhr.jetzt();
    }
}
