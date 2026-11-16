package ch.stvbero.platform.core.event;

import ch.stvbero.platform.core.team.TeamDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Eintrag in der Terminliste, mit eigener Antwort (TER-13). */
@Schema(name = "EventSummary")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventSummaryDto(UUID id, EventType type, String title, OffsetDateTime start, OffsetDateTime end,
                            EventStatus status, String cancellationReason, boolean rescheduled, List<TeamDto> teams,
                            LocationDto location, OffsetDateTime responseDeadline, ResponseDto myResponse, CountsDto counts,
                            UUID seriesId) {
}
