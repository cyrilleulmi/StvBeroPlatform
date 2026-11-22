package ch.stvbero.platform.core.driver;

import ch.stvbero.platform.core.driver.DriverDistributionRules.Assignment;
import ch.stvbero.platform.core.driver.DriverDistributionRules.Day;
import ch.stvbero.platform.core.driver.DriverDistributionRules.Driver;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sharing the trips of a season fairly (DRV-29, DRV-30). */
class DriverDistributionRulesTest {

    static final UUID A = id(1);
    static final UUID B = id(2);
    static final UUID C = id(3);
    static final UUID D = id(4);
    static final UUID DAY_1 = id(11);
    static final UUID DAY_2 = id(12);
    static final UUID DAY_3 = id(13);

    private static UUID id(int n) {
        return UUID.fromString("00000000-0000-4000-8000-%012d".formatted(n));
    }

    private static Day day(UUID event, int needed) {
        return new Day(event, needed, 0, 10_000, Set.of());
    }

    private static Map<UUID, Integer> tripsPerDriver(List<Assignment> assignments) {
        Map<UUID, Integer> trips = new HashMap<>();
        assignments.forEach(a -> trips.merge(a.driver(), 1, Integer::sum));
        return trips;
    }

    @Test
    void drv30EveryMatchDayGetsPlacesForEveryoneExpected() {
        List<Driver> drivers = List.of(new Driver(A, 5, 0, 0), new Driver(B, 5, 0, 0), new Driver(C, 5, 0, 0),
                new Driver(D, 5, 0, 0));
        List<Assignment> result = DriverDistributionRules.distribute(
                List.of(day(DAY_1, 10), day(DAY_2, 10), day(DAY_3, 11)), drivers);

        assertEquals(2, result.stream().filter(a -> a.event().equals(DAY_1)).count());
        assertEquals(2, result.stream().filter(a -> a.event().equals(DAY_2)).count());
        // Eleven people need a third car
        assertEquals(3, result.stream().filter(a -> a.event().equals(DAY_3)).count());
    }

    @Test
    void drv30TripsAreSharedEvenly() {
        List<Driver> drivers = List.of(new Driver(A, 5, 0, 0), new Driver(B, 5, 0, 0), new Driver(C, 5, 0, 0),
                new Driver(D, 5, 0, 0));
        List<Assignment> result = DriverDistributionRules.distribute(
                List.of(day(DAY_1, 10), day(DAY_2, 10), day(DAY_3, 10)), drivers);

        // Six trips for four drivers: nobody drives more than one trip more than anyone else
        Map<UUID, Integer> trips = tripsPerDriver(result);
        assertEquals(6, result.size());
        assertEquals(Set.of(1, 2), Set.copyOf(trips.values()));
        // The first match day takes the first two by name, the second the other two
        assertEquals(List.of(new Assignment(DAY_1, A), new Assignment(DAY_1, B), new Assignment(DAY_2, C),
                new Assignment(DAY_2, D)), result.subList(0, 4));
    }

    @Test
    void drv30WhoDroveLessGoesFirstThenFewerKilometres() {
        // B has driven twice, C once but far, D once and short, A never
        List<Driver> drivers = List.of(new Driver(A, 5, 0, 0), new Driver(B, 5, 2, 20_000),
                new Driver(C, 5, 1, 90_000), new Driver(D, 5, 1, 10_000));
        List<Assignment> result = DriverDistributionRules.distribute(List.of(day(DAY_1, 10), day(DAY_2, 5)), drivers);

        // Match day 1: A (no trip), then D (one short trip). Match day 2: A and C both have one trip, A fewer
        // kilometres (20 km there and back against 90 km)
        assertEquals(List.of(new Assignment(DAY_1, A), new Assignment(DAY_1, D), new Assignment(DAY_2, A)), result);
    }

    @Test
    void drv30CarsThatAreEnteredCountAndTheirDriversAreNotChosenAgain() {
        List<Driver> drivers = List.of(new Driver(A, 5, 1, 0), new Driver(B, 5, 0, 0), new Driver(C, 5, 0, 0));
        // A already drives with five places; ten people need one more car
        List<Assignment> result = DriverDistributionRules.distribute(
                List.of(new Day(DAY_1, 10, 5, null, Set.of(A))), drivers);

        assertEquals(List.of(new Assignment(DAY_1, B)), result);
    }

    @Test
    void drv30NobodyWhoDeclinedAndBigCarsNeedFewerDrivers() {
        List<Driver> drivers = List.of(new Driver(A, 7, 0, 0), new Driver(B, 5, 0, 0), new Driver(C, 5, 0, 0));
        List<Assignment> result = DriverDistributionRules.distribute(
                List.of(new Day(DAY_1, 12, 0, null, Set.of(B))), drivers);

        // A (7 places) and C (5) are enough for twelve; B declined
        assertEquals(List.of(new Assignment(DAY_1, A), new Assignment(DAY_1, C)), result);
    }

    @Test
    void drv30WithTooFewDriversTheMatchDayStaysShort() {
        List<Assignment> result = DriverDistributionRules.distribute(List.of(day(DAY_1, 14)),
                List.of(new Driver(A, 5, 0, 0)));

        assertEquals(List.of(new Assignment(DAY_1, A)), result);
    }

    @Test
    void drv29ExpectedNumberAndPlaces() {
        assertEquals(16, DriverDistributionRules.expected(null, null, 16));
        assertEquals(14, DriverDistributionRules.expected(null, 14, 16));
        assertEquals(9, DriverDistributionRules.expected(9, 14, 16));
        // Two cars with four and three seats for passengers have nine places
        assertEquals(9, DriverDistributionRules.places(List.of(4, 3)));
    }

    @Test
    void drv30HomeMatchDayNeedsNoTrip() {
        assertTrue(DriverDistributionRules.samePlace("Oberstufenschulhaus Beromünster", "Oberstufenschulhaus", "Beromünster"));
        assertTrue(DriverDistributionRules.samePlace(" oberstufenschulhaus,  beromünster", "Oberstufenschulhaus", "Beromünster"));
        assertTrue(DriverDistributionRules.samePlace("Sporthalle Seefeld", "Sporthalle Seefeld", null));
        assertFalse(DriverDistributionRules.samePlace("Parkplatz Sporthalle Nord", "Sporthalle Seefeld", "Sarnen"));
        assertFalse(DriverDistributionRules.samePlace(null, "Sporthalle Seefeld", null));
    }
}
