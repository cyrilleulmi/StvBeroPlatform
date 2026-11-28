package ch.stvbero.platform.attendance.event;

import ch.stvbero.platform.core.person.PersonSummary;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * Overview of an event (ANM-20 to ANM-32): teams by name, each with lines, goalies and «Ohne Linie», plus the
 * coach block and the counts. Comments only for those allowed to see them (ANM-07). Warnings only for coaches and
 * admins (F-16); for everyone else the field is missing.
 */
@Schema(name = "Overview")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OverviewDto(UUID eventId, CountsDto total, List<TeamBlock> teams, List<CoachEntry> coaches,
                          List<WarningDto> warnings,
                          @Schema(description = "Responses to the pre-training of the players in total (ANM-17); missing without one")
                          CountsDto preTrainingTotal) {

    @Schema(name = "OverviewTeamName")
    public record TeamName(UUID id, String name) {
    }

    @Schema(name = "OverviewTeam")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TeamBlock(TeamName team, CountsDto counts, List<Group> groups,
                            @Schema(description = "The team is invited without obligation: players without a response are not counted as pending (TER-15)")
                            boolean optional,
                            @Schema(description = "Responses to the pre-training of the players in counts (ANM-17); missing without one")
                            CountsDto preTrainingCounts) {
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
