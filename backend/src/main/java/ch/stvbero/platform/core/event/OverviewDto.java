package ch.stvbero.platform.core.event;

import ch.stvbero.platform.core.person.PersonSummary;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * Übersicht eines Termins (ANM-20 bis ANM-32): Teams nach Name, darin Linien, Goalies und «Ohne Linie»,
 * dazu der Coach-Block und die Zähler. Kommentare nur für Berechtigte (ANM-07).
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
