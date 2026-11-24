package ch.stvbero.platform.core.driver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Domain rules of the proposed driver list (DRV-19). Without a database, so that unit tests can check them.
 * <p>
 * Everyone who needs a seat is seated. Cars that exist stay and are filled first. Further drivers are chosen among
 * the people who travel anyway and can drive, the one with the fewest trips in the season first; each brings her
 * usual number of free seats. Only as many cars as needed are added.
 */
public final class DriverProposalRules {

    private DriverProposalRules() {
    }

    /** A car that is already in the list, with its passengers. */
    public record ExistingCar(UUID driver, int seats, List<UUID> passengers) {
    }

    /**
     * A person who needs a seat.
     *
     * @param canDrive  can drive a car (DRV-18)
     * @param freeSeats seats for others in her car (her usual places minus herself)
     * @param trips     trips in the season so far, done and planned (DRV-17)
     */
    public record Traveller(UUID person, boolean canDrive, int freeSeats, int trips) {
    }

    /**
     * A car of the proposal.
     *
     * @param existing   the car is already in the list
     * @param passengers everyone in the car after the proposal
     * @param added      the passengers the proposal adds
     */
    public record Car(UUID driver, int seats, boolean existing, List<UUID> passengers, List<UUID> added) {
    }

    /** The proposal: all cars, and who is left without a seat because there are not enough drivers. */
    public record Proposal(List<Car> cars, List<UUID> withoutSeat) {

        public boolean changesSomething() {
            return cars.stream().anyMatch(car -> !car.existing() || !car.added().isEmpty());
        }
    }

    private static final class Draft {
        final UUID driver;
        final int seats;
        final boolean existing;
        final List<UUID> passengers;
        final List<UUID> added = new ArrayList<>();

        Draft(UUID driver, int seats, boolean existing, List<UUID> passengers) {
            this.driver = driver;
            this.seats = seats;
            this.existing = existing;
            this.passengers = new ArrayList<>(passengers);
        }

        int free() {
            return Math.max(0, seats - passengers.size());
        }
    }

    /**
     * @param existing   cars in the list, in display order
     * @param travellers people without a seat who need one, in display order (by name)
     */
    public static Proposal propose(List<ExistingCar> existing, List<Traveller> travellers) {
        List<Draft> cars = new ArrayList<>();
        existing.forEach(car -> cars.add(new Draft(car.driver(), car.seats(), true, car.passengers())));
        List<Traveller> remaining = new ArrayList<>(travellers);
        int free = cars.stream().mapToInt(Draft::free).sum();

        // The fewest trips first; with the same number, the order of the list (by name) decides
        List<Traveller> drivers = new ArrayList<>(travellers.stream().filter(Traveller::canDrive).toList());
        drivers.sort(Comparator.comparingInt(Traveller::trips));
        while (remaining.size() > free && !drivers.isEmpty()) {
            Traveller driver = drivers.remove(0);
            remaining.remove(driver);
            int seats = Math.max(DriverRules.MIN_SEATS, Math.min(DriverRules.MAX_SEATS, driver.freeSeats()));
            cars.add(new Draft(driver.person(), seats, false, List.of()));
            free += seats;
        }

        List<UUID> withoutSeat = new ArrayList<>();
        for (Traveller traveller : remaining) {
            Draft car = cars.stream().filter(c -> c.free() > 0).findFirst().orElse(null);
            if (car == null) {
                withoutSeat.add(traveller.person());
            } else {
                car.passengers.add(traveller.person());
                car.added.add(traveller.person());
            }
        }
        return new Proposal(cars.stream()
                .map(c -> new Car(c.driver, c.seats, c.existing, List.copyOf(c.passengers), List.copyOf(c.added)))
                .toList(), List.copyOf(withoutSeat));
    }

    /** Free seats of a car from the usual places of the person, herself included (DRV-18): 5 places → 4 seats. */
    public static int freeSeats(int carSeats) {
        return Math.max(DriverRules.MIN_SEATS, Math.min(DriverRules.MAX_SEATS, carSeats - 1));
    }
}
