package ch.stvbero.platform.attendance.event;

import ch.stvbero.platform.core.person.PersonSummary;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Call-up of a match day for the coach: all players of the team, grouped as in the overview, with response
 * status and selection (AUF-21, AUF-22). Coaches are not part of it (AUF-28). The warnings are based on the
 * selection (F-16).
 */
@Schema(name = "CallUp")
public record CallUpDto(UUID eventId, CallUpStatus status, OffsetDateTime publishedAt,
                          int calledUpCount, List<Group> groups, List<WarningDto> warnings) {

    @Schema(name = "CallUpGroup")
    public record Group(UUID groupId, OverviewDto.Type type, String name, List<Entry> entries) {
    }

    @Schema(name = "CallUpEntry")
    public record Entry(PersonSummary person, ResponseDto response, boolean calledUp) {
    }
}
