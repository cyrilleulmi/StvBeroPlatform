package ch.stvbero.platform.kern.termin;

import ch.stvbero.platform.kern.person.PersonKurz;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * Antwort einer Person auf einen Termin. Ohne Antwort: Status OFFEN. Felder, die jemand nicht sehen darf
 * (Kommentar, ANM-07), fehlen in der Antwort der API.
 */
@Schema(name = "Antwort")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AntwortDto(AntwortStatus status, String kommentar, OffsetDateTime geaendertAm,
                         PersonKurz eingetragenVon, boolean kurzfristig) {

    public static final AntwortDto OFFEN = new AntwortDto(AntwortStatus.OFFEN, null, null, null, false);
}
