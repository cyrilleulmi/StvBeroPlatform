package ch.stvbero.platform.attendance.event;

import ch.stvbero.platform.core.team.TeamDto;
import ch.stvbero.platform.attendance.variant.EventVariantDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Event with all details (TER-04). Matches, call-up status and the caller's own call-up status exist only for
 * match days (AUF-14, AUF-23, AUF-24). Whether attendance is recorded only for coaches and admins, the own
 * attendance only for players with a recorded value (PRS-06, PRS-08). {@code variant} says how the event looks and
 * what it offers (VAR); without a call-up in the variant, {@code callUpStatus} and {@code myCallUp} are missing
 * (VAR-06). {@code standing} is the rank of the own team in the league table (AUF-34). {@code myGrant}: the caller
 * manages this event like a coach because of a special permission (RR-09); missing otherwise.
 */
@Schema(name = "Event")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventDto(UUID id, EventType type, String title, OffsetDateTime start, OffsetDateTime end,
                        EventStatus status, String cancellationReason, boolean rescheduled, List<TeamDto> teams,
                        LocationDto location, OffsetDateTime responseDeadline, ResponseDto myResponse, CountsDto counts,
                        UUID seriesId, Integer warningCount, String notes, int responseDeadlineHours, MeetingPointDto meetingPoint,
                        List<MatchDto> matches, CallUpStatus callUpStatus, MyCallUp myCallUp,
                        boolean canEdit, boolean attendanceCheck, Boolean attendanceRecorded,
                        boolean canRecordAttendance, AttendanceStatus myAttendance, EventVariantDto variant,
                        StandingDto standing, List<UUID> optionalTeamIds, Participation myParticipation,
                        Boolean myGrant,
                        @Schema(description = "Pinned to the start page of the members of its teams until it is over (TER-26, E-114)")
                        boolean pinned,
                        @Schema(description = "Pre-training before the practice (TER-29); missing if there is none")
                        PreTrainingDto preTraining) {
}
