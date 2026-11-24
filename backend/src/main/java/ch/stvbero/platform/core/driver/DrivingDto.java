package ch.stvbero.platform.core.driver;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Whether a person can drive, and how many places her car usually has, herself included (DRV-18). Defaults: can
 * drive, 5 places (herself and four others). Also the request body for changing it.
 */
@Schema(name = "Driving")
public record DrivingDto(
        @NotNull(message = "Bitte wählen.") Boolean canDrive,
        @Schema(description = "Places in the car including the driver, 2 to 9, default 5")
        @NotNull(message = SEATS_TEXT) @Min(value = MIN_CAR_SEATS, message = SEATS_TEXT)
        @Max(value = MAX_CAR_SEATS, message = SEATS_TEXT) Integer carSeats) {

    public static final int MIN_CAR_SEATS = 2;
    public static final int MAX_CAR_SEATS = 9;
    public static final int DEFAULT_CAR_SEATS = 5;
    public static final String SEATS_TEXT = "Bitte eine Zahl von 2 bis 9 eingeben.";
}
