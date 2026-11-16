package ch.stvbero.platform.core.team;

import ch.stvbero.platform.core.person.PersonSummary;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "TeamMembership")
public record TeamMembershipDto(TeamName team, PersonSummary person, TeamRole teamRole, UUID seasonId, UUID groupId) {

    public record TeamName(UUID id, String name) {
    }

    /**
     * Teamname aus der Saison der Zugehörigkeit, damit das Archiv den damaligen Namen zeigt (SAI-02).
     * {@code gruppeId}: Linie oder Goalies, {@code null} = ohne Linie (LIN-02: höchstens eine).
     */
    public static TeamMembershipDto of(TeamMembership z) {
        UUID groupId = z.groups.stream().map(g -> g.id).findFirst().orElse(null);
        return new TeamMembershipDto(new TeamName(z.team.id, z.team.nameIn(z.season)), PersonSummary.of(z.person),
                z.teamRole, z.season.id, groupId);
    }
}
