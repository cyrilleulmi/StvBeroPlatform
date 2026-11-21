package ch.stvbero.platform.core.mode;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Whether the app talks to simulators instead of the outside world: swiss unihockey ({@code SuSimulator}) and the
 * routing service ({@code RoutingSimulator}).
 * <p>
 * Production never simulates: the simulators do not exist there. With test support ({@code dev}, {@code test})
 * there are two worlds, switched at runtime by {@code POST /api/test/reset?dataset=…}:
 * <ul>
 *   <li><b>test</b>: the fixed seed of docs/test-data.md with simulators. All automated tests use it.</li>
 *   <li><b>club</b>: the club data set with the real connections. Default when starting in {@code dev}.</li>
 * </ul>
 * Start value: {@code stv.simulation} ({@code true} in {@code test}, {@code STV_SIMULATION} in {@code dev}).
 */
@ApplicationScoped
public class Simulation {

    @ConfigProperty(name = "stv.simulation", defaultValue = "false")
    boolean initial;

    private volatile boolean on;

    /** Counts the switches, so that background work of an earlier data set can stop. */
    private volatile int generation;

    @PostConstruct
    void start() {
        on = initial;
    }

    public boolean on() {
        return on;
    }

    public synchronized void set(boolean on) {
        this.on = on;
        generation++;
    }

    public int generation() {
        return generation;
    }
}
