package ch.stvbero.platform.kern.team;

import ch.stvbero.platform.kern.person.PersonKurz;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "Teamzugehoerigkeit")
public record TeamzugehoerigkeitDto(TeamName team, PersonKurz person, Funktion funktion, UUID saisonId, UUID gruppeId) {

    public record TeamName(UUID id, String name) {
    }

    /** Linien (gruppeId) kommen mit F-02; bis dahin immer ohne Gruppe. */
    public static TeamzugehoerigkeitDto von(Teamzugehoerigkeit z) {
        return new TeamzugehoerigkeitDto(new TeamName(z.team.id, z.team.name), PersonKurz.von(z.person),
                z.funktion, z.saison.id, null);
    }
}
