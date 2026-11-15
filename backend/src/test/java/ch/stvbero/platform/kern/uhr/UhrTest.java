package ch.stvbero.platform.kern.uhr;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UhrTest {

    @Test
    void festgesetzteZeitGiltFuerDieInjizierteClock() {
        Uhr uhr = new Uhr();
        Clock clock = new UhrProduzent().clock(uhr);
        Instant zeitpunkt = Instant.parse("2026-11-16T05:00:00Z");

        uhr.festsetzen(zeitpunkt);

        assertTrue(uhr.istFest());
        assertEquals(zeitpunkt, clock.instant());
        assertEquals(ZoneId.of("Europe/Zurich"), clock.getZone());
        assertEquals("2026-11-16T06:00+01:00", uhr.jetztMitOffset().toString());
    }

    @Test
    void geloesteUhrLaeuftMitDerSystemzeit() {
        Uhr uhr = new Uhr();
        uhr.festsetzen(Instant.parse("2000-01-01T00:00:00Z"));

        uhr.loesen();

        assertFalse(uhr.istFest());
        Instant vorher = Instant.now();
        Instant jetzt = uhr.jetzt();
        assertTrue(!jetzt.isBefore(vorher.minusSeconds(1)) && !jetzt.isAfter(Instant.now().plusSeconds(1)));
    }

    @Test
    void andereZoneBehaeltDieSteuerung() {
        Uhr uhr = new Uhr();
        Clock utc = new UhrProduzent().clock(uhr).withZone(ZoneId.of("UTC"));
        Instant zeitpunkt = Instant.parse("2026-12-24T18:00:00Z");

        uhr.festsetzen(zeitpunkt);

        assertEquals(zeitpunkt, utc.instant());
        assertEquals(ZoneId.of("UTC"), utc.getZone());
    }
}
