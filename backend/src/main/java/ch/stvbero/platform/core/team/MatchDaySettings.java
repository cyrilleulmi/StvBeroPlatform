package ch.stvbero.platform.core.team;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Match day settings of a team (TML-02, TML-03, AUF-32): how long a match takes, how long before the first match
 * the team wants to arrive at the venue, and where it usually meets. They apply to the team, not per season, and
 * feed the timeline of a match day and the suggested departure. Also the request body for changing them.
 */
@Schema(name = "MatchDaySettings")
public record MatchDaySettings(
        @Schema(description = "Duration of one match in minutes, default 50")
        @NotNull(message = "Bitte eine Zahl eingeben.") @Min(value = MIN_DURATION, message = "Mindestens 10 Minuten.")
        @Max(value = MAX_MINUTES, message = "Höchstens 240 Minuten.") Integer matchDurationMinutes,
        @Schema(description = "How long before the first match the team wants to arrive, in minutes, default 70")
        @NotNull(message = "Bitte eine Zahl eingeben.") @Min(value = 0, message = "Mindestens 0 Minuten.")
        @Max(value = MAX_MINUTES, message = "Höchstens 240 Minuten.") Integer arrivalLeadMinutes,
        @Schema(nullable = true, description = "Usual meeting point; new match days from swiss unihockey start with it")
        @Size(max = 120, message = "Höchstens 120 Zeichen.") String meetingPoint) {

    public static final int MIN_DURATION = 10;
    public static final int MAX_MINUTES = 240;

    /** Defaults of a new team: a match takes 50 minutes, arrival 70 minutes before the first match. */
    public static final MatchDaySettings DEFAULTS = new MatchDaySettings(50, 70, null);
}
