package ch.stvbero.platform.kern.uhr;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import java.time.Clock;

/** Stellt die injizierbare {@link Clock} bereit (Zeitzone Europe/Zurich). */
@ApplicationScoped
public class UhrProduzent {

    @Produces
    @Singleton
    Clock clock(Uhr uhr) {
        return new SteuerbareClock(uhr, Uhr.ZONE);
    }
}
