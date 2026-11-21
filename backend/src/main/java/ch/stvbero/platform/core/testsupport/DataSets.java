package ch.stvbero.platform.core.testsupport;

import ch.stvbero.platform.core.driver.LiveRouting;
import ch.stvbero.platform.core.driver.RoutingSimulator;
import ch.stvbero.platform.core.mode.Simulation;
import ch.stvbero.platform.core.notification.Deliveries;
import ch.stvbero.platform.core.notification.DeliverySimulator;
import ch.stvbero.platform.core.swissunihockey.SuSimulator;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jboss.logging.Logger;

/**
 * The two data sets of the profiles dev and test (docs/test-data.md), switched at runtime:
 * <ul>
 *   <li>{@link DataSet#TEST}: the fixed seed with simulators and a fixed clock. All automated tests use it;
 *       {@code POST /api/test/reset} loads it.</li>
 *   <li>{@link DataSet#CLUB}: the club with its real teams, live data from swiss unihockey, real routes and the
 *       running clock; {@code POST /api/test/reset?dataset=CLUB} loads it. Default when starting in dev.</li>
 * </ul>
 * On start the data set follows {@code stv.simulation}: simulated means the test data set.
 */
@ApplicationScoped
@IfBuildProperty(name = "stv.test-support", stringValue = "true")
public class DataSets {

    private static final Logger LOG = Logger.getLogger(DataSets.class);

    /** Which data the app holds. */
    @Schema(name = "DataSet")
    public enum DataSet {
        TEST, CLUB
    }

    /** What the test mode login shows about the data set. */
    @Schema(name = "DataSetState")
    public record State(DataSet dataset,
                        @Schema(description = "swiss unihockey and routing are simulated") boolean simulated,
                        @Schema(description = "The club data is still being loaded from swiss unihockey") boolean loading,
                        @Schema(description = "Which service calculates routes: SIMULATOR, GOOGLE, OPEN_ROUTE_SERVICE or OPEN_STREET_MAP")
                        String routing) {
    }

    @Inject
    Seed seed;

    @Inject
    ClubSeed club;

    @Inject
    ClubLoader loader;

    @Inject
    Simulation simulation;

    @Inject
    Deliveries deliveries;

    @Inject
    SuSimulator suSimulator;

    @Inject
    DeliverySimulator deliverySimulator;

    @Inject
    RoutingSimulator routingSimulator;

    @Inject
    LiveRouting liveRouting;

    private volatile DataSet current = DataSet.TEST;

    void onStart(@Observes StartupEvent event) {
        load(simulation.on() ? DataSet.TEST : DataSet.CLUB);
    }

    /** Empties the database and fills in the data set; resets the simulators. */
    public void load(DataSet dataSet) {
        deliveries.exclusively(() -> {
            simulation.set(dataSet == DataSet.TEST);
            current = dataSet;
            if (dataSet == DataSet.TEST) {
                seed.reset();
            } else {
                club.reset();
            }
            suSimulator.reset();
            deliverySimulator.reset();
            routingSimulator.reset();
        });
        LOG.infof("Data set %s loaded", dataSet);
        if (dataSet == DataSet.CLUB) {
            loader.start();
        }
    }

    public State state() {
        return new State(current, simulation.on(), current == DataSet.CLUB && loader.loading(),
                simulation.on() ? "SIMULATOR" : liveRouting.service().name());
    }
}
