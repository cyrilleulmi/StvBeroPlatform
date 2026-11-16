package ch.stvbero.platform.core.event;

import ch.stvbero.platform.core.team.TeamDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Event with all details (TER-04). Matches, call-up status and the caller's own call-up status exist only for
 * match days (AUF-14, AUF-23, AUF-24).
 */
@Schema(name = "Event")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventDto(UUID id, EventType type, String title, OffsetDateTime start, OffsetDateTime end,
                        EventStatus status, String cancellationReason, boolean rescheduled, List<TeamDto> teams,
                        LocationDto location, OffsetDateTime responseDeadline, ResponseDto myResponse, CountsDto counts,
                        UUID seriesId, String notes, int responseDeadlineHours, MeetingPointDto meetingPoint,
                        List<MatchDto> matches, CallUpStatus callUpStatus, MyCallUp myCallUp,
                        boolean canEdit) {
}
