package ch.stvbero.platform.kern.team;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "Team")
public record TeamDto(UUID id, String name, Integer suTeamId, Funktion meineFunktion) {

    /** Team mit dem Namen, den es in dieser Saison trägt (SAI-02). */
    public static TeamDto von(TeamSaison ts, Funktion meineFunktion) {
        return new TeamDto(ts.team.id, ts.name, ts.team.suTeamId, meineFunktion);
    }
}
