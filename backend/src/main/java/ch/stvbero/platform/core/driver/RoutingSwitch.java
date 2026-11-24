package ch.stvbero.platform.core.driver;

import ch.stvbero.platform.core.mode.Simulation;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.Optional;

/**
 * The routing the app asks (DRV-06): the real service ({@link LiveRouting}), or the simulator while the app
 * simulates ({@link Simulation}). In production there is no simulator, so it is always the real service.
 */
@ApplicationScoped
public class RoutingSwitch implements Routing {

    @Inject
    Simulation simulation;

    @Inject
    LiveRouting live;

    @Inject
    Instance<RoutingSimulator> simulator;

    /** The routing in use right now. */
    public Routing current() {
        return simulation.on() && simulator.isResolvable() ? simulator.get() : live;
    }

    @Override
    public Optional<Result> route(Place from, Place to) {
        return current().route(from, to);
    }

    @Override
    public RouteProvider provider() {
        return current().provider();
    }
}
