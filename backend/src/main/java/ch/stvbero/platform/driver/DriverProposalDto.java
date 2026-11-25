package ch.stvbero.platform.driver;

import ch.stvbero.platform.core.person.PersonSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * Proposed driver list of a match day (DRV-19). Nothing is changed until the proposal is applied.
 */
@Schema(name = "DriverProposal")
public record DriverProposalDto(
        UUID eventId,
        @Schema(description = "All cars after the proposal: the existing ones first, then the proposed ones") List<Car> cars,
        @Schema(description = "People left without a seat because there are not enough drivers, by name") List<PersonSummary> withoutSeat,
        @Schema(description = "The proposal adds a car or a passenger; otherwise there is nothing to apply") boolean changes) {

    /** A car of the proposal. */
    @Schema(name = "ProposedCar")
    public record Car(
            PersonSummary driver,
            @Schema(description = "Seats for passengers") int seats,
            @Schema(description = "The car is already in the driver list") boolean existing,
            @Schema(description = "Everyone in the car after the proposal, by name") List<PersonSummary> passengers,
            @Schema(description = "The passengers the proposal adds, by name") List<PersonSummary> added) {
    }

    /** Request: apply a proposal as it was shown (DRV-19). */
    @Schema(name = "DriverProposalInput")
    public record Input(
            @NotNull @Size(max = 40, message = "Höchstens 40 Autos.") List<@Valid @NotNull CarEntry> cars) {
    }

    /** A car to enter or to fill: the driver, her seats (for a new car) and the passengers to add. */
    @Schema(name = "DriverProposalCar")
    public record CarEntry(
            @NotNull UUID driverId,
            @NotNull(message = DriverRules.SEATS_TEXT) @Min(value = DriverRules.MIN_SEATS, message = DriverRules.SEATS_TEXT)
            @Max(value = DriverRules.MAX_SEATS, message = DriverRules.SEATS_TEXT) Integer seats,
            @NotNull @Size(max = 8) List<@NotNull UUID> passengerIds) {
    }
}
