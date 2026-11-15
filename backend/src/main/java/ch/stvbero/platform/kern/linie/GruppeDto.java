package ch.stvbero.platform.kern.linie;

import ch.stvbero.platform.kern.team.GruppenArt;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "Gruppe")
public record GruppeDto(UUID id, GruppenArt art, String name, Integer reihenfolge) {

    public static GruppeDto von(Gruppe g) {
        return new GruppeDto(g.id, g.art, g.name, g.reihenfolge);
    }
}
