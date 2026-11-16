package ch.stvbero.platform.kern.termin;

import ch.stvbero.platform.kern.person.PersonKurz;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.OffsetDateTime;

/** Antwort einer Person auf einen Termin. Ohne Antwort: Status OFFEN. */
@Schema(name = "Antwort")
public record AntwortDto(AntwortStatus status, String kommentar, OffsetDateTime geaendertAm,
                         PersonKurz eingetragenVon, boolean kurzfristig) {

    public static final AntwortDto OFFEN = new AntwortDto(AntwortStatus.OFFEN, null, null, null, false);
}
