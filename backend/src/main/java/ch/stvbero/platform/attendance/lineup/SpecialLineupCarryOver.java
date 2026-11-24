package ch.stvbero.platform.attendance.lineup;

import ch.stvbero.platform.core.clock.BackendClock;
import ch.stvbero.platform.core.season.Season;
import ch.stvbero.platform.core.season.SeasonCarryOver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/** Special line-ups move into the new season together with the lines (LIN-11). */
@ApplicationScoped
public class SpecialLineupCarryOver implements SeasonCarryOver {

    @Inject
    EntityManager em;

    @Inject
    BackendClock clock;

    @Override
    public void carryOver(Season source, Season target) {
        SpecialLineupService.carryOver(em, source, target, clock.now());
    }
}
