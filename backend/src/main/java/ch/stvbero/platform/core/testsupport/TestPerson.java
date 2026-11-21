package ch.stvbero.platform.core.testsupport;

import ch.stvbero.platform.core.team.TeamRole;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * A person of the data set with her key for the test mode login, and her teams in the current season, so that
 * the login page can group the people by team and mark the coaches.
 */
@Schema(name = "TestPerson")
public record TestPerson(String loginKey, UUID id, String firstName, String lastName, boolean admin,
                         @Schema(description = "Teams of the current season, by team name") List<Membership> teams) {

    /** A team the person belongs to in the current season. */
    @Schema(name = "TestPersonTeam")
    public record Membership(UUID teamId, String teamName, TeamRole teamRole) {
    }
}
