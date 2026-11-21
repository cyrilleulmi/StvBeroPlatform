package ch.stvbero.platform.core.driver;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rules of the proposed driver list (DRV-19), without a database. */
class DriverProposalRulesTest {

    static final UUID A = id(1);
    static final UUID B = id(2);
    static final UUID C = id(3);
    static final UUID D = id(4);
    static final UUID E = id(5);
    static final UUID F = id(6);
    static final UUID G = id(7);

    private static UUID id(int nr) {
        return new UUID(0, nr);
    }

    private static DriverProposalRules.Traveller driver(UUID person, int freeSeats, int trips) {
        return new DriverProposalRules.Traveller(person, true, freeSeats, trips);
    }

    private static DriverProposalRules.Traveller rider(UUID person) {
        return new DriverProposalRules.Traveller(person, false, 4, 0);
    }

    @Test
    void existingCarsAreFilledFirst() {
        DriverProposalRules.Proposal proposal = DriverProposalRules.propose(
                List.of(new DriverProposalRules.ExistingCar(A, 3, List.of(B))),
                List.of(driver(C, 4, 0), rider(D)));

        // Two free seats are enough for two people: nobody else has to drive
        assertEquals(1, proposal.cars().size());
        assertEquals(List.of(B, C, D), proposal.cars().get(0).passengers());
        assertEquals(List.of(C, D), proposal.cars().get(0).added());
        assertTrue(proposal.cars().get(0).existing());
        assertTrue(proposal.withoutSeat().isEmpty());
        assertTrue(proposal.changesSomething());
    }

    @Test
    void theOneWithTheFewestTripsDrivesFirst() {
        DriverProposalRules.Proposal proposal = DriverProposalRules.propose(List.of(),
                List.of(driver(A, 4, 3), driver(B, 4, 1), rider(C), rider(D), driver(E, 4, 1)));

        // B and E have one trip each; B comes first by name. One car with four seats takes the other four.
        assertEquals(1, proposal.cars().size());
        assertEquals(B, proposal.cars().get(0).driver());
        assertFalse(proposal.cars().get(0).existing());
        assertEquals(4, proposal.cars().get(0).seats());
        assertEquals(List.of(A, C, D, E), proposal.cars().get(0).passengers());
        assertTrue(proposal.withoutSeat().isEmpty());
    }

    @Test
    void onlyAsManyCarsAsNeeded() {
        DriverProposalRules.Proposal proposal = DriverProposalRules.propose(List.of(),
                List.of(driver(A, 2, 0), driver(B, 2, 0), driver(C, 2, 0), rider(D), rider(E), rider(F), rider(G)));

        // Seven people: A drives (two seats) → six left for two seats; B drives → five for four; C drives → four
        // for six seats. Three cars.
        assertEquals(List.of(A, B, C), proposal.cars().stream().map(DriverProposalRules.Car::driver).toList());
        assertEquals(List.of(D, E), proposal.cars().get(0).passengers());
        assertEquals(List.of(F, G), proposal.cars().get(1).passengers());
        assertTrue(proposal.cars().get(2).passengers().isEmpty());
        assertTrue(proposal.withoutSeat().isEmpty());
    }

    @Test
    void whoIsLeftWithoutEnoughDrivers() {
        DriverProposalRules.Proposal proposal = DriverProposalRules.propose(List.of(),
                List.of(driver(A, 1, 0), rider(B), rider(C), rider(D)));

        assertEquals(1, proposal.cars().size());
        assertEquals(List.of(B), proposal.cars().get(0).passengers());
        assertEquals(List.of(C, D), proposal.withoutSeat());
    }

    @Test
    void nobodyDrivesWithoutNeed() {
        DriverProposalRules.Proposal nobody = DriverProposalRules.propose(
                List.of(new DriverProposalRules.ExistingCar(A, 3, List.of(B))), List.of());
        assertFalse(nobody.changesSomething());
        assertEquals(1, nobody.cars().size());

        // A single traveller who can drive takes her own car
        DriverProposalRules.Proposal alone = DriverProposalRules.propose(List.of(), List.of(driver(A, 4, 0)));
        assertEquals(A, alone.cars().get(0).driver());
        assertTrue(alone.cars().get(0).passengers().isEmpty());

        // Nobody can drive: everyone is left over, nothing changes
        DriverProposalRules.Proposal stuck = DriverProposalRules.propose(List.of(), List.of(rider(A), rider(B)));
        assertTrue(stuck.cars().isEmpty());
        assertEquals(List.of(A, B), stuck.withoutSeat());
        assertFalse(stuck.changesSomething());
    }

    @Test
    void drv18PlacesOfTheCarIncludeTheDriver() {
        assertEquals(4, DriverProposalRules.freeSeats(5));
        assertEquals(1, DriverProposalRules.freeSeats(2));
        assertEquals(8, DriverProposalRules.freeSeats(9));
    }
}
