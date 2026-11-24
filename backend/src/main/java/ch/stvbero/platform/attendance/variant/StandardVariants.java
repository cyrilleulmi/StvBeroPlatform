package ch.stvbero.platform.attendance.variant;

import ch.stvbero.platform.core.team.Team;
import ch.stvbero.platform.core.team.TeamListener;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/** Every new team gets its four standard event variants from the start (VAR-01). */
@ApplicationScoped
public class StandardVariants implements TeamListener {

    @Inject
    EntityManager em;

    @Override
    public void teamCreated(Team team) {
        em.createNativeQuery(VariantRules.ENSURE_STANDARD_SQL).executeUpdate();
    }
}
