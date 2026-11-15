package ch.stvbero.platform.kern.team;

import ch.stvbero.platform.kern.person.PersonKurz;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/** Team mit Coaches und Spielerinnen. Coaches stehen nie bei den Spielerinnen (PTF-07). */
@Schema(name = "TeamDetail")
public record TeamDetailDto(UUID id, String name, Integer suTeamId, Funktion meineFunktion,
                            List<PersonKurz> coaches, Gruppeneinteilung einteilung) {

    @Schema(name = "Gruppeneinteilung")
    public record Gruppeneinteilung(List<GruppeMitSpielerinnen> gruppen, List<PersonKurz> ohneLinie) {
    }

    public record GruppeMitSpielerinnen(UUID id, GruppenArt art, String name, Integer reihenfolge,
                                        List<PersonKurz> spielerinnen) {
    }
}
