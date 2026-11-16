package ch.stvbero.platform.core.event;

import ch.stvbero.platform.core.person.PersonSummary;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * Overview of an event (ANM-20 to ANM-32): teams by name, each with lines, goalies and «Ohne Linie», plus the
 * coach block and the counts. Comments only for those allowed to see them (ANM-07).
 */
@Schema(name = "Overview")
public record OverviewDto(UUID eventId, CountsDto total, List<TeamBlock> teams, List<CoachEntry> coaches) {

    @Schema(name = "OverviewTeamName")
    public record TeamName(UUID id, String name) {
    }

    @Schema(name = "OverviewTeam")
    public record TeamBlock(TeamName team, CountsDto counts, List<Group> groups) {
    }

    @Schema(name = "OverviewGroupType")
    public enum Type {
        LINE, GOALIES, NO_LINE
    }

    @Schema(name = "OverviewGroup")
    public record Group(UUID groupId, Type type, String name, List<Entry> entries) {
    }

    @Schema(name = "OverviewEntry")
    public record Entry(PersonSummary person, ResponseDto response) {
    }

    @Schema(name = "OverviewCoach")
    public record CoachEntry(PersonSummary person, TeamName team, ResponseDto response) {
    }
}
