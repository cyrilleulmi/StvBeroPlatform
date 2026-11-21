package ch.stvbero.platform.core.team;

import ch.stvbero.platform.core.person.PersonSummary;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * Team with coaches and players. Coaches are never listed among the players (PTF-07). The minimums for the
 * warnings and the match day settings only for coaches and admins (F-16, TML-02).
 */
@Schema(name = "TeamDetail")
public record TeamDetailDto(UUID id, String name, Integer suTeamId, TeamRole myTeamRole,
                            List<PersonSummary> coaches, Lineup lineup,
                            @JsonInclude(JsonInclude.Include.NON_NULL) WarningMinimums warningMinimums,
                            @JsonInclude(JsonInclude.Include.NON_NULL) MatchDaySettings matchDaySettings) {

    @Schema(name = "Lineup")
    public record Lineup(List<GroupWithPlayers> groups, List<PersonSummary> noLine) {
    }

    public record GroupWithPlayers(UUID id, GroupType type, String name, Integer sortOrder,
                                        List<PersonSummary> players) {
    }
}
