package ch.stvbero.platform.kern.team;

import ch.stvbero.platform.kern.person.PersonKurz;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "Teamzugehoerigkeit")
public record TeamzugehoerigkeitDto(TeamName team, PersonKurz person, Funktion funktion, UUID saisonId, UUID gruppeId) {

    public record TeamName(UUID id, String name) {
    }

    /**
     * Teamname aus der Saison der Zugehörigkeit, damit das Archiv den damaligen Namen zeigt (SAI-02).
     * {@code gruppeId}: Linie oder Goalies, {@code null} = ohne Linie (LIN-02: höchstens eine).
     */
    public static TeamzugehoerigkeitDto von(Teamzugehoerigkeit z) {
        UUID gruppeId = z.gruppen.stream().map(g -> g.id).findFirst().orElse(null);
        return new TeamzugehoerigkeitDto(new TeamName(z.team.id, z.team.nameIn(z.saison)), PersonKurz.von(z.person),
                z.funktion, z.saison.id, gruppeId);
    }
}
