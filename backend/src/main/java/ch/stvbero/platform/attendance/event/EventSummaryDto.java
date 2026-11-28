package ch.stvbero.platform.attendance.event;

import ch.stvbero.platform.core.team.TeamDto;
import ch.stvbero.platform.attendance.variant.EventVariantDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Entry in the event list, with the caller's own response (TER-13). {@code warningCount} only for coaches and admins
 * (F-16). {@code variant} is the event variant that applies: name and colour for the list (VAR-05).
 */
@Schema(name = "EventSummary")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventSummaryDto(UUID id, EventType type, String title, OffsetDateTime start, OffsetDateTime end,
                            EventStatus status, String cancellationReason, boolean rescheduled, List<TeamDto> teams,
                            LocationDto location, OffsetDateTime responseDeadline, ResponseDto myResponse, CountsDto counts,
                            UUID seriesId, Integer warningCount, EventVariantDto variant,
                            @Schema(description = "Teams invited without obligation (TER-15)") List<UUID> optionalTeamIds,
                            @Schema(description = "How the caller takes part; missing if she is in no team of the event (TER-15)")
                            Participation myParticipation,
                            @Schema(description = "The caller may edit the event: coach, admin or a special permission for it, while it is planned and not over (TER-11, RR-09, E-111)")
                            boolean canEdit,
                            @Schema(description = "Pinned to the start page of the members of its teams until it is over (TER-26, E-114)")
                            boolean pinned,
                            @Schema(description = "Pre-training before the practice (TER-29); missing if there is none")
                            PreTrainingDto preTraining) {
}
