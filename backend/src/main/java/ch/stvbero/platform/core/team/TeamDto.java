package ch.stvbero.platform.core.team;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "Team")
public record TeamDto(UUID id, String name, Integer suTeamId, TeamRole myTeamRole) {

    /** Team with the name it has in this season (SAI-02). */
    public static TeamDto of(TeamSeason ts, TeamRole myTeamRole) {
        return new TeamDto(ts.team.id, ts.name, ts.team.suTeamIdIn(ts.season), myTeamRole);
    }

    /** As {@link #of(TeamSeason, TeamRole)}, with the team ID at swiss unihockey already looked up. */
    public static TeamDto of(TeamSeason ts, TeamRole myTeamRole, Integer suTeamId) {
        return new TeamDto(ts.team.id, ts.name, suTeamId, myTeamRole);
    }
}
