package ch.stvbero.platform.core.driver;

import ch.stvbero.platform.core.driver.DriverPlanRules.Share;
import ch.stvbero.platform.core.driver.DriverPlanRules.Trip;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** DRV-17: how the trips of a season are shared, without a database. */
class DriverPlanRulesTest {

    private static final UUID PETRA = UUID.fromString("20000000-0000-4000-8000-000000000002");
    private static final UUID SARA = UUID.fromString("20000000-0000-4000-8000-000000000008");
    private static final UUID ANNA = UUID.fromString("20000000-0000-4000-8000-000000000005");

    @Test
    void tripsThatAreOverAndPlannedTripsAreCountedSeparately() {
        Map<UUID, Share> shares = DriverPlanRules.shares(List.of(
                new Trip(PETRA, false, true, 23_400),
                new Trip(PETRA, false, true, 10_000),
                new Trip(PETRA, false, false, 12_600),
                new Trip(SARA, false, false, 23_400)));

        // The credit is the return trip: twice the distance (DRV-05)
        assertEquals(new Share(2, 1, 66_800, 25_200), shares.get(PETRA));
        assertEquals(new Share(0, 1, 0, 46_800), shares.get(SARA));
        assertFalse(shares.containsKey(ANNA), "who never drives has no entry");
    }

    @Test
    void cancelledMatchDaysCountNothingAndUnknownDistancesCountAsTrips() {
        Map<UUID, Share> shares = DriverPlanRules.shares(List.of(
                new Trip(PETRA, true, true, 23_400),
                new Trip(PETRA, false, true, null),
                new Trip(PETRA, false, false, null)));

        assertEquals(new Share(1, 1, 0, 0), shares.get(PETRA));
    }
}
