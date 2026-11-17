package ch.stvbero.platform.core.driver;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Change the number of free seats of a car (DRV-02). */
@Schema(name = "SeatsInput")
public record SeatsInput(
        @NotNull(message = DriverRules.SEATS_TEXT) @Min(value = DriverRules.MIN_SEATS, message = DriverRules.SEATS_TEXT)
        @Max(value = DriverRules.MAX_SEATS, message = DriverRules.SEATS_TEXT) Integer seats) {
}
