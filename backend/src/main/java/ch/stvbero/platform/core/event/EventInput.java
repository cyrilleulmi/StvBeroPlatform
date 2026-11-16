package ch.stvbero.platform.core.event;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Einzeltermin erfassen oder ändern (TER-04, TER-10, TER-11). */
@Schema(name = "EventInput")
public record EventInput(
        @NotNull EventType type,
        @NotBlank @Size(max = 80) String title,
        @NotNull OffsetDateTime start,
        @NotNull OffsetDateTime end,
        @NotEmpty List<@NotNull UUID> teamIds,
        @Valid LocationDto location,
        @Size(max = 500) String notes,
        @Min(value = 0, message = EventRules.MIN_RESPONSE_DEADLINE_TEXT)
        @Max(value = EventRules.MAX_RESPONSE_DEADLINE, message = EventRules.MAX_RESPONSE_DEADLINE_TEXT) Integer responseDeadlineHours,
        @Valid MeetingPointDto meetingPoint) {
}
