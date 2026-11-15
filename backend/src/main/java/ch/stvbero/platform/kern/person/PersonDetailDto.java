package ch.stvbero.platform.kern.person;

import ch.stvbero.platform.kern.team.TeamzugehoerigkeitDto;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(name = "PersonDetail")
public record PersonDetailDto(UUID id, String vorname, String nachname, String email, boolean admin,
                              List<TeamzugehoerigkeitDto> zugehoerigkeiten) {
}
