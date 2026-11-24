package ch.stvbero.platform.driver;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Driving time of the one-way route overwritten by hand (DRV-21). */
@Schema(name = "DurationInput")
public record DurationInput(
        @NotNull(message = DriverRules.DURATION_TEXT) @Min(value = 1, message = DriverRules.DURATION_TEXT)
        @Max(value = 600, message = DriverRules.DURATION_TEXT) Integer minutes) {
}
