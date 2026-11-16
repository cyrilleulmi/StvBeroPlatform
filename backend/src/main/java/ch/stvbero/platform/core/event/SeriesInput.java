package ch.stvbero.platform.core.event;

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

/** Serie anlegen oder ändern (TER-05, TER-08, TER-10). */
@Schema(name = "SeriesInput")
public record SeriesInput(
        @NotNull SeriesType type,
        @NotBlank @Size(max = 80) String title,
        @NotEmpty List<@NotNull UUID> teamIds,
        @NotNull Weekday weekday,
        @NotNull @Pattern(regexp = EventRules.TIME_PATTERN, message = "Bitte eine Zeit im Format 18:30 angeben.") String startTime,
        @NotNull @Pattern(regexp = EventRules.TIME_PATTERN, message = "Bitte eine Zeit im Format 18:30 angeben.") String endTime,
        @NotNull LocalDate validFrom,
        @NotNull LocalDate validUntil,
        @Valid LocationDto location,
        @Min(value = 0, message = EventRules.MIN_RESPONSE_DEADLINE_TEXT)
        @Max(value = EventRules.MAX_RESPONSE_DEADLINE, message = EventRules.MAX_RESPONSE_DEADLINE_TEXT) Integer responseDeadlineHours) {
}
