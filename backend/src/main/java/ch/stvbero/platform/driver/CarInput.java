package ch.stvbero.platform.driver;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.UUID;

/** Enter a car (DRV-02): for oneself without {@code driverId}, for someone else only as coach or admin. */
@Schema(name = "CarInput")
public record CarInput(
        @Schema(description = "Driver; missing: the caller herself") UUID driverId,
        @NotNull(message = DriverRules.SEATS_TEXT) @Min(value = DriverRules.MIN_SEATS, message = DriverRules.SEATS_TEXT)
        @Max(value = DriverRules.MAX_SEATS, message = DriverRules.SEATS_TEXT) Integer seats) {
}
