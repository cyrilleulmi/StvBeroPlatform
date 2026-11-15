package ch.stvbero.platform.kern.ich;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.UUID;

/** Teamzugehörigkeit. Teams und Zugehörigkeiten kommen mit F-01; bis dahin ist die Liste leer. */
@Schema(name = "Teamzugehoerigkeit")
public record TeamzugehoerigkeitDto(TeamKurz team, PersonKurz person, Funktion funktion, UUID saisonId, UUID gruppeId) {

    public record TeamKurz(UUID id, String name) {
    }

    @Schema(name = "PersonKurz")
    public record PersonKurz(UUID id, String vorname, String nachname) {
    }

    @Schema(name = "Funktion")
    public enum Funktion {
        SPIELERIN, COACH
    }
}
