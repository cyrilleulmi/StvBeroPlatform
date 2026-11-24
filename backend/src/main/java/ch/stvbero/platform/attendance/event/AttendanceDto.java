package ch.stvbero.platform.attendance.event;

import ch.stvbero.platform.core.person.PersonSummary;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Attendance of an event for coaches and admins (PRS-03, PRS-04): all players of the involved teams, by team and
 * line as in the overview, with response and attendance (recorded or preselected). Coaches are not part of it
 * (PRS-05). A player in two teams appears in both, with the same status.
 */
@Schema(name = "Attendance")
public record AttendanceDto(UUID eventId, boolean recorded, OffsetDateTime recordedAt, PersonSummary recordedBy,
                            List<TeamBlock> teams) {

    /** One involved team with its groups (lines, goalies, «Ohne Linie»); teams without players are left out. */
    @Schema(name = "AttendanceTeam")
    public record TeamBlock(OverviewDto.TeamName team, List<Group> groups) {
    }

    @Schema(name = "AttendanceGroup")
    public record Group(UUID groupId, OverviewDto.Type type, String name, List<Entry> entries) {
    }

    /** {@code recorded}: the status is saved; otherwise it is the preselection from the response. */
    @Schema(name = "AttendanceEntry")
    public record Entry(PersonSummary person, ResponseDto response, AttendanceStatus status, boolean recorded) {
    }
}
