package ch.stvbero.platform.kern.team;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "Team")
public record TeamDto(UUID id, String name, Integer suTeamId, Funktion meineFunktion) {

    public static TeamDto von(Team t, Funktion meineFunktion) {
        return new TeamDto(t.id, t.name, t.suTeamId, meineFunktion);
    }
}
