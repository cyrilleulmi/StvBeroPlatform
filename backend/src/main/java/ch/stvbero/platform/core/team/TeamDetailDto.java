package ch.stvbero.platform.core.team;

import ch.stvbero.platform.core.person.PersonSummary;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/** Team mit Coaches und Spielerinnen. Coaches stehen nie bei den Spielerinnen (PTF-07). */
@Schema(name = "TeamDetail")
public record TeamDetailDto(UUID id, String name, Integer suTeamId, TeamRole myTeamRole,
                            List<PersonSummary> coaches, Lineup lineup) {

    @Schema(name = "Lineup")
    public record Lineup(List<GroupWithPlayers> groups, List<PersonSummary> noLine) {
    }

    public record GroupWithPlayers(UUID id, GroupType type, String name, Integer sortOrder,
                                        List<PersonSummary> players) {
    }
}
