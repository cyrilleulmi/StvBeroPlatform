package ch.stvbero.platform.kern.termin;

import ch.stvbero.platform.kern.team.TeamDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Eintrag in der Terminliste, mit eigener Antwort (TER-13). */
@Schema(name = "TerminKurz")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TerminKurzDto(UUID id, TerminTyp typ, String titel, OffsetDateTime beginn, OffsetDateTime ende,
                            TerminStatus status, String absagegrund, boolean verschoben, List<TeamDto> teams,
                            OrtDto ort, OffsetDateTime antwortfrist, AntwortDto meineAntwort, ZaehlerDto zaehler,
                            UUID serieId) {
}
