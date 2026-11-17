package ch.stvbero.platform.core.team;

import ch.stvbero.platform.core.person.PersonSummary;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "TeamMembership")
public record TeamMembershipDto(TeamName team, PersonSummary person, TeamRole teamRole, UUID seasonId, UUID groupId) {

    public record TeamName(UUID id, String name) {
    }

    /**
     * Team name from the season of the membership, so that the archive shows the name of that time (SAI-02).
     * {@code groupId}: line or goalies, {@code null} = no line (LIN-02: at most one).
     */
    public static TeamMembershipDto of(TeamMembership m) {
        UUID groupId = m.groups.stream().map(g -> g.id).findFirst().orElse(null);
        return new TeamMembershipDto(new TeamName(m.team.id, m.team.nameIn(m.season)), PersonSummary.of(m.person),
                m.teamRole, m.season.id, groupId);
    }
}
