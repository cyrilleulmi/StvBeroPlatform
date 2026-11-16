package ch.stvbero.platform.core.event;

import ch.stvbero.platform.core.person.PersonSummary;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * Antwort einer Person auf einen Termin. Ohne Antwort: Status OFFEN. Felder, die jemand nicht sehen darf
 * (Kommentar, ANM-07), fehlen in der Antwort der API.
 */
@Schema(name = "Response")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResponseDto(ResponseStatus status, String comment, OffsetDateTime changedAt,
                         PersonSummary enteredBy, boolean lateCancellation) {

    public static final ResponseDto PENDING = new ResponseDto(ResponseStatus.PENDING, null, null, null, false);
}
