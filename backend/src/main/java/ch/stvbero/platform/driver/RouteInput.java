package ch.stvbero.platform.driver;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Kilometres of the one-way route entered by hand (DRV-07). */
@Schema(name = "RouteInput")
public record RouteInput(
        @NotNull(message = DriverRules.DISTANCE_TEXT) @DecimalMin(value = "0.1", message = DriverRules.DISTANCE_TEXT)
        @DecimalMax(value = "1000", message = DriverRules.DISTANCE_TEXT) Double distanceKm) {
}
