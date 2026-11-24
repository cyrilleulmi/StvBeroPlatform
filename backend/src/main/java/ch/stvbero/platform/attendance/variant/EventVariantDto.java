package ch.stvbero.platform.attendance.variant;

import ch.stvbero.platform.attendance.event.EventType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * Event variant of a team (VAR). Events carry the variant that applies to them, so that the event list and the
 * event page know its name, colour and sections without asking again.
 */
@Schema(name = "EventVariant")
public record EventVariantDto(
        UUID id,
        UUID teamId,
        VariantKind kind,
        EventType eventType,
        String name,
        VariantColor color,
        @Schema(description = "A call-up is made (VAR-06); only has an effect for match days") boolean callUp,
        @Schema(description = "The meeting time follows the suggested departure (VAR-08); only for match days") boolean autoDeparture,
        @Schema(description = "Default response deadline of new events, in hours (VAR-09)") int responseDeadlineHours,
        @Schema(description = "Default of the attendance check of new events (VAR-09)") boolean attendanceCheck,
        @Schema(description = "«nicht dabei» needs a reason (VAR-12, ANM-05)") boolean declineReasonRequired,
        @Schema(description = "Sections of the event page, in the order they are shown (VAR-07)") List<Section> sections,
        @Schema(description = "One of the four standard variants: cannot be deleted (VAR-01)") boolean standard) {
}
