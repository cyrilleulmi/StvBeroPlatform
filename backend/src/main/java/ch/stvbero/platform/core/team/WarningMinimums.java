package ch.stvbero.platform.core.team;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Minimums of a team for the warnings (F-16): below these numbers coaches and admins see a warning. 0 switches
 * the warning off. Also the request body for changing them.
 */
@Schema(name = "WarningMinimums")
public record WarningMinimums(
        @NotNull(message = "Bitte eine Zahl eingeben.") @Min(value = 0, message = "Mindestens 0.")
        @Max(value = MAX, message = "Höchstens 99.") Integer goalies,
        @NotNull(message = "Bitte eine Zahl eingeben.") @Min(value = 0, message = "Mindestens 0.")
        @Max(value = MAX, message = "Höchstens 99.") Integer fieldPlayersMatchDay,
        @NotNull(message = "Bitte eine Zahl eingeben.") @Min(value = 0, message = "Mindestens 0.")
        @Max(value = MAX, message = "Höchstens 99.") Integer fieldPlayersPractice,
        @NotNull(message = "Bitte eine Zahl eingeben.") @Min(value = 0, message = "Mindestens 0.")
        @Max(value = MAX, message = "Höchstens 99.") Integer perLine) {

    public static final int MAX = 99;

    /** Defaults of a new team: 1 goalie, 10 field players for match days, 6 for practices, 3 per line. */
    public static final WarningMinimums DEFAULTS = new WarningMinimums(1, 10, 6, 3);
}
