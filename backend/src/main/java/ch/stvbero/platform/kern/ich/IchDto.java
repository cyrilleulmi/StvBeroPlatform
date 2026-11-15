package ch.stvbero.platform.kern.ich;

import ch.stvbero.platform.kern.saison.SaisonDto;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(name = "Ich")
public record IchDto(UUID id, String vorname, String nachname, String email, boolean admin,
                     SaisonDto saison, List<TeamzugehoerigkeitDto> zugehoerigkeiten) {
}
