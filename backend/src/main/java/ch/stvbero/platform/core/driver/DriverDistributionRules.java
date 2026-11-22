package ch.stvbero.platform.core.driver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Domain rules for sharing the trips of a season fairly (DRV-30). Without a database, so that unit tests can
 * check them.
 * <p>
 * The match days are filled one after the other. A match day gets drivers until its cars have a place for
 * everyone who is expected (drivers included). The next driver is always the one with the fewest trips in the
 * season; with the same number the one with fewer kilometres, then the one who has not driven for longer, then the
 * order of the list (by name). Cars that are entered stay and count.
 */
public final class DriverDistributionRules {

    private DriverDistributionRules() {
    }

    /**
     * A person who can drive.
     *
     * @param places places of her car, herself included (DRV-18)
     * @param trips  trips in the season so far: done and planned ones that stay
     * @param meters credited metres of those trips
     */
    public record Driver(UUID person, int places, int trips, long meters) {
    }

    /**
     * A match day to fill.
     *
     * @param needed         people who are expected to travel (DRV-29)
     * @param places         places of the cars that are already entered, drivers included
     * @param distanceMeters one way; missing: unknown
     * @param unavailable    people who must not be chosen: they already drive or responded «nicht dabei»
     */
    public record Day(UUID event, int needed, int places, Integer distanceMeters, Set<UUID> unavailable) {
    }

    /** A driver the distribution enters on a match day. */
    public record Assignment(UUID event, UUID driver) {
    }

    private static final class Load {
        final Driver driver;
        final int order;
        int trips;
        long meters;
        int last = -1;

        Load(Driver driver, int order) {
            this.driver = driver;
            this.order = order;
            this.trips = driver.trips();
            this.meters = driver.meters();
        }
    }

    /**
     * @param days    match days to fill, by start
     * @param drivers people who can drive, in display order (by name)
     */
    public static List<Assignment> distribute(List<Day> days, List<Driver> drivers) {
        List<Load> loads = new ArrayList<>();
        Map<UUID, Load> byPerson = new HashMap<>();
        for (Driver driver : drivers) {
            Load load = new Load(driver, loads.size());
            loads.add(load);
            byPerson.put(driver.person(), load);
        }
        Comparator<Load> next = Comparator.<Load>comparingInt(l -> l.trips)
                .thenComparingLong(l -> l.meters)
                .thenComparingInt(l -> l.last)
                .thenComparingInt(l -> l.order);

        List<Assignment> result = new ArrayList<>();
        for (int index = 0; index < days.size(); index++) {
            Day day = days.get(index);
            Set<UUID> taken = new HashSet<>(day.unavailable());
            int places = day.places();
            while (places < day.needed()) {
                Load load = loads.stream().filter(l -> !taken.contains(l.driver.person())).min(next).orElse(null);
                if (load == null) {
                    break;
                }
                taken.add(load.driver.person());
                places += Math.max(1, load.driver.places());
                load.trips++;
                load.meters += day.distanceMeters() == null ? 0 : day.distanceMeters() * 2L;
                load.last = index;
                result.add(new Assignment(day.event(), load.driver.person()));
            }
        }
        return result;
    }

    /** Places of all cars of a match day: the seats for passengers plus the drivers. */
    public static int places(List<Integer> seatsPerCar) {
        return seatsPerCar.stream().mapToInt(seats -> seats + 1).sum();
    }

    /**
     * The meeting point is the venue: nobody has to drive (home match day). «Halle», «Halle Ort» and «Halle, Ort»
     * name the same place, ignoring case and spaces.
     */
    public static boolean samePlace(String meetingPoint, String venueName, String venueAddress) {
        if (meetingPoint == null || venueName == null) {
            return false;
        }
        String text = normal(meetingPoint);
        String name = normal(venueName);
        if (text.isEmpty() || name.isEmpty()) {
            return false;
        }
        String address = venueAddress == null ? "" : normal(venueAddress);
        return text.equals(name) || (!address.isEmpty() && (text.equals(name + " " + address)
                || text.equals(name + ", " + address)));
    }

    private static String normal(String text) {
        return text.strip().toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
    }

    /** The number in effect: the one of the match day, otherwise the one of the team, otherwise everyone. */
    public static int expected(Integer ofMatchDay, Integer ofTeam, int members) {
        if (ofMatchDay != null) {
            return ofMatchDay;
        }
        return ofTeam != null ? ofTeam : Math.max(1, members);
    }
}
