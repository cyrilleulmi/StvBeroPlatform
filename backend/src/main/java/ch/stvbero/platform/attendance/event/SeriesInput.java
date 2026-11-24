package ch.stvbero.platform.attendance.event;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Create or change a series (TER-05, TER-08, TER-10). Without {@code attendanceCheck} a new series has the check off
 * and a changed series keeps its value (PRS-01).
 */
@Schema(name = "SeriesInput")
public record SeriesInput(
        @NotNull SeriesType type,
        @NotBlank @Size(max = 80) String title,
        @NotEmpty List<@NotNull UUID> teamIds,
        @NotNull Weekday weekday,
        @NotNull @Pattern(regexp = EventRules.TIME_PATTERN, message = "Bitte eine Zeit im Format 18:30 angeben.") String startTime,
        @NotNull @Pattern(regexp = EventRules.TIME_PATTERN, message = "Bitte eine Zeit im Format 18:30 angeben.") String endTime,
        @NotNull LocalDate validFrom,
        @Schema(nullable = true, description = "Last day of the series, optional: without it the series runs on and its events are created one year ahead (TER-22, TER-23)")
        LocalDate validUntil,
        @Valid LocationDto location,
        @Min(value = 0, message = EventRules.MIN_RESPONSE_DEADLINE_TEXT)
        @Max(value = EventRules.MAX_RESPONSE_DEADLINE, message = EventRules.MAX_RESPONSE_DEADLINE_TEXT) Integer responseDeadlineHours,
        Boolean attendanceCheck,
        @Schema(nullable = true, description = "Teams of the series that are invited without obligation (TER-15); at least one team stays expected")
        List<@NotNull UUID> optionalTeamIds) {
}
