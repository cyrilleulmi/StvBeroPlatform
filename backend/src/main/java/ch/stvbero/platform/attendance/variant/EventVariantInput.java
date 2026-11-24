package ch.stvbero.platform.attendance.variant;

import ch.stvbero.platform.attendance.event.EventRules;
import ch.stvbero.platform.attendance.event.EventType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * Create or change an event variant (VAR-02 to VAR-09). {@code eventType} is needed for a new variant and
 * ignored when changing one (the type of a variant stays). Fields left out keep their value or get the default.
 */
@Schema(name = "EventVariantInput")
public record EventVariantInput(
        @NotBlank(message = "Bitte einen Namen eingeben.") @Size(max = 40, message = "Höchstens 40 Zeichen.") String name,
        EventType eventType,
        @NotNull(message = "Bitte eine Farbe wählen.") VariantColor color,
        Boolean callUp,
        Boolean autoDeparture,
        @Min(value = 0, message = EventRules.MIN_RESPONSE_DEADLINE_TEXT)
        @Max(value = EventRules.MAX_RESPONSE_DEADLINE, message = EventRules.MAX_RESPONSE_DEADLINE_TEXT) Integer responseDeadlineHours,
        Boolean attendanceCheck,
        Boolean declineReasonRequired,
        List<@NotNull Section> sections) {
}
