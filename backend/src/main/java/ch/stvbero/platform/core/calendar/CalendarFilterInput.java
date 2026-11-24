package ch.stvbero.platform.core.calendar;

import ch.stvbero.platform.core.event.EventType;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * New filter of the calendar feed (CAL-10 to CAL-13). The address stays the same.
 *
 * @param types           at least one event type
 * @param responses       participations besides «dabei»; empty: only events with «dabei»
 * @param excludedTeamIds teams whose events are left out; unknown teams are ignored
 */
@Schema(name = "CalendarFilterInput")
public record CalendarFilterInput(
        @Schema(required = true) @NotNull(message = "Bitte wählen.") List<EventType> types,
        @Schema(required = true) @NotNull(message = "Bitte wählen.") List<CalendarResponse> responses,
        @Schema(required = true) @NotNull(message = "Bitte wählen.") List<UUID> excludedTeamIds) {
}
