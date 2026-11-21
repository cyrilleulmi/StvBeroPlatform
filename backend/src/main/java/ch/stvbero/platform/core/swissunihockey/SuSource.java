package ch.stvbero.platform.core.swissunihockey;

import ch.stvbero.platform.core.mode.Simulation;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.List;

/**
 * The source the app asks (SUH-01): the real swiss unihockey API, or the simulator while the app simulates
 * ({@link Simulation}). In production there is no simulator, so it is always the API.
 */
@ApplicationScoped
public class SuSource implements SwissUnihockeySource {

    @Inject
    Simulation simulation;

    @Inject
    SuApiSource api;

    @Inject
    Instance<SuSimulator> simulator;

    /** The source in use right now. */
    public SwissUnihockeySource current() {
        return simulation.on() && simulator.isResolvable() ? simulator.get() : api;
    }

    @Override
    public List<SuMatch> matches(int suTeamId, int season) {
        return current().matches(suTeamId, season);
    }

    @Override
    public SuLeague league(int suTeamId, int season) {
        return current().league(suTeamId, season);
    }

    @Override
    public List<SuClubTeam> clubTeams(int season) {
        return current().clubTeams(season);
    }
}
