package ch.stvbero.platform.driver;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Domain rules for the driver plan of a season (DRV-17): how the trips are shared. Without a database, so that
 * unit tests can check them.
 */
public final class DriverPlanRules {

    private DriverPlanRules() {
    }

    /** A car on a match day of the team. */
    public record Trip(UUID driverId, boolean cancelled, boolean over, Integer distanceMeters) {
    }

    /**
     * Trips of one person: {@code trips} on match days that are over, {@code plannedTrips} on upcoming ones, each
     * with the credited metres (the return trip, DRV-05) where a distance is known.
     */
    public record Share(int trips, int plannedTrips, int creditMeters, int plannedCreditMeters) {

        public static final Share NONE = new Share(0, 0, 0, 0);
    }

    /** Trips per driver. A cancelled match day counts nothing (DRV-10). */
    public static Map<UUID, Share> shares(Collection<Trip> trips) {
        Map<UUID, Share> shares = new HashMap<>();
        for (Trip trip : trips) {
            if (trip.cancelled()) {
                continue;
            }
            int credit = trip.distanceMeters() == null ? 0 : trip.distanceMeters() * 2;
            Share before = shares.getOrDefault(trip.driverId(), Share.NONE);
            shares.put(trip.driverId(), trip.over()
                    ? new Share(before.trips() + 1, before.plannedTrips(), before.creditMeters() + credit,
                    before.plannedCreditMeters())
                    : new Share(before.trips(), before.plannedTrips() + 1, before.creditMeters(),
                    before.plannedCreditMeters() + credit));
        }
        return shares;
    }
}
